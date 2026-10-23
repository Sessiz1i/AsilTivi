package com.omer.belestiviclone.sharedui

import androidx.compose.runtime.*
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.catalog.Address
import com.omer.belestiviclone.shared.catalog.ComparisonV1
import com.omer.belestiviclone.shared.catalog.SourceAddressKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.*
import com.omer.belestiviclone.shared.catalog.SourceId
import kotlin.random.Random

class PreviewState(private val store: PreviewStore, private val scope: CoroutineScope, private val publicStore: PreviewStore? = null,
    private val sourceLoader: SourceLoader? = null, allocate: () -> OpaqueId = { OpaqueId(Random.nextLong(), Random.nextLong()) },
    private val epgLoader: EpgLoader? = null) {
    private val sources = SourceOperations(allocate)
    val epgCache = EpgCacheManager().also { it.loadSampleDefault(System.currentTimeMillis()) }
    var epgRevision by mutableStateOf(0); private set
    fun epgNowNext(channel: PreviewChannel? = this.channel, nowEpochMs: Long = System.currentTimeMillis()): EpgNowNext {
        epgRevision // Compose state observation
        return epgCache.nowNext(channel, nowEpochMs)
    }
    private var sourceJob: Job? = null
    private var generation = 0
    var committingSource by mutableStateOf(false); private set
    var documentRequest by mutableStateOf<String?>(null); private set
    val savedSources get() = data.library?.saved.orEmpty()
    val editingFile get() = editing?.let { savedSources.getOrNull(it)?.kind == SourceKind.FILE } == true
    val activeCount get() = data.channels.count { it.available }
    private var demo = PreviewData()
    private var publicData: PreviewData? = null
    val isPublic get() = data.profileId == PublicM3u.profile
    val profileName get() = data.library?.main?.name ?: if (isPublic) "trlist" else "Örnek profil"
    var data by mutableStateOf(PreviewData()); private set
    var loaded by mutableStateOf(false); private set
    var storage by mutableStateOf("Kanallar yükleniyor…"); private set
    var busy by mutableStateOf(false); private set
    var page by mutableStateOf("live")
    var exitRequested by mutableStateOf(false)
    var origin by mutableStateOf("live")
    var section by mutableStateOf(0)
    var category by mutableStateOf("Favoriler")
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    var panel by mutableStateOf(true)
    var categoryMenu by mutableStateOf(false)
    var info by mutableStateOf(false)
    var technicalInfo by mutableStateOf(StreamTechnicalInfo())
    var selected by mutableStateOf(PreviewFixture.channels.first().id)
    var dialog by mutableStateOf<String?>(null)
    var target by mutableStateOf<OpaqueId?>(null)
    var notice by mutableStateOf("")
    var paused by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var retry by mutableStateOf(0)
    var moving by mutableStateOf<OpaqueId?>(null)
    var draft by mutableStateOf<PreviewData?>(null)
    var multi by mutableStateOf<MultiSession?>(null)
    var multiRevision by mutableStateOf(0)
    var multiPickerCategory by mutableStateOf("Tümü")
    var multiPickerQuery by mutableStateOf("")
    val multiPickerCategories: List<String> get() =
        listOf("Favoriler") +
            (if (data.pref("history") == "Açık") listOf("Son İzlenenler") else emptyList()) +
            listOf("Tümü") +
            data.categories.filter { it !in data.hiddenCategories && data.channels.any { ch -> ch.available && it in ch.groups } }
    val multiPickerChannels: List<PreviewChannel> get() =
        data.filtered(multiPickerCategory, multiPickerQuery).filter { it.available }
    val savedMultiLayouts: List<SavedMultiLayout> get() =
        MultiLayoutCodec.decodeList(data.preferences["savedMultiLayouts"])
    val lastMultiLayout: SavedMultiLayout? get() =
        MultiLayoutCodec.decodeSingle(data.preferences["lastMultiLayout"].orEmpty())
    fun openMultiChannelPicker(slotIndex: Int? = null) {
        val m = multi ?: return
        if (slotIndex != null && slotIndex in 0 until m.count) {
            m.selected = slotIndex
            multiRevision++
        }
        multiPickerQuery = ""
        if (multiPickerCategory !in multiPickerCategories) multiPickerCategory = "Tümü"
        dialog = "assign"
    }
    fun chooseMultiPickerCategory(cat: String) {
        multiPickerCategory = cat
        multiPickerQuery = ""
    }
    fun assignMultiChannel(channelId: OpaqueId?) {
        val m = multi ?: return
        if (m.selected in 0 until m.count) {
            m.slots[m.selected] = channelId
            multiRevision++
        }
        val encoded = MultiLayoutCodec.encodeSingle(m.toSaved("Son Çoklu Ekran"))
        commit(data.withPref("lastMultiLayout", encoded)) {
            dismiss()
        }
    }
    private fun persistLastMultiLayout() {
        val m = multi ?: return
        val encoded = MultiLayoutCodec.encodeSingle(m.toSaved("Son Çoklu Ekran"))
        if (data.preferences["lastMultiLayout"] == encoded) return
        data = data.withPref("lastMultiLayout", encoded)
    }
    fun startMulti(count: Int) = startMulti(MultiLayoutMode.fromCount(count))
    fun startMulti(mode: MultiLayoutMode) {
        leaveLiveLayers()
        if (multi == null) multi = MultiSession(selected)
        multi!!.applyMode(mode)
        multiRevision++
        page = "multi"
        persistLastMultiLayout()
        dismiss()
    }
    fun openSaveMultiLayoutDialog() {
        if (multi == null || busy) return
        val m = multi!!
        val chNames = (0 until m.count).mapNotNull { idx -> data.channels.find { it.id == m.slots[idx] }?.name }
        formName = if (chNames.isNotEmpty()) chNames.take(2).joinToString(" + ") else m.mode.title
        dialog = "saveMultiLayout"
    }
    fun saveCurrentMultiLayout(customName: String = formName) {
        val m = multi ?: return
        if (busy) return
        val cleanName = customName.trim().take(80).ifEmpty {
            val chNames = (0 until m.count).mapNotNull { idx -> data.channels.find { it.id == m.slots[idx] }?.name }
            if (chNames.isNotEmpty()) chNames.take(2).joinToString(" + ") else m.mode.title
        }
        val entry = m.toSaved(cleanName)
        val updated = listOf(entry) + savedMultiLayouts.filterNot { it.name.equals(cleanName, ignoreCase = true) }
        val next = data
            .withPref("savedMultiLayouts", MultiLayoutCodec.encodeList(updated))
            .withPref("lastMultiLayout", MultiLayoutCodec.encodeSingle(m.toSaved("Son Çoklu Ekran")))
        commit(next) {
            dismiss()
            notice = message("Çoklu ekran düzeni kaydedildi: $cleanName", "Multi-screen layout saved: $cleanName", "Mehrfachbild-Layout gespeichert: $cleanName")
        }
    }
    fun loadSavedMultiLayout(saved: SavedMultiLayout) {
        leaveLiveLayers()
        if (multi == null) multi = MultiSession(selected)
        multi!!.applySaved(saved)
        multiRevision++
        page = "multi"
        persistLastMultiLayout()
        dismiss()
        notice = message("${saved.name} düzeni yüklendi.", "Loaded layout: ${saved.name}.", "Layout geladen: ${saved.name}.")
    }
    fun loadLastMultiLayout(): Boolean {
        val last = lastMultiLayout ?: return false
        loadSavedMultiLayout(last)
        return true
    }
    fun deleteSavedMultiLayout(name: String) {
        if (busy) return
        val updated = savedMultiLayouts.filterNot { it.name == name }
        commit(data.withPref("savedMultiLayouts", MultiLayoutCodec.encodeList(updated))) {
            notice = message("Kayıtlı düzen silindi: $name", "Saved layout deleted: $name", "Gespeichertes Layout gelöscht: $name")
        }
    }
    var formName by mutableStateOf("")
    var formAddress by mutableStateOf("")
    var formUser by mutableStateOf("")
    var formPassword by mutableStateOf("")
    var formMac by mutableStateOf("")
    var editing by mutableStateOf<Int?>(null)
    var lastXtreamAccount by mutableStateOf<XtreamAccountInfo?>(null); private set
    var lastStalkerSession by mutableStateOf<StalkerSession?>(null); private set
    val activeSourceKind: SourceKind get() = data.library?.main?.kind ?: SourceKind.DEFAULT
    val activeSourceKindBadge: String get() = data.library?.main?.kindBadge() ?: "VARSAYILAN"
    val enabledCountryCodes: Set<String> get() = CountryListsCatalog.parseEnabledCodes(data.preferences["countryLists"])
    val countryListsSubtitle: String get() {
        val codes = enabledCountryCodes
        return if (codes.isEmpty()) "Türkiye aktif · KKTC, AZ, Almanya, Dünya ekle"
        else "Türkiye + ${codes.joinToString(", ")} dahil edildi"
    }
    var isRadioMode by mutableStateOf(false); private set
    var lastTvChannel by mutableStateOf<OpaqueId?>(null); private set
    var lastRadioChannel by mutableStateOf<OpaqueId?>(null); private set
    val radioChannels get() = data.channels.filter { it.available && it.isAudioOrRadio }.ifEmpty { PreviewFixture.radioChannels }
    fun enterRadioMode() {
        if (isRadioMode) return
        lastTvChannel = selected
        isRadioMode = true
        panel = true
        val target = lastRadioChannel?.takeIf { id -> radioChannels.any { it.id == id } } ?: radioChannels.firstOrNull()?.id
        if (target != null && target != selected) select(target)
        notice = message("Radyo modu açıldı.", "Radio mode enabled.", "Radiomodus aktiviert.")
    }
    fun exitRadioMode() {
        if (!isRadioMode) return
        lastRadioChannel = selected
        isRadioMode = false
        val tvChannels = data.channels.filter { it.available && !it.isAudioOrRadio }
        val target = lastTvChannel?.takeIf { id -> tvChannels.any { it.id == id } } ?: tvChannels.firstOrNull()?.id
        if (target != null && target != selected) select(target)
        notice = message("TV moduna dönüldü.", "Returned to TV mode.", "Zurück zum TV-Modus.")
    }
    fun toggleRadioMode() {
        if (isRadioMode) exitRadioMode() else enterRadioMode()
    }
    var opacityDraft by mutableStateOf<Int?>(null); private set
    val opacityValue get() = opacityDraft ?: data.pref("opacity").toInt().coerceIn(0, 100)
    fun updateOpacity(value: Int) { if (!busy) opacityDraft = value.coerceIn(0, 100) }
    fun cancelOpacity() { opacityDraft = null }
    fun commitOpacity() {
        val value = opacityDraft ?: return
        if (value.toString() == data.pref("opacity")) { cancelOpacity(); return }
        commit(data.withPref("opacity", value.toString())) { cancelOpacity() }
    }
    var returnAfterClear by mutableStateOf(false)
    var sessionOnly by mutableStateOf(false); private set
    private var sleepTimerJob: Job? = null
    var sleepTimerRemainingSeconds by mutableStateOf<Int?>(null); private set
    fun applySleepTimer(value: String) {
        sleepTimerJob?.cancel()
        if (value == "Kapalı") {
            sleepTimerJob = null
            sleepTimerRemainingSeconds = null
            return
        }
        val minutes = value.removeSuffix(" dk").trim().toIntOrNull() ?: return
        sleepTimerRemainingSeconds = minutes * 60
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                sleepTimerRemainingSeconds = remaining
                delay(1000)
                remaining--
            }
            sleepTimerRemainingSeconds = 0
            paused = true
            exitRequested = true
        }
    }
    val visibleData get() = draft ?: data
    val channel get() = data.channels.find { it.id == selected }
    val channelNumber: Int get() {
        val list = visibleData.filtered(category, query)
        val inFiltered = list.indexOfFirst { it.id == selected }
        if (inFiltered >= 0) return inFiltered + 1
        val inAll = data.channels.indexOfFirst { it.id == selected }
        return if (inAll >= 0) inAll + 1 else 1
    }
    val normalLiveControls get() = loaded && page == "live" && dialog == null && !searching &&
        query.isEmpty() && moving == null && draft == null && multi == null && !busy
    val canRevealLiveControls get() = normalLiveControls && !panel
    val liveHeadingVisible get() = page == "live" && panel
    fun revealLiveControls() { if (canRevealLiveControls) panel = true }
    fun openFormatMenu() {
        if (busy) return
        dialog = if (dialog == "format") null else "format"
    }
    fun toggleInfo() {
        if (page == "live") info = !info
    }
    fun cycleLiveFormat() {
        if (!normalLiveControls) return
        val current = data.pref("format")
        val index = LiveFormatOptions.indexOfFirst { isVideoFormatSelected(current, it) }
        val next = LiveFormatOptions[(index + 1) % LiveFormatOptions.size]
        pref("format", next)
    }
    fun stepChannel(delta: Int) {
        if (!normalLiveControls || delta !in listOf(-1, 1)) return
        val candidates = if (isRadioMode) radioChannels else visibleData.filtered(category, query)
        if (candidates.isEmpty()) return
        val current = candidates.indexOfFirst { it.id == selected }
        val next = if (current < 0) { if (delta > 0) 0 else candidates.lastIndex }
            else (current + delta + candidates.size) % candidates.size
        val id = candidates[next].id
        if (id != selected) select(id)
    }
    fun text(tr: String, en: String) = if (data.pref("language") == "English") en else tr
    suspend fun load() {
        try { data = store.load(); storage = "Yerel kayıt" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { sessionOnly = true; storage = "Yalnız bu oturumda"; notice = "Yerel kayıt açılamadı. Değişiklikler yalnız bu oturumda tutulacak." }
        demo = data
        selected = data.startup(data.preferences["lastChannel"]?.let { runCatching { PreviewCodec.id(it) }.getOrNull() }) ?: selected
        if (publicStore != null) {
            try { publicData = publicStore.load(); switchProfile(true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = "Kamu listesi açılamadı. Mevcut liste korunuyor." }
        }
        if (epgLoader != null) {
            try {
                val sampleXml = epgLoader.loadSampleXml()
                if (sampleXml.isNotBlank()) {
                    epgCache.loadXml(sampleXml, nowEpochMs = System.currentTimeMillis(), rebaseSampleIfOutdated = true)
                    epgRevision++
                }
                val customEpgUrl = data.pref("epgUrl").trim()
                if (customEpgUrl.isNotBlank()) {
                    val remoteXml = epgLoader.fetchXml(customEpgUrl)
                    if (remoteXml.isNotBlank()) {
                        epgCache.loadXml(remoteXml, nowEpochMs = System.currentTimeMillis(), rebaseSampleIfOutdated = false)
                        epgRevision++
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
        page = "live"
        loaded = true
    }
    fun switchProfile(public: Boolean) {
        if (busy) return
        if (isPublic) publicData = data else demo = data
        val next = if (public) publicData ?: return else demo
        data = next; category = if (public) "Tümü" else "Favoriler"
        selected = next.startup(next.preferences["lastChannel"]?.let { runCatching { PreviewCodec.id(it) }.getOrNull() }) ?: selected
        leaveLiveLayers(); panel = true; dialog = null; multi = null
        storage = "$profileName · yerel kayıt"
        page = "live"
    }
    fun commit(next: PreviewData, done: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(NonCancellable) {
                    if (isPublic) publicStore?.save(next) else if (!sessionOnly) store.save(next)
                    data = next
                    if (category in data.hiddenCategories) category = "Tümü"
                    done()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                draft = null; moving = null
                notice = message("Kaydedilemedi. Önceki kayıt korundu.", "Save failed. Previous record preserved.", "Speichern fehlgeschlagen. Vorheriger Eintrag beibehalten.")
            } finally { busy = false }
        }
    }
    fun pref(key: String, value: String) = commit(data.withPref(key, value)) {
        if (key == "history" && value == "Kapalı" && category == "Son İzlenenler") category = "Tümü"
        dialog = null
        if (key == "quality") notice = message("Kalite üst sınırı kaydedildi. İşlenen çözünürlüğü Bilgi alanından görebilirsiniz.", "Quality ceiling saved. See Info for the processed resolution.", "Qualitätsgrenze gespeichert. Verarbeitete Auflösung unter Info.")
        if (key in listOf("audio", "subtitle")) notice = message("Tercih kaydedildi. Gerçek ses/altyazı seçimini Bilgi alanından kontrol edebilirsiniz.", "Preference saved. Check Info for the actual audio/subtitle selection.", "Einstellung gespeichert. Tatsächliche Ton-/Untertitelauswahl unter Info prüfen.")
        if (key == "sleepTimer") {
            applySleepTimer(value)
            notice = if (value == "Kapalı") "Uyku zamanlayıcısı kapatıldı." else "Uyku zamanlayıcısı $value olarak ayarlandı."
        }
        if (key in listOf("subtitleSize", "subtitleColor", "subtitleBgOpacity")) notice = "Altyazı görünüm tercihi güncellendi."
        if (key == "theme") notice = "Tema: $value seçildi."
    }
    fun chooseCategory(value: String) { category = value; query = ""; categoryMenu = false }
    fun categoryAction(route: String) {
        categoryMenu = false
        when (route) { "multi" -> dialog = "warning"; "settings" -> settings() }
    }
    fun toggleCategory(category: String) {
        if (category !in data.categories) return
        commit(data.copy(hiddenCategories = if (category in data.hiddenCategories) data.hiddenCategories - category else data.hiddenCategories + category))
    }
    fun showCategories(visible: Boolean) = commit(data.copy(hiddenCategories = if (visible) emptySet() else data.categories.toSet()))
    fun moveCategory(category: String, delta: Int) {
        val from = data.categories.indexOf(category)
        val to = from + delta
        if (from < 0 || to !in data.categories.indices || delta !in listOf(-1, 1)) return
        val order = data.categories.toMutableList(); order.removeAt(from); order.add(to, category)
        commit(data.copy(categories = order))
    }
    fun select(id: OpaqueId) {
        if (selected != id) technicalInfo = StreamTechnicalInfo()
        selected = id
        paused = false
        if (playbackUri(channel) == null) notice = if (channel?.available == false)
            message("Kanal etkin kaynaklarda erişilemiyor. Favori ve sırası korundu.", "Channel unavailable in active sources. Favorite and order preserved.", "Kanal in aktiven Quellen nicht verfügbar. Favorit und Reihenfolge beibehalten.")
            else if (isPublic) "Bu kaydın oynatma tanımı desteklenmiyor." else "Yayın bağlantısı bulunamadı."
    }
    /** Called by a real first-frame/audio-advancing event, never by focus or channel selection. */
    fun played(id: OpaqueId) {
        if (busy || data.channels.none { it.id == id && it.available }) return
        val next = data.withPref("lastChannel", PreviewCodec.id(id)).let {
            if (it.pref("history") == "Açık") it.copy(history = listOf(id) + it.history.filter { old -> old != id }) else it
        }
        if (next != data) commit(next)
    }
    private fun leaveLiveLayers() {
        searching = false; query = ""; categoryMenu = false; info = false
        moving = null; draft = null
    }
    fun settings() { cancelOpacity(); leaveLiveLayers(); origin = page; page = "settings"; dialog = null }
    fun dismiss() {
        if (committingSource) return
        cancelSource(); documentRequest = null
        dialog = null; formPassword = ""; formUser = ""; formMac = ""; formAddress = ""; formName = ""; editing = null
    }
    fun moveStart() { moving = target; draft = data; dialog = null }
    fun moveBy(delta: Int) { val id = moving ?: return; val d = draft ?: return; draft = d.move(id, d.favorites.indexOf(id) + delta) }
    fun moveFinish() { val next = draft ?: return; commit(next) { draft = null; moving = null } }
    fun clearHistory() = commit(data.copy(history = emptyList())) {
        dialog = null; notice = message("İzleme geçmişi tamamen temizlendi!", "Watch history cleared!", "Wiedergabeverlauf gelöscht!"); returnAfterClear = true
        if (page != "settings") {
            clearReturn()
            panel = false
            searching = false; query = ""; categoryMenu = false
        }
    }
    private fun clearReturn() {
        category = if (data.filtered("Favoriler", "").isEmpty()) "Tümü" else "Favoriler"
        if (category == "Favoriler" && selected !in data.favorites) selected = data.filtered("Favoriler", "").first().id
        returnAfterClear = false
    }
    fun back() {
        if (sourceJob != null && !committingSource) { cancelSource(); return }
        if (busy) { notice = "Kayıt yazılıyor…"; return }
        when {
            dialog != null -> dismiss()
            opacityDraft != null -> cancelOpacity()
            moving != null -> { draft = null; moving = null }
            searching -> { searching = false; query = "" }
            categoryMenu -> categoryMenu = false
            page == "settings" -> { page = origin; panel = false; if (returnAfterClear) clearReturn() }
            page == "multi" -> { selected = multi?.returnChannel() ?: selected; multi = null; page = "live" }
            page == "live" && info -> info = false
            page == "live" && isRadioMode && panel -> panel = false
            page == "live" && isRadioMode -> exitRadioMode()
            page == "live" && panel -> panel = false
            page == "live" -> dialog = "exit"
        }
    }
    fun channelChange(next: PreviewData) {
        val oldList = data.filtered(category, query)
        val index = oldList.indexOfFirst { it.id == target }.coerceAtLeast(0)
        commit(next) {
            val list = data.filtered(category, query)
            if (list.none { it.id == target }) target = list.getOrNull(index.coerceAtMost(list.lastIndex))?.id
            dismiss()
        }
    }
    fun sourceForm(type: String, index: Int? = null) {
        if (busy) return
        if (type == "epg") {
            editing = null; formName = ""; formAddress = data.pref("epgUrl")
            formUser = ""; formPassword = ""; formMac = ""; dialog = "epg"
            return
        }
        val source = if (type == "supplement") data.library?.extra else index?.let { savedSources.getOrNull(it) }
        editing = index
        formName = source?.name.orEmpty()
        formUser = ""
        formPassword = ""
        formMac = ""
        when (source?.kind) {
            SourceKind.XTREAM -> {
                formAddress = source.address.substringBefore("?user=")
                formUser = source.address.substringAfter("?user=", "")
                dialog = "xtream"
            }
            SourceKind.STALKER -> {
                formAddress = source.address.substringBefore("?mac=")
                formMac = source.address.substringAfter("?mac=", "")
                dialog = "stalker"
            }
            else -> {
                formAddress = source?.address.orEmpty()
                dialog = type
            }
        }
    }
    fun saveEpgUrl() {
        if (busy) return
        val address = formAddress.trim()
        if (address.isNotEmpty() && !address.startsWith("http://", ignoreCase = true) && !address.startsWith("https://", ignoreCase = true)) {
            notice = message("Geçerli bir HTTP/HTTPS adresi girin.", "Enter a valid HTTP/HTTPS address.", "Gültige HTTP/HTTPS-Adresse eingeben.")
            return
        }
        val next = data.withPref("epgUrl", address)
        if (epgLoader != null && address.isNotEmpty()) {
            busy = true
            scope.launch {
                try {
                    val xml = epgLoader.fetchXml(address)
                    withContext(NonCancellable) {
                        epgCache.loadXml(xml, nowEpochMs = System.currentTimeMillis(), rebaseSampleIfOutdated = false)
                        epgRevision++
                        if (isPublic) publicStore?.save(next) else if (!sessionOnly) store.save(next)
                        data = next
                        dismiss()
                        notice = message("EPG kaynağı güncellendi.", "EPG source updated.", "EPG-Quelle aktualisiert.")
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    notice = message("EPG kaynağı yüklenemedi. Önceki rehber korundu.", "Failed to load EPG source. Previous guide preserved.", "EPG-Quelle konnte nicht geladen werden.")
                } finally { busy = false }
            }
        } else {
            if (address.isEmpty()) {
                epgCache.loadSampleDefault(nowEpochMs = System.currentTimeMillis())
                epgRevision++
            }
            commit(next) {
                dismiss()
                notice = if (address.isEmpty()) {
                    message("Varsayılan örnek EPG kaynağı etkinleştirildi.", "Default sample EPG source enabled.", "Standard-EPG-Quelle aktiviert.")
                } else {
                    message("EPG kaynağı kaydedildi.", "EPG source saved.", "EPG-Quelle gespeichert.")
                }
            }
        }
    }
    fun clearEpgUrl() {
        if (busy) return
        formAddress = ""
        saveEpgUrl()
    }
    fun saveSource() {
        when (dialog) {
            "xtream" -> { saveXtreamSource(); return }
            "stalker" -> { saveStalkerSource(); return }
        }
        if (editingFile) {
            val id = savedSources[editing!!].id; val name = formName
            sourceOperation { before -> sources.renameFile(before, id, name) }; return
        }
        val loader = sourceLoader ?: run { notice = "Kaynak yükleyici kullanılamıyor."; return }
        val name = formName.trim(); val address = formAddress.trim(); val extra = dialog == "supplement"
        val id = if (extra) data.library?.extra?.id else editing?.let { savedSources.getOrNull(it)?.id }
        try { sources.validate(sources.bootstrap(data), name, address, id, extra) }
        catch (error: SourceProblem) { sourceError(error); return }
        sourceOperation { before -> sources.save(before, name, address, loader.fetch(address), id, extra) }
    }
    fun saveXtreamSource() {
        val loader = sourceLoader ?: run { notice = "Kaynak yükleyici kullanılamıyor."; return }
        val name = formName.trim()
        val serverUrl = formAddress.trim()
        val user = formUser.trim()
        val pass = formPassword.trim()
        try {
            XtreamCodesClient.normalizeBaseUrl(serverUrl)
            if (user.isEmpty() || pass.isEmpty()) throw SourceProblem("credentials")
            if (name.length > 120) throw SourceProblem("name")
        } catch (error: SourceProblem) {
            sourceError(error)
            return
        }
        val id = editing?.let { savedSources.getOrNull(it)?.id }
        sourceOperation { before ->
            val (account, m3uText) = XtreamCodesClient.connectAndBuildM3u(loader, serverUrl, user, pass)
            lastXtreamAccount = account
            sources.saveXtream(before, name, serverUrl, user, m3uText, id)
        }
    }
    fun saveStalkerSource() {
        val loader = sourceLoader ?: run { notice = "Kaynak yükleyici kullanılamıyor."; return }
        val name = formName.trim()
        val portalUrl = formAddress.trim()
        val mac = formMac.trim().ifEmpty { formUser.trim() }
        try {
            StalkerPortalClient.normalizePortalBaseUrl(portalUrl)
            StalkerPortalClient.normalizeMac(mac)
            if (name.length > 120) throw SourceProblem("name")
        } catch (error: SourceProblem) {
            sourceError(error)
            return
        }
        val id = editing?.let { savedSources.getOrNull(it)?.id }
        sourceOperation { before ->
            val (session, m3uText) = StalkerPortalClient.connectAndBuildM3u(loader, portalUrl, mac)
            lastStalkerSession = session
            sources.saveStalker(before, name, portalUrl, mac, m3uText, id)
        }
    }
    fun toggleCountryList(code: String) {
        if (busy) return
        val preset = CountryListsCatalog.byCode(code) ?: return
        if (preset.code == "TR") {
            if (data.library != null && data.library?.active != PublicM3u.source && sourceLoader != null) {
                restoreDefault()
            } else {
                notice = message("Türkiye varsayılan kanal listesi zaten aktif.", "Turkey default channel list is already active.", "Standard-Kanalliste Türkei ist bereits aktiv.")
            }
            return
        }
        val current = enabledCountryCodes
        val upper = preset.code.uppercase()
        val nextCodes = if (upper in current) current - upper else current + upper
        val added = upper in nextCodes
        val nextData = sources.applyCountryLists(data, nextCodes)
        commit(nextData) {
            notice = if (added) {
                message("${preset.flag} ${preset.name} kanalları listeye dahil edildi (${preset.groupTitle}).", "${preset.flag} ${preset.name} channels added (${preset.groupTitle}).", "${preset.flag} ${preset.name}-Kanäle hinzugefügt (${preset.groupTitle}).")
            } else {
                message("${preset.flag} ${preset.name} kanalları listeden çıkarıldı.", "${preset.flag} ${preset.name} channels removed.", "${preset.flag} ${preset.name}-Kanäle entfernt.")
            }
        }
    }
    fun activateCountrySource(code: String) {
        if (busy) return
        val preset = CountryListsCatalog.byCode(code) ?: return
        if (preset.code == "TR" && sourceLoader != null) {
            restoreDefault()
            return
        }
        sourceOperation { before ->
            sources.saveCountry(before, preset, preset.builtinM3u)
        }
    }
    fun filterByCountry(code: String) {
        if (busy) return
        val preset = CountryListsCatalog.byCode(code) ?: return
        val upper = preset.code.uppercase()
        if (upper != "TR" && upper !in enabledCountryCodes) {
            val nextData = sources.applyCountryLists(data, enabledCountryCodes + upper)
            commit(nextData) {
                category = preset.groupTitle
                query = ""
                dismiss()
                page = "live"
                panel = true
                data.filtered(preset.groupTitle, "").firstOrNull()?.let { select(it.id) }
                notice = "${preset.flag} ${preset.name} kategorisi açıldı."
            }
        } else {
            category = preset.groupTitle
            query = ""
            dismiss()
            page = "live"
            panel = true
            data.filtered(preset.groupTitle, "").firstOrNull()?.let { select(it.id) }
            notice = "${preset.flag} ${preset.name} kategorisi açıldı."
        }
    }
    private fun sourceError(error: Exception) {
        notice = when ((error as? SourceProblem)?.code) {
            "name" -> message("Ad gerekli; en fazla 120 karakter kullanın.", "Name is required (up to 120 characters).", "Name erforderlich (maximal 120 Zeichen).")
            "url" -> message("Geçerli bir HTTP/HTTPS adresi girin.", "Enter a valid HTTP/HTTPS address.", "Gültige HTTP/HTTPS-Adresse eingeben.")
            "credentials" -> message("Kullanıcı adı ve şifre boş bırakılamaz.", "Username and password are required.", "Benutzername und Passwort erforderlich.")
            "xtream-auth" -> message("Kimlik doğrulama başarısız: Kullanıcı adı veya şifre geçersiz.", "Authentication failed: Invalid username or password.", "Authentifizierung fehlgeschlagen: Ungültige Zugangsdaten.")
            "xtream-expired" -> message("Xtream hesabı aktif değil veya süresi dolmuş.", "Xtream account is inactive or expired.", "Xtream-Konto ist inaktiv oder abgelaufen.")
            "mac" -> message("Geçerli bir MAC adresi girin (Örn: 00:1A:79:12:34:56).", "Enter a valid MAC address (e.g. 00:1A:79:12:34:56).", "Gültige MAC-Adresse eingeben (z.B. 00:1A:79:12:34:56).")
            "stalker-auth" -> message("Stalker portal el sıkışması başarısız: MAC adresi yetkisiz veya portal yanıt vermedi.", "Stalker handshake failed: Unauthorized MAC or no response.", "Stalker-Handshake fehlgeschlagen: MAC nicht autorisiert.")
            "network" -> message("Ağ hatası: Sunucuya ulaşılamadı. Adresi ve bağlantınızı kontrol edin.", "Network error: Could not reach server.", "Netzwerkfehler: Server nicht erreichbar.")
            "content" -> message("Geçersiz veya boş kanal listesi alındı. Önceki kaynak korundu.", "Invalid or empty channel list. Previous source preserved.", "Ungültige oder leere Kanalliste. Vorherige Quelle beibehalten.")
            "duplicate" -> message("Bu URL zaten kayıtlı. Mevcut kaydı kullanın; adı değiştirilmedi.", "URL already saved. Use the existing record; its name was preserved.", "URL bereits gespeichert. Vorhandenen Eintrag verwenden; Name unverändert.")
            "active-delete" -> message("Önce başka bir ana kaynak seçin. Aktif kayıt silinemez.", "Select another main source before deleting this record.", "Vor dem Löschen eine andere Hauptquelle wählen.")
            else -> message("İşlem tamamlanamadı. Önceki kaynak ve favoriler korundu.", "Operation failed. Previous source and favorites were preserved.", "Vorgang fehlgeschlagen. Quelle und Favoriten bleiben erhalten.")
        }
    }
    fun message(tr: String, en: String, de: String) = when (data.pref("language")) { "English" -> en; "Deutsch" -> de; else -> tr }
    fun cancelSource() {
        if (committingSource || sourceJob == null) return
        generation++; sourceJob?.cancel(); sourceJob = null; busy = false
        notice = message("İptal edildi. Önceki kaynak korundu.", "Canceled. Previous source preserved.", "Abgebrochen. Vorherige Quelle beibehalten.")
    }
    private fun sourceOperation(prepare: suspend (PreviewData) -> PreviewData) {
        if (busy || sessionOnly && !isPublic) return
        val before = data; val public = isPublic; val ticket = ++generation
        busy = true; notice = message("Yükleniyor… Önceki kaynak korunuyor.", "Loading… Previous source preserved.", "Laden… Vorherige Quelle bleibt erhalten.")
        sourceJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val next = withContext(Dispatchers.Default) { prepare(before) }
                ensureActive()
                if (ticket != generation || data.profileId != before.profileId) return@launch
                committingSource = true
                // Cancellation is accepted only before this point. Room write and publication finish together.
                withContext(NonCancellable) {
                    if (public) requireNotNull(publicStore).save(next) else store.save(next)
                    data = next; category = "Tümü"; leaveLiveLayers()
                    if (data.channels.none { it.id == selected && it.available }) paused = false
                    committingSource = false; sourceJob = null; busy = false
                    dismiss()
                    val skipped = next.library?.main?.skippedCount ?: 0
                    notice = message("${next.channels.count { it.available }} içerik yüklendi; $skipped giriş atlandı.", "${next.channels.count { it.available }} items loaded; $skipped skipped.", "${next.channels.count { it.available }} Einträge geladen; $skipped übersprungen.")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (ticket == generation) sourceError(error) }
            finally { if (ticket == generation) { committingSource = false; busy = false; sourceJob = null } }
        }
        sourceJob?.start()
    }
    fun loadSource(index: Int) {
        val record = savedSources.getOrNull(index) ?: return
        sourceOperation { before ->
            val loader = sourceLoader
            val updated = when (record.kind) {
                SourceKind.URL -> if (loader != null) sources.save(before, record.name, record.address, loader.fetch(record.address), record.id) else sources.activate(before, record.id)
                SourceKind.COUNTRY -> {
                    val preset = CountryListsCatalog.presets.firstOrNull { it.remoteUrl == record.address }
                    if (preset != null) sources.saveCountry(before, preset, preset.builtinM3u) else sources.activate(before, record.id)
                }
                else -> sources.activate(before, record.id)
            }
            updated
        }
    }
    fun deleteSource() {
        val id = editing?.let { savedSources.getOrNull(it)?.id } ?: return
        try { val next = sources.delete(data, id); commit(next) { dismiss() } } catch (error: Exception) { sourceError(error) }
    }
    fun restoreDefault() { sourceOperation { before -> sources.default(before, requireNotNull(sourceLoader).defaultText()) } }
    fun refreshSource() {
        sourceOperation { before ->
            val library = sources.bootstrap(before); val main = library.main
            val loader = requireNotNull(sourceLoader)
            var next = when (main.kind) {
                SourceKind.DEFAULT -> sources.default(before, loader.fetch(PublicM3u.url))
                SourceKind.URL -> sources.save(before, main.name, main.address, loader.fetch(main.address), main.id)
                SourceKind.COUNTRY -> {
                    val preset = CountryListsCatalog.presets.firstOrNull { it.remoteUrl == main.address }
                    if (preset != null) sources.saveCountry(before, preset, preset.builtinM3u) else sources.activate(before, main.id)
                }
                SourceKind.STALKER -> {
                    val portal = main.address.substringBefore("?mac=")
                    val mac = main.address.substringAfter("?mac=", "")
                    if (portal.isNotBlank() && mac.isNotBlank()) {
                        val (_, m3u) = StalkerPortalClient.connectAndBuildM3u(loader, portal, mac)
                        sources.saveStalker(before, main.name, portal, mac, m3u, main.id)
                    } else sources.activate(before, main.id)
                }
                SourceKind.XTREAM, SourceKind.FILE -> sources.activate(before, main.id)
            }
            library.extra?.let { extra -> next = sources.save(next, extra.name, extra.address, loader.fetch(extra.address), extra.id, extra = true) }
            next
        }
    }
    fun removeExtra() { sourceOperation { before -> sources.removeExtra(before) } }
    fun requestDocument(kind: String) { if (!busy && documentRequest == null) documentRequest = kind }
    fun documentLaunched() { documentRequest = null }
    fun documentCanceled() { notice = message("Dosya seçimi iptal edildi; veriler korundu.", "File selection canceled; data preserved.", "Dateiauswahl abgebrochen; Daten beibehalten.") }
    fun importM3u(name: String, text: String) { sourceOperation { before -> sources.save(before, name.take(120), "", text, file = true) } }
    fun importFavorites(text: String) {
        try { val archive = FavoriteBackup.decode(text); val next = FavoriteBackup.merge(data, archive)
            val added = next.favorites.size - data.favorites.size
            commit(next) { dismiss(); notice = message("$added yeni favori eklendi; mevcut sıra korundu.", "$added favorites added; existing order preserved.", "$added Favoriten hinzugefügt; Reihenfolge beibehalten.") }
        } catch (_: Exception) { notice = message("Geçersiz veya uyumsuz yedek. Favoriler korundu.", "Invalid or incompatible backup. Favorites preserved.", "Ungültige oder inkompatible Sicherung. Favoriten beibehalten.") }
    }
}
