package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class R33Admission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?, val approval: String? = null)
data class R33Journal(val target: BoundTransition, val sourceBefore: SavedSource,
    val memberships: List<MembershipRecord>, val previous: R28Journal) {
    override fun toString() = "R33Journal([redacted])"
}

/** A single compiled exception above the unchanged R23/R26 journals. Rollback pops only this layer. */
object R33ProfileTransition {
    const val normalSession = "r33-avaz-approved-20261015"
    const val testSession = "toplu-r33-avaz-20261015-r2"
    const val version = "R33-TRT-AVAZ-1"
    const val approval = "R33-TRT-AVAZ-T02-VE-GERCEK-PROFIL-ONAYI-2026-10-15"
    val entry = DefinitionTransition("R20-C014",
        PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/3dd49c7a-001c-4be0-9a16-0f8957e3977c.m3u8")),
        PlaybackDefinition(Address.fromForm("https://tv-trtavaz.medya.trt.com.tr/master.m3u8")))
    fun allowed(a: R33Admission) = a.debug && a.key == 2 && a.action in setOf("preflight", "apply", "rollback") &&
        ((a.session == normalSession && a.databaseName == R23ProfileTransition.normalDatabase && a.approval == approval) ||
            (a.session == testSession && a.databaseName == "$testSession.db"))
    private fun proof(ok: Boolean, code: String) { if (!ok) throw TransitionProblem(code) }
    /** Pure existing-model inspection. No prepare/apply projection or initializer is used. */
    fun audit(data: PreviewData, context: PilotContext, a: R33Admission): R33Journal {
        proof(data.library?.r38c == null, "r38c-layer-protected")
        proof(data.library?.r38b == null, "r38b-layer-must-rollback-first")
        proof(allowed(a) && context.session == a.session, "r33-disabled-profile")
        proof(data.profileId == PublicM3u.profile && context.seedSha256 == VerifiedDefinitionTransition.seedSha256, "r33-seed-binding")
        proof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
            data.channels.map { it.id } == context.seed.map { it.id }, "r33-cache-binding")
        val lib = data.library ?: throw TransitionProblem("r33-missing-library")
        val j23 = lib.transition ?: throw TransitionProblem("r33-missing-r23")
        val j26 = lib.r26 ?: throw TransitionProblem("r33-missing-r26")
        val j28 = lib.r28 ?: throw TransitionProblem("r33-missing-r28")
        val prior = ApprovedR21.manifest.entries + R26ProfileTransition.entry + R28ProfileTransition.entry
        fun bound(e: DefinitionTransition) = BoundTransition(context.seed.single { it.definition == e.old }.id, e)
        fun members(ids: List<OpaqueId>) = lib.snapshot.memberships.filter { it.channelId.value in ids }
        fun source(entries: List<DefinitionTransition>) = context.seedSource.copy(rows = context.seedSource.rows.map { row ->
            entries.singleOrNull { it.old == row.definition }?.let { row.copy(definition = it.new) } ?: row
        })
        val expected23 = TransitionJournal(ApprovedR21.manifest, ApprovedR21.manifest.entries.map(::bound), context.seedSource,
            members(ApprovedR21.manifest.entries.map { bound(it).id }), true)
        val expected26 = R26Journal(bound(R26ProfileTransition.entry), source(ApprovedR21.manifest.entries),
            members(listOf(bound(R26ProfileTransition.entry).id)), expected23)
        val expected28 = R28Journal(bound(R28ProfileTransition.entry), source(prior.take(3)),
            members(listOf(bound(R28ProfileTransition.entry).id)), expected26)
        proof(j23 == expected23 && j26 == expected26 && j28 == expected28, "r33-previous-journal-binding")
        val seedTarget = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("r33-cache-target")
        val seedRow = context.seedSource.rows.singleOrNull { it.rowOrder == 39L && it.definition == entry.old }
            ?: throw TransitionProblem("r33-seed-contribution")
        proof(seedTarget.name == seedRow.name && seedTarget.providerId == seedRow.providerHint.orEmpty(), "r33-metadata-binding")
        val id = seedTarget.id
        val expectedJournal = R33Journal(BoundTransition(id, entry), source(prior), members(listOf(id)), j28)
        val journal = lib.r33 ?: expectedJournal
        proof(journal == expectedJournal, "r33-journal-binding")
        val entries = prior + if (lib.r33 != null) listOf(entry) else emptyList()
        val expectedUi = context.seed.map { original -> entries.singleOrNull { it.old == original.definition }
            ?.let { original.copy(definition = it.new) } ?: original }
        proof(data.channels == expectedUi, "r33-ui-definition-metadata-binding")
        proof(lib.snapshot.channels.size == 408 && lib.snapshot.channels.map { it.id.value }.toSet() == context.seed.map { it.id }.toSet(), "r33-snapshot-ids")
        proof(lib.snapshot.channels.all { record -> expectedUi.single { it.id == record.id.value }.definition == record.definition }, "r33-snapshot-definitions")
        proof(lib.records.singleOrNull { it.id == PublicM3u.source } == source(entries), "r33-source-binding")
        validate(data, journal, lib.r33 != null)
        return journal
    }
    fun prepare(data: PreviewData, context: PilotContext, a: R33Admission): PreviewData {
        val journal = audit(data, context, a)
        return when {
            a.action == "rollback" && data.library!!.r33 != null -> replace(data, journal, false)
            a.action != "rollback" && data.library!!.r33 == null -> replace(data, journal, true)
            else -> data
        }
    }
    private fun expectedSource(j: R33Journal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map {
        if (applied && it.definition == entry.old) it.copy(definition = entry.new) else it
    })
    private fun validate(data: PreviewData, j: R33Journal, applied: Boolean) {
        val lib = data.library ?: throw TransitionProblem("r33-missing-library")
        val id = j.target.id
        proof(j.target.entry == entry && j.previous == lib.r28 && j.previous.previous == lib.r26 && j.previous.previous.previous == lib.transition, "r33-journal-binding")
        proof(lib.active == PublicM3u.source && lib.records.singleOrNull { it.id == PublicM3u.source } == expectedSource(j, applied), "r33-source-changed")
        proof(j.sourceBefore.kind == SourceKind.DEFAULT && !j.sourceBefore.deleted && j.sourceBefore.parent == null &&
            j.sourceBefore.address == PublicM3u.url && j.sourceBefore.id == PublicM3u.source, "r33-source")
        proof(lib.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "r33-source-role")
        // Parser rowOrder is the one-based raw record index. The authorised rawIndex39 is exact.
        proof(j.sourceBefore.rows.filter { it.definition == entry.old }.let { it.size == 1 && it.single().rowOrder == 39L && it.single().sourceId == PublicM3u.source }, "r33-contribution")
        val members = lib.snapshot.memberships.filter { it.channelId.value == id }
        proof(members == j.memberships && members.any { it.isCurrent } && members.all { it.sourceId == PublicM3u.source }, "r33-membership")
        val expected = if (applied) entry.new else entry.old
        proof(lib.snapshot.channels.singleOrNull { it.id.value == id }?.definition == expected, "r33-target")
        proof(data.channels.singleOrNull { it.id == id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "r33-ui-copy")
        val keys = listOf(ComparisonV1.playbackKey(entry.old), ComparisonV1.playbackKey(entry.new))
        proof(lib.snapshot.channels.none { it.id.value != id && ComparisonV1.playbackKey(it.definition) in keys } &&
            data.channels.none { it.id != id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "r33-collision")
        proof(lib.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { ComparisonV1.playbackKey(it.definition) in keys } }, "r33-shared")
        proof(lib.snapshot.channels.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            lib.records.flatMap { it.rows }.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            data.channels.all { it.definition?.let(VerifiedDefinitionTransition::portable) != false }, "r33-unsupported")
    }
    private fun replace(data: PreviewData, j: R33Journal, applied: Boolean): PreviewData {
        val lib = data.library!!; val id = j.target.id; val d = if (applied) entry.new else entry.old
        return data.copy(channels = data.channels.map { if (it.id == id) it.copy(definition = d) else it },
            library = lib.copy(records = lib.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.map { if (it.id.value == id) it.copy(definition = d) else it }),
                r33 = if (applied) j else null))
    }
    fun defaultSource(source: SavedSource, journal: R33Journal?): SavedSource {
        if (journal == null) return source
        proof(source == journal.sourceBefore, "r33-default-snapshot")
        return expectedSource(journal, true)
    }
}

internal object R33JournalCodec {
    fun encode(j: R33Journal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore),
            j.sourceBefore.id, CatalogSnapshot(memberships = j.memberships), transition = j.previous.previous.previous, r26 = j.previous.previous, r28 = j.previous))
        return PreviewCodec.pack(listOf(R33ProfileTransition.version, R33ProfileTransition.approval, PreviewCodec.id(j.target.id),
            LibraryCodec.definition(j.target.entry.old), LibraryCodec.definition(j.target.entry.new), LibraryCodec.encode(evidence)))
    }
    fun decode(value: String): R33Journal {
        val f = PreviewCodec.unpack(value); require(f.size == 6 && f[0] == R33ProfileTransition.version && f[1] == R33ProfileTransition.approval)
        require(LibraryCodec.definition(f[3]) == R33ProfileTransition.entry.old && LibraryCodec.definition(f[4]) == R33ProfileTransition.entry.new)
        val lib = LibraryCodec.decode(f[5], PreviewData()).library!!
        val previous = requireNotNull(lib.r28); require(previous.previous == lib.r26 && previous.previous.previous == lib.transition && lib.r33 == null)
        return R33Journal(BoundTransition(PreviewCodec.id(f[2]), R33ProfileTransition.entry), lib.records.single(), lib.snapshot.memberships, previous)
    }
}
