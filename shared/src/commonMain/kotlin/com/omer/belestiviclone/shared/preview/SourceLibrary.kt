package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

enum class SourceKind { DEFAULT, URL, FILE }
data class SavedSource(
    val id: SourceId, val name: String, val kind: SourceKind, val address: String,
    val rows: List<ChannelContribution>, val parent: SourceId? = null, val deleted: Boolean = false,
    val rawCount: Int = rows.size, val skippedCount: Int = 0,
) {
    override fun toString() = "SavedSource([redacted])"
}
data class SourceLibrary(val records: List<SavedSource>, val active: SourceId, val snapshot: CatalogSnapshot, val transition: TransitionJournal? = null, val r26: R26Journal? = null, val r28: R28Journal? = null, val r33: R33Journal? = null, val r38b: R38BJournal? = null, val r38c: R38CJournal? = null) {
    val saved get() = records.filter { !it.deleted && it.parent == null && it.kind != SourceKind.DEFAULT }
    val main get() = records.first { it.id == active && !it.deleted }
    val extra get() = records.firstOrNull { !it.deleted && it.parent == active }
    override fun toString() = "SourceLibrary([redacted])"
}
interface SourceLoader {
    suspend fun fetch(address: String): String
    suspend fun defaultText(): String
}
class SourceProblem(val code: String) : IllegalArgumentException(code)

/** Pure source operations. Persistence and publication are owned by PreviewState's transaction. */
class SourceOperations(private val allocate: () -> OpaqueId) {
    fun bootstrap(data: PreviewData): SourceLibrary {
        data.library?.let { return it }
        val default = PublicM3u.source
        val channels = data.channels.filter { it.definition != null }
        val categories = channels.flatMap { it.groups }.distinct().mapIndexed { index, label -> CategoryRecord(CategoryId(allocate()), CategoryLabel.fromRaw(label), index.toLong()) }
        val rows = channels.flatMapIndexed { index, ch -> ch.groups.mapIndexed { group, label ->
            ChannelContribution(default, index.toLong() * 1000 + group, ch.definition!!, CategoryLabel.fromRaw(label), ch.name, ch.logo, ch.providerId)
        } }
        val memberships = rows.map { row -> MembershipRecord(MembershipId(allocate()), ChannelId(channels.first { it.definition == row.definition }.id), default, categories.first { it.label == row.category }.id) }
        val snapshot = CatalogSnapshot(listOf(SourceRecord(default, SourceRole.MAIN, true)), channels.mapIndexed { i, ch -> ChannelRecord(ChannelId(ch.id), ch.definition!!, i.toLong()) }, memberships, categories)
        return SourceLibrary(listOf(SavedSource(default, "trlist", SourceKind.DEFAULT, PublicM3u.url, rows)), default, snapshot)
    }
    fun validate(library: SourceLibrary, name: String, address: String, editing: SourceId? = null, extra: Boolean = false) {
        if (name.trim().isEmpty() || name.trim().length > 120) throw SourceProblem("name")
        if (address.length > 8192 || ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, Address.fromForm(address)) == null) throw SourceProblem("url")
        val key = ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, Address.fromForm(address))
        if (library.records.any { !it.deleted && it.kind == SourceKind.URL && it.id != editing && (if (extra) it.parent == library.active else it.parent == null) && ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, Address.fromForm(it.address)) == key }) throw SourceProblem("duplicate")
        if (editing != null && library.records.none { it.id == editing && !it.deleted }) throw SourceProblem("missing")
        if (editing == null && library.records.count { !it.deleted } >= 64) throw SourceProblem("limit")
    }
    fun save(data: PreviewData, name: String, address: String, text: String, editing: SourceId? = null, extra: Boolean = false, file: Boolean = false): PreviewData {
        val library = bootstrap(data)
        if (!file) validate(library, name, address, editing, extra)
        else if (name.isBlank() || name.length > 120 || library.records.count { !it.deleted } >= 64) throw SourceProblem("name")
        val id = editing ?: (if (extra) library.records.firstOrNull { it.parent == library.active }?.id else null) ?: SourceId(allocate())
        val source = parsed(id, name.trim(), if (file) SourceKind.FILE else SourceKind.URL, address.trim(), text, if (extra) library.active else null)
        val records = library.records.filter { it.id != id } + source
        return rebuild(data, library.copy(records = records, active = if (extra) library.active else id))
    }
    fun parsed(id: SourceId, name: String, kind: SourceKind, address: String, text: String, parent: SourceId? = null): SavedSource {
        if (text.length > 4 * 1024 * 1024 || !text.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")) throw SourceProblem("content")
        val parsed = PublicM3u.parse(text)
        // The existing parser deliberately blocks relative/unknown playback definitions.
        val valid = parsed.rows.filter { ComparisonV1.playbackKey(it.definition) != null }
        if (valid.isEmpty() || valid.size > 5000) throw SourceProblem("content")
        return SavedSource(id, name, kind, address, valid.map { it.copy(sourceId = id) }, parent, rawCount = parsed.rawRecords,
            skippedCount = parsed.skipped.values.sum() + parsed.rows.size - valid.size)
    }
    fun activate(data: PreviewData, id: SourceId): PreviewData {
        val library = bootstrap(data)
        require(library.records.any { it.id == id && !it.deleted && it.parent == null })
        return rebuild(data, library.copy(active = id))
    }
    fun renameFile(data: PreviewData, id: SourceId, name: String): PreviewData {
        if (name.trim().length !in 1..120) throw SourceProblem("name")
        val library = bootstrap(data)
        require(library.records.any { it.id == id && it.kind == SourceKind.FILE && !it.deleted })
        return rebuild(data, library.copy(records = library.records.map { if (it.id == id) it.copy(name = name.trim()) else it }, active = id))
    }
    fun default(data: PreviewData, text: String): PreviewData {
        val library = bootstrap(data)
        val source = R38CProfileTransition.defaultSource(R38BProfileTransition.defaultSource(R33ProfileTransition.defaultSource(R28ProfileTransition.defaultSource(R26ProfileTransition.defaultSource(VerifiedDefinitionTransition.defaultSource(parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text), library.transition), library.r26), library.r28), library.r33), library.r38b), library.r38c)
        return rebuild(data, library.copy(records = library.records.filter { it.id != source.id } + source, active = source.id))
    }
    fun delete(data: PreviewData, id: SourceId): PreviewData {
        val library = bootstrap(data)
        if (id == library.active) throw SourceProblem("active-delete")
        return rebuild(data, library.copy(records = library.records.map { if (it.id == id || it.parent == id) it.copy(deleted = true, rows = emptyList()) else it }))
    }
    fun removeExtra(data: PreviewData): PreviewData {
        val library = bootstrap(data)
        return rebuild(data, library.copy(records = library.records.map { if (it.parent == library.active) it.copy(deleted = true, rows = emptyList()) else it }))
    }
    fun rebuild(data: PreviewData, library: SourceLibrary): PreviewData {
        // The pilot's unchanged source is already a complete committed snapshot. Replanning
        // duplicate seed rows would unnecessarily allocate memberships on an unchanged default.
        if ((library.transition != null || VerifiedDefinitionTransition.legacyDefault(library)) && data.library?.let { old -> old.active == library.active &&
                old.records.toSet() == library.records.toSet() && old.snapshot == library.snapshot && old.transition == library.transition && old.r26 == library.r26 && old.r28 == library.r28 && old.r33 == library.r33 && old.r38b == library.r38b && old.r38c == library.r38c } == true) {
            return data.copy(library = library)
        }
        if (library.r38c != null) throw TransitionProblem("r38c-layer-protected")
        if (library.r38b != null) throw TransitionProblem("r38b-layer-must-rollback-first")
        if (library.r33 != null) throw TransitionProblem("r33-layer-must-rollback-first")
        if (library.r28 != null) throw TransitionProblem("r28-layer-must-rollback-first")
        if (library.r26 != null) throw TransitionProblem("r26-layer-must-rollback-first")
        val sources = library.records.map { SourceRecord(it.id, if (it.parent == null) SourceRole.MAIN else SourceRole.SUPPLEMENT,
            !it.deleted && (it.id == library.active || it.parent == library.active), it.parent) }
        val rows = library.records.filter { !it.deleted }.flatMap { it.rows }
        if (rows.size > 10000) throw SourceProblem("limit")
        val count = rows.size
        val plan = CatalogPlanner.plan(library.snapshot.copy(sources = sources), rows, IdentitySupply(
            List(count) { ChannelId(allocate()) }, List(count) { MembershipId(allocate()) }, List(count) { CategoryId(allocate()) }))
        val labels = plan.categories.associate { it.id to when (val label = it.label) { is CategoryLabel.Exact -> label.text; CategoryLabel.Uncategorized -> "Kategorisiz" } }
        val active = plan.catalog.filter { it.hasUsableActiveMembership }.map { ch ->
            val row = ch.contributions.first { contribution -> sources.first { it.id == contribution.input.sourceId }.inActiveSet }.input
            val groups = ch.categoryIds.map { labels.getValue(it) }
            PreviewChannel(ch.id.value, ch.name ?: "Adsız kanal", groups.first(), ch.logo.orEmpty(), definition = row.definition, groups = groups, providerId = row.providerHint.orEmpty())
        }
        val activeIds = active.map { it.id }.toSet()
        val archived = data.channels.filter { it.id !in activeIds }.map { it.copy(available = false) }
        val snapshot = CatalogSnapshot(sources, plan.channels.map { it.record }, plan.memberships.map { it.record.copy(isCurrent = it.isCurrentInPlan) }, plan.categories)
        val categories = data.categories + active.flatMap { it.groups }.filter { it !in data.categories }.distinct()
        return data.copy(channels = active + archived, categories = categories, library = library.copy(snapshot = snapshot))
    }
}

/** Versioned envelope stored in the existing Room sources column in the same atomic row. */
object LibraryCodec {
    private fun pack(vararg values: String) = PreviewCodec.pack(values.toList())
    private fun id(value: SourceId) = PreviewCodec.id(value.value)
    private fun sid(value: String) = SourceId(PreviewCodec.id(value))
    internal fun definition(d: PlaybackDefinition): String {
        require(d.protocol == null && d.drm == null && d.authorityScope == null && d.options.isEmpty() && d.isLossless) { "Unsupported codec definition" }
        return pack(d.address.raw, PreviewCodec.pack(d.headers.flatMap { listOf(it.name, it.value) }), d.mimeType.orEmpty(), PreviewCodec.pack(d.unknownRequiredDirectives))
    }
    internal fun definition(value: String): PlaybackDefinition {
        val f = PreviewCodec.unpack(value); require(f.size == 4)
        val headers = PreviewCodec.unpack(f[1]); require(headers.size % 2 == 0)
        return PlaybackDefinition(Address.fromForm(f[0]), headers.chunked(2).map { Header(it[0], it[1]) }, mimeType = f[2].ifEmpty { null }, unknownRequiredDirectives = PreviewCodec.unpack(f[3]))
    }
    private fun label(value: CategoryLabel) = (value as? CategoryLabel.Exact)?.text.orEmpty()
    fun channels(values: List<PreviewChannel>) = PreviewCodec.pack(values.map { ch -> pack(PreviewCodec.id(ch.id), ch.name, ch.logo, PreviewCodec.pack(ch.groups), ch.providerId, ch.definition?.let(::definition).orEmpty(), ch.available.toString(), ch.video.toString(), PreviewCodec.pack(ch.sourceReferences.map(PreviewCodec::id))) })
    fun channels(value: String) = PreviewCodec.unpack(value).map { row ->
        val f = PreviewCodec.unpack(row); require(f.size == 9)
        val groups = PreviewCodec.unpack(f[3]); require(groups.isNotEmpty())
        PreviewChannel(PreviewCodec.id(f[0]), f[1], groups.first(), f[2], f[7].toBooleanStrict(), f[5].ifEmpty { null }?.let(::definition), groups, f[4], f[6].toBooleanStrict(), PreviewCodec.unpack(f[8]).map(PreviewCodec::id))
    }
    fun encode(data: PreviewData): String {
        val library = data.library ?: return pack("BT-PROFILE-1", PreviewCodec.pack(data.sources), channels(data.channels))
        library.r38c?.let { journal ->
            require(library.r33 == null && library.r38b == null && library.r28 == journal.previous && library.r26 == journal.previous.previous && library.transition == journal.previous.previous.previous)
            require((journal.manifest == R38CProfileTransition.normalManifest && journal.target.id == R38CProfileTransition.targetId) ||
                (journal.manifest == R38CProfileTransition.fixtureManifest && journal.target.id == R38CProfileTransition.syntheticId))
            return pack(R38CProfileTransition.envelope, encode(data.copy(library = library.copy(r38c = null))), R38CJournalCodec.encode(journal))
        }
        library.r38b?.let { journal ->
            require(library.r33 == null && library.r28 == journal.previous && library.r26 == journal.previous.previous && library.transition == journal.previous.previous.previous)
            require(journal.manifest == R38BProfileTransition.fixtureManifest && journal.target.id == R38BProfileTransition.targetId)
            return pack(R38BProfileTransition.envelope, encode(data.copy(library = library.copy(r38b = null))), R38BJournalCodec.encode(journal))
        }
        library.r33?.let { journal ->
            require(library.r28 == journal.previous && library.r26 == journal.previous.previous && library.transition == journal.previous.previous.previous)
            return pack("BT-SOURCES-5", encode(data.copy(library = library.copy(r33 = null))), R33JournalCodec.encode(journal))
        }
        library.r28?.let { journal ->
            require(library.r26 == journal.previous && library.transition == journal.previous.previous)
            return pack("BT-SOURCES-4", encode(data.copy(library = library.copy(r28 = null))), R28JournalCodec.encode(journal))
        }
        library.r26?.let { journal ->
            require(library.transition == journal.previous)
            return pack("BT-SOURCES-3", encode(data.copy(library = library.copy(r26 = null))), R26JournalCodec.encode(journal))
        }
        library.transition?.let { journal ->
            return pack("BT-SOURCES-2", encode(data.copy(library = library.copy(transition = null))), TransitionJournalCodec.encode(journal))
        }
        val snapshot = library.snapshot
        val records = PreviewCodec.pack(library.records.map { source -> pack(id(source.id), source.name, source.kind.name, source.address, source.parent?.let(::id).orEmpty(), source.deleted.toString(), source.rawCount.toString(), source.skippedCount.toString(),
            PreviewCodec.pack(source.rows.map { row -> pack(row.rowOrder.toString(), definition(row.definition), label(row.category), row.name.orEmpty(), row.logo.orEmpty(), row.providerHint.orEmpty()) })) })
        val ch = PreviewCodec.pack(snapshot.channels.map { pack(PreviewCodec.id(it.id.value), definition(it.definition), it.firstSeen.toString()) })
        val cat = PreviewCodec.pack(snapshot.categories.map { pack(PreviewCodec.id(it.id.value), label(it.label), it.firstSeen.toString()) })
        val member = PreviewCodec.pack(snapshot.memberships.map { pack(PreviewCodec.id(it.id.value), PreviewCodec.id(it.channelId.value), id(it.sourceId), PreviewCodec.id(it.categoryId.value), it.isCurrent.toString()) })
        return pack("BT-SOURCES-1", PreviewCodec.pack(data.sources), records, id(library.active), ch, cat, member, channels(data.channels))
    }
    fun decode(value: String, base: PreviewData): PreviewData {
        val f = PreviewCodec.unpack(value)
        if (f.firstOrNull() == R38CProfileTransition.envelope) {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-4")
            val decoded = decode(f[1], base)
            val journal = R38CJournalCodec.decode(f[2])
            require(decoded.library!!.r33 == null && decoded.library.r38b == null && decoded.library.r28 == journal.previous && decoded.library.r26 == journal.previous.previous && decoded.library.transition == journal.previous.previous.previous)
            require((journal.manifest == R38CProfileTransition.normalManifest && journal.target.id == R38CProfileTransition.targetId) ||
                (journal.manifest == R38CProfileTransition.fixtureManifest && journal.target.id == R38CProfileTransition.syntheticId))
            return decoded.copy(library = decoded.library.copy(r38c = journal))
        }
        if (f.firstOrNull() == R38BProfileTransition.envelope) {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-4")
            val decoded = decode(f[1], base)
            val journal = R38BJournalCodec.decode(f[2])
            require(decoded.library!!.r33 == null && decoded.library.r28 == journal.previous && decoded.library.r26 == journal.previous.previous && decoded.library.transition == journal.previous.previous.previous)
            return decoded.copy(library = decoded.library.copy(r38b = journal))
        }
        if (f.firstOrNull() == "BT-SOURCES-5") {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-4")
            val decoded = decode(f[1], base)
            val journal = R33JournalCodec.decode(f[2])
            require(decoded.library!!.r28 == journal.previous && decoded.library.r26 == journal.previous.previous && decoded.library.transition == journal.previous.previous.previous)
            return decoded.copy(library = decoded.library.copy(r33 = journal))
        }
        if (f.firstOrNull() == "BT-SOURCES-4") {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-3")
            val decoded = decode(f[1], base)
            val journal = R28JournalCodec.decode(f[2])
            require(decoded.library!!.r26 == journal.previous && decoded.library.transition == journal.previous.previous)
            return decoded.copy(library = decoded.library.copy(r28 = journal))
        }
        if (f.firstOrNull() == "BT-SOURCES-3") {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-2")
            val decoded = decode(f[1], base)
            val journal = R26JournalCodec.decode(f[2])
            require(decoded.library!!.transition == journal.previous)
            return decoded.copy(library = decoded.library.copy(r26 = journal))
        }
        if (f.firstOrNull() == "BT-SOURCES-2") {
            require(f.size == 3 && PreviewCodec.unpack(f[1]).firstOrNull() == "BT-SOURCES-1")
            val decoded = decode(f[1], base)
            return decoded.copy(library = decoded.library!!.copy(transition = TransitionJournalCodec.decode(f[2])))
        }
        require(f.firstOrNull()?.startsWith("BT-SOURCES-") != true || f.firstOrNull() == "BT-SOURCES-1") { "Unsupported source envelope" }
        if (f.firstOrNull() == "BT-PROFILE-1") { require(f.size == 3); return base.copy(sources = PreviewCodec.unpack(f[1]), channels = channels(f[2])) }
        if (f.firstOrNull() != "BT-SOURCES-1") return base.copy(sources = f)
        require(f.size == 8)
        val records = PreviewCodec.unpack(f[2]).map { row ->
            val r = PreviewCodec.unpack(row); require(r.size == 9)
            val source = sid(r[0])
            SavedSource(source, r[1], SourceKind.valueOf(r[2]), r[3], PreviewCodec.unpack(r[8]).map { raw ->
                val v = PreviewCodec.unpack(raw); require(v.size == 6)
                ChannelContribution(source, v[0].toLong(), definition(v[1]), CategoryLabel.fromRaw(v[2]), v[3], v[4], v[5])
            }, r[4].ifEmpty { null }?.let(::sid), r[5].toBooleanStrict(), r[6].toInt(), r[7].toInt())
        }
        val active = sid(f[3])
        val sources = records.map { SourceRecord(it.id, if (it.parent == null) SourceRole.MAIN else SourceRole.SUPPLEMENT, !it.deleted && (it.id == active || it.parent == active), it.parent) }
        val ch = PreviewCodec.unpack(f[4]).map { row -> val r = PreviewCodec.unpack(row); require(r.size == 3); ChannelRecord(ChannelId(PreviewCodec.id(r[0])), definition(r[1]), r[2].toLong()) }
        val cat = PreviewCodec.unpack(f[5]).map { row -> val r = PreviewCodec.unpack(row); require(r.size == 3); CategoryRecord(CategoryId(PreviewCodec.id(r[0])), CategoryLabel.fromRaw(r[1]), r[2].toLong()) }
        val member = PreviewCodec.unpack(f[6]).map { row -> val r = PreviewCodec.unpack(row); require(r.size == 5); MembershipRecord(MembershipId(PreviewCodec.id(r[0])), ChannelId(PreviewCodec.id(r[1])), sid(r[2]), CategoryId(PreviewCodec.id(r[3])), r[4].toBooleanStrict()) }
        return base.copy(sources = PreviewCodec.unpack(f[1]), channels = channels(f[7]), library = SourceLibrary(records, active, CatalogSnapshot(sources, ch, member, cat)))
    }
}
