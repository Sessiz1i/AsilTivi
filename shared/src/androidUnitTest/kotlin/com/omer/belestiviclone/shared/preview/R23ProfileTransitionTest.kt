package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import kotlin.test.*

class R23ProfileTransitionTest {
    private var sequence = 20000L
    private fun allocate() = OpaqueId(99, sequence++)
    private val ops = SourceOperations(::allocate)
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val parsed = PublicM3u.parse(text)
    private val seed = PublicM3u.catalog(parsed, IdentitySupply(List(411) { ChannelId(OpaqueId(1, it.toLong())) }, List(411) { MembershipId(OpaqueId(2, it.toLong())) }, List(411) { CategoryId(OpaqueId(3, it.toLong())) }))
    private val context = PilotContext(R23ProfileTransition.normalSession, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text))
    private val admission = R23Admission(true, 2, R23ProfileTransition.normalDatabase, context.session, "apply")
    private val manifest = ApprovedR21.manifest
    private val ids = manifest.entries.map { e -> seed.single { it.definition == e.old }.id }
    private val legacy = PreviewData(channels = seed, profileId = PublicM3u.profile, sources = listOf(R23ProfileTransition.defaultLabel),
        favorites = ids.reversed(), history = ids, hidden = setOf(seed.last().id), hiddenCategories = setOf("Spor"), categories = seed.flatMap { it.groups }.distinct().reversed())
        .withPref("lastChannel", PreviewCodec.id(ids.last()))
    private fun base() = legacy.copy(library = ops.bootstrap(legacy).copy(records = listOf(context.seedSource)))
    private fun prepare(data: PreviewData = base(), a: R23Admission = admission, c: PilotContext = context, m: TransitionManifest = manifest) =
        R23ProfileTransition.prepare(data, c, a, m, ::allocate)
    private fun rejected(data: PreviewData = base(), a: R23Admission = admission, c: PilotContext = context, m: TransitionManifest = manifest) {
        val old = LibraryCodec.encode(data)
        assertFailsWith<TransitionProblem> { prepare(data, a, c, m) }
        assertEquals(old, LibraryCodec.encode(data))
    }
    @Test fun exactNormalAndTestGatesAreSeparateFromOldPilots() {
        listOf("preflight", "apply", "rollback", "legacy-exit").forEach { action ->
            assertTrue(R23ProfileTransition.allowed(admission.copy(action = action)))
            assertTrue(R23ProfileTransition.allowed(admission.copy(session = R23ProfileTransition.testSession, databaseName = "${R23ProfileTransition.testSession}.db", action = action)))
        }
        assertFalse(pilotStoreAllowed(2, admission.databaseName, context.session))
        assertNull(r21PilotAction(true, context.session, "apply")); assertNull(r22PilotAction(true, context.session, "apply"))
    }
    @Test fun releaseMissingActionKey1WrongDbAndOtherSessionsAreClosed() {
        listOf(admission.copy(debug = false), admission.copy(key = 1), admission.copy(action = null), admission.copy(action = "inspect"),
            admission.copy(databaseName = "other.db"), admission.copy(session = null), admission.copy(session = VerifiedDefinitionTransition.session),
            admission.copy(session = VerifiedDefinitionTransition.compatibilitySession), admission.copy(session = R23ProfileTransition.testSession),
            admission.copy(databaseName = "${R23ProfileTransition.testSession}.db")).forEach { assertFalse(R23ProfileTransition.allowed(it)); rejected(a = it) }
    }
    @Test fun arbitraryManifestSessionAndSeedAreRejected() {
        rejected(m = manifest.copy(digest = "arbitrary")); rejected(m = manifest.copy(entries = manifest.entries.reversed()))
        rejected(c = context.copy(session = R23ProfileTransition.testSession)); rejected(c = context.copy(seedSha256 = "wrong"))
    }
    @Test fun bothIdsAllRelationsAnd406OtherDefinitionsAreUnchanged() {
        val old = base(); val next = prepare(old)
        assertEquals(old.copy(channels = next.channels, library = next.library), next)
        assertEquals(old.channels.map { it.id }, next.channels.map { it.id })
        assertEquals(old.channels.filter { it.id !in ids }, next.channels.filter { it.id !in ids })
        assertEquals(406, next.channels.count { it.id !in ids })
        assertEquals(old.library!!.snapshot.memberships, next.library!!.snapshot.memberships)
        assertEquals(old.library!!.snapshot.categories, next.library!!.snapshot.categories)
        assertEquals(old.library!!.snapshot.channels.map { it.id to it.firstSeen }, next.library!!.snapshot.channels.map { it.id to it.firstSeen })
        assertEquals(2, old.channels.zip(next.channels).count { it.first != it.second })
    }
    @Test fun oldCacheTargetIdMismatchEvenWithValidSnapshotIsClosed() {
        val ch = seed.single { it.id == ids.last() }
        rejected(c = context.copy(seed = seed.map { if (it.id == ch.id) it.copy(id = OpaqueId(9, 9)) else it }))
        rejected(c = context.copy(seed = seed.dropLast(1)))
        rejected(c = context.copy(seed = seed.mapIndexed { i, value -> if (i == 10) value.copy(id = seed.first().id) else value }))
    }
    @Test fun legacyRequiresPersistedCompleteOldChannelsAndExactDefaultProof() {
        val next = prepare(legacy)
        assertEquals(legacy.channels.map { it.id }, next.channels.map { it.id })
        assertNotNull(next.library)
        assertEquals(legacy.favorites, next.favorites); assertEquals(legacy.preferences, next.preferences)
        rejected(legacy.copy(sources = listOf("personal-file"))); rejected(legacy.copy(channels = seed.dropLast(1)))
        rejected(legacy.copy(profileId = PreviewData().profileId))
    }
    @Test fun urlFileSupplementWrongRoleAndMissingSecondTargetRejectBoth() {
        val old = base(); val lib = old.library!!
        listOf(lib.copy(records = listOf(lib.main.copy(kind = SourceKind.URL))), lib.copy(records = listOf(lib.main.copy(kind = SourceKind.FILE))),
            lib.copy(records = listOf(lib.main.copy(parent = SourceId(OpaqueId(7, 7))))), lib.copy(snapshot = lib.snapshot.copy(sources = emptyList())),
            lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.filter { it.id.value != ids.last() }))).forEach { rejected(old.copy(library = it)) }
    }
    @Test fun sharedAndNewCollisionOrDuplicateOldTargetRejectBoth() {
        val old = base(); val lib = old.library!!
        val extra = lib.main.copy(id = SourceId(OpaqueId(8, 8)), rows = listOf(lib.main.rows.first { it.definition == manifest.entries.last().old }))
        rejected(old.copy(library = lib.copy(records = lib.records + extra)))
        listOf(manifest.entries.last().old, manifest.entries.last().new).forEach { d ->
            rejected(old.copy(library = lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels + ChannelRecord(ChannelId(OpaqueId(8, 9)), d, 999)))))
        }
    }
    @Test fun appliedNoOpRoundtripDefaultAndRebuildNeedNoAllocations() {
        val next = prepare()
        assertSame(next, prepare(next))
        val reopened = LibraryCodec.decode(LibraryCodec.encode(next), next.copy(library = null, channels = emptyList()))
        assertEquals(next, reopened); assertSame(reopened, prepare(reopened))
        val noAllocation = SourceOperations { error("unexpected-allocation") }
        assertEquals(next, noAllocation.default(reopened, text)); assertEquals(next, noAllocation.rebuild(reopened, reopened.library!!))
    }
    @Test fun rollbackAndAppliedOrRolledbackLegacyExitPreserveInterveningPreferences() {
        val intervening = prepare().copy(favorites = ids, history = ids.reversed()).withPref("format", "Sığdır (Fit)").withPref("lastChannel", PreviewCodec.id(seed.last().id))
        val rollback = prepare(intervening, admission.copy(action = "rollback"))
        assertFalse(rollback.library!!.transition!!.applied)
        listOf(intervening, rollback).forEach { data ->
            val exit = prepare(data, admission.copy(action = "legacy-exit"))
            assertNull(exit.library!!.transition); assertEquals("BT-SOURCES-1", PreviewCodec.unpack(LibraryCodec.encode(exit)).first())
            assertEquals(data.favorites, exit.favorites); assertEquals(data.history, exit.history); assertEquals(data.preferences, exit.preferences)
            assertEquals(seed.map { it.id to it.definition }, exit.channels.map { it.id to it.definition })
            assertSame(exit, prepare(exit, admission.copy(action = "legacy-exit")))
            assertTrue(prepare(exit).library!!.transition!!.applied)
        }
    }
    @Test fun actionlessDecodeAfterExitDoesNotReapplyAndOtherSourceIsUntouched() {
        val exited = prepare(prepare(), admission.copy(action = "legacy-exit"))
        val reopened = LibraryCodec.decode(LibraryCodec.encode(exited), exited.copy(library = null, channels = emptyList()))
        assertEquals(exited, reopened); assertNull(reopened.library!!.transition)
        assertEquals(seed.map { it.definition }, reopened.channels.map { it.definition })
    }
    @Test fun tamperedJournalSourceOrChangedTargetNeverBecomesNoOp() {
        val next = prepare(); val lib = next.library!!; val journal = lib.transition!!
        rejected(next.copy(library = lib.copy(transition = journal.copy(sourceBefore = journal.sourceBefore.copy(name = "changed")))))
        rejected(next.copy(channels = next.channels.map { if (it.id == ids.last()) it.copy(definition = manifest.entries.last().old) else it }))
    }
}
