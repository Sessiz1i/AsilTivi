package com.omer.belestiviclone

import androidx.media3.common.MimeTypes
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

data class ResolvedStream(
    val uri: String,
    val headers: Map<String, String>,
    val mimeType: String? = null,
)

object WebStreamResolver {
    const val CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    const val DEFAULT_CNN_TURK_CONTENT_ID = "62d6814670380e2cdc7c124c"

    private val DIRECT_MEDIA_EXTENSIONS = listOf(
        ".m3u8", ".mpd", ".ism", ".mp4", ".mp3", ".aac", ".m4a", ".ts", ".ogg", ".wav", ".webm", ".smil", ".flv"
    )

    private val TURKUVAZ_STREAMS: List<Pair<String, String>> = listOf(
        "atv.com.tr/a2tv/canli-yayin" to "https://trkvz.daioncdn.net/a2tv/a2tv.m3u8?ce=3&app=59363a60-be96-4f73-9eff-355d0ff2c758",
        "atv.com.tr/canli-yayin" to "https://trkvz.daioncdn.net/atv/atv.m3u8?ce=3&app=d1ce2d40-5256-4550-b02e-e73c185a314e",
        "ahaber.com.tr/canli-yayin" to "https://trkvz.daioncdn.net/ahaber/ahaber.m3u8?app=web",
        "apara.com.tr/canli-yayin" to "https://trkvz.daioncdn.net/apara/apara.m3u8?app=web",
        "aspor.com.tr/webtv/canli-yayin" to "https://trkvz.daioncdn.net/aspor/aspor.m3u8?ce=3&app=45f847c4-04e8-419a-a561-2ebf87084765",
        "atvavrupa.tv/canli-yayin" to "https://trkvz-live.ercdn.net/atvavrupa/atvavrupa.m3u8",
        "anews.com.tr/webtv/live-broadcast" to "https://trkvz-live.ercdn.net/anewshd/anewshd.m3u8",
        "minikacocuk.com.tr/webtv/canli-yayin" to "https://trkvz.daioncdn.net/minikago_cocuk/minikago_cocuk.m3u8?app=web&ce=3",
        "minikago.com.tr/webtv/canli-yayin" to "https://trkvz.daioncdn.net/minikago/minikago.m3u8?app=web&ce=3",
        "vavtv.com.tr/canli-yayin" to "https://trkvz-live.ercdn.net/vavtv/vavtv.m3u8",
    )

    private val KNOWN_WEB_DOMAINS = listOf(
        "cnnturk.com",
        "cnn.impresents.workers.dev",
        "kanald.com.tr",
        "eurod.tv",
        "teve2.com.tr",
        "atv.com.tr",
        "ahaber.com.tr",
        "apara.com.tr",
        "aspor.com.tr",
        "atvavrupa.tv",
        "anews.com.tr",
        "minikacocuk.com.tr",
        "minikago.com.tr",
        "vavtv.com.tr",
        "startv.com.tr",
        "ntv.com.tr",
        "cnbce.com",
        "showtv.com.tr",
        "nowtv.com.tr",
        "tv8.com.tr",
        "haberturk.com",
        "kanal7.com",
        "showturk.com.tr",
        "showmax.com.tr",
    )

    fun isWebPageUrl(uri: String): Boolean {
        val trimmed = uri.trim()
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host?.lowercase().orEmpty()
        val path = parsed.path?.lowercase().orEmpty()
        if (DIRECT_MEDIA_EXTENSIONS.any { path.endsWith(it) }) return false
        val lower = trimmed.lowercase()
        if (TURKUVAZ_STREAMS.any { (pattern, _) -> lower.contains(pattern) }) return true
        if (KNOWN_WEB_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }) return true
        return path.contains("/canli-yayin") ||
            path.contains("/canli-izle") ||
            path.contains("/live-broadcast") ||
            path.contains("/webtv/")
    }

    fun resolve(uri: String, existingHeaders: Map<String, String> = emptyMap()): ResolvedStream {
        val trimmed = uri.trim()
        val lower = trimmed.lowercase()
        val parsedUri = runCatching { URI(trimmed) }.getOrNull()
        val host = parsedUri?.host?.lowercase().orEmpty()
        val originBase = if (host.isNotBlank()) "${parsedUri?.scheme ?: "https"}://$host" else "https://www.cnnturk.com"

        return runCatching {
            when {
                host.contains("cnnturk.com") || host == "cnn.impresents.workers.dev" ->
                    resolveCnnTurk(existingHeaders)

                TURKUVAZ_STREAMS.any { (pattern, _) -> lower.contains(pattern) } -> {
                    val (_, baseStreamUrl) = TURKUVAZ_STREAMS.first { (pattern, _) -> lower.contains(pattern) }
                    resolveTurkuvaz(trimmed, originBase, baseStreamUrl, existingHeaders)
                }

                host.contains("kanald.com.tr") || host.contains("eurod.tv") || host.contains("teve2.com.tr") ->
                    resolveDemiroren(trimmed, originBase, existingHeaders)

                host.contains("startv.com.tr") ->
                    resolveDyg(trimmed, originBase, "Startv", existingHeaders)

                host.contains("ntv.com.tr") ->
                    resolveDyg(trimmed, originBase, "Ntv", existingHeaders)

                host.contains("cnbce.com") ->
                    resolveCnbce(trimmed, originBase, existingHeaders)

                else ->
                    resolveGenericWebPage(trimmed, originBase, existingHeaders)
            }
        }.getOrElse {
            ResolvedStream(
                uri = trimmed,
                headers = existingHeaders + browserHeaders(originBase, trimmed),
                mimeType = null,
            )
        }
    }

    private fun resolveCnnTurk(existingHeaders: Map<String, String>): ResolvedStream {
        val pageUrl = "https://www.cnnturk.com/canli-yayin"
        val origin = "https://www.cnnturk.com"
        val referer = "https://www.cnnturk.com/"
        val reqHeaders = browserHeaders(origin, referer)

        val html = runCatching { httpRequest(pageUrl, "GET", reqHeaders) }.getOrDefault("")
        val contentId = extractCnnTurkContentId(html)
        val apiUrl = "https://www.cnnturk.com/api/cnnvideo/media?id=$contentId&isMobile=false"
        val json = runCatching { httpRequest(apiUrl, "GET", reqHeaders) }.getOrDefault("")

        val resolvedM3u8 = parseDemirorenMediaJson(json)
            ?: if (contentId != DEFAULT_CNN_TURK_CONTENT_ID) {
                val fallbackApi = "https://www.cnnturk.com/api/cnnvideo/media?id=$DEFAULT_CNN_TURK_CONTENT_ID&isMobile=false"
                val fallbackJson = runCatching { httpRequest(fallbackApi, "GET", reqHeaders) }.getOrDefault("")
                parseDemirorenMediaJson(fallbackJson)
            } else {
                null
            }
            ?: extractFirstM3u8FromHtml(html)
            ?: pageUrl

        return ResolvedStream(
            uri = resolvedM3u8,
            headers = existingHeaders + reqHeaders,
            mimeType = MimeTypes.APPLICATION_M3U8,
        )
    }

    private fun resolveDemiroren(
        pageUrl: String,
        originBase: String,
        existingHeaders: Map<String, String>,
    ): ResolvedStream {
        val referer = "$originBase/"
        val reqHeaders = browserHeaders(originBase, referer)
        val html = runCatching { httpRequest(pageUrl, "GET", reqHeaders) }.getOrDefault("")

        val directM3u8 = extractFirstM3u8FromHtml(html)
        if (!directM3u8.isNullOrBlank()) {
            return ResolvedStream(
                uri = directM3u8,
                headers = existingHeaders + reqHeaders,
                mimeType = MimeTypes.APPLICATION_M3U8,
            )
        }

        val contentId = extractHex24Id(html)
        if (contentId != null) {
            val apiEndpoints = listOf(
                "$originBase/actions/media?id=$contentId&isMobile=false",
                "$originBase/api/cnnvideo/media?id=$contentId&isMobile=false",
            )
            for (api in apiEndpoints) {
                val json = runCatching { httpRequest(api, "GET", reqHeaders) }.getOrDefault("")
                val mediaUrl = parseDemirorenMediaJson(json)
                if (!mediaUrl.isNullOrBlank()) {
                    return ResolvedStream(
                        uri = mediaUrl,
                        headers = existingHeaders + reqHeaders,
                        mimeType = MimeTypes.APPLICATION_M3U8,
                    )
                }
            }
        }

        return ResolvedStream(
            uri = pageUrl,
            headers = existingHeaders + reqHeaders,
            mimeType = null,
        )
    }

    private fun resolveTurkuvaz(
        pageUrl: String,
        originBase: String,
        baseStreamUrl: String,
        existingHeaders: Map<String, String>,
    ): ResolvedStream {
        val encodedUrl = URLEncoder.encode(baseStreamUrl, "UTF-8")
        val tokenEndpoint = "https://securevideotoken.tmgrup.com.tr/webtv/secure?url=$encodedUrl"
        val tokenHeaders = mapOf(
            "User-Agent" to CHROME_USER_AGENT,
            "Referer" to pageUrl,
            "Origin" to originBase.trimEnd('/'),
            "X-isApp" to "false",
            "X-Rand" to System.currentTimeMillis().toString(),
        )
        val json = runCatching { httpRequest(tokenEndpoint, "GET", tokenHeaders) }.getOrDefault("")
        val streamUrl = parseTurkuvazSecureJson(json, baseStreamUrl)
        val playbackHeaders = browserHeaders(originBase, pageUrl)
        return ResolvedStream(
            uri = streamUrl,
            headers = existingHeaders + playbackHeaders,
            mimeType = MimeTypes.APPLICATION_M3U8,
        )
    }

    private fun resolveDyg(
        pageUrl: String,
        originBase: String,
        channelKey: String,
        existingHeaders: Map<String, String>,
    ): ResolvedStream {
        val playbackHeaders = browserHeaders(originBase, "$originBase/")
        val buildJsUrl = "https://player-live.dygdigital.com/js/build.js"
        val js = runCatching { httpRequest(buildJsUrl, "GET", playbackHeaders) }.getOrDefault("")
        val fromJs = parseDygPlayerBuildJs(js, channelKey)
        if (!fromJs.isNullOrBlank()) {
            return ResolvedStream(
                uri = fromJs,
                headers = existingHeaders + playbackHeaders,
                mimeType = MimeTypes.APPLICATION_M3U8,
            )
        }
        return resolveGenericWebPage(pageUrl, originBase, existingHeaders)
    }

    private fun resolveCnbce(
        pageUrl: String,
        originBase: String,
        existingHeaders: Map<String, String>,
    ): ResolvedStream {
        val playbackHeaders = browserHeaders(originBase, pageUrl)
        val apiUrl = "https://www.cnbce.com/api/live-stream/source?test=1"
        val json = runCatching { httpRequest(apiUrl, "POST", playbackHeaders) }.getOrDefault("")
        val sourceUrl = parseCnbceSourceJson(json)
        if (!sourceUrl.isNullOrBlank()) {
            return ResolvedStream(
                uri = sourceUrl,
                headers = existingHeaders + playbackHeaders,
                mimeType = MimeTypes.APPLICATION_M3U8,
            )
        }
        return resolveGenericWebPage(pageUrl, originBase, existingHeaders)
    }

    private fun resolveGenericWebPage(
        pageUrl: String,
        originBase: String,
        existingHeaders: Map<String, String>,
    ): ResolvedStream {
        val playbackHeaders = browserHeaders(originBase, "$originBase/")
        val html = runCatching { httpRequest(pageUrl, "GET", playbackHeaders) }.getOrDefault("")
        val m3u8 = extractFirstM3u8FromHtml(html)
        return if (!m3u8.isNullOrBlank()) {
            ResolvedStream(
                uri = m3u8,
                headers = existingHeaders + playbackHeaders,
                mimeType = MimeTypes.APPLICATION_M3U8,
            )
        } else {
            ResolvedStream(
                uri = pageUrl,
                headers = existingHeaders + playbackHeaders,
                mimeType = null,
            )
        }
    }

    internal fun browserHeaders(originBase: String, refererUrl: String = "${originBase.trimEnd('/')}/"): Map<String, String> =
        mapOf(
            "User-Agent" to CHROME_USER_AGENT,
            "Referer" to refererUrl,
            "Origin" to originBase.trimEnd('/'),
        )

    internal fun extractCnnTurkContentId(html: String): String =
        extractHex24Id(html) ?: DEFAULT_CNN_TURK_CONTENT_ID

    internal fun extractHex24Id(html: String): String? {
        if (html.isBlank()) return null
        val varRegex = Regex("""\b(?:const|let|var)\s+contentId\s*=\s*["']([a-fA-F0-9]{24})["']""")
        varRegex.find(html)?.groupValues?.get(1)?.let { return it }
        val attrRegex = Regex(
            """(?:contentId|data-id|mediaId|video-id|data-media-id)[^a-fA-F0-9]{1,24}([a-fA-F0-9]{24})""",
            RegexOption.IGNORE_CASE
        )
        return attrRegex.find(html)?.groupValues?.get(1)
    }

    internal fun parseDemirorenMediaJson(json: String): String? {
        if (json.isBlank()) return null
        val defaultServiceUrl = Regex(""""DefaultServiceUrl"\s*:\s*"([^"]+)"""")
            .find(json)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        val securePath = Regex(""""SecurePath"\s*:\s*"([^"]+)"""")
            .find(json)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        if (!securePath.isNullOrBlank()) {
            if (securePath.startsWith("http://", ignoreCase = true) || securePath.startsWith("https://", ignoreCase = true)) {
                return securePath
            }
            if (!defaultServiceUrl.isNullOrBlank()) {
                val base = defaultServiceUrl.trimEnd('/')
                val path = if (securePath.startsWith("/")) securePath else "/$securePath"
                return base + path
            }
        }
        return extractFirstM3u8FromHtml(json)
    }

    internal fun parseTurkuvazSecureJson(json: String, fallbackUrl: String): String {
        if (json.isBlank()) return fallbackUrl
        val url = Regex(""""Url"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(json)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        return if (!url.isNullOrBlank() && (url.startsWith("http://", true) || url.startsWith("https://", true))) {
            url
        } else {
            fallbackUrl
        }
    }

    internal fun parseDygPlayerBuildJs(js: String, channelKey: String): String? {
        if (js.isBlank()) return null
        val specific = Regex(
            """\b${Regex.escape(channelKey)}\s*:\s*\{\s*development\s*:\s*"[^"]+"\s*,\s*production\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE
        ).find(js)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        if (!specific.isNullOrBlank()) return specific
        val generic = Regex("""(?:Startv|Ntv)\s*:\s*\{\s*development\s*:\s*"[^"]+"\s*,\s*production\s*:\s*"([^"]+)"""")
            .find(js)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        return generic?.takeIf { it.isNotBlank() }
    }

    internal fun parseCnbceSourceJson(json: String): String? {
        if (json.isBlank()) return null
        val source = Regex(""""source"\s*:\s*"([^"]+)"""")
            .find(json)?.groupValues?.get(1)?.let(::unescapeUrl)?.trim()
        return source?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
    }

    internal fun extractFirstM3u8FromHtml(html: String): String? {
        if (html.isBlank()) return null
        val normalized = unescapeUrl(html)
        val matches = Regex("""https?://[^\s"'<>\\`]+\.m3u8[^\s"'<>\\`]*""", RegexOption.IGNORE_CASE)
            .findAll(normalized)
            .map { it.value.trimEnd(',', ';', ')', ']') }
            .toList()
        if (matches.isEmpty()) return null
        val preferredDomains = listOf("daioncdn.net", "duhnet.tv", "ercdn.net", "mncdn.com", "dygdigital.com", "medianova")
        return matches.firstOrNull { url -> preferredDomains.any { d -> url.contains(d, ignoreCase = true) } }
            ?: matches.first()
    }

    internal fun unescapeUrl(raw: String): String =
        raw.replace("""\/""", "/")
            .replace("""\u0026""", "&")
            .replace("""\u0026""", "&")
            .replace("""\u003d""", "=", ignoreCase = true)
            .replace("""\u003f""", "?", ignoreCase = true)
            .replace("&amp;", "&")

    private fun httpRequest(url: String, method: String, headers: Map<String, String>): String {
        var currentUri = URI(url)
        repeat(5) {
            val connection = (currentUri.toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                useCaches = false
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (method == "POST") {
                    connection.doOutput = true
                    connection.outputStream.use { }
                }
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val loc = connection.getHeaderField("Location") ?: return ""
                    currentUri = currentUri.resolve(loc)
                } else if (code in 200..299) {
                    val rawStream: InputStream = connection.inputStream
                    val stream = if ("gzip".equals(connection.contentEncoding, ignoreCase = true)) {
                        GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }
                    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    return ""
                }
            } finally {
                connection.disconnect()
            }
        }
        return ""
    }
}
