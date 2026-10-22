package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class R38CAdmission(val debug: Boolean, val key: Int, val databaseName: String, val session: String?, val action: String?, val manifest: String, val approvalSha256: String? = null, val recordSha256: String? = null, val privateSha256: String? = null, val manifestSha256: String? = null, val candidate: String? = null)
data class R38CJournal(val target: BoundTransition, val sourceBefore: SavedSource,
    val memberships: List<MembershipRecord>, val previous: R28Journal, val manifest: String) {
    override fun toString() = "R38CJournal([redacted])"
}

/** Exact normal C013 binding; synthetic admission has a separate tag, identity and database prefix. */
object R38CProfileTransition {
    const val version = "R38C-C013-1"
    const val normalManifest = "R38C-NORMAL-C013-20261016-V1"
    const val normalSession = "r38c-normal-c013-approved-20261016"
    const val fixtureManifest = "R38C-FIXTURE-C013-20261016-V1-NOT-NORMAL-APPROVAL"
    const val envelope = "BT-SOURCES-7"
    val targetId = OpaqueId(4093774985743516282L, -6857945044662352196L)
    val syntheticId = OpaqueId(38016, 37)
    val sessions = (listOf("main", "fault", "cancel", "boundary", "hold") + (0..6).map { "stale$it" }).map { "toplu-r38c-20261016-c013-$it" }.toSet()
    val entry = DefinitionTransition("R20-C013",
        PlaybackDefinition(Address.fromForm("https://streams.uzunmuhalefet.com/stream/d8fcd134-0f23-4883-ae8f-4c04cc3cc49a.m3u8")),
        PlaybackDefinition(Address.fromForm("https://tv-trtturk.medya.trt.com.tr/master.m3u8")))
    fun allowed(a: R38CAdmission) = a.debug && a.key == 2 && a.databaseName == R23ProfileTransition.normalDatabase &&
        a.session == normalSession && a.action in setOf("preflight", "apply") && a.manifest == normalManifest &&
        a.candidate == "R20-C013" && a.approvalSha256 == R38CManifestBinding.approvalSha256 &&
        a.recordSha256 == R38CManifestBinding.recordSha256 && a.privateSha256 == R38CManifestBinding.privateSha256 &&
        a.manifestSha256 == R38CManifestBinding.manifestSha256
    fun allowedSynthetic(a: R38CAdmission) = a.debug && a.key == 2 && a.action in setOf("preflight", "apply", "rollback") &&
        a.session in sessions && a.databaseName == "${a.session}.db" && a.manifest == fixtureManifest && a.candidate == "R20-C013"
    internal fun admitted(a: R38CAdmission) = allowed(a) || allowedSynthetic(a)
    fun <T> construct(a: R38CAdmission, constructor: (String, Int) -> T): T {
        if (!allowed(a)) throw TransitionProblem("r38c-disabled-normal-profile")
        return constructor(a.databaseName, a.key)
    }
    fun <T> constructSynthetic(a: R38CAdmission, constructor: (String, Int) -> T): T {
        if (!allowedSynthetic(a)) throw TransitionProblem("r38c-disabled-synthetic-profile")
        return constructor(a.databaseName, a.key)
    }
    private fun proof(ok: Boolean, code: String) { if (!ok) throw TransitionProblem(code) }
    /** Pure existing-model inspection. No prepare/apply projection or initializer is used. */
    fun audit(data: PreviewData, context: PilotContext, a: R38CAdmission): R38CJournal {
        proof(data.library?.r33 == null && data.library?.r38b == null, "r38c-wrong-layer")
        proof(admitted(a) && context.session == a.session, "r38c-disabled-profile")
        proof(data.profileId == PublicM3u.profile && context.seedSha256 == VerifiedDefinitionTransition.seedSha256, "r38c-seed-binding")
        proof(context.seed.size == 408 && context.seed.map { it.id }.distinct().size == 408 &&
            data.channels.map { it.id } == context.seed.map { it.id }, "r38c-cache-binding")
        val lib = data.library ?: throw TransitionProblem("r38c-missing-library")
        val j23 = lib.transition ?: throw TransitionProblem("r38c-missing-r23")
        val j26 = lib.r26 ?: throw TransitionProblem("r38c-missing-r26")
        val j28 = lib.r28 ?: throw TransitionProblem("r38c-missing-r28")
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
        proof(j23 == expected23 && j26 == expected26 && j28 == expected28, "r38c-previous-journal-binding")
        val seedTarget = context.seed.singleOrNull { it.definition == entry.old } ?: throw TransitionProblem("r38c-cache-target")
        proof(seedTarget.id == (if (allowed(a)) targetId else syntheticId) && seedTarget.providerId.isEmpty(), "r38c-synthetic-id-binding")
        val seedRow = context.seedSource.rows.singleOrNull { it.rowOrder == 38L && it.definition == entry.old }
            ?: throw TransitionProblem("r38c-seed-contribution")
        proof(seedTarget.name == seedRow.name && seedTarget.providerId == seedRow.providerHint.orEmpty(), "r38c-metadata-binding")
        val id = seedTarget.id
        val expectedJournal = R38CJournal(BoundTransition(id, entry), source(prior), members(listOf(id)), j28, a.manifest)
        val journal = lib.r38c ?: expectedJournal
        proof(journal.manifest == a.manifest && journal == expectedJournal, "r38c-journal-binding")
        val entries = prior + if (lib.r38c != null) listOf(entry) else emptyList()
        val expectedUi = context.seed.map { original -> entries.singleOrNull { it.old == original.definition }
            ?.let { original.copy(definition = it.new) } ?: original }
        proof(data.channels == expectedUi, "r38c-ui-definition-metadata-binding")
        proof(lib.snapshot.channels.size == 408 && lib.snapshot.channels.map { it.id.value }.toSet() == context.seed.map { it.id }.toSet(), "r38c-snapshot-ids")
        proof(lib.snapshot.channels.all { record -> expectedUi.single { it.id == record.id.value }.definition == record.definition }, "r38c-snapshot-definitions")
        proof(lib.records.singleOrNull { it.id == PublicM3u.source } == source(entries), "r38c-source-binding")
        validate(data, journal, lib.r38c != null)
        return journal
    }
    fun prepare(data: PreviewData, context: PilotContext, a: R38CAdmission): PreviewData {
        val journal = audit(data, context, a)
        return when {
            a.action == "rollback" && data.library!!.r38c != null -> replace(data, journal, false)
            a.action != "rollback" && data.library!!.r38c == null -> replace(data, journal, true)
            else -> data
        }
    }
    private fun expectedSource(j: R38CJournal, applied: Boolean) = j.sourceBefore.copy(rows = j.sourceBefore.rows.map {
        if (applied && it.definition == entry.old) it.copy(definition = entry.new) else it
    })
    private fun validate(data: PreviewData, j: R38CJournal, applied: Boolean) {
        val lib = data.library ?: throw TransitionProblem("r38c-missing-library")
        val id = j.target.id
        proof(((j.manifest == normalManifest && j.target.id == targetId) || (j.manifest == fixtureManifest && j.target.id == syntheticId)) && j.target.entry == entry && j.previous == lib.r28 && j.previous.previous == lib.r26 && j.previous.previous.previous == lib.transition, "r38c-journal-binding")
        proof(lib.active == PublicM3u.source && lib.records.singleOrNull { it.id == PublicM3u.source } == expectedSource(j, applied), "r38c-source-changed")
        proof(j.sourceBefore.kind == SourceKind.DEFAULT && !j.sourceBefore.deleted && j.sourceBefore.parent == null &&
            j.sourceBefore.address == PublicM3u.url && j.sourceBefore.id == PublicM3u.source, "r38c-source")
        proof(lib.snapshot.sources.singleOrNull { it.id == PublicM3u.source } == SourceRecord(PublicM3u.source, SourceRole.MAIN, true), "r38c-source-role")
        // Parser rowOrder is the one-based raw record index. The authorised rawIndex38 is exact.
        proof(j.sourceBefore.rows.filter { it.definition == entry.old }.let { it.size == 1 && it.single().rowOrder == 38L && it.single().sourceId == PublicM3u.source }, "r38c-contribution")
        val members = lib.snapshot.memberships.filter { it.channelId.value == id }
        proof(members == j.memberships && members.any { it.isCurrent } && members.all { it.sourceId == PublicM3u.source }, "r38c-membership")
        val expected = if (applied) entry.new else entry.old
        proof(lib.snapshot.channels.singleOrNull { it.id.value == id }?.definition == expected, "r38c-target")
        proof(data.channels.singleOrNull { it.id == id }?.let { it.definition == expected && it.available && it.sourceReferences.isEmpty() } == true, "r38c-ui-copy")
        val keys = listOf(ComparisonV1.playbackKey(entry.old), ComparisonV1.playbackKey(entry.new))
        proof(lib.snapshot.channels.none { it.id.value != id && ComparisonV1.playbackKey(it.definition) in keys } &&
            data.channels.none { it.id != id && it.definition?.let(ComparisonV1::playbackKey) in keys }, "r38c-collision")
        proof(lib.records.filter { it.id != PublicM3u.source }.none { s -> s.rows.any { ComparisonV1.playbackKey(it.definition) in keys } }, "r38c-shared")
        proof(lib.snapshot.channels.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            lib.records.flatMap { it.rows }.all { VerifiedDefinitionTransition.portable(it.definition) } &&
            data.channels.all { it.definition?.let(VerifiedDefinitionTransition::portable) != false }, "r38c-unsupported")
    }
    private fun replace(data: PreviewData, j: R38CJournal, applied: Boolean): PreviewData {
        val lib = data.library!!; val id = j.target.id; val d = if (applied) entry.new else entry.old
        return data.copy(channels = data.channels.map { if (it.id == id) it.copy(definition = d) else it },
            library = lib.copy(records = lib.records.map { if (it.id == PublicM3u.source) expectedSource(j, applied) else it },
                snapshot = lib.snapshot.copy(channels = lib.snapshot.channels.map { if (it.id.value == id) it.copy(definition = d) else it }),
                r38c = if (applied) j else null))
    }
    fun defaultSource(source: SavedSource, journal: R38CJournal?): SavedSource {
        if (journal == null) return source
        proof(source == journal.sourceBefore, "r38c-default-snapshot")
        return expectedSource(journal, true)
    }
}

internal object R38CJournalCodec {
    fun encode(j: R38CJournal): String {
        val evidence = PreviewData(channels = emptyList(), sources = emptyList(), library = SourceLibrary(listOf(j.sourceBefore),
            j.sourceBefore.id, CatalogSnapshot(memberships = j.memberships), transition = j.previous.previous.previous, r26 = j.previous.previous, r28 = j.previous))
        return PreviewCodec.pack(listOf(R38CProfileTransition.version, j.manifest, PreviewCodec.id(j.target.id),
            LibraryCodec.definition(j.target.entry.old), LibraryCodec.definition(j.target.entry.new), LibraryCodec.encode(evidence),
            R38CManifestBinding.manifestSha256, R38CManifestBinding.approvalSha256, R38CManifestBinding.recordSha256, R38CManifestBinding.privateSha256))
    }
    fun decode(value: String): R38CJournal {
        val f = PreviewCodec.unpack(value); require(f.size == 10 && f[0] == R38CProfileTransition.version && f[1] in setOf(R38CProfileTransition.fixtureManifest, R38CProfileTransition.normalManifest))
        require(f.drop(6) == listOf(R38CManifestBinding.manifestSha256, R38CManifestBinding.approvalSha256, R38CManifestBinding.recordSha256, R38CManifestBinding.privateSha256))
        require(LibraryCodec.definition(f[3]) == R38CProfileTransition.entry.old && LibraryCodec.definition(f[4]) == R38CProfileTransition.entry.new)
        val lib = LibraryCodec.decode(f[5], PreviewData()).library!!
        val previous = requireNotNull(lib.r28); require(previous.previous == lib.r26 && previous.previous.previous == lib.transition && lib.r38c == null && lib.r38b == null)
        return R38CJournal(BoundTransition(PreviewCodec.id(f[2]), R38CProfileTransition.entry), lib.records.single(), lib.snapshot.memberships, previous, f[1])
    }
}
