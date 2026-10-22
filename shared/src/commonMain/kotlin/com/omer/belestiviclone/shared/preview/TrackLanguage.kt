package com.omer.belestiviclone.shared.preview

/** Stored labels remain byte-for-byte intact. Only runtime requests use these language tags. */
object TrackLanguage {
    val labels = mapOf("Türkçe" to "tr", "English" to "en", "Deutsch" to "de", "Français" to "fr",
        "Español" to "es", "العربية" to "ar", "Русский" to "ru")
    fun normalize(tag: String?): String? = tag?.trim()?.replace('_', '-')?.lowercase()
        ?.takeIf { it.isNotEmpty() && it.substringBefore('-') != "und" && it.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")) }
        ?.let { tag ->
            val base=tag.substringBefore('-')
            val normalized=when(base) { "eng" -> "en"; "tur" -> "tr"; "deu", "ger" -> "de"; "fra", "fre" -> "fr"; "spa" -> "es"; "ara" -> "ar"; "rus" -> "ru";else -> base }
            normalized+tag.removePrefix(base)
        }
    fun matches(request: String, actual: String?): Boolean = normalize(actual)?.let {
        it == request || it.substringBefore('-') == request.substringBefore('-')
    } == true
    fun audio(label: String) = when (label) {
        "Auto" -> LanguageRequest(LanguageMode.DEFAULT)
        in labels -> LanguageRequest(LanguageMode.LANGUAGE, labels.getValue(label))
        else -> LanguageRequest(LanguageMode.UNKNOWN)
    }
    fun subtitle(label: String) = when (label) {
        "Kapalı" -> LanguageRequest(LanguageMode.OFF)
        "Otomatik (İlk Bulunan)" -> LanguageRequest(LanguageMode.FIRST_SUPPORTED)
        in labels -> LanguageRequest(LanguageMode.LANGUAGE, labels.getValue(label))
        else -> LanguageRequest(LanguageMode.UNKNOWN)
    }
}
enum class LanguageMode { DEFAULT, OFF, LANGUAGE, FIRST_SUPPORTED, UNKNOWN }
data class LanguageRequest(val mode: LanguageMode, val tag: String? = null)
data class LanguageTrack(val group: String, val index: Int, val id: String?, val language: String?,
    val supported: Boolean, val selected: Boolean, val role: Int = 0, val default: Boolean = false)

/** A missing/unsupported requested subtitle never silently displays a different language. */
fun subtitleTrack(request: LanguageRequest, tracks: List<LanguageTrack>): LanguageTrack? {
    val available = tracks.filter { it.supported }
    return when (request.mode) {
        LanguageMode.FIRST_SUPPORTED -> available.firstOrNull()
        LanguageMode.LANGUAGE -> available.firstOrNull { TrackLanguage.normalize(it.language) == request.tag }
            ?: available.firstOrNull { TrackLanguage.matches(requireNotNull(request.tag), it.language) }
        else -> null
    }
}
fun languageStatus(label: String, request: LanguageRequest, tracks: List<LanguageTrack>, text: Boolean = false): String {
    val selected = tracks.firstOrNull { it.selected }
    val actual = selected?.let { "${TrackLanguage.normalize(it.language) ?: "dil belirsiz"} (${it.id ?: it.group})" } ?: "seçilmedi"
    val reason = when {
        request.mode == LanguageMode.OFF -> "kapalı"
        request.mode == LanguageMode.UNKNOWN -> if(text) "bilinmeyen tercih; altyazı kapalı" else "bilinmeyen tercih; kaynak varsayılanı"
        request.mode == LanguageMode.DEFAULT -> "kaynak varsayılanı"
        tracks.isEmpty() -> "track listesi henüz yok"
        tracks.none { it.supported } -> "bu cihazda desteklenen track yok"
        request.mode == LanguageMode.LANGUAGE && tracks.none { it.supported && TrackLanguage.matches(requireNotNull(request.tag), it.language) } -> if(text) "istenen dil yok/desteklenmiyor; altyazı yok" else "istenen dil yok/desteklenmiyor; kaynak varsayılanı"
        request.mode == LanguageMode.LANGUAGE && !TrackLanguage.matches(requireNotNull(request.tag), selected?.language) -> "dil geçişi henüz doğrulanmadı"
        else -> "gerçek seçilen track"
    }
    return "$label: $reason · $actual"
}

data class CueOwner(val attempt: String, val generation: Long)
/** Immutable owner captured when the listener is installed, never inferred from a late event. */
class CueOwnerGate {
    private var generation = 0L
    private var active: CueOwner? = null
    fun advance(attempt: String?): CueOwner? { generation++;active=attempt?.let { CueOwner(it,generation) };return active }
    fun accepts(owner: CueOwner?, mediaId: String?, enabled: Boolean) = enabled && owner != null && owner == active && owner.attempt == mediaId
}
