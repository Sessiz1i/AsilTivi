package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class DefinitionTransition(val candidate: String, val old: PlaybackDefinition, val new: PlaybackDefinition)
data class TransitionManifest(val version: String, val digest: String, val session: String,
    val seedSha256: String, val entries: List<DefinitionTransition>)
data class BoundTransition(val id: OpaqueId, val entry: DefinitionTransition)
data class TransitionJournal(val manifest: TransitionManifest, val targets: List<BoundTransition>,
    val sourceBefore: SavedSource, val memberships: List<MembershipRecord>, val applied: Boolean) {
    override fun toString() = "TransitionJournal([redacted])"
}
data class PilotContext(val session: String, val seedSha256: String, val seed: List<PreviewChannel>, val seedSource: SavedSource)
class TransitionProblem(val code: String) : IllegalStateException(code)

/** No manifest is supplied to normal, release, another round, or the media-only profile. */
fun r21PilotAction(debug: Boolean, session: String?, action: String?): String? =
    action?.takeIf { debug && session == VerifiedDefinitionTransition.session && it in listOf("apply", "rollback") }

fun r22PilotAction(debug: Boolean, session: String?, action: String?): String? =
    action?.takeIf { debug && session == VerifiedDefinitionTransition.compatibilitySession && it in listOf("apply", "rollback", "legacy-exit") }

fun pilotStoreAllowed(key: Int, databaseName: String, session: String) = key == 2 &&
    session in setOf(VerifiedDefinitionTransition.session, VerifiedDefinitionTransition.compatibilitySession) && databaseName == "$session.db"

/** Two explicitly approved definitions. The general CatalogPlanner never participates in this exception. */
object VerifiedDefinitionTransition {
    const val session = "toplu-r21-pilot-20261014"
    const val compatibilitySession = "toplu-r22-compat-20261014"
    const val version = "R21-TV8-TRTHABER-1"
    const val seedSha256 = "ff4490b2563117b522e12f751ecf4dca936b180dd01837487fdd3ea8dd170a2c"
    private fun check(value: Boolean, code: String) { if (!value) throw TransitionProblem(code) }
    fun portable(d: PlaybackDefinition) = d.protocol == null && d.drm == null && d.authorityScope == null &&
        d.options.isEmpty() && d.unknownRequiredDirectives.isEmpty() && d.isLossless && ComparisonV1.playbackKey(d) != null
    private fun gate(data: PreviewData, context: PilotContext, manifest: TransitionManifest) {
        check(data.library?.r38c == null, "r38c-layer-protected")
        check(data.library?.r38b == null, "r38b-layer-must-rollback-first")
        check(data.library?.r33 == null, "r33-layer-must-rollback-first")
        check(data.library?.r28 == null, "r28-layer-must-rollback-first")
        check(data.library?.r26 == null, "r26-layer-must-rollback-first")
        check(context.session in setOf(session, compatibilitySession, R23ProfileTransition.normalSession, R23ProfileTransition.testSession) && manifest.session == session && data.profileId == PublicM3u.profile, "disabled-profile")
        check(context.seedSha256 == seedSha256 && manifest.seedSha256 == seedSha256 && manifest.version == version, "manifest-binding")
        check(manifest == ApprovedR21.manifest, "unapproved-manifest")
        check(manifest.entries.size == 2 && manifest.entries.map { it.candidate }.toSet() == setOf("R20-C002", "R20-C003"), "targets")
        check(manifest.entries.all { portable(it.old) && portable(it.new) }, "unsupported-definition")
    }
    fun apply(data: PreviewData, context: PilotContext, manifest: TransitionManifest, allocate: () -> OpaqueId): PreviewData {
        gate(data, context, manifest)
        data.library?.transition?.let { journal ->
            check(journal.manifest == manifest, "journal-conflict")
            validate(data, journal, journal.applied)
            if (journal.applied) return data
            // A rollback is durable; only another explicit apply request reapplies it.
            return replace(data, journal, true)
        }
        val library = data.library ?: run {
            check(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
                data.channels == context.seed, "unproven-legacy")
            SourceOperations(allocate).bootstrap(data).let { it.copy(records = listOf(context.seedSource)) }
        }
        check(library.active == PublicM3u.source, "wrong-source")
        val source = library.records.singleOrNull { it.id == PublicM3u.source } ?: throw TransitionProblem("missing-source")
        check(!source.deleted && source.kind == SourceKind.DEFAULT && source.parent == null && source.address == PublicM3u.url, "wrong-source")
        check(source == context.seedSource, "source-snapshot")
        check(library.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "source-role")
        val targets = manifest.entries.map { entry ->
            val key = ComparisonV1.playbackKey(entry.old)
            val channel = library.snapshot.channels.filter { ComparisonV1.playbackKey(it.definition) == key }.singleOrNull()
                ?: throw TransitionProblem("ambiguous-old")
            check(channel.definition == entry.old, "old-definition")
            val ui = data.channels.filter { it.id == channel.id.value }.singleOrNull() ?: throw TransitionProblem("missing-copy")
            check(ui.available && ui.definition == entry.old && ui.sourceReferences.isEmpty(), "copy-binding")
            check(source.rows.any { it.definition == entry.old }, "missing-row")
            check(library.records.filter { it.id != source.id }.none { other -> other.rows.any { ComparisonV1.playbackKey(it.definition) == key } }, "shared-target")
            check(library.snapshot.memberships.filter { it.channelId == channel.id }.all { it.sourceId == source.id }, "shared-membership")
            check(library.snapshot.memberships.any { it.channelId == channel.id && it.isCurrent }, "missing-membership")
            check(library.snapshot.channels.none { it.id != channel.id && ComparisonV1.playbackKey(it.definition) == ComparisonV1.playbackKey(entry.new) }, "new-collision")
            check(library.records.flatMap { it.rows }.none { ComparisonV1.playbackKey(it.definition) == ComparisonV1.playbackKey(entry.new) }, "new-row-collision")
            check(data.channels.none { it.id != channel.id.value && it.definition?.let(ComparisonV1::playbackKey) == ComparisonV1.playbackKey(entry.new) }, "copy-collision")
            BoundTransition(channel.id.value, entry)
        }
        check(targets.map { it.id }.distinct().size == 2, "shared-id")
        // Everything that will enter the pilot envelope must survive the existing codec losslessly.
        check(library.snapshot.channels.all { portable(it.definition) } && library.records.flatMap { it.rows }.all { portable(it.definition) } &&
            data.channels.all { it.definition?.let(::portable) != false }, "unsupported-envelope")
        val journal = TransitionJournal(manifest, targets, source,
            library.snapshot.memberships.filter { m -> targets.any { it.id == m.channelId.value } }, false)
        val base = data.copy(library = library)
        validate(base, journal, false)
        return replace(base, journal, true)
    }
    fun rollback(data: PreviewData, context: PilotContext, manifest: TransitionManifest): PreviewData {
        gate(data, context, manifest)
        val journal = data.library?.transition ?: throw TransitionProblem("missing-journal")
        check(journal.manifest == manifest, "journal-conflict")
        validate(data, journal, journal.applied)
        return if (journal.applied) replace(data, journal, false) else data
    }
    /** One prepared replacement, never a rollback commit followed by a journal-removal commit. */
    fun legacyExit(data: PreviewData, context: PilotContext, manifest: TransitionManifest): PreviewData {
        check(context.session in setOf(compatibilitySession, R23ProfileTransition.normalSession, R23ProfileTransition.testSession), "disabled-profile")
        gate(data, context, manifest)
        val library = data.library ?: throw TransitionProblem("missing-library")
        val journal = library.transition ?: run {
            // A journals-free second request must prove the same exact old snapshot and bindings.
            // Pure apply validates both targets; its temporary result is never persisted/published.
            apply(data, context, manifest) { throw TransitionProblem("unexpected-allocation") }
            return data
        }
        check(journal.manifest == manifest && journal.sourceBefore == context.seedSource, "journal-conflict")
        validate(data, journal, journal.applied)
        val reverted = if (journal.applied) replace(data, journal, false) else data
        val next = reverted.copy(library = reverted.library!!.copy(transition = null))
        check(next.library!!.snapshot.channels.all { portable(it.definition) } && next.library.records.flatMap { it.rows }.all { portable(it.definition) } &&
            next.channels.all { it.definition?.let(::portable) != false }, "unsupported-envelope")
        check(LibraryCodec.decode(LibraryCodec.encode(next), next.copy(library = null, channels = emptyList())) == next, "lossy-envelope")
        return next
    }
    private fun expectedSource(j: TransitionJournal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map { row ->
        val target = j.targets.singleOrNull { it.entry.old == row.definition }
        if (applied && target != null) row.copy(definition = target.entry.new) else row
    })
    private fun validate(data: PreviewData, j: TransitionJournal, applied: Boolean) {
        val library = data.library ?: throw TransitionProblem("missing-library")
        check(j.manifest == ApprovedR21.manifest && j.targets.map { it.entry } == j.manifest.entries && j.targets.map { it.id }.distinct().size == 2, "journal-conflict")
        check(library.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "source-role")
        check(library.active == PublicM3u.source && library.records.filter { it.id == PublicM3u.source }.singleOrNull() == expectedSource(j, applied), "source-changed")
        check(library.snapshot.memberships.filter { m -> j.targets.any { it.id == m.channelId.value } } == j.memberships, "membership-changed")
        j.targets.forEach { target ->
            val expected = if (applied) target.entry.new else target.entry.old
            check(library.snapshot.channels.singleOrNull { it.id.value == target.id }?.definition == expected &&
                data.channels.singleOrNull { it.id == target.id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "target-changed")
            val keys = listOf(ComparisonV1.playbackKey(target.entry.old), ComparisonV1.playbackKey(target.entry.new))
            check(library.snapshot.channels.none { it.id.value != target.id && ComparisonV1.playbackKey(it.definition) in keys }, "target-collision")
            check(data.channels.none { it.id != target.id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "copy-collision")
            check(library.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { row ->
                ComparisonV1.playbackKey(row.definition) in listOf(ComparisonV1.playbackKey(target.entry.old), ComparisonV1.playbackKey(target.entry.new))
            } }, "shared-target")
        }
    }
    /** Exact unchanged legacy default also needs no planner allocation after removing its journal. */
    fun legacyDefault(library: SourceLibrary) = library.active == PublicM3u.source &&
        library.records.singleOrNull { it.id == PublicM3u.source }?.let { !it.deleted && it.kind == SourceKind.DEFAULT && it.parent == null && it.address == PublicM3u.url } == true &&
        ApprovedR21.manifest.entries.all { entry -> library.snapshot.channels.count { it.definition == entry.old } == 1 }
    private fun replace(data: PreviewData, j: TransitionJournal, applied: Boolean): PreviewData {
        val library = data.library!!
        fun definition(id: OpaqueId, current: PlaybackDefinition): PlaybackDefinition = j.targets.singleOrNull { it.id == id }?.let {
            if (applied) it.entry.new else it.entry.old
        } ?: current
        return data.copy(channels = data.channels.map { ch -> ch.definition?.let { ch.copy(definition = definition(ch.id, it)) } ?: ch },
            library = library.copy(records = library.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = library.snapshot.copy(channels = library.snapshot.channels.map { it.copy(definition = definition(it.id.value, it.definition)) }),
                transition = j.copy(applied = applied)))
    }
    /** Reapply only to the unchanged packaged default rows; never to URL/file/supplement sources. */
    fun defaultSource(source: SavedSource, journal: TransitionJournal?): SavedSource {
        if (journal?.applied != true) return source
        check(journal.manifest == ApprovedR21.manifest && source == journal.sourceBefore, "default-snapshot-changed")
        return expectedSource(journal, true)
    }
}

/** Preparation can be cancelled; the short CAS commit follows the existing NonCancellable contract. */
suspend fun <Row> commitTransition(expected: Row, next: Row, atomicCompareAndSet: suspend (Row, Row) -> Boolean) {
    currentCoroutineContext().ensureActive()
    withContext(NonCancellable) {
        if (!atomicCompareAndSet(expected, next)) throw TransitionProblem("stale-row")
    }
}

internal object TransitionJournalCodec {
    fun encode(j: TransitionJournal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore), j.sourceBefore.id,
            CatalogSnapshot(memberships = j.memberships)))
        return PreviewCodec.pack(listOf(j.manifest.version, j.manifest.digest, j.manifest.session, j.manifest.seedSha256, j.applied.toString(),
            PreviewCodec.pack(j.targets.map { PreviewCodec.pack(listOf(PreviewCodec.id(it.id), it.entry.candidate,
                LibraryCodec.definition(it.entry.old), LibraryCodec.definition(it.entry.new))) }), LibraryCodec.encode(evidence)))
    }
    fun decode(value: String): TransitionJournal {
        val f = PreviewCodec.unpack(value); require(f.size == 7)
        val targets = PreviewCodec.unpack(f[5]).map { raw -> val r = PreviewCodec.unpack(raw); require(r.size == 4)
            BoundTransition(PreviewCodec.id(r[0]), DefinitionTransition(r[1], LibraryCodec.definition(r[2]), LibraryCodec.definition(r[3]))) }
        val library = LibraryCodec.decode(f[6], PreviewData()).library!!
        val manifest = TransitionManifest(f[0], f[1], f[2], f[3], targets.map { it.entry })
        require(manifest == ApprovedR21.manifest && targets.size == 2 && targets.map { it.id }.distinct().size == 2)
        return TransitionJournal(manifest, targets, library.records.single(), library.snapshot.memberships, f[4].toBooleanStrict())
    }
}
