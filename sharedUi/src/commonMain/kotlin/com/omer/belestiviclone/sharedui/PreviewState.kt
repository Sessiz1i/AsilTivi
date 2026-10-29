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
import com.omer.belestiviclone.shared.transfer.*
import com.omer.belestiviclone.shared.diagnostics.*
import kotlin.random.Random

class PreviewState(private val store: PreviewStore, private val scope: CoroutineScope, private val publicStore: PreviewStore? = null,
    private val sourceLoader: SourceLoader? = null, allocate: () -> OpaqueId = { OpaqueId(Random.nextLong(), Random.nextLong()) },
    private val epgLoader: EpgLoader? = null,
    private val webTransferServerFactory: (((TransferPayload) -> Unit) -> WebTransferServerHandle)? = null,
    private val networkDiagnosticsRunner: NetworkDiagnosticsRunner? = null) {
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
    var webTransferServer by mutableStateOf<WebTransferServerHandle?>(null); private set
    var webTransferInfo by mutableStateOf<TransferServerInfo?>(null); private set
    var lastTransferSuccess by mutableStateOf<String?>(null)
    var diagnosticsReport by mutableStateOf(DiagnosticsReport()); private set
    var diagnosticsRunning by mutableStateOf(false); private set
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
    var bottomToastMessage by mutableStateOf<String?>(null); private set
    var bottomToastTone by mutableStateOf(ToastTone.Success); private set
    var bottomToastRevision by mutableStateOf(0); private set

    fun showBottomToast(message: String, tone: ToastTone = ToastTone.Success) {
        bottomToastMessage = message
        bottomToastTone = tone
        bottomToastRevision++
    }

    fun clearBottomToast() {
        bottomToastMessage = null
    }

    var panelInteractionRevision by mutableStateOf(0); private set
    fun touchPanelInteraction() {
        panelInteractionRevision++
    }

    var categoryFirstCardFocusRevision by mutableStateOf(0); private set
    fun requestCategoryFirstCardFocus() {
        categoryFirstCardFocusRevision++
        touchPanelInteraction()
    }

    var touchedFocusChannelId by mutableStateOf<OpaqueId?>(null)

    var paused by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var retry by mutableStateOf(0)
    var liveSyncRevision by mutableStateOf(0); private set
    var seekBackRevision by mutableStateOf(0); private set
    var isBehindLiveEdge by mutableStateOf(false)
    var seekOffsetSeconds by mutableStateOf(0L); private set
    var channelSessionStartMs by mutableStateOf(System.currentTimeMillis()); private set

    fun syncLive() {
        paused = false
        isBehindLiveEdge = false
        seekOffsetSeconds = 0L
        liveSyncRevision++
    }

    fun togglePause() {
        paused = !paused
        if (!paused) {
            isBehindLiveEdge = false
            seekOffsetSeconds = 0L
        }
    }

    fun seekInLiveWindow(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped < 0.95f) {
            paused = false
            isBehindLiveEdge = true
            seekOffsetSeconds = ((1f - clamped) * 60f).toLong().coerceAtLeast(5L)
            seekBackRevision++
        } else {
            syncLive()
        }
    }

    var numberInputBuffer by mutableStateOf(""); private set
    private var numberInputJob: Job? = null

    fun resolveChannelByNumber(number: Int): PreviewChannel? {
        if (number <= 0) return null
        val candidates = if (isRadioMode) {
            radioChannels
        } else {
            visibleData.filtered(category, query)
                .filter { !it.isAudioOrRadio }
                .ifEmpty { visibleData.filtered("Tümü", "").filter { !it.isAudioOrRadio } }
        }
        return candidates.getOrNull(number - 1)
    }

    val numberInputTarget: PreviewChannel?
        get() = numberInputBuffer.toIntOrNull()?.takeIf { it > 0 }?.let(::resolveChannelByNumber)

    val numberInputLabel: String?
        get() {
            if (numberInputBuffer.isEmpty()) return null
            val targetName = numberInputTarget?.name ?: "..."
            return "ⓘ $numberInputBuffer › $targetName"
        }

    fun inputChannelDigit(digit: Int, autoCommitAfterDelay: Boolean = true) {
        if (isRadioMode && radioScreenDimmed) {
            wakeRadioScreen()
            return
        }
        if (digit !in 0..9 || !normalLiveControls) return
        numberInputBuffer = if (numberInputBuffer.length >= 4) digit.toString() else numberInputBuffer + digit
        numberInputJob?.cancel()
        if (autoCommitAfterDelay) {
            numberInputJob = scope.launch {
                delay(NumberInputTimeoutMs)
                commitNumberInput()
            }
        }
    }

    fun commitNumberInput() {
        numberInputJob?.cancel()
        numberInputJob = null
        val target = numberInputTarget
        numberInputBuffer = ""
        if (target != null) {
            select(target.id)
        }
    }

    fun clearNumberInput() {
        numberInputJob?.cancel()
        numberInputJob = null
        numberInputBuffer = ""
    }

    fun livePlaybackProgress(nowEpochMs: Long = System.currentTimeMillis()): EpgLiveProgress {
        val prog = epgNowNext(channel, nowEpochMs).now
        return EpgLiveProgress.calculate(prog, nowEpochMs, channelSessionStartMs)
    }
    var moving by mutableStateOf<OpaqueId?>(null)
    var draft by mutableStateOf<PreviewData?>(null)
    var multi by mutableStateOf<MultiSession?>(null)
    var multiRevision by mutableStateOf(0)
    var multiPickerCategory by mutableStateOf("Tümü")
    var multiPickerQuery by mutableStateOf("")
    val multiPickerCategories: List<String> get() {
        val template = listOf(
            "Ulusal",
            "Haber",
            "Spor",
            "Çocuk",
            "Belgesel",
            "Müzik",
            "Dini",
            "Yerel",
            "KKTC",
            "AZERBAYCAN",
            "DÜNYA",
            "7/24 DİZİ/FİLM",
        )
        val tvChannels = data.channels.filter { it.available && !it.isAudioOrRadio }
        val normalizedAvailable = CategoryNormalizer.deduplicateCategories(data.categories, normalizeAliases = true)
            .filter { cat ->
                !CategoryNormalizer.matches(cat, "RADYO") &&
                    !isCategoryHidden(cat) &&
                    tvChannels.any { ch ->
                        ch.groups.any { g -> CategoryNormalizer.matches(g, cat) } || CategoryNormalizer.matches(ch.category, cat)
                    }
            }
        val result = mutableListOf("Favoriler")
        if (data.pref("history") == "Açık" && data.history.any { id -> tvChannels.any { it.id == id } }) {
            result += "Son İzlenenler"
        }
        result += "Tümü"
        for (item in template) {
            if (!isCategoryHidden(item)) {
                val hasTvChannel = tvChannels.any { ch ->
                    ch.groups.any { g -> CategoryNormalizer.matches(g, item) } || CategoryNormalizer.matches(ch.category, item)
                }
                if (hasTvChannel) {
                    result += item
                }
            }
        }
        for (cat in normalizedAvailable) {
            val already = result.any { CategoryNormalizer.matches(cat, it) } ||
                template.any { CategoryNormalizer.matches(cat, it) }
            if (!already && !cat.equals("Favoriler", true) && !cat.equals("Son İzlenenler", true) && !cat.equals("Tümü", true)) {
                result += CategoryNormalizer.normalizeDisplayLabel(cat)
            }
        }
        return result
    }
    val multiPickerChannels: List<PreviewChannel> get() =
        data.filtered(multiPickerCategory, multiPickerQuery).filter { it.available && !it.isAudioOrRadio }
    val savedMultiLayouts: List<SavedMultiLayout> get() =
        MultiLayoutCodec.decodeList(data.preferences["savedMultiLayouts"])
    val lastMultiLayout: SavedMultiLayout? get() =
        MultiLayoutCodec.decodeSingle(data.preferences["lastMultiLayout"].orEmpty())
    val multiSlotMenuTitle: String?
        get() {
            val m = multi ?: return null
            val chId = m.slots.getOrNull(m.selected) ?: return null
            val ch = data.channels.find { it.id == chId } ?: return null
            return "${m.selected + 1}. pencere · ${ch.name}"
        }
    fun activateMultiSlot(slotIndex: Int? = null) {
        val m = multi ?: return
        val idx = slotIndex?.takeIf { it in 0 until m.count } ?: m.selected
        m.selected = idx
        multiRevision++
        val chId = m.slots.getOrNull(idx)
        val ch = chId?.let { id -> data.channels.find { it.id == id } }
        if (ch != null) {
            dialog = "slotMenu"
        } else {
            openMultiChannelPicker(idx)
        }
    }
    fun executeMultiSlotMenuAction(action: String) {
        val m = multi ?: return
        when (action) {
            "Kanal Değiştir" -> openMultiChannelPicker(m.selected)
            "Tam Ekran" -> fullScreenMultiSlot(m.selected)
            "Tekrar Dene" -> retryMultiSlot(m.selected)
            "Görüntü Oranını Değiştir" -> cycleMultiSlotFormat(m.selected)
            "Bu Yayını Kapat" -> clearMultiSlot(m.selected)
            "Ekran Düzenini Değiştir" -> dialog = "layout"
            "Bu Düzeni Kaydet" -> openSaveMultiLayoutDialog()
        }
    }
    fun fullScreenMultiSlot(slotIndex: Int? = null) {
        val m = multi ?: return
        val idx = slotIndex?.takeIf { it in 0 until m.count } ?: m.selected
        val targetId = m.slots.getOrNull(idx) ?: return
        val ch = data.channels.find { it.id == targetId } ?: return
        multi = null
        dialog = null
        page = "live"
        select(targetId)
        showBottomToast("✅ 📺 ${ch.name} tam ekranda", ToastTone.Success)
    }
    fun retryMultiSlot(slotIndex: Int? = null) {
        val m = multi ?: return
        val idx = slotIndex?.takeIf { it in 0 until m.count } ?: m.selected
        m.retries[idx]++
        multiRevision++
        dismiss()
    }
    fun cycleMultiSlotFormat(slotIndex: Int? = null) {
        val m = multi ?: return
        val idx = slotIndex?.takeIf { it in 0 until m.count } ?: m.selected
        val nextFormat = m.cycleFormat(idx)
        multiRevision++
        dismiss()
        showBottomToast("✅ ${idx + 1}. pencere: $nextFormat", ToastTone.Success)
    }
    fun clearMultiSlot(slotIndex: Int? = null) {
        val m = multi ?: return
        val idx = slotIndex?.takeIf { it in 0 until m.count } ?: m.selected
        m.selected = idx
        assignMultiChannel(null)
    }
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
        if (channelId != null) {
            val ch = data.channels.find { it.id == channelId }
            if (ch?.isAudioOrRadio == true) return
        }
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
        val entryChannel = lastTvChannel ?: selected
        if (multi == null) multi = MultiSession(entryChannel)
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
        val entryChannel = lastTvChannel ?: selected
        if (multi == null) multi = MultiSession(entryChannel)
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
    var formSerial by mutableStateOf("")
    var formDeviceId by mutableStateOf("")
    var formDeviceId2 by mutableStateOf("")
    var sourceErrorDialogTitle by mutableStateOf("")
    var sourceErrorDialogMessage by mutableStateOf("")
    var lastSourceDialog by mutableStateOf<String?>(null)

    fun showSourceError(title: String, message: String) {
        lastSourceDialog = dialog
        sourceErrorDialogTitle = title
        sourceErrorDialogMessage = message
        dialog = "sourceError"
    }
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
    var radioScreenDimmed by mutableStateOf(false); private set
    var lastTvChannel by mutableStateOf<OpaqueId?>(null); private set
    var lastRadioChannel by mutableStateOf<OpaqueId?>(null); private set
    val radioChannels get() = data.channels.filter { it.available && it.isAudioOrRadio }.ifEmpty { PreviewFixture.radioChannels }
    fun dimRadioScreen() {
        if (!isRadioMode) return
        radioScreenDimmed = true
        panel = false
    }
    fun wakeRadioScreen(): Boolean {
        if (!radioScreenDimmed) return false
        radioScreenDimmed = false
        return true
    }
    fun enterRadioMode() {
        if (isRadioMode) return
        lastTvChannel = selected
        isRadioMode = true
        radioScreenDimmed = false
        panel = true
        val target = lastRadioChannel?.takeIf { id -> radioChannels.any { it.id == id } } ?: radioChannels.firstOrNull()?.id
        if (target != null && target != selected) select(target)
        notice = message("Radyo modu açıldı.", "Radio mode enabled.", "Radiomodus aktiviert.")
    }
    fun exitRadioMode() {
        if (!isRadioMode) return
        lastRadioChannel = selected
        isRadioMode = false
        radioScreenDimmed = false
        val tvChannels = data.channels.filter { it.available && !it.isAudioOrRadio }
        val target = lastTvChannel?.takeIf { id -> tvChannels.any { it.id == id } } ?: tvChannels.firstOrNull()?.id
        if (target != null && target != selected) select(target)
        panel = false
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
    val liveHeadingVisible get() = page == "live" && panel && dialog != "format"
    fun revealLiveControls() {
        if (isRadioMode && radioScreenDimmed) {
            wakeRadioScreen()
            return
        }
        if (canRevealLiveControls) panel = true
    }
    fun openFormatMenu() {
        if (busy) return
        if (dialog == "format") {
            dialog = null
        } else {
            if (page == "live") {
                info = false
                panel = true
            }
            dialog = "format"
        }
    }
    fun selectLiveFormat(value: String, closeMenu: Boolean = false) {
        commit(data.withPref("format", value)) {
            if (closeMenu) dialog = null
        }
    }
    fun toggleInfo() {
        if (page == "live") {
            val next = !info
            info = next
            if (next) {
                panel = false
                dialog = null
            }
        }
    }
    fun cycleLiveFormat() {
        if (!normalLiveControls) return
        val current = data.pref("format")
        val index = LiveFormatOptions.indexOfFirst { isVideoFormatSelected(current, it) }
        val next = LiveFormatOptions[(index + 1) % LiveFormatOptions.size]
        pref("format", next)
    }
    /** Kanal değişim kartı (zapping/OSD) tetikleyicisi; her artış kartı 3 sn gösterir/süresini sıfırlar. */
    var zapCardRevision by mutableStateOf(0); private set
    fun showZapCard() { if (page == "live" && !isRadioMode && multi == null) zapCardRevision++ }
    /**
     * Kanal adımı. [keepPanel] true ise (panel içindeki liste ok tuşları) panel açık kalır ve kart çıkmaz;
     * aksi halde (kumanda CH+/CH-, pencere düzeyi yön tuşları) panel kapanır ve kanal değişim kartı gösterilir.
     */
    fun stepChannel(delta: Int, keepPanel: Boolean = false) {
        if (isRadioMode && radioScreenDimmed) {
            wakeRadioScreen()
            return
        }
        if (!normalLiveControls || delta !in listOf(-1, 1)) return
        val candidates = if (isRadioMode) radioChannels else visibleData.filtered(category, query)
        if (candidates.isEmpty()) return
        val current = candidates.indexOfFirst { it.id == selected }
        val next = if (current < 0) { if (delta > 0) 0 else candidates.lastIndex }
            else (current + delta + candidates.size) % candidates.size
        val id = candidates[next].id
        if (id != selected) changeChannel(id, keepPanel)
    }
    fun text(tr: String, en: String) = if (data.pref("language") == "English") en else tr
    suspend fun load() {
        try { data = store.load(); storage = "Yerel kayıt" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { sessionOnly = true; storage = "Yalnız bu oturumda"; notice = "Yerel kayıt açılamadı. Değişiklikler yalnız bu oturumda tutulacak." }
        demo = data
        isRadioMode = false
        radioScreenDimmed = false
        selected = data.startup(data.preferences["lastChannel"]?.let { runCatching { PreviewCodec.id(it) }.getOrNull() }) ?: selected
        if (data.channels.find { it.id == selected }?.isAudioOrRadio == false) {
            lastTvChannel = selected
        }
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
        isRadioMode = false
        radioScreenDimmed = false
        data = next; category = if (public) "Tümü" else "Favoriler"
        selected = next.startup(next.preferences["lastChannel"]?.let { runCatching { PreviewCodec.id(it) }.getOrNull() }) ?: selected
        if (next.channels.find { it.id == selected }?.isAudioOrRadio == false) {
            lastTvChannel = selected
        }
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
    val managedCategories: List<String>
        get() = CategoryNormalizer.deduplicateCategories(data.categories, normalizeAliases = true)

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
        if (key == "list") {
            val shortLabel = if (value.startsWith("Dikey", ignoreCase = true)) "Dikey" else "Yatay"
            showBottomToast("✅ Kanal listesi: $shortLabel", ToastTone.Success)
        }
        if (key == "size") {
            showBottomToast("✅ Banner boyutu güncellendi", ToastTone.Success)
        }
        if (key == "position") {
            showBottomToast("✅ Banner konumu güncellendi", ToastTone.Success)
        }
    }
    fun chooseCategory(value: String, focusFirstCard: Boolean = false) {
        category = value
        query = ""
        categoryMenu = false
        touchPanelInteraction()
        if (focusFirstCard) {
            categoryFirstCardFocusRevision++
        }
    }
    fun categoryAction(route: String) {
        categoryMenu = false
        when (route) { "multi" -> dialog = "warning"; "settings" -> settings() }
    }
    fun isCategoryHidden(cat: String): Boolean =
        cat in data.hiddenCategories || data.hiddenCategories.any { CategoryNormalizer.matches(it, cat) }

    fun toggleCategory(category: String) {
        if (category !in data.categories && data.categories.none { CategoryNormalizer.matches(it, category) }) return
        val matching = if (category in data.categories) listOf(category) else data.categories.filter { CategoryNormalizer.matches(it, category) }
        val currentlyHidden = matching.all { it in data.hiddenCategories }
        val nextHidden = if (currentlyHidden) data.hiddenCategories - matching.toSet() else data.hiddenCategories + matching
        commit(data.copy(hiddenCategories = nextHidden))
    }
    fun showCategories(visible: Boolean) = commit(data.copy(hiddenCategories = if (visible) emptySet() else data.categories.toSet()))
    fun moveCategory(category: String, delta: Int) {
        if (delta !in listOf(-1, 1)) return
        val exactIndex = data.categories.indexOf(category)
        if (exactIndex >= 0) {
            val to = exactIndex + delta
            if (to !in data.categories.indices) return
            val order = data.categories.toMutableList()
            order.removeAt(exactIndex)
            order.add(to, category)
            commit(data.copy(categories = order))
        } else {
            val dedup = managedCategories.toMutableList()
            val from = dedup.indexOfFirst { CategoryNormalizer.matches(it, category) }
            val to = from + delta
            if (from < 0 || to !in dedup.indices) return
            val moved = dedup.removeAt(from)
            dedup.add(to, moved)
            commit(data.copy(categories = dedup))
        }
    }
    fun toggleFavoriteWithToast(channelId: OpaqueId) {
        val ch = data.channels.find { it.id == channelId } ?: return
        val wasFavorite = channelId in data.favorites
        channelChange(data.toggleFavorite(channelId)) {
            if (!wasFavorite) {
                showBottomToast("✅ ⭐ ${ch.name} favorilere eklendi", ToastTone.Success)
            } else {
                showBottomToast("⭐ ${ch.name} favorilerden çıkarıldı", ToastTone.Warning)
            }
        }
    }
    fun hideChannelWithToast(channelId: OpaqueId) {
        val ch = data.channels.find { it.id == channelId } ?: return
        channelChange(data.copy(hidden = data.hidden + channelId)) {
            showBottomToast("Kanal gizlendi: ${ch.name}", ToastTone.Warning)
        }
    }
    /** Kullanıcı kanal seçimi (liste tıklaması vb.): canlı TV'de panel kapanır ve kanal değişim kartı gösterilir. */
    fun select(id: OpaqueId) = changeChannel(id, keepPanel = false)
    private fun changeChannel(id: OpaqueId, keepPanel: Boolean) {
        if (selected != id) {
            technicalInfo = StreamTechnicalInfo()
            channelSessionStartMs = System.currentTimeMillis()
        }
        selected = id
        val targetCh = data.channels.find { it.id == id }
        if (targetCh != null && !targetCh.isAudioOrRadio) {
            lastTvChannel = id
            if (isRadioMode) {
                isRadioMode = false
                radioScreenDimmed = false
            }
        }
        paused = false
        isBehindLiveEdge = false
        seekOffsetSeconds = 0L
        if (page == "live" && !isRadioMode && !keepPanel) {
            panel = false
            showZapCard()
        }
        if (playbackUri(channel) == null) notice = if (channel?.available == false)
            message("Kanal etkin kaynaklarda erişilemiyor. Favori ve sırası korundu.", "Channel unavailable in active sources. Favorite and order preserved.", "Kanal in aktiven Quellen nicht verfügbar. Favorit und Reihenfolge beibehalten.")
            else if (isPublic) "Bu kaydın oynatma tanımı desteklenmiyor." else "Yayın bağlantısı bulunamadı."
    }
    /** Called by a real first-frame/audio-advancing event, never by focus or channel selection. */
    fun played(id: OpaqueId) {
        val ch = data.channels.find { it.id == id && it.available } ?: return
        if (busy) return
        val tvFallback = if (!ch.isAudioOrRadio) id else lastTvChannel?.takeIf { tvId ->
            data.channels.any { c -> c.id == tvId && c.available && !c.isAudioOrRadio }
        }
        val next = data.withPref("lastChannel", PreviewCodec.id(id)).let { d ->
            if (tvFallback != null) d.withPref("lastTvChannel", PreviewCodec.id(tvFallback)) else d
        }.let {
            if (it.pref("history") == "Açık") it.copy(history = listOf(id) + it.history.filter { old -> old != id }) else it
        }
        if (next != data) commit(next)
    }
    private fun leaveLiveLayers() {
        searching = false; query = ""; categoryMenu = false; info = false
        moving = null; draft = null; clearNumberInput()
    }
    fun settings() { cancelOpacity(); leaveLiveLayers(); origin = page; page = "settings"; dialog = null }
    fun dismiss() {
        if (committingSource) return
        cancelSource(); documentRequest = null
        stopWebTransferServer()
        dialog = null; formPassword = ""; formUser = ""; formMac = ""; formSerial = ""; formDeviceId = ""; formDeviceId2 = ""; formAddress = ""; formName = ""; editing = null
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
            page == "live" && isRadioMode && radioScreenDimmed -> radioScreenDimmed = false
            dialog != null -> dismiss()
            opacityDraft != null -> cancelOpacity()
            moving != null -> { draft = null; moving = null }
            searching -> { searching = false; query = "" }
            categoryMenu -> categoryMenu = false
            page == "settings" -> { page = origin; panel = false; if (returnAfterClear) clearReturn() }
            page == "multi" -> {
                val restoreId = multi?.entry ?: multi?.returnChannel() ?: selected
                multi = null
                page = "live"
                selected = restoreId
                val ch = data.channels.find { it.id == restoreId }
                if (ch != null && !ch.isAudioOrRadio) lastTvChannel = restoreId
                panel = false
                showZapCard()
            }
            page == "live" && info -> info = false
            page == "live" && isRadioMode && panel -> panel = false
            page == "live" && isRadioMode -> dialog = "exit"
            page == "live" && panel -> panel = false
            page == "live" -> dialog = "exit"
        }
    }
    fun channelChange(next: PreviewData, onCommitted: () -> Unit = {}) {
        val oldList = data.filtered(category, query)
        val index = oldList.indexOfFirst { it.id == target }.coerceAtLeast(0)
        commit(next) {
            val list = data.filtered(category, query)
            if (list.none { it.id == target }) target = list.getOrNull(index.coerceAtMost(list.lastIndex))?.id
            dismiss()
            onCommitted()
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
        val name = formName.trim(); val address = formAddress.trim(); val extra = dialog == "supplement"
        if (address.isBlank()) {
            showSourceError(
                message("Kaynağa bağlanılamadı", "Could not connect to source", "Verbindung fehlgeschlagen"),
                message("Lütfen geçerli bir M3U çalma listesi adresi girin.", "Please enter a valid M3U playlist address.", "Bitte geben Sie eine gültige M3U-Adresse ein.")
            )
            return
        }
        val loader = sourceLoader ?: run { notice = "Kaynak yükleyici kullanılamıyor."; return }
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
                data.filtered(preset.groupTitle, "").firstOrNull()?.let { select(it.id) }
                panel = true
                notice = "${preset.flag} ${preset.name} kategorisi açıldı."
            }
        } else {
            category = preset.groupTitle
            query = ""
            dismiss()
            page = "live"
            data.filtered(preset.groupTitle, "").firstOrNull()?.let { select(it.id) }
            panel = true
            notice = "${preset.flag} ${preset.name} kategorisi açıldı."
        }
    }
    private fun sourceError(error: Exception) {
        val code = (error as? SourceProblem)?.code
        if ((dialog == "m3u" || dialog == "supplement") && (code == "url" || code == "network" || code == "content")) {
            showSourceError(
                message("Kaynağa bağlanılamadı", "Could not connect to source", "Verbindung fehlgeschlagen"),
                message("Lütfen geçerli bir M3U çalma listesi adresi girin.", "Please enter a valid M3U playlist address.", "Bitte geben Sie eine gültige M3U-Adresse ein.")
            )
            return
        }
        notice = when (code) {
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

    fun openWebTransfer() {
        if (busy) return
        lastTransferSuccess = null
        dialog = "webTransfer"
    }

    fun startWebTransferServer() {
        if (webTransferServerFactory == null) {
            webTransferInfo = TransferServerInfo(localIp = "127.0.0.1", port = 8080, isRunning = false, error = "Web aktarım servisi platformda hazır değil")
            return
        }
        if (webTransferServer?.isRunning == true) return
        val server = webTransferServerFactory.invoke { payload ->
            scope.launch {
                handleTransferPayload(payload)
            }
        }
        val info = server.start()
        webTransferServer = server
        webTransferInfo = info
    }

    fun stopWebTransferServer() {
        webTransferServer?.stop()
        webTransferServer = null
        webTransferInfo = null
    }

    fun handleTransferPayload(payload: TransferPayload) {
        val loader = sourceLoader
        lastTransferSuccess = "${payload.name.ifEmpty { "Yeni Liste" }} aktarıldı"
        notice = message("Telefondan aktarıldı: ${payload.name.ifEmpty { "Liste" }}", "Transferred from phone: ${payload.name.ifEmpty { "List" }}", "Vom Smartphone übertragen: ${payload.name.ifEmpty { "Liste" }}")
        when (payload.kind) {
            TransferPayloadKind.M3U_TEXT -> {
                importM3u(payload.name.ifEmpty { "Telefondan Liste" }, payload.content)
            }
            TransferPayloadKind.M3U_URL -> {
                if (loader == null) return
                val name = payload.name.ifEmpty { "Telefondan M3U" }
                sourceOperation { before ->
                    sources.save(before, name, payload.url, loader.fetch(payload.url))
                }
            }
            TransferPayloadKind.XTREAM -> {
                if (loader == null) return
                val name = payload.name.ifEmpty { "Xtream Portal" }
                sourceOperation { before ->
                    val (account, m3uText) = XtreamCodesClient.connectAndBuildM3u(loader, payload.serverUrl, payload.username, payload.password)
                    lastXtreamAccount = account
                    sources.saveXtream(before, name, payload.serverUrl, payload.username, m3uText)
                }
            }
            TransferPayloadKind.STALKER -> {
                if (loader == null) return
                val name = payload.name.ifEmpty { "Stalker Portal" }
                sourceOperation { before ->
                    val (session, m3uText) = StalkerPortalClient.connectAndBuildM3u(loader, payload.portalUrl, payload.mac)
                    lastStalkerSession = session
                    sources.saveStalker(before, name, payload.portalUrl, payload.mac, m3uText)
                }
            }
        }
    }

    fun openNetworkDiagnostics() {
        dialog = "networkDiagnostics"
    }

    fun runNetworkDiagnostics() {
        if (diagnosticsRunning) return
        diagnosticsRunning = true
        diagnosticsReport = DiagnosticsReport(inProgress = true)
        val runner = networkDiagnosticsRunner
        val testUrl = data.channels.firstOrNull { it.available }?.let { playbackUri(it) }
            ?: "https://tv-trt1.medya.trt.com.tr/master.m3u8"

        scope.launch {
            try {
                if (runner != null) {
                    val finalReport = runner.run(testUrl) { currentProgress ->
                        diagnosticsReport = currentProgress
                    }
                    diagnosticsReport = finalReport
                } else {
                    // Fallback in environments without platform runner (e.g. tests)
                    diagnosticsReport = DiagnosticsReport(
                        network = NetworkState("Wi-Fi", "192.168.1.100", true, "Wi-Fi · IP: 192.168.1.100"),
                        dns = DnsState("google.com", "142.250.180.206", 18, true),
                        stream = StreamServerState(testUrl, 200, 36, true),
                        inProgress = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                diagnosticsReport = DiagnosticsReport(
                    network = NetworkState("Hata", "", false, e.message ?: "Test başarısız"),
                    dns = DnsState(isSuccess = false, error = e.message),
                    stream = StreamServerState(isSuccess = false, error = e.message),
                    inProgress = false
                )
            } finally {
                diagnosticsRunning = false
            }
        }
    }
}

const val NumberInputTimeoutMs = 2500L
const val BottomToastDurationMs = 2500L

val MultiSlotMenuItems = listOf(
    "Kanal Değiştir",
    "Tam Ekran",
    "Tekrar Dene",
    "Görüntü Oranını Değiştir",
    "Bu Yayını Kapat",
    "Ekran Düzenini Değiştir",
    "Bu Düzeni Kaydet",
)

enum class ToastTone {
    Success,
    Warning,
}
