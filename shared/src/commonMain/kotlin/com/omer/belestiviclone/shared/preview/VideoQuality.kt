package com.omer.belestiviclone.shared.preview

/** A resolution ceiling, never a promise to upscale or choose a particular variant. */
data class PlaybackPreferences(val quality: String = "720p HD", val audio: String = "Türkçe", val subtitle: String = "Kapalı") {
    val cap: VideoQualityCap? get() = when (quality) {
        "360p" -> VideoQualityCap(640, 360)
        "480p" -> VideoQualityCap(854, 480)
        "720p HD" -> VideoQualityCap(1280, 720)
        "1080p FHD" -> VideoQualityCap(1920, 1080)
        "4K UHD" -> VideoQualityCap(3840, 2160)
        else -> null // Preserve unknown stored labels; do not rewrite the user's preference.
    }
}
data class VideoQualityCap(val width: Int, val height: Int) {
    fun contains(width: Int, height: Int) = width > 0 && height > 0 && width <= this.width && height <= this.height
}

/** Common, immutable measurements. Track membership alone is not decoder input evidence. */
data class VideoTrackFormat(val width: Int, val height: Int, val bitrate: Int, val codec: String?)
data class VideoQualityObservation(val input: VideoTrackFormat? = null, val tracks: List<VideoTrackFormat> = emptyList(), val unsupported: Boolean = false) {
    fun description(pref: PlaybackPreferences): String {
        val cap = pref.cap
        val request = if (cap == null) "${pref.quality}: bilinmeyen tercih; kalite sınırı yok" else "Üst sınır: ${pref.quality} (${cap.width}×${cap.height})"
        val measured = input?.let { "İşlenen: ${it.width}×${it.height} · ${if (it.bitrate > 0) "${it.bitrate / 1000} kb/s" else "bitrate bilinmiyor"} · ${it.codec ?: "codec bilinmiyor"}" }
            ?: "İşlenen video formatı henüz ölçülmedi"
        val reason = when {
            cap == null -> ""
            unsupported -> " · Video track'leri bu cihazda desteklenmiyor"
            tracks.isEmpty() || tracks.any { it.width <= 0 || it.height <= 0 } -> " · Kaynak seviyeleri bilinmiyor"
            tracks.none { cap.contains(it.width, it.height) } -> " · Sınır içinde desteklenen track yok; video seçilemedi"
            input != null && !cap.contains(input.width, input.height) -> " · İşlenen format sınır üstünde; geçiş henüz doğrulanmadı"
            tracks.none { it.height == cap.height } -> " · İstenen seviye kaynakta yok; alt seviyeler kullanılabilir"
            else -> " · Adaptif seçim sınır altında çalışabilir"
        }
        return "$request\n$measured$reason"
    }
}
