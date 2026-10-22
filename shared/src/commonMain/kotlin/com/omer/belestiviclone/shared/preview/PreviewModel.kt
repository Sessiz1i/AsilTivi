package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.catalog.PlaybackDefinition
import com.omer.belestiviclone.shared.catalog.ComparisonV1

data class PreviewChannel(val id: OpaqueId, val name: String, val category: String, val logo: String, val video: Boolean = false,
    val definition: PlaybackDefinition? = null, val groups: List<String> = listOf(category), val providerId: String = "", val available: Boolean = true,
    val sourceReferences: List<OpaqueId> = emptyList(), val tvgName: String = "")
object PreviewFixture {
    val channels = listOf(
        PreviewChannel(OpaqueId(0x50524556, 1), "Örnek video", "Örnek", "ikon_tv", true),
        PreviewChannel(OpaqueId(0x50524556, 2), "TRT 1", "Ulusal", "logo_trt1"),
        PreviewChannel(OpaqueId(0x50524556, 3), "Show TV", "Ulusal", "logo_show"),
        PreviewChannel(OpaqueId(0x50524556, 4), "ATV", "Ulusal", "logo_atv"),
        PreviewChannel(OpaqueId(0x50524556, 5), "NOW", "Ulusal", "logo_now"),
        PreviewChannel(OpaqueId(0x50524556, 6), "Star TV", "Ulusal", "logo_star"),
        PreviewChannel(OpaqueId(0x50524556, 7), "TV8", "Ulusal", "logo_tv8"),
        PreviewChannel(OpaqueId(0x50524556, 8), "A Haber", "Haber", "logo_ahaber"),
        PreviewChannel(OpaqueId(0x50524556, 9), "NTV", "Haber", "logo_ntv"),
        PreviewChannel(OpaqueId(0x50524556, 10), "TRT GENÇ", "Çocuk", "logo_trtgenc"),
        PreviewChannel(OpaqueId(0x50524556, 11), "TRT Belgesel", "Belgesel", "logo_trtbelgesel"),
        PreviewChannel(OpaqueId(0x50524556, 12), "TRT 4K", "Ulusal", "logo_trt4k"),
    )
    val categories = listOf("Örnek", "Ulusal", "Haber", "Spor", "Çocuk", "Müzik", "Belgesel")
    const val videoUri = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4"
}

/** Length-prefixed strings preserve exact labels and separators without JSON. */
object PreviewCodec {
    fun pack(values: List<String>): String = values.joinToString("") { "${it.length}:$it" }
    fun unpack(value: String): List<String> {
        val result = mutableListOf<String>(); var offset = 0
        while (offset < value.length) {
            val colon = value.indexOf(':', offset); require(colon >= offset)
            val size = value.substring(offset, colon).toInt(); require(size >= 0 && size <= value.length - colon - 1)
            offset = colon + 1; result += value.substring(offset, offset + size); offset += size
        }
        return result
    }
    fun id(value: OpaqueId): String = "${value.high},${value.low}"
    fun id(value: String): OpaqueId = value.split(',').let { require(it.size == 2); OpaqueId(it[0].toLong(), it[1].toLong()) }
}

data class PreviewData(
    val favorites: List<OpaqueId> = PreviewFixture.channels.take(5).map { it.id },
    val hidden: Set<OpaqueId> = emptySet(),
    val history: List<OpaqueId> = PreviewFixture.channels.take(3).map { it.id },
    val categories: List<String> = PreviewFixture.categories,
    val hiddenCategories: Set<String> = emptySet(),
    val preferences: Map<String, String> = mapOf(
        "list" to "Yatay (Alt)",
        "opacity" to "72",
        "position" to "Orta",
        "size" to "Normal",
        "language" to "Türkçe",
        "history" to "Açık",
        "startup" to "Açık",
        "quality" to "720p HD",
        "format" to "Orijinal",
        "audio" to "Türkçe",
        "subtitle" to "Kapalı",
        "theme" to "Beleş TiVi Klasik",
        "sleepTimer" to "Kapalı",
        "subtitleSize" to "Normal",
        "subtitleColor" to "Beyaz",
        "subtitleBgOpacity" to "%75",
        "startupFavorites" to "Kapalı",
        "backgroundAudio" to "Kapalı",
        "autoPip" to "Açık",
        "epgUrl" to ""
    ),
    val sources: List<String> = listOf("Örnek kayıt"),
    val channels: List<PreviewChannel> = PreviewFixture.channels,
    val profileId: OpaqueId = OpaqueId(0x50524556, 0),
    val library: SourceLibrary? = null,
) {
    fun pref(key: String) = preferences.getValue(key)
    fun withPref(key: String, value: String) = copy(preferences = preferences + (key to value))
    fun filtered(category: String, query: String): List<PreviewChannel> {
        val base = when (category) {
            "Favoriler" -> favorites.mapNotNull { id -> channels.find { it.id == id } }
            "Son İzlenenler" -> history.mapNotNull { id -> channels.find { it.id == id } }
            "Tümü" -> channels.filter { it.available }
            else -> channels.filter { it.available && category in it.groups }
        }
        return base.filter { it.id !in hidden && it.name.contains(query, ignoreCase = true) }
    }
    fun toggleFavorite(id: OpaqueId) = copy(favorites = if (id in favorites) favorites - id else favorites + id)
    fun move(id: OpaqueId, index: Int): PreviewData {
        if (id !in favorites) return this
        val order = favorites.toMutableList(); order.remove(id); order.add(index.coerceIn(0, order.size), id)
        return copy(favorites = order)
    }
    fun startup(last: OpaqueId?): OpaqueId? {
        if (preferences["startupFavorites"] == "Açık") {
            val favoriteFirst = filtered("Favoriler", "").firstOrNull { it.available }?.id
            if (favoriteFirst != null) return favoriteFirst
        }
        return last?.takeIf { pref("startup") == "Açık" && channels.any { ch -> ch.id == it && ch.available } }
            ?: filtered("Favoriler", "").firstOrNull { it.available }?.id ?: filtered("Tümü", "").firstOrNull()?.id
    }
}
interface PreviewStore {
    suspend fun load(): PreviewData
    suspend fun save(data: PreviewData)
}
class MultiSession(val entry: OpaqueId?) {
    val slots = MutableList<OpaqueId?>(4) { null }.also { it[0] = entry }
    val fit = MutableList(4) { true }
    var count = 2; private set
    var selected = 0
    fun layout(value: Int) { require(value == 2 || value == 4); count = value; if (selected >= count) selected = 0 }
    fun returnChannel() = slots[selected] ?: entry
}
enum class PlaybackStatus { IDLE, LOADING, READY, ENDED, ERROR }
interface PreviewPlayer {
    val status: PlaybackStatus
    val error: String?
    fun preferences(value: PlaybackPreferences)
    fun select(channel: PreviewChannel?)
    fun pause(paused: Boolean)
    fun mute(muted: Boolean)
    fun retry()
    fun release()
}

/** Even a forged fixture video flag cannot cause requests to another channel. */
fun sampleVideoUri(channel: PreviewChannel?): String? =
    if (channel?.video == true && channel.id == PreviewFixture.channels.first().id) PreviewFixture.videoUri else null

/** Public definitions retain their complete signed/query URI; they never substitute the sample. */
fun playbackUri(channel: PreviewChannel?): String? = if (channel?.available == false) null else channel?.definition?.let {
    if (ComparisonV1.playbackKey(it) != null) it.address.raw else null
} ?: if (channel?.definition == null) sampleVideoUri(channel) else null

enum class OkAction { NONE, SHORT, LONG }
data class KeyPressSignal(val longPress: Boolean = false, val repeat: Boolean = false)
class OkGesture {
    private var started: Long? = null
    private var longFired = false
    fun down(now: Long, hasLong: Boolean, platformLongPress: Boolean = false, repeat: Boolean = false): OkAction {
        val start = started
        if (start == null && repeat) return OkAction.NONE
        if (start == null) started = now
        if (hasLong && !longFired && (platformLongPress || (start != null && now - start >= 500))) {
            longFired = true; return OkAction.LONG
        }
        return OkAction.NONE
    }
    fun up(now: Long, hasLong: Boolean): OkAction {
        val start = started ?: return OkAction.NONE
        val action = if (longFired) OkAction.NONE else if (hasLong && now - start >= 500) OkAction.LONG else OkAction.SHORT
        reset(); return action
    }
    fun reset() { started = null; longFired = false }
}
