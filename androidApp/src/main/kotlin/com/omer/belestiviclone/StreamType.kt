package com.omer.belestiviclone

import java.net.URI

/** Request addresses stay untouched; only the runtime MediaItem MIME hint is inferred. */
internal object StreamType {
    const val HLS = "application/x-mpegURL"
    fun needsProbe(uri: String, explicitMime: String?): Boolean {
        if (!explicitMime.isNullOrBlank()) return false
        val address = runCatching { URI(uri) }.getOrNull() ?: return false
        if (address.scheme?.lowercase() !in setOf("http", "https")) return false
        val path = address.path.orEmpty().lowercase()
        return listOf(".m3u8", ".mpd", ".ism", ".mp4", ".mp3", ".aac", ".m4a", ".ts", ".ogg", ".wav", ".webm").none(path::endsWith)
    }
    fun fromResponse(contentType: String?, prefix: ByteArray): String? {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        val text = prefix.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return if (type in setOf("application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl") ||
            (text.startsWith("#EXTM3U") && text.contains("#EXT-X-"))) HLS else null
    }
}
