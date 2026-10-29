package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*

data class M3uResult(val rawRecords: Int, val rows: List<ChannelContribution>, val skipped: Map<String, Int>)

/** One bounded public source. Credentials and complete playback definitions never enter logs. */
object PublicM3u {
    const val url = "https://raw.githubusercontent.com/impresents/my-iptv-list/refs/heads/main/TiviListe.m3u"
    val source = SourceId(OpaqueId(0x411535243, 1))
    val profile = OpaqueId(0x411505246, 1)
    fun extractEpgUrl(text: String): String? {
        val header = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.firstOrNull { it.startsWith("#EXTM3U", ignoreCase = true) } ?: return null
        fun headerAttr(name: String) = Regex("(?:^|\\s)${Regex.escape(name)}=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.trim().orEmpty()
        return listOf("x-tvg-url", "url-tvg", "tvg-url").firstNotNullOfOrNull { key ->
            headerAttr(key).takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
    }
    fun parse(text: String, resolve: (String) -> String? = { it.takeIf { v -> v.startsWith("http://", true) || v.startsWith("https://", true) } }): M3uResult {
        var raw = 0
        var metadata: String? = null
        var headers = mutableListOf<Header>()
        var unsupported = mutableListOf<String>()
        val rows = mutableListOf<ChannelContribution>()
        val skipped = mutableMapOf<String, Int>()
        fun skip(reason: String) { skipped[reason] = (skipped[reason] ?: 0) + 1 }
        fun attr(meta: String, name: String) = Regex("(?:^|\\s)${Regex.escape(name)}=\"([^\"]*)\"").find(meta)?.groupValues?.get(1).orEmpty()
        for (line in text.removePrefix("\uFEFF").lineSequence().map { it.trim() }) {
            when {
                line.startsWith("#EXTINF:") -> {
                    if (metadata != null) skip("missing-uri")
                    raw++; metadata = line; headers = mutableListOf(); unsupported = mutableListOf()
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val option = line.substringAfter(':'); val key = option.substringBefore('=')
                    val name = when (key) { "http-user-agent" -> "User-Agent"; "http-referrer", "http-referer" -> "Referer"; else -> null }
                    if (name == null) unsupported += key else headers += Header(name, option.substringAfter('=', ""))
                }
                line.startsWith("#KODIPROP:") || line.startsWith("#EXTHTTP:") -> unsupported += line.substringBefore(':')
                line.isBlank() || line.startsWith('#') -> Unit
                metadata != null -> {
                    val meta = metadata!!; metadata = null
                    // Commas inside quoted attributes are not the display-name separator.
                    var quoted = false
                    val comma = meta.indices.firstOrNull { i -> if (meta[i] == '"') quoted = !quoted; meta[i] == ',' && !quoted }
                    val uri = resolve(line)
                    if (uri == null || '|' in uri) { skip("unsupported-or-relative-uri"); continue }
                    val definition = PlaybackDefinition(Address.fromForm(uri), headers.toList(), unknownRequiredDirectives = unsupported.toList())
                    if (ComparisonV1.addressKey(definition.address) == null) { skip("invalid-http-uri"); continue }
                    val name = comma?.let { meta.substring(it + 1).trim() }.orEmpty().ifEmpty { attr(meta, "tvg-name") }.ifEmpty { "Adsız kanal" }
                    rows += ChannelContribution(source, raw.toLong(), definition, CategoryLabel.fromRaw(attr(meta, "group-title")), name, attr(meta, "tvg-logo"), attr(meta, "tvg-id"), attr(meta, "tvg-name"))
                }
                else -> skip("orphan-uri")
            }
        }
        if (metadata != null) skip("missing-uri")
        return M3uResult(raw, rows, skipped)
    }
    fun catalog(parsed: M3uResult, identities: IdentitySupply): List<PreviewChannel> {
        val plan = CatalogPlanner.plan(CatalogSnapshot(sources = listOf(SourceRecord(source, SourceRole.MAIN, true))), parsed.rows, identities)
        val labels = plan.categories.associate { it.id to when (val label = it.label) { is CategoryLabel.Exact -> label.text; CategoryLabel.Uncategorized -> "Kategorisiz" } }
        return plan.catalog.map { ch ->
            val row = ch.contributions.first().input
            val groups = ch.categoryIds.map { labels.getValue(it) }
            val isRadio = groups.any { it.equals("Radyo", ignoreCase = true) } ||
                (ch.name?.contains("Radyo", ignoreCase = true) == true) ||
                (ch.name?.contains(" FM", ignoreCase = true) == true)
            PreviewChannel(ch.id.value, ch.name ?: "Adsız kanal", groups.first(), ch.logo.orEmpty(), definition = row.definition, groups = groups, providerId = row.providerHint.orEmpty(), tvgName = row.tvgName, isRadio = isRadio)
        }
    }
}

