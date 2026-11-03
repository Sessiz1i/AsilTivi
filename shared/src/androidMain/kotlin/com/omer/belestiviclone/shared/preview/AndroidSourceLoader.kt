package com.omer.belestiviclone.shared.preview

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.*

class AndroidSourceLoader(private val context: Context) : SourceLoader {
    override suspend fun defaultText(): String = withContext(Dispatchers.IO) {
        context.assets.open("TiviListe.m3u").use { it.boundedText(4 * 1024 * 1024) }
    }
    override suspend fun fetchLatestDefault(preferences: Map<String, String>): String = withContext(Dispatchers.IO) {
        val assetCfg = readAssetSyncConfig()
        val repo = preferences["githubRepo"].orEmpty().trim().ifEmpty { assetCfg["repo"].orEmpty().trim() }
        val path = preferences["githubPath"].orEmpty().trim().ifEmpty {
            assetCfg["path"].orEmpty().trim().ifEmpty {
                val br = assetCfg["branch"].orEmpty().trim().ifEmpty { "main" }
                "$br/TiviListe.m3u"
            }
        }
        val token = preferences["githubToken"].orEmpty().trim().ifEmpty { assetCfg["token"].orEmpty().trim() }
        if (repo.isNotEmpty()) {
            val req = PublicM3u.buildRemoteRequest(repoOrUrl = repo, branchAndPath = path, token = token)
            if (req != null) {
                try {
                    val remoteText = fetch(req.url, req.headers)
                    if (remoteText.trimStart().removePrefix("\uFEFF").startsWith("#EXTM3U", ignoreCase = true)) {
                        return@withContext remoteText
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Uzak sunucuya ulaşılamazsa yerleşik TiviListe.m3u dosyasına düş
                }
            }
        }
        defaultText()
    }
    private fun readAssetSyncConfig(): Map<String, String> = try {
        val raw = context.assets.open("github_sync.json").use { it.boundedText(64 * 1024) }
        val map = mutableMapOf<String, String>()
        Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(raw).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2]
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
    override suspend fun fetch(address: String): String = fetch(address, emptyMap())
    override suspend fun fetch(address: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        var uri = URI(address)
        repeat(6) { redirects ->
            require(uri.scheme.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
            headers.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) connection.setRequestProperty(k, v) }
            // Disconnect promptly on cancellation, including blocking connect/read.
            val watcher = launch(Dispatchers.IO) { try { awaitCancellation() } finally { connection.disconnect() } }
            try {
                currentCoroutineContext().ensureActive()
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    require(redirects < 5)
                    val next = uri.resolve(connection.getHeaderField("Location") ?: error("redirect"))
                    require(!(uri.scheme == "https" && next.scheme == "http"))
                    uri = next
                } else {
                    require(code in 200..299)
                    val text = connection.inputStream.use { it.boundedText(4 * 1024 * 1024) }
                    currentCoroutineContext().ensureActive()
                    return@withContext text
                }
            } finally { watcher.cancel(); connection.disconnect() }
        }
        error("redirect limit")
    }
}

fun java.io.InputStream.boundedText(limit: Int): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer); if (size < 0) break
        require(output.size() + size <= limit)
        output.write(buffer, 0, size)
    }
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    return decoder.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
}

