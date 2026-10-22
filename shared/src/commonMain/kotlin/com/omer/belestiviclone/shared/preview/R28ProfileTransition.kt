package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class R28Admission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?, val approval: String? = null)
data class R28Journal(val target: BoundTransition, val sourceBefore: SavedSource,
    val memberships: List<MembershipRecord>, val previous: R26Journal) {
    override fun toString() = "R28Journal([redacted])"
}

/** A single compiled exception above the unchanged R23/R26 journals. Rollback pops only this layer. */
object R28ProfileTransition {
    const val normalSession = "r28-ekoturk-approved-20261015"
    const val testSession = "toplu-r28-ekoturk-20261015-r1"
    const val version = "R28-EKOTURK-1"
    const val approval = "R28-EKOTURK-T02-VE-GERCEK-PROFIL-ONAYI-2026-10-15"
    val entry = DefinitionTransition("R20-C020",
        PlaybackDefinition(Address.fromForm("https://live.cdn-vizi.workers.dev/?ID=51")),
        PlaybackDefinition(Address.fromForm("https://cdn.ekoturk.net/hls/master.m3u8")))
    fun allowed(a: R28Admission) = a.debug && a.key == 2 && a.action in setOf("preflight", "apply", "rollback") &&
        ((a.session == normalSession && a.databaseName == R23ProfileTransition.normalDatabase && a.approval == approval) ||
            (a.session == testSession && a.databaseName == "$testSession.db"))
    private fun proof(ok: Boolean, code: String) { if (!ok) throw TransitionProblem(code) }
    fun prepare(data: PreviewData, context: PilotContext, a: R28Admission): PreviewData {
        proof(data.library?.r38c == null, "r38c-layer-protected")
        proof(data.library?.r38b == null, "r38b-layer-must-rollback-first")
        proof(data.library?.r33 == null, "r33-layer-must-rollback-first")
        proof(allowed(a) && context.session == a.session, "r28-disabled-profile")
        proof(data.profileId == PublicM3u.profile && context.seedSha256 == VerifiedDefinitionTransition.seedSha256, "r28-seed-binding")
        proof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
            data.channels.map { it.id } == context.seed.map { it.id }, "r28-cache-binding")
        val lib = data.library ?: throw TransitionProblem("r28-missing-library")
        val previous = lib.r26 ?: throw TransitionProblem("r28-missing-r26")
        proof(previous.target.entry == R26ProfileTransition.entry && previous.previous == lib.transition, "r28-r26-binding")
        val seedTarget = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("r28-cache-target")
        val id = seedTarget.id
        val layer = lib.r28
        if (layer != null) {
            proof(layer.target == BoundTransition(id, entry) && layer.previous == previous, "r28-journal-binding")
            validate(data, layer, true)
            val popped = replace(data, layer, false)
            validateBase(popped, context)
            return if (a.action == "rollback") popped else data
        }
        validateBase(data, context)
        val source = lib.records.singleOrNull { it.id == PublicM3u.source } ?: throw TransitionProblem("r28-source")
        val journal = R28Journal(BoundTransition(id, entry), source,
            lib.snapshot.memberships.filter { it.channelId.value == id }, previous)
        validate(data, journal, false)
        return if (a.action == "rollback") data else replace(data, journal, true)
    }
    private fun validateBase(data: PreviewData, context: PilotContext) {
        // Validate the entire underlying three-update state without writing or allocating.
        val c = context.copy(session = R26ProfileTransition.testSession)
        val a = R26Admission(true, 2, "${c.session}.db", c.session, "preflight")
        proof(data.library?.r28 == null && R26ProfileTransition.prepare(data, c, a) === data, "r28-r26-not-applied")
    }
    private fun expectedSource(j: R28Journal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map {
        if (applied && it.definition == entry.old) it.copy(definition = entry.new) else it
    })
    private fun validate(data: PreviewData, j: R28Journal, applied: Boolean) {
        val lib = data.library ?: throw TransitionProblem("r28-missing-library")
        val id = j.target.id
        proof(j.target.entry == entry && j.previous == lib.r26 && j.previous.previous == lib.transition, "r28-journal-binding")
        proof(lib.active == PublicM3u.source && lib.records.singleOrNull { it.id == PublicM3u.source } == expectedSource(j, applied), "r28-source-changed")
        proof(j.sourceBefore.kind == SourceKind.DEFAULT && !j.sourceBefore.deleted && j.sourceBefore.parent == null &&
            j.sourceBefore.address == PublicM3u.url && j.sourceBefore.id == PublicM3u.source, "r28-source")
        proof(lib.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "r28-source-role")
        // Parser rowOrder is the one-based raw record index. The authorised rawIndex48 is exact.
        proof(j.sourceBefore.rows.filter { it.definition == entry.old }.let { it.size == 1 && it.single().rowOrder == 48L && it.single().sourceId == PublicM3u.source }, "r28-contribution")
        val members = lib.snapshot.memberships.filter { it.channelId.value == id }
        proof(members == j.memberships && members.any { it.isCurrent } && members.all { it.sourceId == PublicM3u.source }, "r28-membership")
        val expected = if (applied) entry.new else entry.old
        proof(lib.snapshot.channels.singleOrNull { it.id.value == id }?.definition == expected, "r28-target")
        proof(data.channels.singleOrNull { it.id == id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "r28-ui-copy")
        val keys = listOf(ComparisonV1.playbackKey(entry.old), ComparisonV1.playbackKey(entry.new))
        proof(lib.snapshot.channels.none { it.id.value != id && ComparisonV1.playbackKey(it.definition) in keys } &&
            data.channels.none { it.id != id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "r28-collision")
        proof(lib.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { ComparisonV1.playbackKey(it.definition) in keys } }, "r28-shared")
        proof(lib.snapshot.channels.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            lib.records.flatMap { it.rows }.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            data.channels.all { it.definition?.let(VerifiedDefinitionTransition::portable) != false }, "r28-unsupported")
    }
    private fun replace(data: PreviewData, j: R28Journal, applied: Boolean): PreviewData {
        val lib = data.library!!; val id = j.target.id; val d = if (applied) entry.new else entry.old
        return data.copy(channels = data.channels.map { if (it.id == id) it.copy(definition = d) else it },
            library = lib.copy(records = lib.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.map { if (it.id.value == id) it.copy(definition = d) else it }),
                r28 = if (applied) j else null))
    }
    fun defaultSource(source: SavedSource, journal: R28Journal?): SavedSource {
        if (journal == null) return source
        proof(source == journal.sourceBefore, "r28-default-snapshot")
        return expectedSource(journal, true)
    }
}

internal object R28JournalCodec {
    fun encode(j: R28Journal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore),
            j.sourceBefore.id, CatalogSnapshot(memberships = j.memberships), transition = j.previous.previous, r26 = j.previous))
        return PreviewCodec.pack(listOf(R28ProfileTransition.version, R28ProfileTransition.approval, PreviewCodec.id(j.target.id),
            LibraryCodec.definition(j.target.entry.old), LibraryCodec.definition(j.target.entry.new), LibraryCodec.encode(evidence)))
    }
    fun decode(value: String): R28Journal {
        val f = PreviewCodec.unpack(value); require(f.size == 6 && f[0] == R28ProfileTransition.version && f[1] == R28ProfileTransition.approval)
        require(LibraryCodec.definition(f[3]) == R28ProfileTransition.entry.old && LibraryCodec.definition(f[4]) == R28ProfileTransition.entry.new)
        val lib = LibraryCodec.decode(f[5], PreviewData()).library!!
        val previous = requireNotNull(lib.r26); require(previous.previous == lib.transition && lib.r28 == null)
        return R28Journal(BoundTransition(PreviewCodec.id(f[2]), R28ProfileTransition.entry), lib.records.single(), lib.snapshot.memberships, previous)
    }
}
