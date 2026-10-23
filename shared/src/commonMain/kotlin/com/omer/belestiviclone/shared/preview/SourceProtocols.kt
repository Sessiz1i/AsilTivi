package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import kotlinx.coroutines.CancellationException

data class XtreamAccountInfo(
    val username: String,
    val status: String,
    val expDate: String? = null,
    val isTrial: Boolean = false,
    val activeCons: Int = 0,
    val maxConnections: Int = 1,
    val allowedOutputFormats: List<String> = listOf("m3u8", "ts"),
)

data class XtreamCategory(
    val categoryId: String,
    val categoryName: String,
)

data class XtreamLiveStream(
    val streamId: String,
    val name: String,
    val streamIcon: String = "",
    val epgChannelId: String = "",
    val categoryId: String = "",
    val directSource: String = "",
)

data class StalkerSession(
    val portalBaseUrl: String,
    val loadPhpUrl: String,
    val macAddress: String,
    val token: String,
)

data class StalkerChannel(
    val id: String,
    val name: String,
    val number: Int = 0,
    val genreId: String = "",
    val cmd: String,
    val logo: String = "",
    val xmltvId: String = "",
)

data class CountryListPreset(
    val code: String,
    val name: String,
    val flag: String,
    val groupTitle: String,
    val description: String,
    val remoteUrl: String,
    val builtinM3u: String,
)

/**
 * Dependency-free JSON parser for Xtream Codes API (`player_api.php`) and Stalker Middleware (`load.php`).
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val clean = text.removePrefix("\uFEFF").trim()
        if (clean.isEmpty()) return null
        return Parser(clean).parseValue()
    }

    private class Parser(private val input: String) {
        private var pos = 0

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) return null
            return when (input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumberOrLiteral()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // '{'
            skipWhitespace()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return map
            }
            while (pos < input.length) {
                skipWhitespace()
                if (pos >= input.length || input[pos] == '}') {
                    if (pos < input.length) pos++
                    break
                }
                val key = if (input[pos] == '"') parseString() else parseNumberOrLiteral().toString()
                skipWhitespace()
                if (pos < input.length && input[pos] == ':') pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == '}') {
                    pos++
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // '['
            skipWhitespace()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return list
            }
            while (pos < input.length) {
                skipWhitespace()
                if (pos >= input.length || input[pos] == ']') {
                    if (pos < input.length) pos++
                    break
                }
                list.add(parseValue())
                skipWhitespace()
                if (pos < input.length && input[pos] == ',') {
                    pos++
                } else if (pos < input.length && input[pos] == ']') {
                    pos++
                    break
                }
            }
            return list
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < input.length) {
                val c = input[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < input.length) {
                    when (val esc = input[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= input.length) {
                                val hex = input.substring(pos, pos + 4)
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseBoolean(): Boolean {
            return if (input.startsWith("true", pos)) {
                pos += 4
                true
            } else if (input.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                parseNumberOrLiteral().toString().toBoolean()
            }
        }

        private fun parseNull(): Any? {
            if (input.startsWith("null", pos)) {
                pos += 4
                return null
            }
            return parseNumberOrLiteral()
        }

        private fun parseNumberOrLiteral(): String {
            val start = pos
            while (pos < input.length && input[pos] !in ",}] \t\r\n:") pos++
            return input.substring(start, pos)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun asObject(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun asArray(value: Any?): List<Any?>? = value as? List<Any?>

    fun asString(map: Map<String, Any?>, key: String): String =
        map[key]?.toString()?.trim().orEmpty()
}

private fun urlEncodeComponent(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size)
    for (b in bytes) {
        val ch = (b.toInt() and 0xFF).toChar()
        if (ch in unreserved) {
            sb.append(ch)
        } else {
            sb.append('%')
            val hex = (b.toInt() and 0xFF).toString(16).uppercase()
            if (hex.length == 1) sb.append('0')
            sb.append(hex)
        }
    }
    return sb.toString()
}

private fun escapeM3uAttr(value: String): String =
    value.replace("\"", "'").replace("\r", " ").replace("\n", " ").trim()

/**
 * Real Xtream Codes API (`player_api.php` & `get.php`) handshake, category & live stream converter.
 */
object XtreamCodesClient {
    fun normalizeBaseUrl(rawUrl: String): String {
        var cleaned = rawUrl.trim()
        if (cleaned.isEmpty() || cleaned.length > 4096) throw SourceProblem("url")
        cleaned = cleaned
            .replace(Regex("(?i)/(player_api|get)\\.php.*$"), "")
            .trimEnd('/')
        if (ComparisonV1.addressKey(Address.fromForm(cleaned)) == null) {
            throw SourceProblem("url")
        }
        return cleaned
    }

    fun buildPlayerApiUrl(
        baseUrl: String,
        username: String,
        password: String,
        action: String? = null,
    ): String {
        val base = normalizeBaseUrl(baseUrl)
        val u = urlEncodeComponent(username.trim())
        val p = urlEncodeComponent(password.trim())
        val act = if (!action.isNullOrBlank()) "&action=${urlEncodeComponent(action)}" else ""
        return "$base/player_api.php?username=$u&password=$p$act"
    }

    fun buildM3uPlusUrl(
        baseUrl: String,
        username: String,
        password: String,
        output: String = "ts",
    ): String {
        val base = normalizeBaseUrl(baseUrl)
        val u = urlEncodeComponent(username.trim())
        val p = urlEncodeComponent(password.trim())
        val out = urlEncodeComponent(output.trim().ifEmpty { "ts" })
        return "$base/get.php?username=$u&password=$p&type=m3u_plus&output=$out"
    }

    fun parseAuthResponse(jsonText: String, fallbackUser: String): XtreamAccountInfo {
        val root = MiniJson.asObject(MiniJson.parse(jsonText)) ?: throw SourceProblem("xtream-auth")
        val userInfo = MiniJson.asObject(root["user_info"]) ?: throw SourceProblem("xtream-auth")
        val authRaw = MiniJson.asString(userInfo, "auth")
        val status = MiniJson.asString(userInfo, "status").ifEmpty { "Active" }
        val isAuthorized = authRaw == "1" || authRaw.equals("true", ignoreCase = true) ||
            (authRaw.isEmpty() && status.equals("Active", ignoreCase = true))
        if (!isAuthorized) {
            if (status.equals("Expired", ignoreCase = true) ||
                status.equals("Banned", ignoreCase = true) ||
                status.equals("Disabled", ignoreCase = true)
            ) {
                throw SourceProblem("xtream-expired")
            }
            throw SourceProblem("xtream-auth")
        }
        if (status.equals("Expired", ignoreCase = true) ||
            status.equals("Banned", ignoreCase = true) ||
            status.equals("Disabled", ignoreCase = true)
        ) {
            throw SourceProblem("xtream-expired")
        }
        val formats = MiniJson.asArray(userInfo["allowed_output_formats"])
            ?.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("m3u8", "ts")
        return XtreamAccountInfo(
            username = MiniJson.asString(userInfo, "username").ifEmpty { fallbackUser.trim() },
            status = status,
            expDate = MiniJson.asString(userInfo, "exp_date").takeIf { it.isNotEmpty() && it != "null" },
            isTrial = MiniJson.asString(userInfo, "is_trial") == "1",
            activeCons = MiniJson.asString(userInfo, "active_cons").toIntOrNull() ?: 0,
            maxConnections = MiniJson.asString(userInfo, "max_connections").toIntOrNull() ?: 1,
            allowedOutputFormats = formats,
        )
    }

    fun parseCategories(jsonText: String): List<XtreamCategory> {
        val array = MiniJson.asArray(MiniJson.parse(jsonText)) ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = MiniJson.asObject(item) ?: return@mapNotNull null
            val id = MiniJson.asString(obj, "category_id")
            val name = MiniJson.asString(obj, "category_name")
            if (id.isEmpty() || name.isEmpty()) null else XtreamCategory(id, name)
        }
    }

    fun parseLiveStreams(jsonText: String): List<XtreamLiveStream> {
        val array = MiniJson.asArray(MiniJson.parse(jsonText)) ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = MiniJson.asObject(item) ?: return@mapNotNull null
            val streamId = MiniJson.asString(obj, "stream_id")
            val name = MiniJson.asString(obj, "name").ifEmpty { MiniJson.asString(obj, "title") }
            if (streamId.isEmpty() && name.isEmpty()) return@mapNotNull null
            XtreamLiveStream(
                streamId = streamId,
                name = name.ifEmpty { "Kanal $streamId" },
                streamIcon = MiniJson.asString(obj, "stream_icon"),
                epgChannelId = MiniJson.asString(obj, "epg_channel_id"),
                categoryId = MiniJson.asString(obj, "category_id"),
                directSource = MiniJson.asString(obj, "direct_source"),
            )
        }
    }

    fun toM3u(
        baseUrl: String,
        username: String,
        password: String,
        account: XtreamAccountInfo,
        categories: List<XtreamCategory>,
        streams: List<XtreamLiveStream>,
    ): String {
        val base = normalizeBaseUrl(baseUrl)
        val catMap = categories.associate { it.categoryId to it.categoryName }
        val ext = when {
            account.allowedOutputFormats.any { it.equals("m3u8", ignoreCase = true) } -> "m3u8"
            account.allowedOutputFormats.any { it.equals("ts", ignoreCase = true) } -> "ts"
            else -> "m3u8"
        }
        val u = urlEncodeComponent(username.trim())
        val p = urlEncodeComponent(password.trim())
        val sb = StringBuilder("#EXTM3U\n")
        for (stream in streams) {
            val group = catMap[stream.categoryId].orEmpty().ifEmpty { "Canlı TV" }
            val uri = stream.directSource.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            } ?: if (stream.streamId.isNotEmpty()) {
                "$base/live/$u/$p/${urlEncodeComponent(stream.streamId)}.$ext"
            } else continue
            sb.append("#EXTINF:-1")
            if (stream.epgChannelId.isNotBlank()) {
                sb.append(" tvg-id=\"").append(escapeM3uAttr(stream.epgChannelId)).append('"')
            }
            sb.append(" tvg-name=\"").append(escapeM3uAttr(stream.name)).append('"')
            if (stream.streamIcon.isNotBlank()) {
                sb.append(" tvg-logo=\"").append(escapeM3uAttr(stream.streamIcon)).append('"')
            }
            sb.append(" group-title=\"").append(escapeM3uAttr(group)).append("\",")
            sb.append(stream.name.replace("\r", " ").replace("\n", " ").trim()).append('\n')
            sb.append(uri).append('\n')
        }
        return sb.toString()
    }

    suspend fun connectAndBuildM3u(
        loader: SourceLoader,
        serverUrl: String,
        username: String,
        password: String,
    ): Pair<XtreamAccountInfo, String> {
        val cleanBase = normalizeBaseUrl(serverUrl)
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) throw SourceProblem("credentials")

        val authUrl = buildPlayerApiUrl(cleanBase, cleanUser, cleanPass)
        val authRaw = try {
            loader.fetch(authUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: SourceProblem) {
            throw problem
        } catch (_: Exception) {
            throw SourceProblem("network")
        }

        if (authRaw.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")) {
            return XtreamAccountInfo(username = cleanUser, status = "Active") to authRaw
        }

        val account = parseAuthResponse(authRaw, cleanUser)
        val categoriesRaw = try {
            loader.fetch(buildPlayerApiUrl(cleanBase, cleanUser, cleanPass, "get_live_categories"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "[]"
        }
        val streamsRaw = try {
            loader.fetch(buildPlayerApiUrl(cleanBase, cleanUser, cleanPass, "get_live_streams"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }

        val categories = parseCategories(categoriesRaw)
        val streams = parseLiveStreams(streamsRaw)
        if (streams.isNotEmpty()) {
            val m3u = toM3u(cleanBase, cleanUser, cleanPass, account, categories, streams)
            return account to m3u
        }

        val preferredOutput = if (account.allowedOutputFormats.any { it.equals("m3u8", ignoreCase = true) }) "m3u8" else "ts"
        val fallbackM3u = try {
            loader.fetch(buildM3uPlusUrl(cleanBase, cleanUser, cleanPass, preferredOutput))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: SourceProblem) {
            throw problem
        } catch (_: Exception) {
            throw SourceProblem("content")
        }
        return account to fallbackM3u
    }
}

/**
 * Stalker Middleware / Ministra MAC Portal handshake (`action=handshake`, `get_genres`, `get_all_channels`).
 */
object StalkerPortalClient {
    private val macRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    private val cmdPrefixRegex = Regex("^(?:ffrt\\d*|ffmpeg|auto|solution)\\s+", RegexOption.IGNORE_CASE)

    fun normalizeMac(rawMac: String): String {
        val candidate = rawMac.trim().replace('-', ':').uppercase()
        if (!macRegex.matches(candidate)) throw SourceProblem("mac")
        return candidate
    }

    fun normalizePortalBaseUrl(portalUrl: String): String {
        val cleaned = portalUrl.trim().trimEnd('/')
        if (cleaned.isEmpty() || cleaned.length > 4096) throw SourceProblem("url")
        if (ComparisonV1.addressKey(Address.fromForm(cleaned)) == null) {
            throw SourceProblem("url")
        }
        return cleaned
    }

    fun resolveLoadPhpUrl(portalUrl: String): String {
        val base = normalizePortalBaseUrl(portalUrl)
        return when {
            base.endsWith(".php", ignoreCase = true) -> base
            base.endsWith("/c", ignoreCase = true) -> base.dropLast(2) + "/stalker_portal/server/load.php"
            base.endsWith("/stalker_portal/server", ignoreCase = true) -> "$base/load.php"
            base.endsWith("/stalker_portal", ignoreCase = true) -> "$base/server/load.php"
            else -> "$base/stalker_portal/server/load.php"
        }
    }

    fun extractPortalOrigin(portalUrl: String): String {
        val base = normalizePortalBaseUrl(portalUrl)
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return base
        val pathStart = base.indexOf('/', schemeEnd + 3)
        return if (pathStart < 0) base else base.substring(0, pathStart)
    }

    fun buildHeaders(macAddress: String, token: String? = null): Map<String, String> {
        val mac = normalizeMac(macAddress)
        val headers = LinkedHashMap<String, String>()
        headers["Cookie"] = "mac=$mac; stb_lang=tr; timezone=Europe/Istanbul"
        headers["User-Agent"] = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        headers["X-User-Agent"] = "Model: MAG250; Link: WiFi"
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer ${token.trim()}"
        }
        return headers
    }

    fun parseHandshakeToken(jsonText: String): String {
        val root = MiniJson.asObject(MiniJson.parse(jsonText)) ?: throw SourceProblem("stalker-auth")
        val jsObj = MiniJson.asObject(root["js"]) ?: root
        val token = MiniJson.asString(jsObj, "token")
        if (token.isEmpty() || token == "null") throw SourceProblem("stalker-auth")
        return token
    }

    fun parseGenres(jsonText: String): Map<String, String> {
        val root = MiniJson.parse(jsonText)
        val array = when (root) {
            is Map<*, *> -> MiniJson.asArray(root["js"])
            is List<*> -> root
            else -> null
        } ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (item in array) {
            val obj = MiniJson.asObject(item) ?: continue
            val id = MiniJson.asString(obj, "id")
            val title = MiniJson.asString(obj, "title").ifEmpty { MiniJson.asString(obj, "name") }
            if (id.isNotEmpty() && id != "*" && title.isNotEmpty()) {
                result[id] = title
            }
        }
        return result
    }

    fun extractStreamUriFromCmd(cmd: String, portalOrigin: String): String? {
        val stripped = cmd.trim().replace(cmdPrefixRegex, "").trim()
        if (stripped.isEmpty()) return null
        val httpIdx = stripped.indexOf("http://", ignoreCase = true)
            .takeIf { it >= 0 } ?: stripped.indexOf("https://", ignoreCase = true).takeIf { it >= 0 }
        val candidate = when {
            httpIdx != null -> stripped.substring(httpIdx).substringBefore(' ')
            stripped.startsWith("/") -> portalOrigin.trimEnd('/') + stripped.substringBefore(' ')
            else -> return null
        }
        val replacedLocalhost = candidate
            .replace(Regex("(?i)^https?://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?"), portalOrigin.trimEnd('/'))
        return replacedLocalhost.takeIf { ComparisonV1.addressKey(Address.fromForm(it)) != null }
    }

    fun parseAllChannels(jsonText: String): List<StalkerChannel> {
        val root = MiniJson.parse(jsonText) ?: return emptyList()
        val dataArray = when (root) {
            is Map<*, *> -> {
                val js = root["js"]
                when (js) {
                    is Map<*, *> -> MiniJson.asArray(js["data"])
                    is List<*> -> js
                    else -> MiniJson.asArray(root["data"])
                }
            }
            is List<*> -> root
            else -> null
        } ?: return emptyList()

        return dataArray.mapNotNull { item ->
            val obj = MiniJson.asObject(item) ?: return@mapNotNull null
            val id = MiniJson.asString(obj, "id")
            val name = MiniJson.asString(obj, "name").ifEmpty { MiniJson.asString(obj, "title") }
            val cmd = MiniJson.asString(obj, "cmd").ifEmpty {
                MiniJson.asArray(obj["cmds"])
                    ?.firstOrNull()
                    ?.let { MiniJson.asObject(it) }
                    ?.let { MiniJson.asString(it, "url") }
                    .orEmpty()
            }
            if (name.isEmpty() || cmd.isEmpty()) return@mapNotNull null
            StalkerChannel(
                id = id.ifEmpty { name },
                name = name,
                number = MiniJson.asString(obj, "number").toIntOrNull() ?: 0,
                genreId = MiniJson.asString(obj, "tv_genre_id").ifEmpty { MiniJson.asString(obj, "genre_id") },
                cmd = cmd,
                logo = MiniJson.asString(obj, "logo"),
                xmltvId = MiniJson.asString(obj, "xmltv_id"),
            )
        }
    }

    fun toM3u(
        channels: List<StalkerChannel>,
        genres: Map<String, String>,
        portalOrigin: String,
    ): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (ch in channels) {
            val uri = extractStreamUriFromCmd(ch.cmd, portalOrigin) ?: continue
            val group = genres[ch.genreId].orEmpty().ifEmpty { "Portal TV" }
            val logoUrl = when {
                ch.logo.startsWith("http://", ignoreCase = true) || ch.logo.startsWith("https://", ignoreCase = true) -> ch.logo
                ch.logo.startsWith("/") -> portalOrigin.trimEnd('/') + ch.logo
                else -> ""
            }
            sb.append("#EXTINF:-1")
            if (ch.xmltvId.isNotBlank()) {
                sb.append(" tvg-id=\"").append(escapeM3uAttr(ch.xmltvId)).append('"')
            }
            sb.append(" tvg-name=\"").append(escapeM3uAttr(ch.name)).append('"')
            if (logoUrl.isNotBlank()) {
                sb.append(" tvg-logo=\"").append(escapeM3uAttr(logoUrl)).append('"')
            }
            sb.append(" group-title=\"").append(escapeM3uAttr(group)).append("\",")
            sb.append(ch.name.replace("\r", " ").replace("\n", " ").trim()).append('\n')
            sb.append(uri).append('\n')
        }
        return sb.toString()
    }

    suspend fun connectAndBuildM3u(
        loader: SourceLoader,
        portalUrl: String,
        rawMac: String,
    ): Pair<StalkerSession, String> {
        val cleanPortal = normalizePortalBaseUrl(portalUrl)
        val loadPhp = resolveLoadPhpUrl(cleanPortal)
        val mac = normalizeMac(rawMac)
        val origin = extractPortalOrigin(cleanPortal)

        val handshakeUrl = "$loadPhp?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
        val handshakeRaw = try {
            loader.fetch(handshakeUrl, buildHeaders(mac))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: SourceProblem) {
            throw problem
        } catch (_: Exception) {
            throw SourceProblem("network")
        }

        if (handshakeRaw.removePrefix("\uFEFF").trimStart().startsWith("#EXTM3U")) {
            val session = StalkerSession(cleanPortal, loadPhp, mac, "direct-m3u")
            return session to handshakeRaw
        }

        val token = parseHandshakeToken(handshakeRaw)
        val authHeaders = buildHeaders(mac, token)

        val genresRaw = try {
            loader.fetch("$loadPhp?type=itv&action=get_genres&JsHttpRequest=1-xml", authHeaders)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            "{}"
        }
        val channelsRaw = try {
            loader.fetch("$loadPhp?type=itv&action=get_all_channels&JsHttpRequest=1-xml", authHeaders)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: SourceProblem) {
            throw problem
        } catch (_: Exception) {
            throw SourceProblem("network")
        }

        val genres = parseGenres(genresRaw)
        val channels = parseAllChannels(channelsRaw)
        if (channels.isEmpty()) throw SourceProblem("content")
        val m3u = toM3u(channels, genres, origin)
        return StalkerSession(cleanPortal, loadPhp, mac, token) to m3u
    }
}

/**
 * Built-in v4.1 Country Lists (Türkiye, KKTC, Azerbaycan, Almanya / Gurbetçi, Dünya).
 */
object CountryListsCatalog {
    val presets: List<CountryListPreset> = listOf(
        CountryListPreset(
            code = "TR",
            name = "Türkiye (Varsayılan)",
            flag = "🇹🇷",
            groupTitle = "Ulusal",
            description = "Ulusal, haber, spor, belgesel ve yerel Türkiye kanalları",
            remoteUrl = PublicM3u.url,
            builtinM3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="TRT1.tr" tvg-name="TRT 1" tvg-logo="logo_trt1" group-title="Ulusal",TRT 1
                https://trt.daioncdn.net/trt-1/master.m3u8?app=clean
                #EXTINF:-1 tvg-id="ATV.tr" tvg-name="ATV" tvg-logo="logo_atv" group-title="Ulusal",ATV
                https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv.m3u8
                #EXTINF:-1 tvg-id="ShowTV.tr" tvg-name="Show TV" tvg-logo="logo_show" group-title="Ulusal",Show TV
                https://streams.uzunmuhalefet.com/stream/0ed1c367-312d-4a42-9772-ef257a36b775.m3u8
                #EXTINF:-1 tvg-id="NOW.tr" tvg-name="NOW TV" tvg-logo="logo_now" group-title="Ulusal",NOW TV
                https://uycyyuuzyh.turknet.ercdn.net/nphindgytw/nowtv/nowtv.m3u8
                #EXTINF:-1 tvg-id="StarTV.tr" tvg-name="Star TV" tvg-logo="logo_star" group-title="Ulusal",Star TV
                http://dygvideo.dygdigital.com/live/hls/startv4puhu?m3u8
                #EXTINF:-1 tvg-id="TV8.tr" tvg-name="TV8" tvg-logo="logo_tv8" group-title="Ulusal",TV8
                #EXTVLCOPT:http-user-agent=Mozilla/5.0
                https://tv8.daioncdn.net/tv8/tv8.m3u8?app=7ddc255a-ef47-4e81-ab14-c0e5f2949788&ce=3
                #EXTINF:-1 tvg-id="TRTHaber.tr" tvg-name="TRT Haber" tvg-logo="logo_trt1" group-title="Haber",TRT Haber
                #EXTVLCOPT:http-user-agent=Mozilla/5.0
                https://tv-trthaber.medya.trt.com.tr/master.m3u8
                #EXTINF:-1 tvg-id="NTV.tr" tvg-name="NTV" tvg-logo="logo_ntv" group-title="Haber",NTV
                https://streams.uzunmuhalefet.com/stream/f27d4ced-d7e4-4d63-95b4-c47302cad618.m3u8
            """.trimIndent() + "\n"
        ),
        CountryListPreset(
            code = "KKTC",
            name = "KKTC (Kuzey Kıbrıs)",
            flag = "🇨🇾",
            groupTitle = "KKTC",
            description = "BRT 1 HD, BRT 2 HD, Kıbrıs Genç TV, Ada TV, Kanal Sim, Kıbrıs TV, Kanal T, Diyalog TV",
            remoteUrl = "https://belestivi.kktc.list/kktc.m3u",
            builtinM3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="BRT1.kktc" tvg-name="BRT 1 HD" tvg-logo="ikon_tv" group-title="KKTC",BRT 1 HD
                https://sc-kuzeykibris.medya.trt.com.tr/brt1/master.m3u8
                #EXTINF:-1 tvg-id="BRT2.kktc" tvg-name="BRT 2 HD" tvg-logo="ikon_tv" group-title="KKTC",BRT 2 HD
                https://sc-kuzeykibris.medya.trt.com.tr/brt2/master.m3u8
                #EXTINF:-1 tvg-id="BRT3.kktc" tvg-name="BRT 3" tvg-logo="ikon_tv" group-title="KKTC",BRT 3
                https://sc-kuzeykibris.medya.trt.com.tr/brt3/master.m3u8
                #EXTINF:-1 tvg-id="KibrisGencTV.kktc" tvg-name="Kıbrıs Genç TV" tvg-logo="ikon_tv" group-title="KKTC",Kıbrıs Genç TV
                https://live.kibrisgenctv.com/hls/genctv.m3u8
                #EXTINF:-1 tvg-id="KibrisTV.kktc" tvg-name="Kıbrıs TV" tvg-logo="ikon_tv" group-title="KKTC",Kıbrıs TV
                https://live.kibristv.com/hls/kibristv.m3u8
                #EXTINF:-1 tvg-id="AdaTV.kktc" tvg-name="Ada TV" tvg-logo="ikon_tv" group-title="KKTC",Ada TV
                https://live.adatv.tv/hls/adatv.m3u8
                #EXTINF:-1 tvg-id="KanalSim.kktc" tvg-name="Kanal Sim" tvg-logo="ikon_tv" group-title="KKTC",Kanal Sim
                https://live.kanalsim.com/hls/kanalsim.m3u8
                #EXTINF:-1 tvg-id="KanalT.kktc" tvg-name="Kanal T Kıbrıs" tvg-logo="ikon_tv" group-title="KKTC",Kanal T Kıbrıs
                https://live.kanalt.com/hls/kanalt.m3u8
                #EXTINF:-1 tvg-id="DiyalogTV.kktc" tvg-name="Diyalog TV" tvg-logo="ikon_tv" group-title="KKTC",Diyalog TV
                https://live.diyalogtv.com/hls/diyalogtv.m3u8
            """.trimIndent() + "\n"
        ),
        CountryListPreset(
            code = "AZ",
            name = "Azerbaycan",
            flag = "🇦🇿",
            groupTitle = "AZERBAYCAN",
            description = "AzTV, İctimai TV, Xəzər TV, ATV AZ, Space TV, Real TV, ARB, ARB 24, CBC, Baku TV",
            remoteUrl = "https://belestivi.az.list/azerbaycan.m3u",
            builtinM3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="AZTV.tr" tvg-name="Azərbaycan Televiziyası" tvg-logo="logo_a77_ictimai" group-title="AZERBAYCAN",Azərbaycan Televiziyası
                https://boncuktvv.yalniz-senmi-zekisin.workers.dev/aztv
                #EXTINF:-1 tvg-id="IctimaiTV.az" tvg-name="İctimai TV" tvg-logo="logo_a77_ictimai" group-title="AZERBAYCAN",İctimai TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/ictimai-tv.m3u8
                #EXTINF:-1 tvg-id="XazarTV.az" tvg-name="Xəzər TV" tvg-logo="logo_a77_xazar" group-title="AZERBAYCAN",Xəzər TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/xezer-tv.m3u8
                #EXTINF:-1 tvg-id="ATVAz.tr" tvg-name="ATV Azerbaycan" tvg-logo="logo_atv" group-title="AZERBAYCAN",ATV Azerbaycan
                https://lives.atv.az:5443/ATV_TV_STREAM/streams/atvcanli.m3u8
                #EXTINF:-1 tvg-id="ARB.tr" tvg-name="ARB TV" tvg-logo="logo_a77_arb" group-title="AZERBAYCAN",ARB TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/arb.m3u8
                #EXTINF:-1 tvg-id="ARB24.tr" tvg-name="ARB 24" tvg-logo="logo_a77_arb24" group-title="AZERBAYCAN",ARB 24
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/arb-24.m3u8
                #EXTINF:-1 tvg-id="ARBGunes.tr" tvg-name="ARB Günəş" tvg-logo="logo_a77_arbgunes" group-title="AZERBAYCAN",ARB Günəş
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/arb-gunes.m3u8
                #EXTINF:-1 tvg-id="CBC.tr" tvg-name="CBC" tvg-logo="logo_a77_cbc" group-title="AZERBAYCAN",CBC
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/cbc.m3u8
                #EXTINF:-1 tvg-id="BakuTV.tr" tvg-name="BakuTV" tvg-logo="logo_a77_bakutv" group-title="AZERBAYCAN",BakuTV
                https://rtmp.baku.tv/hls/bakutv_1080p.m3u8
                #EXTINF:-1 tvg-id="SpaceTV.tr" tvg-name="Space TV" tvg-logo="logo_a77_space" group-title="AZERBAYCAN",Space TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/space-tv.m3u8
                #EXTINF:-1 tvg-id="RealTV.tr" tvg-name="Real TV" tvg-logo="logo_a77_real" group-title="AZERBAYCAN",Real TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/real-tv.m3u8
                #EXTINF:-1 tvg-id="MedeniyyetTV.tr" tvg-name="Mədəniyyət TV" tvg-logo="logo_a77_medeniyyet" group-title="AZERBAYCAN",Mədəniyyət TV
                https://str.yodacdn.net/medeniyyettele/index.m3u8
                #EXTINF:-1 tvg-id="DunyaTV.tr" tvg-name="Dünya TV" tvg-logo="logo_a77_dunya" group-title="AZERBAYCAN",Dünya TV
                https://raw.githubusercontent.com/UzunMuhalefet/streams/main/myvideo-az/dunya-tv.m3u8
            """.trimIndent() + "\n"
        ),
        CountryListPreset(
            code = "DE",
            name = "Almanya / Gurbetçi",
            flag = "🇩🇪",
            groupTitle = "Almanya / Avrupa",
            description = "TRT Türk, Euro D, Show Türk, ATV Avrupa, Kanal 7 Avrupa, TGRT EU, Kanal Avrupa, DW, ZDF, ARD",
            remoteUrl = "https://belestivi.de.list/almanya.m3u",
            builtinM3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="TRTTurk.tr" tvg-name="TRT Türk" tvg-logo="logo_trt1" group-title="Almanya / Avrupa",TRT Türk
                https://tv-trtturk.medya.trt.com.tr/master.m3u8
                #EXTINF:-1 tvg-id="ATVAvrupa.tr" tvg-name="ATV Avrupa" tvg-logo="logo_atv" group-title="Almanya / Avrupa",ATV Avrupa
                https://streams.uzunmuhalefet.com/stream/017621fb-d6f1-4e21-91d8-2510bdcb4ceb.m3u8
                #EXTINF:-1 tvg-id="EuroD.tr" tvg-name="Euro D" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",Euro D
                https://streams.uzunmuhalefet.com/stream/864c7cfd-0427-4997-b2dc-31b4aee312c9.m3u8
                #EXTINF:-1 tvg-id="ShowTurk.tr" tvg-name="Show Türk" tvg-logo="logo_show" group-title="Almanya / Avrupa",Show Türk
                https://streams.uzunmuhalefet.com/stream/16e43b9b-e175-482c-a105-2253c06515c9.m3u8
                #EXTINF:-1 tvg-id="Kanal7Avrupa.tr" tvg-name="Kanal 7 Avrupa" tvg-logo="logo_kanal7" group-title="Almanya / Avrupa",Kanal 7 Avrupa
                https://streams.uzunmuhalefet.com/stream/49c9a96d-f9c0-474e-b2f7-5e62bbdd0290.m3u8
                #EXTINF:-1 tvg-id="TGRTEU.tr" tvg-name="TGRT EU" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",TGRT EU
                http://208.79.153.167/inim03/live1/tgrteu/playlist.m3u8
                #EXTINF:-1 tvg-id="KanalAvrupa.tr" tvg-name="Kanal Avrupa" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",Kanal Avrupa
                https://streams.uzunmuhalefet.com/stream/92ccdd1d-1d3e-47eb-b84b-e75effe16829.m3u8
                #EXTINF:-1 tvg-id="DW.de" tvg-name="DW (Deutsche Welle)" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",DW (Deutsche Welle)
                https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8
                #EXTINF:-1 tvg-id="ARD.de" tvg-name="ARD Das Erste" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",ARD Das Erste
                https://mcdn.daserste.de/daserste/de/master.m3u8
                #EXTINF:-1 tvg-id="ZDF.de" tvg-name="ZDF HD" tvg-logo="ikon_tv" group-title="Almanya / Avrupa",ZDF HD
                https://zdf-hls-15.akamaized.net/hls/live/2016498/de/high/master.m3u8
            """.trimIndent() + "\n"
        ),
        CountryListPreset(
            code = "WORLD",
            name = "Dünya Kanalları",
            flag = "🌍",
            groupTitle = "Dünya",
            description = "TRT World, Al Jazeera, Euronews, France 24, NASA TV, Red Bull TV, Fashion TV",
            remoteUrl = "https://belestivi.world.list/dunya.m3u",
            builtinM3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="TRTWorld.tr" tvg-name="TRT World" tvg-logo="logo_trt1" group-title="Dünya",TRT World
                https://tv-trtworld.medya.trt.com.tr/master.m3u8
                #EXTINF:-1 tvg-id="AlJazeera.int" tvg-name="Al Jazeera English" tvg-logo="ikon_tv" group-title="Dünya",Al Jazeera English
                https://live-hls-web-aje.getaj.net/AJE/index.m3u8
                #EXTINF:-1 tvg-id="France24.int" tvg-name="France 24 English" tvg-logo="ikon_tv" group-title="Dünya",France 24 English
                https://live.france24.com/hls/live/2037218-b/F24_EN_HI_HLS/master_5000.m3u8
                #EXTINF:-1 tvg-id="Euronews.int" tvg-name="Euronews" tvg-logo="ikon_tv" group-title="Dünya",Euronews
                https://rakuten-euronews-1-gb.samsung.wurl.tv/playlist.m3u8
                #EXTINF:-1 tvg-id="NASATV.int" tvg-name="NASA TV Public" tvg-logo="ikon_tv" group-title="Dünya",NASA TV Public
                https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8
                #EXTINF:-1 tvg-id="RedBullTV.int" tvg-name="Red Bull TV" tvg-logo="ikon_tv" group-title="Dünya",Red Bull TV
                https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8
                #EXTINF:-1 tvg-id="FashionTV.int" tvg-name="Fashion TV" tvg-logo="logo_fashion" group-title="Dünya",Fashion TV
                https://fash1043.cloudycdn.services/slive/_definst_/ftv_ftv_midnite_k1y_27049_midnite_secr_108_hls.smil/playlist.m3u8
            """.trimIndent() + "\n"
        ),
    )

    fun byCode(code: String): CountryListPreset? =
        presets.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    fun parseEnabledCodes(rawPref: String?): Set<String> {
        if (rawPref.isNullOrBlank()) return emptySet()
        return rawPref.split(',')
            .map { it.trim().uppercase() }
            .filter { code -> presets.any { it.code == code && it.code != "TR" } }
            .toSet()
    }

    fun formatEnabledCodes(codes: Collection<String>): String =
        codes.map { it.trim().uppercase() }
            .filter { code -> presets.any { it.code == code && it.code != "TR" } }
            .distinct()
            .joinToString(",")

    fun buildChannelsForPreset(preset: CountryListPreset): List<PreviewChannel> {
        val parsed = PublicM3u.parse(preset.builtinM3u)
        val codeHash = preset.code.fold(0x434F554EL) { acc, c -> acc * 31 + c.code }
        return parsed.rows.mapIndexed { index, row ->
            val group = (row.category as? CategoryLabel.Exact)?.text ?: preset.groupTitle
            PreviewChannel(
                id = OpaqueId(codeHash, (index + 1).toLong()),
                name = row.name ?: "Kanal ${index + 1}",
                category = group,
                logo = row.logo?.ifBlank { "ikon_tv" } ?: "ikon_tv",
                definition = row.definition,
                groups = listOf(group),
                providerId = row.providerHint.orEmpty(),
                tvgName = row.tvgName,
            )
        }
    }
}
