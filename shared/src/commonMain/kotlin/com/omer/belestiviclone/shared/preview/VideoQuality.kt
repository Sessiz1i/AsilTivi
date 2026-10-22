package com.omer.belestiviclone.shared.preview

/** A resolution ceiling, never a promise to upscale or choose a particular variant. */
data class PlaybackPreferences(
    val quality: String = "720p HD",
    val audio: String = "Türkçe",
    val subtitle: String = "Kapalı",
    val subtitleSize: String = "Normal",
    val subtitleColor: String = "Beyaz",
    val subtitleBgOpacity: String = "%75",
    val backgroundAudio: String = "Kapalı",
) {
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

val LiveFormatOptions = listOf(
    "Otomatik (Orijinal yayın oranı)",
    "Ekrana Yay (Stretch / Fill)",
    "Yakınlaştır (Zoom / Crop)",
    "16:9 (Geniş ekran)",
    "4:3 (Klasik ekran)",
    "1.85:1 (Geniş sinema)",
    "2.39:1 (Anamorfik sinema)",
)

fun isVideoFormatSelected(stored: String, option: String): Boolean = when {
    stored == option -> true
    stored in listOf("Orijinal", "Otomatik", "Otomatik (Orijinal)", "Sığdır (Fit)") && option.startsWith("Otomatik") -> true
    stored in listOf("Ekrana Yay", "Ekrana yay") && option.startsWith("Ekrana Yay") -> true
    stored in listOf("Tam Ekran (Kes)", "Yakınlaştır") && option.startsWith("Yakınlaştır") -> true
    stored == "16:9" && option.startsWith("16:9") -> true
    stored == "4:3" && option.startsWith("4:3") -> true
    stored in listOf("1.85:1", "Sinema 1.85:1") && option.startsWith("1.85:1") -> true
    stored in listOf("2.39:1", "Sinema 2.39:1") && option.startsWith("2.39:1") -> true
    else -> false
}

data class StreamTechnicalInfo(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoCodec: String? = null,
    val videoMimeType: String? = null,
    val videoBitrate: Int = 0,
    val audioCodec: String? = null,
    val audioMimeType: String? = null,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val audioBitrate: Int = 0,
    val measuredBitrate: Int = 0,
    val streamTitle: String? = null,
) {
    val resolutionBadge: String get() = when {
        width <= 0 || height <= 0 -> "CANLI"
        height >= 2160 || width >= 3840 -> "4K UHD"
        height >= 1080 || width >= 1920 -> "1080p FHD"
        height >= 720 || width >= 1280 -> "720p HD"
        height >= 576 -> "576p SD"
        height >= 480 -> "480p SD"
        height >= 360 -> "360p"
        else -> "${height}p"
    }

    val resolutionLabel: String get() =
        if (width > 0 && height > 0) "${width}x${height} (${resolutionBadge.substringBefore(' ')})"
        else "—"

    val fpsLabel: String get() {
        if (frameRate <= 0f || !frameRate.isFinite()) return "—"
        val rounded = kotlin.math.round(frameRate).toInt()
        return if (kotlin.math.abs(frameRate - rounded) < 0.05f) "$rounded fps"
        else "${kotlin.math.round(frameRate * 100f) / 100f} fps"
    }

    val videoCodecLabel: String get() {
        val raw = "${videoMimeType.orEmpty()} ${videoCodec.orEmpty()}".lowercase()
        return when {
            raw.isBlank() -> "—"
            "avc" in raw || "h264" in raw || "h.264" in raw -> "H.264 / AVC"
            "hevc" in raw || "hvc" in raw || "hev1" in raw || "h265" in raw || "h.265" in raw -> "H.265 / HEVC"
            "vp9" in raw -> "VP9"
            "av01" in raw || "av1" in raw -> "AV1"
            "mp2v" in raw || "mpeg2" in raw -> "MPEG-2"
            "mp4v" in raw -> "MPEG-4"
            !videoCodec.isNullOrBlank() -> videoCodec
            !videoMimeType.isNullOrBlank() -> videoMimeType.substringAfter('/').uppercase()
            else -> "—"
        }
    }

    val audioCodecLabel: String get() {
        val raw = "${audioMimeType.orEmpty()} ${audioCodec.orEmpty()}".lowercase()
        return when {
            raw.isBlank() -> "—"
            "mp4a" in raw || "aac" in raw -> "AAC"
            "eac3" in raw || "ec-3" in raw -> "E-AC-3"
            "ac3" in raw || "ac-3" in raw -> "AC-3"
            "mpeg" in raw || "mp3" in raw -> "MP3"
            "opus" in raw -> "Opus"
            "vorbis" in raw -> "Vorbis"
            "flac" in raw -> "FLAC"
            !audioCodec.isNullOrBlank() -> audioCodec
            !audioMimeType.isNullOrBlank() -> audioMimeType.substringAfter('/').uppercase()
            else -> "—"
        }
    }

    val audioFormatLabel: String get() {
        val parts = mutableListOf<String>()
        val codec = audioCodecLabel
        if (codec != "—") parts += codec
        if (audioChannels > 0) parts += "$audioChannels kanal"
        if (audioSampleRate > 0) {
            val khz = if (audioSampleRate % 1000 == 0) "${audioSampleRate / 1000} kHz"
            else "${(audioSampleRate / 100) / 10.0} kHz"
            parts += khz
        }
        return if (parts.isEmpty()) "—" else parts.joinToString(", ")
    }

    val effectiveBitrate: Int get() = when {
        videoBitrate > 0 -> videoBitrate + audioBitrate.coerceAtLeast(0)
        measuredBitrate > 0 -> measuredBitrate
        audioBitrate > 0 -> audioBitrate
        else -> 0
    }

    val bitrateLabel: String get() {
        val bps = effectiveBitrate
        if (bps <= 0) return "—"
        val kbps = bps / 1000
        return if (bps >= 1_000_000) {
            val mbps = (bps / 100_000) / 10.0
            "$mbps Mbps ($kbps kbps)"
        } else {
            "$kbps kbps"
        }
    }

    val summaryLine: String get() =
        "$resolutionLabel · $fpsLabel · $videoCodecLabel · $bitrateLabel"
}
