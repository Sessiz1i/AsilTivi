package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.catalog.PlaybackDefinition
import com.omer.belestiviclone.shared.catalog.ComparisonV1

data class PreviewChannel(val id: OpaqueId, val name: String, val category: String, val logo: String, val video: Boolean = false,
    val definition: PlaybackDefinition? = null, val groups: List<String> = listOf(category), val providerId: String = "", val available: Boolean = true,
    val sourceReferences: List<OpaqueId> = emptyList(), val tvgName: String = "", val isRadio: Boolean = false) {
    val isAudioOrRadio: Boolean get() = isRadio || groups.any { it.equals("Radyo", ignoreCase = true) } || category.equals("Radyo", ignoreCase = true)
}
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
    val radioChannels = listOf(
        PreviewChannel(OpaqueId(0x50524556, 101), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo")),
        PreviewChannel(OpaqueId(0x50524556, 102), "Kral FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo")),
        PreviewChannel(OpaqueId(0x50524556, 103), "Power FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo")),
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
        "theme" to "Klasik",
        "sleepTimer" to "Kapalı",
        "subtitleSize" to "Normal",
        "subtitleColor" to "Beyaz",
        "subtitleBgOpacity" to "%75",
        "startupFavorites" to "Kapalı",
        "backgroundAudio" to "Kapalı",
        "autoPip" to "Açık",
        "epgUrl" to "",
        "savedMultiLayouts" to "",
        "lastMultiLayout" to ""
    ),
    val sources: List<String> = listOf("Örnek kayıt"),
    val channels: List<PreviewChannel> = PreviewFixture.channels,
    val profileId: OpaqueId = OpaqueId(0x50524556, 0),
    val library: SourceLibrary? = null,
) {
    fun pref(key: String): String {
        val raw = preferences.getValue(key)
        return if (key == "theme" && (raw == "Beleş TiVi Klasik" || raw.isBlank())) "Klasik" else raw
    }
    fun withPref(key: String, value: String) = copy(preferences = preferences + (key to value))
    fun filtered(category: String, query: String): List<PreviewChannel> {
        val base = when {
            category.equals("Favoriler", ignoreCase = true) || category.equals("FAVORİ TV", ignoreCase = true) ->
                favorites.mapNotNull { id -> channels.find { it.id == id } }
            category.equals("Son İzlenenler", ignoreCase = true) ->
                history.mapNotNull { id -> channels.find { it.id == id } }
            category.equals("Tümü", ignoreCase = true) || category.equals("TÜMÜ", ignoreCase = true) ->
                channels.filter { it.available }
            category.equals("AZ", ignoreCase = true) || category.equals("AZERBAYCAN", ignoreCase = true) ->
                channels.filter { it.available && (it.groups.any { g -> g.equals("AZ", true) || g.equals("AZERBAYCAN", true) } || it.category.equals("AZ", true) || it.category.equals("AZERBAYCAN", true)) }
            else ->
                channels.filter { it.available && (it.groups.any { g -> g.equals(category, true) } || it.category.equals(category, true)) }
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
enum class MultiLayoutMode(
    val code: String,
    val title: String,
    val subtitle: String,
    val slotCount: Int,
) {
    TWO_SIDE_BY_SIDE("TWO_SIDE_BY_SIDE", "2 Ekran · Yan Yana", "Sol ve sağ 2 eşit yayın · Orta performans", 2),
    ONE_BIG_TWO_SMALL("ONE_BIG_TWO_SMALL", "1 Büyük + 2 Küçük", "Sol büyük ana yayın, sağda 2 küçük yayın", 3),
    ONE_BIG_THREE_SMALL("ONE_BIG_THREE_SMALL", "1 Büyük + 3 Küçük", "Sol büyük ana yayın, sağda 3 küçük yayın", 4),
    FOUR_EQUAL("FOUR_EQUAL", "4 Ekran · Eşit", "2x2 matriste 4 eşit yayın · Yüksek performans", 4);

    companion object {
        fun fromCode(code: String): MultiLayoutMode = entries.find { it.code == code } ?: TWO_SIDE_BY_SIDE
        fun fromCount(count: Int): MultiLayoutMode = when (count) {
            2 -> TWO_SIDE_BY_SIDE
            3 -> ONE_BIG_TWO_SMALL
            4 -> FOUR_EQUAL
            else -> throw IllegalArgumentException("Unsupported screen count: $count")
        }
    }
}

data class SavedMultiLayout(
    val name: String,
    val mode: MultiLayoutMode,
    val slots: List<OpaqueId?>,
    val fit: List<Boolean> = List(4) { true },
) {
    val activeSlotCount: Int get() = slots.take(mode.slotCount).count { it != null }
}

object MultiLayoutCodec {
    fun encodeSingle(layout: SavedMultiLayout): String {
        val normSlots = List(4) { index -> layout.slots.getOrNull(index)?.let(PreviewCodec::id).orEmpty() }
        val normFit = List(4) { index -> if (layout.fit.getOrNull(index) != false) "1" else "0" }
        return PreviewCodec.pack(listOf(layout.name, layout.mode.code) + normSlots + normFit)
    }

    fun decodeSingle(encoded: String): SavedMultiLayout? {
        if (encoded.isBlank()) return null
        return runCatching {
            val parts = PreviewCodec.unpack(encoded)
            require(parts.size >= 6)
            val name = parts[0]
            val mode = MultiLayoutMode.fromCode(parts[1])
            val slots = (2..5).map { idx -> parts[idx].takeIf { it.isNotEmpty() }?.let(PreviewCodec::id) }
            val fit = if (parts.size >= 10) (6..9).map { idx -> parts[idx] != "0" } else List(4) { true }
            SavedMultiLayout(name = name, mode = mode, slots = slots, fit = fit)
        }.getOrNull()
    }

    fun encodeList(layouts: List<SavedMultiLayout>): String =
        PreviewCodec.pack(layouts.map(::encodeSingle))

    fun decodeList(encoded: String?): List<SavedMultiLayout> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            PreviewCodec.unpack(encoded).mapNotNull(::decodeSingle)
        }.getOrDefault(emptyList())
    }
}

class MultiSession(val entry: OpaqueId?) {
    val slots = MutableList<OpaqueId?>(4) { null }.also { it[0] = entry }
    val fit = MutableList(4) { true }
    var mode: MultiLayoutMode = MultiLayoutMode.TWO_SIDE_BY_SIDE; private set
    val count: Int get() = mode.slotCount
    var selected = 0
    fun layout(value: Int) = applyMode(MultiLayoutMode.fromCount(value))
    fun applyMode(nextMode: MultiLayoutMode) {
        mode = nextMode
        if (selected >= count) selected = 0
    }
    fun applySaved(saved: SavedMultiLayout) {
        mode = saved.mode
        for (i in 0 until 4) {
            slots[i] = saved.slots.getOrNull(i)
            fit[i] = saved.fit.getOrNull(i) ?: true
        }
        if (selected >= count) selected = 0
    }
    fun toSaved(name: String): SavedMultiLayout =
        SavedMultiLayout(name = name.trim().ifEmpty { "Çoklu Ekran" }, mode = mode, slots = slots.toList(), fit = fit.toList())
    fun returnChannel() = slots.getOrNull(selected) ?: slots.firstOrNull { it != null } ?: entry
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
