package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.net.URLClassLoader
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.*

class LegacyCompatibilityTest {
    private var seq = 50000L
    private fun allocate() = OpaqueId(22, seq++)
    private val ops = SourceOperations(::allocate)
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val seed = PublicM3u.catalog(PublicM3u.parse(text), IdentitySupply(List(411) { ChannelId(OpaqueId(1, it.toLong())) }, List(411) { MembershipId(OpaqueId(2, it.toLong())) }, List(411) { CategoryId(OpaqueId(3, it.toLong())) }))
    private val context = PilotContext(VerifiedDefinitionTransition.compatibilitySession, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text))
    private val manifest = ApprovedR21.manifest
    private val ids = manifest.entries.map { e -> seed.single { it.definition == e.old }.id }
    private fun base(): PreviewData {
        val data = PreviewData(channels = seed, profileId = PublicM3u.profile, favorites = ids, history = ids.reversed(),
            hidden = setOf(seed.last().id), categories = seed.flatMap { it.groups }.distinct().reversed(), hiddenCategories = setOf("Spor"))
            .withPref("lastChannel", PreviewCodec.id(ids.last()))
        return data.copy(library = ops.bootstrap(data).copy(records = listOf(context.seedSource)))
    }
    private fun apply(data: PreviewData) = VerifiedDefinitionTransition.apply(data, context, manifest, ::allocate)
    private fun exit(data: PreviewData) = VerifiedDefinitionTransition.legacyExit(data, context, manifest)
    private fun relations(data: PreviewData) = data.copy(channels = emptyList(), library = null)
    private fun row(data: PreviewData) = arrayOf(LibraryCodec.encode(data), PreviewCodec.pack(data.favorites.map(PreviewCodec::id)),
        PreviewCodec.pack(data.hidden.map(PreviewCodec::id)), PreviewCodec.pack(data.history.map(PreviewCodec::id)), PreviewCodec.pack(data.categories),
        PreviewCodec.pack(data.hiddenCategories.toList()), PreviewCodec.pack(data.preferences.flatMap { listOf(it.key, it.value) }))
    private fun oldRead(data: PreviewData): Array<String> {
        val out = listOf(File("../../outputs/R22-Eski-Zarf-Uyumluluk-Pilotu-2026-10-14"), File("../outputs/R22-Eski-Zarf-Uyumluluk-Pilotu-2026-10-14")).first { it.isDirectory }
        val jar = File(out, "r19b-reader.jar")
        val hash = MessageDigest.getInstance("SHA-256").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
        assertTrue(File(out, "old-reader-binding.json").readText().contains(hash))
        val filtered = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("com.omer.belestiviclone.shared.")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        return URLClassLoader(arrayOf(jar.toURI().toURL()), filtered).use { loader ->
            val type = loader.loadClass("com.omer.belestiviclone.shared.evidence.R19Bridge")
            assertSame(loader, type.classLoader)
            assertSame(loader, loader.loadClass("com.omer.belestiviclone.shared.preview.LibraryCodec").classLoader)
            @Suppress("UNCHECKED_CAST")
            (type.getMethod("read", Array<String>::class.java).invoke(null, row(data) as Any) as Array<String>)
        }
    }
    @Test fun appliedAndRolledbackExitAreLosslessSources1WithSameIdsAndRelations() {
        val initial = base(); val applied = apply(initial)
        listOf(applied, VerifiedDefinitionTransition.rollback(applied, context, manifest)).forEach { before ->
            val after = exit(before)
            assertNull(after.library!!.transition)
            assertEquals("BT-SOURCES-1", PreviewCodec.unpack(LibraryCodec.encode(after)).first())
            assertEquals(initial, after)
            assertEquals(relations(before), relations(after))
        }
    }
    @Test fun actualOldReaderDecodesEntireExitRowAndMetadataCounts() {
        val after = exit(apply(base())); val actual = oldRead(after)
        assertEquals(row(after).toList(), actual.take(7))
        assertEquals(listOf("408", after.library!!.snapshot.memberships.size.toString(), after.library!!.snapshot.categories.size.toString()), actual.drop(7))
    }
    @Test fun actualOldReaderV2SilentFallbackIsACounterexampleNotSuccess() {
        val data = apply(base()); val actual = oldRead(data)
        assertNotEquals(row(data)[0], actual[0])
        assertEquals("0", actual[7]); assertEquals("0", actual[8])
    }
    @Test fun interveningFavoritesOrderHistoryPrivacyLastChannelAndPreferencesSurvive() {
        val before = apply(base()).copy(favorites = ids.reversed(), history = ids + seed.last().id, hiddenCategories = setOf("Haber"),
            hidden = setOf(seed.first().id), categories = listOf("Spor", "Haber"))
            .withPref("format", "Fit").withPref("history", "Kapalı").withPref("lastChannel", PreviewCodec.id(ids.first()))
        val after = exit(before)
        assertEquals(relations(before), relations(after))
        assertEquals(before.library!!.snapshot.memberships, after.library!!.snapshot.memberships)
        assertEquals(before.library!!.snapshot.categories, after.library!!.snapshot.categories)
        assertEquals(before.library!!.snapshot.channels.map { it.id to it.firstSeen }, after.library!!.snapshot.channels.map { it.id to it.firstSeen })
        assertEquals(row(after).toList(), oldRead(after).take(7))
    }
    @Test fun noOpReopenDefaultRebuildAndExplicitReapplyKeepIdentity() {
        val after = exit(apply(base())); assertSame(after, exit(after))
        val reopen = LibraryCodec.decode(LibraryCodec.encode(after), after.copy(channels = emptyList(), library = null))
        assertEquals(after, reopen); assertSame(reopen, exit(reopen))
        assertEquals(after, ops.rebuild(reopen, reopen.library!!))
        assertEquals(after, ops.default(reopen, text))
        val reapply = apply(reopen)
        assertTrue(reapply.library!!.transition!!.applied)
        assertEquals(after.channels.map { it.id }, reapply.channels.map { it.id })
        assertEquals(relations(after), relations(reapply))
    }
    @Test fun invalidSecondTargetSourceMembershipOrCollisionRejectsEntirePreparation() {
        val before = apply(base()); val lib = before.library!!
        val other = SourceId(OpaqueId(9, 9))
        val variants = listOf(
            before.copy(channels = before.channels.filter { it.id != ids.last() }),
            before.copy(channels = before.channels.map { if (it.id == ids.last()) it.copy(definition = manifest.entries.last().old) else it }),
            before.copy(library = lib.copy(records = listOf(lib.main.copy(name = "edited")))),
            before.copy(library = lib.copy(records = emptyList())),
            before.copy(library = lib.copy(snapshot = lib.snapshot.copy(memberships = lib.snapshot.memberships.filter { it.channelId.value != ids.last() }))),
            before.copy(library = lib.copy(snapshot = lib.snapshot.copy(sources = emptyList()))),
            before.copy(library = lib.copy(snapshot = lib.snapshot.copy(channels = lib.snapshot.channels + ChannelRecord(ChannelId(OpaqueId(9, 8)), manifest.entries.last().old, 99)))),
            before.copy(library = lib.copy(records = lib.records + lib.main.copy(id = other, rows = listOf(lib.main.rows.first { it.definition == manifest.entries.last().new }).map { it.copy(sourceId = other) })))
        )
        variants.forEach { data -> val bytes = LibraryCodec.encode(data); assertFailsWith<TransitionProblem> { exit(data) }; assertEquals(bytes, LibraryCodec.encode(data)) }
    }
    @Test fun tamperedJournalBindingAndMissingJournalOnNewDefinitionsFailClosed() {
        val data = apply(base()); val lib = data.library!!; val j = lib.transition!!
        listOf(j.copy(sourceBefore = j.sourceBefore.copy(name = "edited")), j.copy(targets = j.targets.reversed()),
            j.copy(manifest = manifest.copy(session = context.session))).forEach {
            assertFailsWith<TransitionProblem> { exit(data.copy(library = lib.copy(transition = it))) }
        }
        assertFailsWith<TransitionProblem> { exit(data.copy(library = lib.copy(transition = null))) }
        assertFailsWith<TransitionProblem> { exit(data.copy(library = null)) }
    }
    @Test fun changedUiSourceReferenceRejectsBothAppliedAndRolledbackExits() {
        val applied = apply(base())
        listOf(applied, VerifiedDefinitionTransition.rollback(applied, context, manifest)).forEach { before ->
            val changed = before.copy(channels = before.channels.map { if (it.id == ids.last()) it.copy(sourceReferences = listOf(OpaqueId(9, 9))) else it })
            val bytes = LibraryCodec.encode(changed)
            assertFailsWith<TransitionProblem> { exit(changed) }
            assertEquals(bytes, LibraryCodec.encode(changed))
        }
    }
    @Test fun exactSessionDebugActionAndDatabaseGateNegatives() {
        listOf(null, "none", VerifiedDefinitionTransition.session, "toplu-r22-other", "toplu-r21-media-20261014").forEach {
            listOf("apply", "rollback", "legacy-exit").forEach { action -> assertNull(r22PilotAction(true, it, action)) }
        }
        assertNull(r22PilotAction(false, context.session, "legacy-exit")); assertNull(r22PilotAction(true, context.session, null))
        assertNull(r22PilotAction(true, context.session, "unknown"))
        listOf("apply", "rollback", "legacy-exit").forEach { assertEquals(it, r22PilotAction(true, context.session, it)) }
        assertNull(r21PilotAction(true, context.session, "apply"))
        assertTrue(pilotStoreAllowed(2, "${context.session}.db", context.session))
        listOf("belestivi_preview_v1.db", "${VerifiedDefinitionTransition.session}.db", "toplu-r22-other.db").forEach { assertFalse(pilotStoreAllowed(2, it, context.session)) }
        assertFalse(pilotStoreAllowed(1, "${context.session}.db", context.session))
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.legacyExit(apply(base()), context.copy(session = VerifiedDefinitionTransition.session), manifest) }
        assertFailsWith<TransitionProblem> { exit(apply(base()).copy(profileId = PreviewData().profileId)) }
        assertEquals(VerifiedDefinitionTransition.session, manifest.session)
    }
    @Test fun staleFullRowIncludingInterveningPreferencesRejectsPreparedExit() = runBlocking {
        val before = apply(base()); val expected = row(before).toList(); val next = row(exit(before)).toList()
        var current = row(before.withPref("opacity", "31")).toList()
        val untouched = current
        assertFailsWith<TransitionProblem> { commitTransition(expected, next) { old, update ->
            if (current != old) false else { current = update; true }
        } }
        assertEquals(untouched, current)
    }
    @Test fun cancellationAfterActualPreparationNeverEntersCas() = runBlocking {
        val before = apply(base()); val expected = row(before).toList()
        val ready = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>(); var writes = 0
        val job = launch {
            val next = row(exit(before)).toList(); ready.complete(Unit); proceed.await()
            commitTransition(expected, next) { _, _ -> writes++; true }
        }
        ready.await(); job.cancel(); proceed.complete(Unit); job.join(); assertEquals(0, writes)
    }
}
