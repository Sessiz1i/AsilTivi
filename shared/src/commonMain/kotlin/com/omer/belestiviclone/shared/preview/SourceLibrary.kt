package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

enum class SourceKind { DEFAULT, URL, FILE, XTREAM, STALKER, COUNTRY }
data class SavedSource(
    val id: SourceId, val name: String, val kind: SourceKind, val address: String,
    val rows: List<ChannelContribution>, val parent: SourceId? = null, val deleted: Boolean = false,
    val rawCount: Int = rows.size, val skippedCount: Int = 0,
) {
    fun kindBadge(): String = when (kind) {
        SourceKind.DEFAULT -> "VARSAYILAN"
        SourceKind.URL -> "M3U URL"
        SourceKind.FILE -> "DOSYA"
        SourceKind.XTREAM -> "XTREAM"
        SourceKind.STALKER -> "STALKER / MAC"
        SourceKind.COUNTRY -> "ÜLKE LİSTESİ"
    }

    fun maskedAddress(): String = when (kind) {
        SourceKind.FILE -> "Uygulamaya alınmış dosya kopyası"
        SourceKind.DEFAULT -> "Yerleşik Türkiye Kanal Listesi (trlist.m3u)"
        SourceKind.COUNTRY -> "Ülke Paketi · ${address.substringAfterLast('/')}"
        else -> address
            .replace(Regex("//[^/@]*@"), "//***@")
            .replace(Regex("(?i)(password=)[^&\\s]+"), "$1***")
    }

    override fun toString() = "SavedSource([redacted])"
}
data class SourceLibrary(val records: List<SavedSource>, val active: SourceId, val snapshot: CatalogSnapshot) {
    val saved get() = records.filter { !it.deleted && it.parent == null && it.kind != SourceKind.DEFAULT }
    val main get() = records.first { it.id == active && !it.deleted }
    val extra get() = records.firstOrNull { !it.deleted && it.parent == active }
    override fun toString() = "SourceLibrary([redacted])"
}
interface SourceLoader {
    suspend fun fetch(address: String): String
    suspend fun fetch(address: String, headers: Map<String, String>): String = fetch(address)
    suspend fun defaultText(): String
}
class SourceProblem(val code: String) : IllegalArgumentException(code)

/** Pure source operations. Persistence and publication are owned by PreviewState's transaction. */
class SourceOperations(private val allocate: () -> OpaqueId) {
    fun bootstrap(data: PreviewData): SourceLibrary {
        data.library?.let { return it }
        val default = PublicM3u.source
        val presetIds = CountryListsCatalog.presets.filter { it.code != "TR" }
            .flatMap { CountryListsCatalog.buildChannelsForPreset(it) }.map { it.id }.toSet()
        val channels = data.channels.filter { it.definition != null && it.id !in presetIds }
        val categories = channels.flatMap { it.groups }.distinct().mapIndexed { index, label -> CategoryRecord(CategoryId(allocate()), CategoryLabel.fromRaw(label), index.toLong()) }
        val rows = channels.flatMapIndexed { index, ch -> ch.groups.mapIndexed { group, label ->
            ChannelContribution(default, index.toLong() * 1000 + group, ch.definition!!, CategoryLabel.fromRaw(label), ch.name, ch.logo, ch.providerId, ch.tvgName)
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
        val source = parsed(PublicM3u.source, "trlist", SourceKind.DEFAULT, PublicM3u.url, text)
        return rebuild(data, library.copy(records = library.records.filter { it.id != source.id } + source, active = source.id))
    }
    fun saveXtream(
        data: PreviewData,
        name: String,
        serverUrl: String,
        username: String,
        text: String,
        editing: SourceId? = null,
    ): PreviewData {
        val library = bootstrap(data)
        val cleanBase = XtreamCodesClient.normalizeBaseUrl(serverUrl)
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || cleanUser.length > 120) throw SourceProblem("credentials")
        val resolvedName = name.trim().ifEmpty {
            val host = cleanBase.substringAfter("://").substringBefore('/').substringBefore(':')
            "Xtream ($host · $cleanUser)"
        }
        if (resolvedName.length > 120) throw SourceProblem("name")
        if (editing == null && library.records.count { !it.deleted } >= 64) throw SourceProblem("limit")
        val addressRecord = "$cleanBase?user=$cleanUser"
        val id = editing ?: library.records.firstOrNull {
            !it.deleted && it.kind == SourceKind.XTREAM && it.address.equals(addressRecord, ignoreCase = true)
        }?.id ?: SourceId(allocate())
        val source = parsed(id, resolvedName, SourceKind.XTREAM, addressRecord, text)
        val records = library.records.filter { it.id != id } + source
        return rebuild(data, library.copy(records = records, active = id))
    }
    fun saveStalker(
        data: PreviewData,
        name: String,
        portalUrl: String,
        macAddress: String,
        text: String,
        editing: SourceId? = null,
    ): PreviewData {
        val library = bootstrap(data)
        val cleanPortal = StalkerPortalClient.normalizePortalBaseUrl(portalUrl)
        val cleanMac = StalkerPortalClient.normalizeMac(macAddress)
        val resolvedName = name.trim().ifEmpty {
            val host = cleanPortal.substringAfter("://").substringBefore('/').substringBefore(':')
            "Stalker ($host · $cleanMac)"
        }
        if (resolvedName.length > 120) throw SourceProblem("name")
        if (editing == null && library.records.count { !it.deleted } >= 64) throw SourceProblem("limit")
        val addressRecord = "$cleanPortal?mac=$cleanMac"
        val id = editing ?: library.records.firstOrNull {
            !it.deleted && it.kind == SourceKind.STALKER && it.address.equals(addressRecord, ignoreCase = true)
        }?.id ?: SourceId(allocate())
        val source = parsed(id, resolvedName, SourceKind.STALKER, addressRecord, text)
        val records = library.records.filter { it.id != id } + source
        return rebuild(data, library.copy(records = records, active = id))
    }
    fun saveCountry(
        data: PreviewData,
        preset: CountryListPreset,
        text: String = preset.builtinM3u,
    ): PreviewData {
        if (preset.code.equals("TR", ignoreCase = true)) return default(data, text)
        val library = bootstrap(data)
        val addressRecord = preset.remoteUrl
        val existingId = library.records.firstOrNull {
            !it.deleted && it.kind == SourceKind.COUNTRY && it.address == addressRecord
        }?.id
        val id = existingId ?: SourceId(allocate())
        val source = parsed(id, "${preset.flag} ${preset.name}", SourceKind.COUNTRY, addressRecord, text)
        val records = library.records.filter { it.id != id } + source
        return rebuild(data, library.copy(records = records, active = id))
    }
    fun applyCountryLists(data: PreviewData, enabledCodes: Collection<String>): PreviewData {
        val formatted = CountryListsCatalog.formatEnabledCodes(enabledCodes)
        val allPresetChannels = CountryListsCatalog.presets
            .filter { it.code != "TR" }
            .flatMap { CountryListsCatalog.buildChannelsForPreset(it) }
        val presetIds = allPresetChannels.map { it.id }.toSet()
        val baseChannels = data.channels.filter { it.id !in presetIds }
        val existingKeys = baseChannels.filter { it.available }.mapNotNull { it.definition?.let(ComparisonV1::playbackKey) }.toSet()
        val activeCodes = CountryListsCatalog.parseEnabledCodes(formatted)
        val addedChannels = CountryListsCatalog.presets
            .filter { it.code in activeCodes }
            .flatMap { CountryListsCatalog.buildChannelsForPreset(it) }
            .filter { ch -> ch.definition?.let(ComparisonV1::playbackKey) !in existingKeys }
        val mergedChannels = baseChannels + addedChannels
        val newGroups = addedChannels.flatMap { it.groups }.distinct()
        val updatedCategories = CategoryNormalizer.deduplicateCategories(data.categories + newGroups, normalizeAliases = true)
        return data.withPref("countryLists", formatted).copy(
            channels = mergedChannels,
            categories = updatedCategories,
        )
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
            PreviewChannel(ch.id.value, ch.name ?: "Adsız kanal", groups.first(), ch.logo.orEmpty(), definition = row.definition, groups = groups, providerId = row.providerHint.orEmpty(), tvgName = row.tvgName)
        }
        val activeIds = active.map { it.id }.toSet()
        val archived = data.channels.filter { it.id !in activeIds }.map { it.copy(available = false) }
        val snapshot = CatalogSnapshot(sources, plan.channels.map { it.record }, plan.memberships.map { it.record.copy(isCurrent = it.isCurrentInPlan) }, plan.categories)
        val categories = CategoryNormalizer.deduplicateCategories(data.categories + active.flatMap { it.groups }.distinct(), normalizeAliases = true)
        val baseRebuilt = data.copy(channels = active + archived, categories = categories, library = library.copy(snapshot = snapshot))
        val enabledCountries = CountryListsCatalog.parseEnabledCodes(data.preferences["countryLists"])
        return if (enabledCountries.isEmpty()) baseRebuilt else applyCountryLists(baseRebuilt, enabledCountries)
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
    fun channels(values: List<PreviewChannel>) = PreviewCodec.pack(values.map { ch -> pack(PreviewCodec.id(ch.id), ch.name, ch.logo, PreviewCodec.pack(ch.groups), ch.providerId, ch.definition?.let(::definition).orEmpty(), ch.available.toString(), ch.video.toString(), PreviewCodec.pack(ch.sourceReferences.map(PreviewCodec::id)), ch.tvgName) })
    fun channels(value: String) = PreviewCodec.unpack(value).map { row ->
        val f = PreviewCodec.unpack(row); require(f.size in 9..10)
        val groups = PreviewCodec.unpack(f[3]); require(groups.isNotEmpty())
        PreviewChannel(PreviewCodec.id(f[0]), f[1], groups.first(), f[2], f[7].toBooleanStrict(), f[5].ifEmpty { null }?.let(::definition), groups, f[4], f[6].toBooleanStrict(), PreviewCodec.unpack(f[8]).map(PreviewCodec::id), f.getOrElse(9) { "" })
    }
    fun encode(data: PreviewData): String {
        val library = data.library ?: return pack("BT-PROFILE-1", PreviewCodec.pack(data.sources), channels(data.channels))
        val snapshot = library.snapshot
        val records = PreviewCodec.pack(library.records.map { source -> pack(id(source.id), source.name, source.kind.name, source.address, source.parent?.let(::id).orEmpty(), source.deleted.toString(), source.rawCount.toString(), source.skippedCount.toString(),
            PreviewCodec.pack(source.rows.map { row -> pack(row.rowOrder.toString(), definition(row.definition), label(row.category), row.name.orEmpty(), row.logo.orEmpty(), row.providerHint.orEmpty(), row.tvgName) })) })
        val ch = PreviewCodec.pack(snapshot.channels.map { pack(PreviewCodec.id(it.id.value), definition(it.definition), it.firstSeen.toString()) })
        val cat = PreviewCodec.pack(snapshot.categories.map { pack(PreviewCodec.id(it.id.value), label(it.label), it.firstSeen.toString()) })
        val member = PreviewCodec.pack(snapshot.memberships.map { pack(PreviewCodec.id(it.id.value), PreviewCodec.id(it.channelId.value), id(it.sourceId), PreviewCodec.id(it.categoryId.value), it.isCurrent.toString()) })
        return pack("BT-SOURCES-1", PreviewCodec.pack(data.sources), records, id(library.active), ch, cat, member, channels(data.channels))
    }
    private fun legacyInner(f: List<String>): String? {
        val tag = f.firstOrNull() ?: return null
        if (!tag.startsWith("BT-SOURCES-") || tag == "BT-SOURCES-1" || f.size < 2) return null
        val inner = runCatching { PreviewCodec.unpack(f[1]).firstOrNull() }.getOrNull() ?: return null
        return f[1].takeIf { inner.startsWith("BT-SOURCES-") }
    }
    fun decode(value: String, base: PreviewData): PreviewData {
        val f = PreviewCodec.unpack(value)
        // BT-SOURCES-2..7 were debug transition journals wrapped around a complete inner envelope.
        // The inner BT-SOURCES-1 row already holds the committed definitions; journals are dropped.
        legacyInner(f)?.let { return decode(it, base) }
        require(f.firstOrNull()?.startsWith("BT-SOURCES-") != true || f.firstOrNull() == "BT-SOURCES-1") { "Unsupported source envelope" }
        if (f.firstOrNull() == "BT-PROFILE-1") { require(f.size == 3); return base.copy(sources = PreviewCodec.unpack(f[1]), channels = channels(f[2])) }
        if (f.firstOrNull() != "BT-SOURCES-1") return base.copy(sources = f)
        require(f.size == 8)
        val records = PreviewCodec.unpack(f[2]).map { row ->
            val r = PreviewCodec.unpack(row); require(r.size == 9)
            val source = sid(r[0])
            SavedSource(source, r[1], SourceKind.valueOf(r[2]), r[3], PreviewCodec.unpack(r[8]).map { raw ->
                val v = PreviewCodec.unpack(raw); require(v.size in 6..7)
                ChannelContribution(source, v[0].toLong(), definition(v[1]), CategoryLabel.fromRaw(v[2]), v[3], v[4], v[5], v.getOrElse(6) { "" })
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
