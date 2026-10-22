package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class R26Admission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?, val approval: String? = null)
data class R26Journal(val target: BoundTransition, val sourceBefore: SavedSource,
    val memberships: List<MembershipRecord>, val previous: TransitionJournal) {
    override fun toString() = "R26Journal([redacted])"
}

/** A single compiled exception above the unchanged R21 journal. Rollback pops only this layer. */
object R26ProfileTransition {
    const val normalSession = "r26-24tv-approved-20261015"
    const val testSession = "toplu-r26-24tv-20261015-r1"
    const val version = "R26-24TV-1"
    const val approval = "R26-24TV-T02-VE-GERCEK-PROFIL-ONAYI-2026-10-15"
    val entry = DefinitionTransition("R20-C009",
        PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/35b6fa6f-7915-4303-8198-30c2d7b82929.m3u8")),
        PlaybackDefinition(Address.fromForm("https://turkmedya-live.ercdn.net/tv24/tv24.m3u8")))
    fun allowed(a: R26Admission) = a.debug && a.key == 2 && a.action in setOf("preflight", "apply", "rollback") &&
        ((a.session == normalSession && a.databaseName == R23ProfileTransition.normalDatabase && a.approval == approval) ||
            (a.session == testSession && a.databaseName == "$testSession.db"))
    private fun proof(ok: Boolean, code: String) { if (!ok) throw TransitionProblem(code) }
    fun prepare(data: PreviewData, context: PilotContext, a: R26Admission): PreviewData {
        proof(data.library?.r38c == null, "r38c-layer-protected")
        proof(data.library?.r38b == null, "r38b-layer-must-rollback-first")
        proof(data.library?.r33 == null, "r33-layer-must-rollback-first")
        proof(data.library?.r28 == null, "r28-layer-must-rollback-first")
        proof(allowed(a) && context.session == a.session, "r26-disabled-profile")
        proof(data.profileId == PublicM3u.profile && context.seedSha256 == VerifiedDefinitionTransition.seedSha256, "r26-seed-binding")
        proof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
            data.channels.map { it.id } == context.seed.map { it.id }, "r26-cache-binding")
        val lib = data.library ?: throw TransitionProblem("r26-missing-library")
        val previous = lib.transition ?: throw TransitionProblem("r26-missing-r23")
        proof(previous.applied && previous.manifest == ApprovedR21.manifest && previous.sourceBefore == context.seedSource, "r26-r23-binding")
        val seedTarget = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("r26-cache-target")
        val id = seedTarget.id
        val layer = lib.r26
        if (layer != null) {
            proof(layer.target == BoundTransition(id, entry) && layer.previous == previous, "r26-journal-binding")
            validate(data, layer, true)
            val popped = replace(data, layer, false)
            validateBase(popped, context)
            return if (a.action == "rollback") popped else data
        }
        validateBase(data, context)
        val source = lib.records.singleOrNull { it.id == PublicM3u.source } ?: throw TransitionProblem("r26-source")
        val journal = R26Journal(BoundTransition(id, entry), source,
            lib.snapshot.memberships.filter { it.channelId.value == id }, previous)
        validate(data, journal, false)
        return if (a.action == "rollback") data else replace(data, journal, true)
    }
    private fun validateBase(data: PreviewData, context: PilotContext) {
        // Existing two-entry validation runs on an exact in-memory projection, never a write.
        val c = context.copy(session = R23ProfileTransition.normalSession)
        val a = R23Admission(true, 2, R23ProfileTransition.normalDatabase, c.session, "preflight")
        proof(R23ProfileTransition.prepare(data, c, a, ApprovedR21.manifest) { throw TransitionProblem("r26-no-allocation") } === data, "r26-r23-not-applied")
    }
    private fun expectedSource(j: R26Journal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map {
        if (applied && it.definition == entry.old) it.copy(definition = entry.new) else it
    })
    private fun validate(data: PreviewData, j: R26Journal, applied: Boolean) {
        val lib = data.library ?: throw TransitionProblem("r26-missing-library")
        val id = j.target.id
        proof(j.target.entry == entry && j.previous == lib.transition && j.previous.applied, "r26-journal-binding")
        proof(lib.active == PublicM3u.source && lib.records.singleOrNull { it.id == PublicM3u.source } == expectedSource(j, applied), "r26-source-changed")
        proof(j.sourceBefore.kind == SourceKind.DEFAULT && !j.sourceBefore.deleted && j.sourceBefore.parent == null &&
            j.sourceBefore.address == PublicM3u.url && j.sourceBefore.id == PublicM3u.source, "r26-source")
        proof(lib.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "r26-source-role")
        // Parser rowOrder is the one-based raw record index. The authorised rawIndex22 is exact.
        proof(j.sourceBefore.rows.filter { it.definition == entry.old }.let { it.size == 1 && it.single().rowOrder == 22L && it.single().sourceId == PublicM3u.source }, "r26-contribution")
        val members = lib.snapshot.memberships.filter { it.channelId.value == id }
        proof(members == j.memberships && members.any { it.isCurrent } && members.all { it.sourceId == PublicM3u.source }, "r26-membership")
        val expected = if (applied) entry.new else entry.old
        proof(lib.snapshot.channels.singleOrNull { it.id.value == id }?.definition == expected, "r26-target")
        proof(data.channels.singleOrNull { it.id == id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "r26-ui-copy")
        val keys = listOf(ComparisonV1.playbackKey(entry.old), ComparisonV1.playbackKey(entry.new))
        proof(lib.snapshot.channels.none { it.id.value != id && ComparisonV1.playbackKey(it.definition) in keys } &&
            data.channels.none { it.id != id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "r26-collision")
        proof(lib.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { ComparisonV1.playbackKey(it.definition) in keys } }, "r26-shared")
        proof(lib.snapshot.channels.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            lib.records.flatMap { it.rows }.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            data.channels.all { it.definition?.let(VerifiedDefinitionTransition::portable) != false }, "r26-unsupported")
    }
    private fun replace(data: PreviewData, j: R26Journal, applied: Boolean): PreviewData {
        val lib = data.library!!; val id = j.target.id; val d = if (applied) entry.new else entry.old
        return data.copy(channels = data.channels.map { if (it.id == id) it.copy(definition = d) else it },
            library = lib.copy(records = lib.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.map { if (it.id.value == id) it.copy(definition = d) else it }),
                r26 = if (applied) j else null))
    }
    fun defaultSource(source: SavedSource, journal: R26Journal?): SavedSource {
        if (journal == null) return source
        proof(source == journal.sourceBefore, "r26-default-snapshot")
        return expectedSource(journal, true)
    }
}

internal object R26JournalCodec {
    fun encode(j: R26Journal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore),
            j.sourceBefore.id, CatalogSnapshot(memberships = j.memberships), transition = j.previous))
        return PreviewCodec.pack(listOf(R26ProfileTransition.version, R26ProfileTransition.approval, PreviewCodec.id(j.target.id),
            LibraryCodec.definition(j.target.entry.old), LibraryCodec.definition(j.target.entry.new), LibraryCodec.encode(evidence)))
    }
    fun decode(value: String): R26Journal {
        val f = PreviewCodec.unpack(value); require(f.size == 6 && f[0] == R26ProfileTransition.version && f[1] == R26ProfileTransition.approval)
        require(LibraryCodec.definition(f[3]) == R26ProfileTransition.entry.old && LibraryCodec.definition(f[4]) == R26ProfileTransition.entry.new)
        val lib = LibraryCodec.decode(f[5], PreviewData()).library!!
        val previous = requireNotNull(lib.transition); require(previous.applied && previous.manifest == ApprovedR21.manifest && lib.r26 == null)
        return R26Journal(BoundTransition(PreviewCodec.id(f[2]), R26ProfileTransition.entry), lib.records.single(), lib.snapshot.memberships, previous)
    }
}
