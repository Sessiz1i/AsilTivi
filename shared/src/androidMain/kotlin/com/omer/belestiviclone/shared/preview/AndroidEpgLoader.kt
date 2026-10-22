package com.omer.belestiviclone.shared.preview

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * Android EPG yükleyicisi:
 * - `assets/epg_sample.xml` yerel kaynağını okur (zengin varsayılan şablonla birleştirir)
 * - Harici XMLTV URL (`.xml` veya `.xml.gz`) kaynağını indirir ve yerel önbellekte saklar
 */
class AndroidEpgLoader(private val context: Context) : EpgLoader {
    private val remoteCacheFile get() = File(context.cacheDir, "epg_remote_cache_v1.xml")

    override suspend fun loadSampleXml(): String = withContext(Dispatchers.IO) {
        val assetXml = runCatching {
            context.assets.open("epg_sample.xml").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        if (!assetXml.isNullOrBlank() && "<programme" in assetXml) {
            assetXml
        } else {
            EpgSampleData.DEFAULT_XML
        }
    }

    override suspend fun fetchXml(url: String): String = withContext(Dispatchers.IO) {
        var uri = URI(url.trim())
        repeat(6) { redirects ->
            require(uri.scheme.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept-Encoding", "gzip, identity")
            val watcher = launch(Dispatchers.IO) {
                try { awaitCancellation() } finally { connection.disconnect() }
            }
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
                    val encoding = connection.contentEncoding.orEmpty().lowercase()
                    val isGzip = encoding.contains("gzip") || uri.path.orEmpty().lowercase().endsWith(".gz")
                    val rawStream = if (isGzip) GZIPInputStream(connection.inputStream) else connection.inputStream
                    val xml = rawStream.use { it.boundedText(12 * 1024 * 1024) }
                    currentCoroutineContext().ensureActive()
                    runCatching { remoteCacheFile.writeText(xml, Charsets.UTF_8) }
                    return@withContext xml
                }
            } finally {
                watcher.cancel()
                connection.disconnect()
            }
        }
        error("redirect limit")
    }
}
