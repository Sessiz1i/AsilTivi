package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class R38BAdmission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?, val manifest: String = R38BProfileTransition.fixtureManifest)
data class R38BJournal(val target: BoundTransition, val sourceBefore: SavedSource,
    val memberships: List<MembershipRecord>, val previous: R28Journal, val manifest: String = R38BProfileTransition.fixtureManifest) {
    override fun toString() = "R38BJournal([redacted])"
}

/** Synthetic-only C013 transition above the three unchanged prior journals. No normal admission. */
object R38BProfileTransition {
    const val version = "R38B-C013-SYNTHETIC-1"
    const val fixtureManifest = "R38B-FIXTURE-C013-20261016-V1-NOT-NORMAL-APPROVAL"
    const val envelope = "BT-SOURCES-6"
    val targetId = OpaqueId(38016, 37)
    val sessions = (listOf("main", "fault", "cancel", "boundary", "hold") + (0..6).map { "stale$it" }).map { "toplu-r38b-20261016-c013-$it" }.toSet()
    val entry = DefinitionTransition("R20-C013",
        PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/d8fcd134-0f23-4883-ae8f-4c04cc3cc49a.m3u8")),
        PlaybackDefinition(Address.fromForm("https://tv-trtturk.medya.trt.com.tr/master.m3u8")))
    fun allowed(a: R38BAdmission) = a.debug && a.key == 2 && a.action in setOf("preflight", "apply", "rollback") &&
        a.session in sessions && a.databaseName == "${a.session}.db" && a.manifest == fixtureManifest
    fun <T> construct(a: R38BAdmission, constructor: (String, Int) -> T): T {
        if (!allowed(a)) throw TransitionProblem("r38b-disabled-profile")
        return constructor(a.databaseName, a.key)
    }
    private fun proof(ok: Boolean, code: String) { if (!ok) throw TransitionProblem(code) }
    /** Pure existing-model inspection. No prepare/apply projection or initializer is used. */
    fun audit(data: PreviewData, context: PilotContext, a: R38BAdmission): R38BJournal {
        proof(data.library?.r38c == null, "r38c-layer-protected")
        proof(data.library?.r33 == null, "r38b-wrong-layer")
        proof(allowed(a) && context.session == a.session, "r38b-disabled-profile")
        proof(data.profileId == PublicM3u.profile && context.seedSha256 == VerifiedDefinitionTransition.seedSha256, "r38b-seed-binding")
        proof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
            data.channels.map { it.id } == context.seed.map { it.id }, "r38b-cache-binding")
        val lib = data.library ?: throw TransitionProblem("r38b-missing-library")
        val j23 = lib.transition ?: throw TransitionProblem("r38b-missing-r23")
        val j26 = lib.r26 ?: throw TransitionProblem("r38b-missing-r26")
        val j28 = lib.r28 ?: throw TransitionProblem("r38b-missing-r28")
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
        proof(j23 == expected23 && j26 == expected26 && j28 == expected28, "r38b-previous-journal-binding")
        val seedTarget = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("r38b-cache-target")
        proof(seedTarget.id == targetId && seedTarget.providerId.isEmpty(), "r38b-synthetic-id-binding")
        val seedRow = context.seedSource.rows.singleOrNull { it.rowOrder == 38L && it.definition == entry.old }
            ?: throw TransitionProblem("r38b-seed-contribution")
        proof(seedTarget.name == seedRow.name && seedTarget.providerId == seedRow.providerHint.orEmpty(), "r38b-metadata-binding")
        val id = seedTarget.id
        val expectedJournal = R38BJournal(BoundTransition(id, entry), source(prior), members(listOf(id)), j28)
        val journal = lib.r38b ?: expectedJournal
        proof(journal.manifest == fixtureManifest && journal == expectedJournal, "r38b-journal-binding")
        val entries = prior + if (lib.r38b != null) listOf(entry) else emptyList()
        val expectedUi = context.seed.map { original -> entries.singleOrNull { it.old == original.definition }
            ?.let { original.copy(definition = it.new) } ?: original }
        proof(data.channels == expectedUi, "r38b-ui-definition-metadata-binding")
        proof(lib.snapshot.channels.size == 408 && lib.snapshot.channels.map { it.id.value }.toSet() == context.seed.map { it.id }.toSet(), "r38b-snapshot-ids")
        proof(lib.snapshot.channels.all { record -> expectedUi.single { it.id == record.id.value }.definition == record.definition }, "r38b-snapshot-definitions")
        proof(lib.records.singleOrNull { it.id == PublicM3u.source } == source(entries), "r38b-source-binding")
        validate(data, journal, lib.r38b != null)
        return journal
    }
    fun prepare(data: PreviewData, context: PilotContext, a: R38BAdmission): PreviewData {
        val journal = audit(data, context, a)
        return when {
            a.action == "rollback" && data.library!!.r38b != null -> replace(data, journal, false)
            a.action != "rollback" && data.library!!.r38b == null -> replace(data, journal, true)
            else -> data
        }
    }
    private fun expectedSource(j: R38BJournal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map {
        if (applied && it.definition == entry.old) it.copy(definition = entry.new) else it
    })
    private fun validate(data: PreviewData, j: R38BJournal, applied: Boolean) {
        val lib = data.library ?: throw TransitionProblem("r38b-missing-library")
        val id = j.target.id
        proof(j.manifest == fixtureManifest && j.target.id == targetId && j.target.entry == entry && j.previous == lib.r28 && j.previous.previous == lib.r26 && j.previous.previous.previous == lib.transition, "r38b-journal-binding")
        proof(lib.active == PublicM3u.source && lib.records.singleOrNull { it.id == PublicM3u.source } == expectedSource(j, applied), "r38b-source-changed")
        proof(j.sourceBefore.kind == SourceKind.DEFAULT && !j.sourceBefore.deleted && j.sourceBefore.parent == null &&
            j.sourceBefore.address == PublicM3u.url && j.sourceBefore.id == PublicM3u.source, "r38b-source")
        proof(lib.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "r38b-source-role")
        // Parser rowOrder is the one-based raw record index. The authorised rawIndex38 is exact.
        proof(j.sourceBefore.rows.filter { it.definition == entry.old }.let { it.size == 1 && it.single().rowOrder == 38L && it.single().sourceId == PublicM3u.source }, "r38b-contribution")
        val members = lib.snapshot.memberships.filter { it.channelId.value == id }
        proof(members == j.memberships && members.any { it.isCurrent } && members.all { it.sourceId == PublicM3u.source }, "r38b-membership")
        val expected = if (applied) entry.new else entry.old
        proof(lib.snapshot.channels.singleOrNull { it.id.value == id }?.definition == expected, "r38b-target")
        proof(data.channels.singleOrNull { it.id == id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "r38b-ui-copy")
        val keys = listOf(ComparisonV1.playbackKey(entry.old), ComparisonV1.playbackKey(entry.new))
        proof(lib.snapshot.channels.none { it.id.value != id && ComparisonV1.playbackKey(it.definition) in keys } &&
            data.channels.none { it.id != id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "r38b-collision")
        proof(lib.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { ComparisonV1.playbackKey(it.definition) in keys } }, "r38b-shared")
        proof(lib.snapshot.channels.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            lib.records.flatMap { it.rows }.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            data.channels.all { it.definition?.let(VerifiedDefinitionTransition::portable) != false }, "r38b-unsupported")
    }
    private fun replace(data: PreviewData, j: R38BJournal, applied: Boolean): PreviewData {
        val lib = data.library!!; val id = j.target.id; val d = if (applied) entry.new else entry.old
        return data.copy(channels = data.channels.map { if (it.id == id) it.copy(definition = d) else it },
            library = lib.copy(records = lib.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.map { if (it.id.value == id) it.copy(definition = d) else it }),
                r38b = if (applied) j else null))
    }
    fun defaultSource(source: SavedSource, journal: R38BJournal?): SavedSource {
        if (journal == null) return source
        proof(source == journal.sourceBefore, "r38b-default-snapshot")
        return expectedSource(journal, true)
    }
}

internal object R38BJournalCodec {
    fun encode(j: R38BJournal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore),
            j.sourceBefore.id, CatalogSnapshot(memberships = j.memberships), transition = j.previous.previous.previous, r26 = j.previous.previous, r28 = j.previous))
        return PreviewCodec.pack(listOf(R38BProfileTransition.version, R38BProfileTransition.fixtureManifest, PreviewCodec.id(j.target.id),
            LibraryCodec.definition(j.target.entry.old), LibraryCodec.definition(j.target.entry.new), LibraryCodec.encode(evidence)))
    }
    fun decode(value: String): R38BJournal {
        val f = PreviewCodec.unpack(value); require(f.size == 6 && f[0] == R38BProfileTransition.version && f[1] == R38BProfileTransition.fixtureManifest)
        require(LibraryCodec.definition(f[3]) == R38BProfileTransition.entry.old && LibraryCodec.definition(f[4]) == R38BProfileTransition.entry.new)
        val lib = LibraryCodec.decode(f[5], PreviewData()).library!!
        val previous = requireNotNull(lib.r28); require(previous.previous == lib.r26 && previous.previous.previous == lib.transition && lib.r38b == null)
        return R38BJournal(BoundTransition(PreviewCodec.id(f[2]), R38BProfileTransition.entry), lib.records.single(), lib.snapshot.memberships, previous)
    }
}
