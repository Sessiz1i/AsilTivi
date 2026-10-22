package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.*

class VerifiedDefinitionTransitionTest {
    private var sequence = 10000L
    private fun allocate() = OpaqueId(99, sequence++)
    private val ops = SourceOperations(::allocate)
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val parsed = PublicM3u.parse(text)
    private val seed = PublicM3u.catalog(parsed, IdentitySupply(List(411) { ChannelId(OpaqueId(1, it.toLong())) }, List(411) { MembershipId(OpaqueId(2, it.toLong())) }, List(411) { CategoryId(OpaqueId(3, it.toLong())) }))
    private val context = PilotContext(VerifiedDefinitionTransition.session, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text))
    private val manifest = ApprovedR21.manifest
    private val targets = manifest.entries.map { e -> seed.single { it.definition == e.old }.id }
    private val legacy = PreviewData(channels = seed, profileId = PublicM3u.profile, favorites = targets.reversed(), history = targets,
        hidden = setOf(seed.last().id), categories = seed.flatMap { it.groups }.distinct().reversed(), hiddenCategories = setOf("Spor"))
        .withPref("lastChannel", PreviewCodec.id(targets.last()))
    private fun base() = legacy.copy(library = ops.bootstrap(legacy).copy(records = listOf(context.seedSource)))
    private fun apply(data: PreviewData = base()) = VerifiedDefinitionTransition.apply(data, context, manifest, ::allocate)
    private fun rollback(data: PreviewData) = VerifiedDefinitionTransition.rollback(data, context, manifest)
    private fun relations(data: PreviewData) = data.copy(channels = emptyList(), library = null)
    private fun rejected(data: PreviewData, code: String? = null) {
        val bytes = LibraryCodec.encode(data)
        val error = assertFailsWith<TransitionProblem> { apply(data) }
        if (code != null) assertEquals(code, error.code)
        assertEquals(bytes, LibraryCodec.encode(data))
    }
    @Test fun bothDefinitionsAndAllExistingIdsRelationsAndFirstSeenSurvive() {
        val old = base(); val next = apply(old)
        assertEquals(relations(old), relations(next))
        assertEquals(old.channels.map { it.id }, next.channels.map { it.id })
        assertEquals(old.library!!.snapshot.channels.map { it.id to it.firstSeen }, next.library!!.snapshot.channels.map { it.id to it.firstSeen })
        assertEquals(old.library!!.snapshot.categories, next.library!!.snapshot.categories)
        assertEquals(old.library!!.snapshot.memberships, next.library!!.snapshot.memberships)
        assertEquals(old.library!!.snapshot.sources, next.library!!.snapshot.sources)
        assertEquals(2, old.channels.zip(next.channels).count { it.first != it.second })
        next.library!!.transition!!.targets.forEach { t ->
            assertEquals(t.entry.new, next.channels.single { it.id == t.id }.definition)
            assertEquals(t.entry.new, next.library!!.snapshot.channels.single { it.id.value == t.id }.definition)
            assertTrue(next.library!!.main.rows.any { it.definition == t.entry.new })
            assertFalse(next.library!!.main.rows.any { it.definition == t.entry.old })
        }
    }
    @Test fun legacyUsesExactSameCacheIdsWithoutNewChannelAllocation() {
        val next = VerifiedDefinitionTransition.apply(legacy, context, manifest, ::allocate)
        assertEquals(legacy.channels.map { it.id }, next.channels.map { it.id })
        assertEquals(relations(legacy), relations(next)); assertNotNull(next.library)
    }
    @Test fun legacyWithoutCompleteSameProfileCacheProofFails() {
        rejected(legacy.copy(channels = seed.dropLast(1)), "unproven-legacy")
        rejected(legacy.copy(channels = seed.mapIndexed { i, ch -> if (i == 50) ch.copy(id = OpaqueId(4, 5)) else ch }), "unproven-legacy")
    }
    @Test fun realAndOtherProfilesAreDisabled() {
        assertEquals("disabled-profile", assertFailsWith<TransitionProblem> {
            VerifiedDefinitionTransition.apply(base(), context.copy(session = "none"), manifest, ::allocate)
        }.code)
        rejected(base().copy(profileId = PreviewData().profileId), "disabled-profile")
    }
    @Test fun normalReleaseAndWrongProfilesNeverReceiveManifestEvenWithAction() {
        listOf(null, "none", "toplu-r19b-20261014", "toplu-r21-media-20261014").forEach {
            assertNull(r21PilotAction(true, it, "apply")); assertNull(r21PilotAction(true, it, "rollback"))
        }
        assertNull(r21PilotAction(false, context.session, "apply"))
        assertNull(r21PilotAction(true, context.session, "unknown"))
        assertNull(r21PilotAction(true, context.session, null))
        assertEquals("apply", r21PilotAction(true, context.session, "apply"))
        assertEquals("rollback", r21PilotAction(true, context.session, "rollback"))
    }
    @Test fun versionDigestAndSnapshotAreMandatory() {
        listOf(manifest.copy(version = "2"), manifest.copy(digest = "wrong"), manifest.copy(seedSha256 = "wrong"),
            manifest.copy(entries = manifest.entries.take(1)), manifest.copy(entries = manifest.entries + manifest.entries.first())).forEach {
            assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.apply(base(), context, it, ::allocate) }
        }
    }
    @Test fun urlFileWrongSourceAndEditedSnapshotFailAsWholePilot() {
        val b = base(); val lib = b.library!!
        listOf(lib.main.copy(kind = SourceKind.FILE), lib.main.copy(kind = SourceKind.URL), lib.main.copy(address = "https://other.test/list"),
            lib.main.copy(rows = lib.main.rows.dropLast(1))).forEach { rejected(b.copy(library = lib.copy(records = listOf(it)))) }
    }
    @Test fun oneMissingTargetLeavesBothDefinitionsUnchanged() {
        val b = base(); val lib = b.library!!
        rejected(b.copy(library = lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.filter { it.id.value != targets.last() }))), "ambiguous-old")
    }
    @Test fun ambiguousOldDefinitionIsNeverFirstMatched() {
        val b = base(); val lib = b.library!!; val old = lib.snapshot.channels.single { it.id.value == targets.first() }
        rejected(b.copy(library = lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels + old.copy(id = ChannelId(OpaqueId(7, 7)))))), "ambiguous-old")
    }
    @Test fun newDefinitionCollisionRejectsBoth() {
        val b = base(); val lib = b.library!!
        rejected(b.copy(library = lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels + ChannelRecord(ChannelId(OpaqueId(7, 7)), manifest.entries.last().new, 999)))), "new-collision")
    }
    @Test fun passiveOrSupplementSharedTargetRejectsBoth() {
        val b = base(); val lib = b.library!!
        val extra = lib.main.copy(id = SourceId(OpaqueId(8, 8)), kind = SourceKind.URL, rows = listOf(lib.main.rows.first { it.definition == manifest.entries.first().old }).map { it.copy(sourceId = SourceId(OpaqueId(8, 8))) })
        rejected(b.copy(library = lib.copy(records = lib.records + extra)), "shared-target")
        val membership = lib.snapshot.memberships.first { it.channelId.value == targets.first() }.copy(id = MembershipId(OpaqueId(8, 9)), sourceId = extra.id, isCurrent = false)
        rejected(b.copy(library = lib.copy(snapshot = lib.snapshot.copy(memberships = lib.snapshot.memberships + membership))), "shared-membership")
    }
    @Test fun unsupportedDrmScopeOptionsAndLossFlagAreNeverSilentlyEncoded() {
        val old = manifest.entries.first().old
        listOf(old.copy(drm = DrmDefinition("widevine")), old.copy(protocol = "hls"), old.copy(authorityScope = "scope"),
            old.copy(options = listOf(NamedPlaybackOption("x", "y"))), old.copy(isLossless = false)).forEach { d ->
            assertFalse(VerifiedDefinitionTransition.portable(d))
            assertFailsWith<IllegalArgumentException> { LibraryCodec.channels(listOf(seed.first().copy(definition = d))) }
        }
    }
    @Test fun secondApplyIsByteIdenticalNoOpAndReopenKeepsJournal() {
        val next = apply(); assertSame(next, apply(next))
        val reopened = LibraryCodec.decode(LibraryCodec.encode(next), next.copy(channels = seed, library = null))
        assertEquals(next, reopened); assertSame(reopened, apply(reopened))
    }
    @Test fun oldProfileAndSourceEnvelopesRemainReadableUnknownVersionsFailClosed() {
        assertEquals(legacy, LibraryCodec.decode(LibraryCodec.encode(legacy), legacy.copy(channels = emptyList())))
        val b = base(); assertEquals(b, LibraryCodec.decode(LibraryCodec.encode(b), b.copy(library = null)))
        assertFailsWith<IllegalArgumentException> { LibraryCodec.decode(PreviewCodec.pack(listOf("BT-SOURCES-99")), legacy) }
    }
    @Test fun defaultAndRebuildKeepUpdatedDefinitionsAndOpaqueIds() {
        val next = apply(); val rebuilt = ops.rebuild(next, next.library!!)
        val default = ops.default(next, text)
        assertEquals(next.channels.map { it.id to it.definition }, rebuilt.channels.map { it.id to it.definition })
        assertEquals(next.channels.map { it.id to it.definition }, default.channels.map { it.id to it.definition })
        assertEquals(next.library!!.snapshot.memberships, default.library!!.snapshot.memberships)
        assertSame(default, apply(default))
    }
    @Test fun rollbackOnlyChangesTargetsAndJournalPreservingInterveningRelations() {
        val next = apply().withPref("opacity", "49").withPref("lastChannel", PreviewCodec.id(seed.last().id)).copy(favorites = targets, history = targets.reversed(), hiddenCategories = setOf("Ulusal"))
        val reverted = rollback(next)
        assertEquals(relations(next), relations(reverted))
        assertEquals(seed.map { it.id to it.definition }, reverted.channels.map { it.id to it.definition })
        assertFalse(reverted.library!!.transition!!.applied)
        assertSame(reverted, rollback(reverted))
        assertEquals(reverted, LibraryCodec.decode(LibraryCodec.encode(reverted), reverted.copy(library = null)))
        assertEquals(seed.map { it.id to it.definition }, ops.default(reverted, text).channels.map { it.id to it.definition })
        assertTrue(apply(reverted).library!!.transition!!.applied)
    }
    @Test fun rollbackRejectsChangedDefinitionSourceOrMembership() {
        val next = apply(); val lib = next.library!!
        val changedMemberships = lib.snapshot.copy(memberships = lib.snapshot.memberships.filter { it.channelId.value != targets.first() })
        listOf(next.copy(channels = next.channels.map { if (it.id == targets.first()) it.copy(definition = manifest.entries.first().old) else it }),
            next.copy(library = lib.copy(records = listOf(lib.main.copy(name = "edited")))) ,
            next.copy(library = lib.copy(snapshot = changedMemberships))).forEach {
            assertFailsWith<TransitionProblem> { rollback(it) }
        }
    }
    @Test fun staleExpectedRevisionDoesNotWrite() = runBlocking {
        var writes = 0
        val error = assertFailsWith<TransitionProblem> { commitTransition("old", "new") { _, _ -> writes++; false } }
        assertEquals("stale-row", error.code); assertEquals(1, writes)
    }
    @Test fun cancellationDuringPreparationNeverEntersCommit() = runBlocking {
        var writes = 0
        val job = launch(start = CoroutineStart.LAZY) { commitTransition("old", "new") { _, _ -> writes++; true } }
        job.cancel(); job.join(); assertEquals(0, writes)
    }
    @Test fun commitFinishesOnceEvenWhenCallerIsCancelled() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>(); var value = "old"
        val job = launch { commitTransition("old", "new") { _, new -> entered.complete(Unit); proceed.await(); value = new; true } }
        entered.await(); job.cancel(); proceed.complete(Unit); job.join(); assertEquals("new", value)
    }
}
