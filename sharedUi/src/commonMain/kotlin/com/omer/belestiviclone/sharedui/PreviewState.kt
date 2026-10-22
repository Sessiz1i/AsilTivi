package com.omer.belestiviclone.sharedui

import androidx.compose.runtime.*
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.*
import com.omer.belestiviclone.shared.catalog.SourceId
import kotlin.random.Random

class PreviewState(private val store: PreviewStore, private val scope: CoroutineScope, private val publicStore: PreviewStore? = null,
    private val sourceLoader: SourceLoader? = null, allocate: () -> OpaqueId = { OpaqueId(Random.nextLong(), Random.nextLong()) }) {
    private val sources = SourceOperations(allocate)
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
    var formName by mutableStateOf("")
    var formAddress by mutableStateOf("")
    var formUser by mutableStateOf("")
    var formPassword by mutableStateOf("")
    var editing by mutableStateOf<Int?>(null)
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
        val candidates = visibleData.filtered(category, query)
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
    fun select(id: OpaqueId) { if (selected != id) technicalInfo = StreamTechnicalInfo(); selected = id; paused = false; if (playbackUri(channel) == null) notice = if (channel?.available == false)
        message("Kanal etkin kaynaklarda erişilemiyor. Favori ve sırası korundu.", "Channel unavailable in active sources. Favorite and order preserved.", "Kanal in aktiven Quellen nicht verfügbar. Favorit und Reihenfolge beibehalten.")
        else if (isPublic) "Bu kaydın oynatma tanımı desteklenmiyor." else "Yayın bağlantısı bulunamadı." }
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
    fun startMulti(count: Int) {
        leaveLiveLayers()
        if (multi == null) multi = MultiSession(selected)
        multi!!.layout(count); multiRevision++; page = "multi"; dismiss()
    }
    fun dismiss() {
        if (committingSource) return
        cancelSource(); documentRequest = null
        dialog = null; formPassword = ""; formUser = ""; formAddress = ""; formName = ""; editing = null
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
        val source = if (type == "supplement") data.library?.extra else index?.let { savedSources.getOrNull(it) }
        editing = index; formName = source?.name.orEmpty(); formAddress = source?.address.orEmpty()
        formUser = ""; formPassword = ""; dialog = type
    }
    fun saveSource() {
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
    private fun sourceError(error: Exception) {
        notice = when ((error as? SourceProblem)?.code) {
            "name" -> message("Ad gerekli; en fazla 120 karakter kullanın.", "Name is required (up to 120 characters).", "Name erforderlich (maximal 120 Zeichen).")
            "url" -> message("Geçerli bir HTTP/HTTPS adresi girin.", "Enter a valid HTTP/HTTPS address.", "Gültige HTTP/HTTPS-Adresse eingeben.")
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
            val loader = requireNotNull(sourceLoader)
            val updated = if (record.kind == SourceKind.URL) sources.save(before, record.name, record.address, loader.fetch(record.address), record.id) else sources.activate(before, record.id)
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
            var next = when (main.kind) {
                SourceKind.DEFAULT -> sources.default(before, requireNotNull(sourceLoader).fetch(PublicM3u.url))
                SourceKind.URL -> sources.save(before, main.name, main.address, requireNotNull(sourceLoader).fetch(main.address), main.id)
                SourceKind.FILE -> sources.activate(before, main.id)
            }
            library.extra?.let { extra -> next = sources.save(next, extra.name, extra.address, requireNotNull(sourceLoader).fetch(extra.address), extra.id, extra = true) }
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
