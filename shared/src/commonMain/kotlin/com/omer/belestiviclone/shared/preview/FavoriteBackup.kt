package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId

data class FavoriteReference(val id: OpaqueId, val name: String, val sources: List<OpaqueId>)
data class FavoriteArchive(val profile: OpaqueId, val favorites: List<FavoriteReference>)

/** Clone-only v1: order is array order. No playback URL, header, password, or full restore. */
object FavoriteBackup {
    const val maxBytes = 1024 * 1024
    fun encode(data: PreviewData): String {
        val entries = data.favorites.distinct().map { id ->
            val sourceIds = (data.library?.snapshot?.memberships.orEmpty().filter { it.channelId.value == id }.map { it.sourceId.value } + data.channels.firstOrNull { it.id == id }?.sourceReferences.orEmpty()).distinct()
            "{\"id\":${quote(PreviewCodec.id(id))},\"name\":${quote(data.channels.firstOrNull { it.id == id }?.name?.take(512)?.ifEmpty { "Erişilemiyor" } ?: "Erişilemiyor")},\"sources\":[${sourceIds.joinToString(",") { quote(PreviewCodec.id(it)) }}]}"
        }
        return "{\"app\":\"com.omer.belestiviclone\",\"schemaVersion\":1,\"profile\":${quote(PreviewCodec.id(data.profileId))},\"favorites\":[${entries.joinToString(",")}] }"
            .also { require(it.encodeToByteArray().size <= maxBytes) }
    }
    fun decode(text: String): FavoriteArchive {
        require(text.encodeToByteArray().size <= maxBytes)
        val root = JsonReader(text).read() as? Map<*, *> ?: error("object")
        require(root["app"] == "com.omer.belestiviclone" && root["schemaVersion"] == 1L)
        val profile = PreviewCodec.id(root["profile"] as? String ?: error("profile"))
        val rows = root["favorites"] as? List<*> ?: error("favorites")
        require(rows.size <= 5000)
        val entries = rows.map { value ->
            val row = value as? Map<*, *> ?: error("entry")
            val id = PreviewCodec.id(row["id"] as? String ?: error("id"))
            val name = row["name"] as? String ?: error("name"); require(name.length in 1..512)
            val sources = row["sources"] as? List<*> ?: error("sources"); require(sources.size <= 64)
            FavoriteReference(id, name, sources.map { PreviewCodec.id(it as? String ?: error("source")) }.distinct())
        }
        // Unknown fields are ignored only after bounded, strict JSON parsing. Unknown versions fail.
        return FavoriteArchive(profile, entries.distinctBy { it.id })
    }
    fun merge(data: PreviewData, archive: FavoriteArchive): PreviewData {
        require(archive.profile == data.profileId) { "Different profile" }
        val unknown = archive.favorites.filter { row -> data.channels.none { it.id == row.id } }
            .map { PreviewChannel(it.id, it.name, "Kategorisiz", "ikon_tv", available = false, sourceReferences = it.sources) }
        return data.copy(favorites = (data.favorites + archive.favorites.map { it.id }).distinct(), channels = data.channels + unknown)
    }
    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c)
        } }
        append('"')
    }
}

/** Small bounded JSON reader for the portable backup schema; duplicate keys and trailing data fail. */
private class JsonReader(private val text: String) {
    private var pos = 0
    private var nodes = 0
    fun read(): Any? { val value = value(0); whitespace(); require(pos == text.length); return value }
    private fun whitespace() { while (pos < text.length && text[pos] in " \t\r\n") pos++ }
    private fun take(c: Char): Boolean { whitespace(); return if (pos < text.length && text[pos] == c) { pos++; true } else false }
    private fun value(depth: Int): Any? {
        require(depth <= 12 && ++nodes <= 50000); whitespace(); require(pos < text.length)
        return when (text[pos]) {
            '"' -> string()
            '{' -> {
                pos++; val result = linkedMapOf<String, Any?>()
                if (!take('}')) do {
                    whitespace(); val key = string(); require(key !in result && take(':')); result[key] = value(depth + 1)
                    if (take('}')) break
                    require(take(','))
                } while (true)
                result
            }
            '[' -> {
                pos++; val result = mutableListOf<Any?>()
                if (!take(']')) do {
                    result += value(depth + 1)
                    if (take(']')) break
                    require(take(','))
                } while (true)
                result
            }
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> {
                val start = pos
                if (text[pos] == '-') pos++
                require(pos < text.length && text[pos].isDigit())
                if (text[pos] == '0') pos++ else while (pos < text.length && text[pos] in '0'..'9') pos++
                // Schema has only integer values. Other numeric forms are rejected explicitly.
                text.substring(start, pos).toLong()
            }
        }
    }
    private fun literal(token: String, value: Any?): Any? { require(text.startsWith(token, pos)); pos += token.length; return value }
    private fun string(): String {
        require(pos < text.length && text[pos++] == '"')
        return buildString {
            while (true) {
                require(pos < text.length); val c = text[pos++]
                if (c == '"') break
                require(c.code >= 32)
                if (c != '\\') append(c) else {
                    require(pos < text.length)
                    when (val escape = text[pos++]) {
                        '"', '\\', '/' -> append(escape)
                        'b' -> append('\b'); 'f' -> append('\u000c'); 'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t')
                        'u' -> { require(pos + 4 <= text.length); append(text.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                        else -> error("escape")
                    }
                }
                require(length <= 8192)
            }
        }
    }
}
