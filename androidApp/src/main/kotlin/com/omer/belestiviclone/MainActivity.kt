package com.omer.belestiviclone

import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.VideoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.common.Timeline
import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.transfer.*
import com.omer.belestiviclone.shared.diagnostics.*
import com.omer.belestiviclone.sharedui.PreviewApp
import com.omer.belestiviclone.sharedui.PreviewState

class MainActivity : ComponentActivity() {
    private var previewState: PreviewState? = null
    private val navigationPress = NavigationPress()
    private var focusTarget = "none"
    private var wakingRadioDimOnKeyUp = false
    private val navigationKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV_ZOOM_MODE) +
        (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toSet() +
        (KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9).toSet()

    private fun immersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive() else navigationPress.reset()
    }
    private fun observeKey(event: KeyEvent, delivery: String) {
        if (!BuildConfig.DEBUG || event.keyCode !in navigationKeys) return
        val s = previewState
        android.util.Log.i("BelesNav", "delivery=$delivery code=${event.keyCode} action=${event.action} repeat=${event.repeatCount} source=${event.source} route=${s?.page} dialog=${s?.dialog} panel=${s?.panel} windowFocus=${window.decorView.hasWindowFocus()} target=$focusTarget")
    }
    private fun handleNavigationKey(event: KeyEvent): Boolean {
        observeKey(event, "window")
        val s = previewState
        if (s != null && s.loaded && s.page == "live" && s.isRadioMode && (s.radioScreenDimmed || wakingRadioDimOnKeyUp)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (s.radioScreenDimmed) {
                    s.wakeRadioScreen()
                    wakingRadioDimOnKeyUp = true
                }
                return true
            }
            if (event.action == KeyEvent.ACTION_UP) {
                wakingRadioDimOnKeyUp = false
                return true
            }
        }
        if (s != null && s.loaded && s.page == "live" && event.action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            if (event.keyCode in listOf(KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) s.toggleInfo()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_TV_ZOOM_MODE) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) s.openFormatMenu()
                return true
            }
            val digit = tvDigitFromKeyCode(event.keyCode)
            if (digit != null) {
                val ime = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (s.normalLiveControls && !ime) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        s.inputChannelDigit(digit)
                    }
                    return true
                }
            }
        }
        val command = tvNavigationCommand(event.keyCode)
        if (s != null && command != null && event.action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val ime = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            val eligible = command.eligible(s.normalLiveControls, s.panel, ime)
            val result = navigationPress.event(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount > 0, eligible)
            if (result.perform) {
                if (s.page == "live" && s.playbackError != null && command.delta == 0) {
                    s.retryLive()
                } else if (command.delta == 0) s.revealLiveControls() else s.stepChannel(command.delta)
                if (BuildConfig.DEBUG) android.util.Log.i("BelesNav", "action=${if (command.delta == 0) "reveal" else "channel-step"} delta=${command.delta} selected=${PreviewCodec.id(s.selected)} panel=${s.panel}")
            }
            if (result.consumed) return true
        }
        return false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val s = previewState
        if (s != null && s.data.pref("autoPip") == "Açık" && !s.paused && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val s = previewState
        if (isInPictureInPictureMode && s != null) {
            s.panel = false
            s.info = false
        }
    }

    private fun enforceLandscapeOrientation() {
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onResume() {
        super.onResume()
        enforceLandscapeOrientation()
        immersive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The public Window callback preserves Android/Compose dispatch for every unowned event.
        val activityCallback = window.callback
        window.callback = object : android.view.Window.Callback by activityCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handleNavigationKey(event) || activityCallback.dispatchKeyEvent(event)
        }
        enableEdgeToEdge()
        immersive()
        enforceLandscapeOrientation()
        val databaseName = "belestivi_preview_v1.db"
        val store = AndroidPreviewStore(applicationContext, databaseName = databaseName)
        val catalog = AndroidPublicCatalog(applicationContext, "public-tiviliste-catalog-v2")
        val publicStore = AndroidPreviewStore(applicationContext, key = 2, databaseName = databaseName) {
            val channels = catalog.load()
            PreviewData(favorites = emptyList(), history = emptyList(), categories = CategoryNormalizer.deduplicateCategories(channels.flatMap { it.groups }.distinct()),
                preferences = store.load().preferences, sources = listOf("Özel M3U · TiviListesi"), channels = channels, profileId = PublicM3u.profile)
        }
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember {
                PreviewState(
                    store,
                    scope,
                    publicStore,
                    AndroidSourceLoader(applicationContext),
                    epgLoader = AndroidEpgLoader(applicationContext),
                    webTransferServerFactory = { onPayload -> AndroidWebTransferServer(onPayload) },
                    networkDiagnosticsRunner = { testUrl, onProgress ->
                        AndroidNetworkDiagnostics.runFullDiagnostics(applicationContext, testUrl, onProgress)
                    }
                )
            }
            var captionReservedBottomPx by remember { mutableIntStateOf(0) }
            DocumentBridge(state)
            SideEffect { previewState = state }
            LaunchedEffect(state.exitRequested) { if (state.exitRequested) finish() }
            BackHandler(enabled = true, onBack = state::back)
            PreviewApp(state, appVersionName = BuildConfig.VERSION_NAME,
                onFocusTarget = { focusTarget = it; if (BuildConfig.DEBUG) android.util.Log.i("BelesNav", "focus=$it route=${state.page} dialog=${state.dialog}") },
                onNavigationKey = { observeKey(it.nativeKeyEvent, "compose") },
                onLayoutBounds = { part, rect ->
                    if (part == "channel-info") captionReservedBottomPx = (window.decorView.height - rect.top.toInt() + 8).coerceAtLeast(0)
                },
                platformKeySignal = { KeyPressSignal(it.nativeKeyEvent.isLongPress, it.nativeKeyEvent.repeatCount > 0) }, image = { name, modifier ->
                PreviewImage(name, modifier)
            }, video = { channel, paused, muted, retry, format, preferences, onQuality, modifier ->
                SampleVideo(channel, paused, muted, retry, format, modifier, state::played, preferences, onQuality,
                    captionBottomInsetPx = if (state.page == "live" && state.info) captionReservedBottomPx else 0,
                    onTechnicalInfo = { if (state.page == "live") state.technicalInfo = it },
                    onPlaybackStatus = { status, err ->
                        if (state.page == "live") {
                            state.playbackStatus = status
                            state.playbackError = err
                        }
                    },
                    isMultiScreen = state.page == "multi",
                    liveSync = state.liveSyncRevision,
                    seekBack = state.seekBackRevision)
            })
        }
    }
}

/** Clean artwork is a display concern; catalog IDs and stored logo URLs remain unchanged. */
@Composable internal fun PreviewImage(name: String, modifier: Modifier) {
    val asset = approvedLogo(name)
    if (forceLogoFallback(name)) {
        Image(painterResource(R.drawable.ikon_tv), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else if (asset != null || name.startsWith("https://") || name.startsWith("http://")) {
        val bitmap = channelLogoBitmap(name, asset)
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
        else Image(painterResource(R.drawable.ikon_tv), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Image(painterResource(previewDrawables[name] ?: R.drawable.ikon_tv), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private val previewDrawables = mapOf(
    "bg_retro_radio" to R.drawable.bg_retro_radio,
    "logo_radio" to R.drawable.logo_radio,
    "beles_tivi_plus" to R.drawable.beles_tivi_plus,
    "ev_cikis" to R.drawable.ev_cikis,
    "ev_dizi" to R.drawable.ev_dizi,
    "ev_dizi_kucuk" to R.drawable.ev_dizi_kucuk,
    "ev_film" to R.drawable.ev_film,
    "ev_tv" to R.drawable.ev_tv,
    "ikon_app" to R.drawable.ikon_app,
    "ikon_audio" to R.drawable.ikon_audio,
    "ikon_ayar" to R.drawable.ikon_ayar,
    "ikon_cache" to R.drawable.ikon_cache,
    "ikon_canli" to R.drawable.ikon_canli,
    "ikon_category" to R.drawable.ikon_category,
    "ikon_cikis" to R.drawable.ikon_cikis,
    "ikon_coklu" to R.drawable.ikon_coklu,
    "ikon_contact" to R.drawable.ikon_contact,
    "ikon_cop" to R.drawable.ikon_cop,
    "ikon_donus" to R.drawable.ikon_donus,
    "ikon_dort" to R.drawable.ikon_dort,
    "ikon_dosya" to R.drawable.ikon_dosya,
    "ikon_duraklat" to R.drawable.ikon_duraklat,
    "ikon_export" to R.drawable.ikon_export,
    "ikon_format" to R.drawable.ikon_format,
    "ikon_gorunum" to R.drawable.ikon_gorunum,
    "ikon_guncelle" to R.drawable.ikon_guncelle,
    "ikon_hakkinda" to R.drawable.ikon_hakkinda,
    "ikon_hide" to R.drawable.ikon_hide,
    "ikon_icerik" to R.drawable.ikon_icerik,
    "ikon_iki" to R.drawable.ikon_iki,
    "ikon_import" to R.drawable.ikon_import,
    "ikon_kaydedildi" to R.drawable.ikon_kaydedildi,
    "ikon_kaydet" to R.drawable.ikon_kaydet,
    "ikon_kaynak" to R.drawable.ikon_kaynak,
    "ikon_kullanici" to R.drawable.ikon_kullanici,
    "ikon_language" to R.drawable.ikon_language,
    "ikon_list" to R.drawable.ikon_list,
    "ikon_liste_adi" to R.drawable.ikon_liste_adi,
    "ikon_menu_duzenle" to R.drawable.ikon_menu_duzenle,
    "ikon_menu_favori" to R.drawable.ikon_menu_favori,
    "ikon_menu_favoriden_cikar" to R.drawable.ikon_menu_favoriden_cikar,
    "ikon_menu_gerigetir" to R.drawable.ikon_menu_gerigetir,
    "ikon_menu_gizle" to R.drawable.ikon_menu_gizle,
    "ikon_menu_vazgec" to R.drawable.ikon_menu_vazgec,
    "ikon_menu_yukle" to R.drawable.ikon_menu_yukle,
    "ikon_opacity" to R.drawable.ikon_opacity,
    "ikon_oynatma" to R.drawable.ikon_oynatma,
    "ikon_plus" to R.drawable.ikon_plus,
    "ikon_position" to R.drawable.ikon_position,
    "ikon_quality" to R.drawable.ikon_quality,
    "ikon_saat" to R.drawable.ikon_saat,
    "ikon_ses" to R.drawable.ikon_ses,
    "ikon_show" to R.drawable.ikon_show,
    "ikon_sifre" to R.drawable.ikon_sifre,
    "ikon_size" to R.drawable.ikon_size,
    "ikon_startup" to R.drawable.ikon_startup,
    "ikon_subtitle" to R.drawable.ikon_subtitle,
    "ikon_sunucu" to R.drawable.ikon_sunucu,
    "ikon_tasima_bilgi" to R.drawable.ikon_tasima_bilgi,
    "ikon_telegram" to R.drawable.ikon_telegram,
    "ikon_temizle" to R.drawable.ikon_temizle,
    "ikon_tv" to R.drawable.ikon_tv,
    "ikon_url" to R.drawable.ikon_url,
    "ikon_uyari" to R.drawable.ikon_uyari,
    "ikon_xtream" to R.drawable.ikon_xtream,
    "kategori_ara" to R.drawable.kategori_ara,
    "kategori_belgesel" to R.drawable.kategori_belgesel,
    "kategori_cocuk" to R.drawable.kategori_cocuk,
    "kategori_dini" to R.drawable.kategori_dini,
    "kategori_dunya" to R.drawable.kategori_dunya,
    "kategori_favoriler" to R.drawable.kategori_favoriler,
    "kategori_gecmis" to R.drawable.kategori_gecmis,
    "kategori_haber" to R.drawable.kategori_haber,
    "kategori_muzik" to R.drawable.kategori_muzik,
    "kategori_spor" to R.drawable.kategori_spor,
    "kategori_tumu" to R.drawable.kategori_tumu,
    "kategori_ulusal" to R.drawable.kategori_ulusal,
    "logo_360" to R.drawable.logo_360,
    "logo_a2" to R.drawable.logo_a2,
    "logo_a77_arb" to R.drawable.logo_a77_arb,
    "logo_a77_arb24" to R.drawable.logo_a77_arb24,
    "logo_a77_arbgunes" to R.drawable.logo_a77_arbgunes,
    "logo_a77_bakutv" to R.drawable.logo_a77_bakutv,
    "logo_a77_cbc" to R.drawable.logo_a77_cbc,
    "logo_a77_dunya" to R.drawable.logo_a77_dunya,
    "logo_a77_ictimai" to R.drawable.logo_a77_ictimai,
    "logo_a77_medeniyyet" to R.drawable.logo_a77_medeniyyet,
    "logo_a77_real" to R.drawable.logo_a77_real,
    "logo_a77_space" to R.drawable.logo_a77_space,
    "logo_a77_xazar" to R.drawable.logo_a77_xazar,
    "logo_ahaber" to R.drawable.logo_ahaber,
    "logo_ahaber_kamera" to R.drawable.logo_ahaber_kamera,
    "logo_atv" to R.drawable.logo_atv,
    "logo_beyaz" to R.drawable.logo_beyaz,
    "logo_diyanet" to R.drawable.logo_diyanet,
    "logo_fashion" to R.drawable.logo_fashion,
    "logo_kanal7" to R.drawable.logo_kanal7,
    "logo_now" to R.drawable.logo_now,
    "logo_ntv" to R.drawable.logo_ntv,
    "logo_semerkand" to R.drawable.logo_semerkand,
    "logo_show" to R.drawable.logo_show,
    "logo_star" to R.drawable.logo_star,
    "logo_tbmm" to R.drawable.logo_tbmm,
    "logo_tivi6" to R.drawable.logo_tivi6,
    "logo_trt1" to R.drawable.logo_trt1,
    "logo_trt4k" to R.drawable.logo_trt4k,
    "logo_trtbelgesel" to R.drawable.logo_trtbelgesel,
    "logo_trtgenc" to R.drawable.logo_trtgenc,
    "logo_tv8" to R.drawable.logo_tv8,
    "logo_ulke" to R.drawable.logo_ulke,
    "logo_vav" to R.drawable.logo_vav,
    "tv_banner" to R.drawable.tv_banner,
    "yildiz" to R.drawable.yildiz,
)

/** Android objects never cross into common code. Public channels retain their own exact URI. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AndroidPreviewPlayer(private val context: android.content.Context, private val isMultiScreen: Boolean = false, private val onPlayed: (com.omer.belestiviclone.shared.catalog.OpaqueId) -> Unit = {}) : PreviewPlayer {
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ if (isMultiScreen) 25_000 else 50_000,
            /* maxBufferMs = */ if (isMultiScreen) 45_000 else 90_000,
            /* bufferForPlaybackMs = */ if (isMultiScreen) 4_000 else 6_500,
            /* bufferForPlaybackAfterRebufferMs = */ if (isMultiScreen) 8_000 else 12_000,
        )
        .setBackBuffer(
            /* backBufferDurationMs = */ if (isMultiScreen) 15_000 else 45_000,
            /* retainBackBufferFromKeyframe = */ true,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    val exo = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .build()
    override var status by mutableStateOf(PlaybackStatus.IDLE); private set
    override var error by mutableStateOf<String?>(null); private set
    private var channel: PreviewChannel? = null
    private var attempt: PlaybackAttempt? = null
    private var released = false
    private val sourceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val probeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var sourceJob: Job? = null
    private var recoveryJob: Job? = null
    private var hasStartedPlayback = false
    private var hasAppliedInitialLiveCushion = false
    private var autoRecoveryCount = 0
    private var temporaryResolutionCapped = false
    private var requestedPause = false
    private var playbackPreferences by mutableStateOf(PlaybackPreferences())
    private var qualityObservation by mutableStateOf(VideoQualityObservation())
    var technicalInfo by mutableStateOf(StreamTechnicalInfo()); private set
    val qualityDescription get() = qualityObservation.description(playbackPreferences) + "\nSes: " +
        languageStatus(playbackPreferences.audio,TrackLanguage.audio(playbackPreferences.audio),audioTracks) + "\nAltyazı: " +
        languageStatus(playbackPreferences.subtitle,TrackLanguage.subtitle(playbackPreferences.subtitle),textTracks,text=true)
    private fun updateTechnicalVideoFormat(format: androidx.media3.common.Format) {
        val w = if (format.width > 0) format.width else technicalInfo.width
        val h = if (format.height > 0) format.height else technicalInfo.height
        val fps = if (format.frameRate > 0f) format.frameRate else technicalInfo.frameRate
        val br = when {
            format.bitrate > 0 -> format.bitrate
            format.peakBitrate > 0 -> format.peakBitrate
            format.averageBitrate > 0 -> format.averageBitrate
            else -> technicalInfo.videoBitrate
        }
        technicalInfo = technicalInfo.copy(
            width = w,
            height = h,
            frameRate = fps,
            videoCodec = format.codecs ?: technicalInfo.videoCodec,
            videoMimeType = format.sampleMimeType ?: technicalInfo.videoMimeType,
            videoBitrate = br,
        )
    }
    private fun updateTechnicalAudioFormat(format: androidx.media3.common.Format) {
        val ch = if (format.channelCount > 0) format.channelCount else technicalInfo.audioChannels
        val sr = if (format.sampleRate > 0) format.sampleRate else technicalInfo.audioSampleRate
        val br = when {
            format.bitrate > 0 -> format.bitrate
            format.averageBitrate > 0 -> format.averageBitrate
            else -> technicalInfo.audioBitrate
        }
        val lang = format.language?.takeIf { it.isNotBlank() } ?: format.label?.takeIf { it.isNotBlank() }
        technicalInfo = technicalInfo.copy(
            audioCodec = format.codecs ?: technicalInfo.audioCodec,
            audioMimeType = format.sampleMimeType ?: technicalInfo.audioMimeType,
            audioChannels = ch,
            audioSampleRate = sr,
            audioBitrate = br,
            audioLanguage = lang ?: technicalInfo.audioLanguage,
        )
    }

    fun pollLiveMetrics(): StreamTechnicalInfo {
        if (released) return technicalInfo
        val bufMs = exo.totalBufferedDuration.coerceAtLeast(
            (exo.bufferedPosition - exo.currentPosition).coerceAtLeast(0L)
        )
        val rawOffset = exo.currentLiveOffset
        val offsetMs = when {
            rawOffset != C.TIME_UNSET && rawOffset > 0L -> rawOffset
            exo.duration > 0L && exo.duration > exo.currentPosition -> (exo.duration - exo.currentPosition).coerceAtLeast(bufMs)
            bufMs > 0L -> bufMs + 4200L
            else -> 0L
        }
        val playing = exo.playWhenReady && exo.playbackState != Player.STATE_IDLE && exo.playbackState != Player.STATE_ENDED
        if (bufMs != technicalInfo.bufferedDurationMs || offsetMs != technicalInfo.liveOffsetMs || playing != technicalInfo.isPlaying) {
            technicalInfo = technicalInfo.copy(
                bufferedDurationMs = bufMs,
                liveOffsetMs = offsetMs,
                isPlaying = playing,
            )
        }
        return technicalInfo
    }
    private var audioTracks by mutableStateOf(emptyList<LanguageTrack>())
    private var textTracks by mutableStateOf(emptyList<LanguageTrack>())
    var captions by mutableStateOf(emptyList<androidx.media3.common.text.Cue>()); private set
    private val cueGate = CueOwnerGate()
    private var cueOwner: CueOwner? = null
    private var cueListener: AnalyticsListener? = null
    private fun resetCueObserver() {
        cueListener?.let(exo::removeAnalyticsListener)
        captions=emptyList()
        cueOwner=cueGate.advance(attempt?.id)
        val captured=cueOwner
        val installedMs=android.os.SystemClock.elapsedRealtime()
        cueListener = if (captured == null) null else object : AnalyticsListener {
            override fun onCues(eventTime: AnalyticsListener.EventTime, cueGroup: androidx.media3.common.text.CueGroup) {
                if (eventTime.realtimeMs < installedMs) return
                val active=observedAttempt(eventTime) ?: return
                val target=subtitleTrack(TrackLanguage.subtitle(playbackPreferences.subtitle),textTracks) ?: return
                if (textTracks.none { it.supported && it.selected && it.group == target.group && it.index == target.index }) return
                receiveCues(captured,active.id,cueGroup.cues.toList())
            }
        }.also(exo::addAnalyticsListener)
    }
    private fun receiveCues(owner: CueOwner, mediaId: String, cues: List<androidx.media3.common.text.Cue>) {
        if (released) return // No Player getters after release.
        val enabled=TrackLanguage.subtitle(playbackPreferences.subtitle).mode !in listOf(LanguageMode.OFF,LanguageMode.UNKNOWN) &&
            C.TRACK_TYPE_TEXT !in exo.trackSelectionParameters.disabledTrackTypes
        if (!cueGate.accepts(owner,mediaId,enabled) || attempt?.accepts(mediaId) != true) return
        captions=cues.toList()
    }
    private fun applyTextParameters() {
        val selector=exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector
        val next=LanguageTrackSelection.text(selector.parameters,playbackPreferences.subtitle,exo.currentTracks)
        if (next != selector.parameters) exo.trackSelectionParameters=next
    }
    private var preferencesApplied = false
    private var preferenceSequence = 0
    override fun preferences(value: PlaybackPreferences) {
        if (released) return
        check(android.os.Looper.myLooper() == exo.applicationLooper)
        if (preferencesApplied && value == playbackPreferences) return
        preferencesApplied = true
        val previous=playbackPreferences
        playbackPreferences = value; preferenceSequence++
        val selector = exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector
        if (previous.quality != value.quality || preferenceSequence == 1)
            exo.trackSelectionParameters = VideoQualitySelection.parameters(selector.parameters, value)
        if (previous.audio != value.audio || preferenceSequence == 1)
            exo.trackSelectionParameters = LanguageTrackSelection.audio(selector.parameters,value.audio)
        if (previous.subtitle != value.subtitle || preferenceSequence == 1) { resetCueObserver();applyTextParameters() }
    }

    private fun log(active: PlaybackAttempt, detail: String) {
        if (BuildConfig.DEBUG) android.util.Log.i("BelesPreview", "${active.logContext} $detail")
    }

    private fun observedAttempt(event: AnalyticsListener.EventTime): PlaybackAttempt? {
        if (released) return null
        if (event.timeline.isEmpty || event.windowIndex !in 0 until event.timeline.windowCount) return null
        val mediaId = event.timeline.getWindow(event.windowIndex, Timeline.Window()).mediaItem.mediaId
        val period=event.mediaPeriodId
        if (period != null) {
            val index=event.timeline.getIndexOfPeriod(period.periodUid)
            if (index == C.INDEX_UNSET || event.timeline.getPeriod(index,Timeline.Period()).windowIndex != event.windowIndex) return null
        }
        return attempt?.takeIf { it.accepts(mediaId) }
    }

    private fun loadStage(data: MediaLoadData) = when (data.dataType) {
        C.DATA_TYPE_MANIFEST -> "manifest"
        C.DATA_TYPE_MEDIA, C.DATA_TYPE_MEDIA_INITIALIZATION -> "segment-or-media"
        else -> "other"
    }

    private fun httpStatus(failure: Throwable): Int? =
        generateSequence(failure) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode

    init {
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                observedAttempt(eventTime) ?: return
                qualityObservation = qualityObservation.copy(input = VideoTrackFormat(format.width, format.height, format.bitrate, format.codecs ?: format.sampleMimeType))
                updateTechnicalVideoFormat(format)
            }
            override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                observedAttempt(eventTime) ?: return
                updateTechnicalAudioFormat(format)
            }
            override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
                observedAttempt(eventTime) ?: return
                if (videoSize.width > 0 && videoSize.height > 0) {
                    technicalInfo = technicalInfo.copy(width = videoSize.width, height = videoSize.height)
                }
            }
            override fun onMediaMetadataChanged(eventTime: AnalyticsListener.EventTime, mediaMetadata: androidx.media3.common.MediaMetadata) {
                observedAttempt(eventTime) ?: return
                val title = mediaMetadata.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: mediaMetadata.displayTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (title != null) technicalInfo = technicalInfo.copy(streamTitle = title)
            }
            override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
                observedAttempt(eventTime) ?: return
                if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) mediaLoadData.trackFormat?.let(::updateTechnicalVideoFormat)
                if (mediaLoadData.trackType == C.TRACK_TYPE_AUDIO) mediaLoadData.trackFormat?.let(::updateTechnicalAudioFormat)
                val segmentDurationMs = mediaLoadData.mediaEndTimeMs - mediaLoadData.mediaStartTimeMs
                if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA && loadEventInfo.bytesLoaded > 0 && segmentDurationMs > 0) {
                    val bps = ((loadEventInfo.bytesLoaded * 8_000L) / segmentDurationMs).coerceIn(1L, 100_000_000L).toInt()
                    technicalInfo = technicalInfo.copy(measuredBitrate = bps)
                }
            }
            override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: androidx.media3.common.Tracks) {
                observedAttempt(eventTime) ?: return
                val formats = mutableListOf<VideoTrackFormat>()
                var videoTracks = 0
                tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
                    for (index in 0 until group.length) {
                        val f = group.getTrackFormat(index)
                        videoTracks++
                        if (group.isTrackSupported(index)) formats += VideoTrackFormat(f.width, f.height, f.bitrate, f.codecs ?: f.sampleMimeType)
                        if (group.isTrackSelected(index)) updateTechnicalVideoFormat(f)
                    }
                }
                tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                    for (index in 0 until group.length) {
                        if (group.isTrackSelected(index)) updateTechnicalAudioFormat(group.getTrackFormat(index))
                    }
                }
                exo.videoFormat?.let(::updateTechnicalVideoFormat)
                exo.audioFormat?.let(::updateTechnicalAudioFormat)
                qualityObservation = qualityObservation.copy(tracks = formats, unsupported = videoTracks > 0 && formats.isEmpty())
                audioTracks=LanguageTrackSelection.tracks(tracks,C.TRACK_TYPE_AUDIO)
                textTracks=LanguageTrackSelection.tracks(tracks,C.TRACK_TYPE_TEXT)
                applyTextParameters()
            }
            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
                val active = observedAttempt(eventTime) ?: return
                if (status == PlaybackStatus.ERROR && playbackState == Player.STATE_IDLE) return
                if (playbackState == Player.STATE_READY) {
                    if (!hasAppliedInitialLiveCushion && channel?.video != true) {
                        hasAppliedInitialLiveCushion = true
                        applyLiveCushionIfTooClose()
                    }
                    hasStartedPlayback = true
                    autoRecoveryCount = 0
                    error = null
                }
                if (playbackState == Player.STATE_ENDED && channel?.video != true) {
                    log(active, "auto-recover stage=LIVE_STATE_ENDED")
                    error = null
                    if (!hasStartedPlayback) status = PlaybackStatus.LOADING
                    hasAppliedInitialLiveCushion = false
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.playWhenReady = !requestedPause
                    return
                }
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> if (hasStartedPlayback) PlaybackStatus.READY else PlaybackStatus.LOADING
                    Player.STATE_READY -> PlaybackStatus.READY
                    Player.STATE_ENDED -> PlaybackStatus.ENDED
                    else -> PlaybackStatus.IDLE
                }
                log(active, "status=$status positionMs=${exo.currentPosition}")
            }
            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                observedAttempt(eventTime) ?: return
                hasStartedPlayback = true
                autoRecoveryCount = 0
                error = null
                if (exo.volume > 0f) channel?.id?.let(onPlayed)
            }
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                val active = observedAttempt(eventTime) ?: return
                hasStartedPlayback = true
                autoRecoveryCount = 0
                error = null
                log(active, "first-frame positionMs=${exo.currentPosition}")
                if (exo.volume > 0f) channel?.id?.let(onPlayed)
            }
            override fun onLoadError(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, failure: java.io.IOException, wasCanceled: Boolean) {
                val active = observedAttempt(eventTime) ?: return
                log(active, "load-error stage=${loadStage(data)} exception=${failure.javaClass.simpleName} causes=${causeClasses(failure)} http=${httpStatus(failure) ?: "unobserved"} canceled=$wasCanceled")
            }
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, value: PlaybackException) {
                val active = observedAttempt(eventTime) ?: return
                val http = httpStatus(value)
                val causes = causeClasses(value)
                if (PlaybackFailure.isBehindLiveWindow(value.errorCode, causes)) {
                    recoveryJob?.cancel()
                    recoveryJob = null
                    log(active, "auto-recover stage=BEHIND_LIVE_WINDOW code=${value.errorCodeName}")
                    error = null
                    if (!hasStartedPlayback) status = PlaybackStatus.LOADING
                    hasAppliedInitialLiveCushion = false
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.playWhenReady = !requestedPause
                    return
                }
                val stage = PlaybackFailure.classify(value.errorCode, http, causes)
                val canRecover = if (isMultiScreen) {
                    hasStartedPlayback && stage != PlaybackFailure.DECODER && autoRecoveryCount < 4
                } else {
                    (hasStartedPlayback || stage == PlaybackFailure.DECODER) && autoRecoveryCount < 4
                }
                if (canRecover) {
                    autoRecoveryCount++
                    val attemptStep = autoRecoveryCount
                    error = null
                    log(active, "auto-recover stage=$stage step=$attemptStep code=${value.errorCodeName} http=${http ?: "unobserved"}")
                    recoveryJob?.cancel()
                    recoveryJob = sourceScope.launch {
                        kotlinx.coroutines.delay(500L * attemptStep)
                        if (released || attempt?.accepts(active.id) != true) return@launch
                        if (attemptStep == 1) {
                            hasAppliedInitialLiveCushion = false
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.playWhenReady = !requestedPause
                        } else {
                            if (stage == PlaybackFailure.DECODER) {
                                temporaryResolutionCapped = true
                                val builder = exo.trackSelectionParameters.buildUpon().setMaxVideoSize(1920, 1080)
                                if (builder is androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder) {
                                    builder.setExceedVideoConstraintsIfNecessary(false)
                                }
                                exo.trackSelectionParameters = builder.build()
                                log(active, "auto-recover capping video to 1080p fallback for channel=${channel?.name}")
                            }
                            selectInternal(channel, preserveRecoveryState = true)
                        }
                    }
                    return
                }
                status = PlaybackStatus.ERROR
                error = stage.displayMessage(channel?.name, isMultiScreen)
                log(active, "status=ERROR stage=$stage code=${value.errorCodeName} codeNumber=${value.errorCode} exception=${value.javaClass.simpleName} causes=$causes http=${http ?: "unobserved"}")
            }
        })
    }
    override fun select(channel: PreviewChannel?) {
        selectInternal(channel, preserveRecoveryState = false)
    }
    private fun selectInternal(channel: PreviewChannel?, preserveRecoveryState: Boolean) {
        if (released) return
        sourceJob?.cancel()
        recoveryJob?.cancel()
        recoveryJob = null
        if (!preserveRecoveryState) {
            hasStartedPlayback = false
            hasAppliedInitialLiveCushion = false
            autoRecoveryCount = 0
            if (temporaryResolutionCapped) {
                temporaryResolutionCapped = false
                val selector = exo.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
                val base = selector?.parameters ?: exo.trackSelectionParameters
                exo.trackSelectionParameters = VideoQualitySelection.parameters(base, playbackPreferences)
            }
        }
        attempt = null // Ignore stop/clear and any callbacks from the previous media item.
        resetCueObserver();audioTracks=emptyList();textTracks=emptyList()
        this.channel = channel; error = null
        qualityObservation = VideoQualityObservation()
        technicalInfo = StreamTechnicalInfo()
        exo.stop(); exo.clearMediaItems()
        status = if (preserveRecoveryState && channel != null) (if (hasStartedPlayback) PlaybackStatus.READY else PlaybackStatus.LOADING) else PlaybackStatus.IDLE
        val uri = playbackUri(channel)
        if (uri != null) {
            val profile = if (channel?.definition != null) "public:${PreviewCodec.id(PublicM3u.profile)}" else "demo"
            val active = PlaybackAttempt.create(profile, PreviewCodec.id(channel!!.id), uri)
            attempt = active
            resetCueObserver()
            log(active, if (preserveRecoveryState) "select-recovery step=$autoRecoveryCount" else "select")
            sourceJob = sourceScope.launch {
                var stage = PlaybackFailure.HEADERS
                try {
                    val headers = channel.definition?.headers.orEmpty()
                    require(headers.map { it.name.lowercase() }.distinct().size == headers.size) { "Repeated headers unsupported" }
                    var effectiveUri = uri
                    var effectiveHeaders = headers.associate { it.name to it.value }
                    var mime = channel.definition?.mimeType
                    if (WebStreamResolver.isWebPageUrl(effectiveUri)) {
                        if (!hasStartedPlayback) status = PlaybackStatus.LOADING
                        val resolved = withContext(probeDispatcher) {
                            WebStreamResolver.resolve(effectiveUri, effectiveHeaders)
                        }
                        effectiveUri = resolved.uri
                        effectiveHeaders = effectiveHeaders + resolved.headers
                        if (resolved.mimeType != null) {
                            mime = resolved.mimeType
                        }
                    }
                    val http = DefaultHttpDataSource.Factory().apply {
                        effectiveHeaders["User-Agent"]?.let(::setUserAgent)
                        setAllowCrossProtocolRedirects(true)
                        setConnectTimeoutMs(12_000)
                        setReadTimeoutMs(12_000)
                        setDefaultRequestProperties(effectiveHeaders)
                    }
                    if (StreamType.needsProbe(effectiveUri, mime)) {
                        if (!hasStartedPlayback) status = PlaybackStatus.LOADING
                        mime = withContext(probeDispatcher) {
                            // One bounded normal GET; original URI and stored headers are used again for playback.
                            val probe = DefaultHttpDataSource.Factory().apply {
                                effectiveHeaders["User-Agent"]?.let(::setUserAgent)
                                setAllowCrossProtocolRedirects(true)
                                setConnectTimeoutMs(3000)
                                setReadTimeoutMs(3000)
                                setDefaultRequestProperties(effectiveHeaders)
                            }.createDataSource()
                            try {
                                probe.open(DataSpec.Builder().setUri(effectiveUri).build())
                                val bytes = ByteArray(1024)
                                val count = probe.read(bytes, 0, bytes.size).coerceAtLeast(0)
                                val type = probe.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
                                StreamType.fromResponse(type, bytes.copyOf(count))
                            } catch (failure: java.io.IOException) {
                                null // Normal Media3 load reports the authoritative playback failure.
                            } finally { probe.close() }
                        }
                    }
                    if (released || attempt?.accepts(active.id) != true) return@launch
                    stage = PlaybackFailure.MEDIA_SOURCE
                    val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
                        .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6))
                    val itemBuilder = MediaItem.Builder().setUri(effectiveUri).setMimeType(mime).setMediaId(active.id)
                    if (!channel.video) {
                        itemBuilder.setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(38_000L) // Canlı kenardan ~38 sn geriden gel (6+ tam HLS segmenti yastık!)
                                .setMinOffsetMs(24_000L)    // En az 24 sn geride kal, canlı kenara asla fazla yaklaşma
                                .setMaxOffsetMs(75_000L)    // 75 sn'ye kadar geride kalmaya izin ver
                                .setMinPlaybackSpeed(1.0f)  // Hızı asla düşürme/artırma, tamponu eritme!
                                .setMaxPlaybackSpeed(1.0f)
                                .build()
                        )
                    }
                    val item = itemBuilder.build()
                    val source = factory.createMediaSource(item)
                    exo.setMediaSource(source)
                    stage = PlaybackFailure.PLAYER
                    exo.prepare(); exo.playWhenReady = !requestedPause
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Throwable) {
                    if (released || attempt?.accepts(active.id) != true) return@launch
                    val causes = causeClasses(failure)
                    val classified = PlaybackFailure.classify(PlaybackException.ERROR_CODE_UNSPECIFIED, httpStatus(failure), causes)
                    val effectiveStage = if (classified == PlaybackFailure.DECODER) PlaybackFailure.DECODER else stage
                    val canRecover = if (isMultiScreen) {
                        hasStartedPlayback && effectiveStage != PlaybackFailure.DECODER && autoRecoveryCount < 4
                    } else {
                        (hasStartedPlayback || effectiveStage == PlaybackFailure.DECODER) && autoRecoveryCount < 4
                    }
                    if (canRecover) {
                        autoRecoveryCount++
                        val attemptStep = autoRecoveryCount
                        error = null
                        log(active, "auto-recover stage=$effectiveStage step=$attemptStep exception=${failure.javaClass.simpleName}")
                        recoveryJob?.cancel()
                        recoveryJob = sourceScope.launch {
                            kotlinx.coroutines.delay(500L * attemptStep)
                            if (released || attempt?.accepts(active.id) != true) return@launch
                            if (attemptStep >= 2 && effectiveStage == PlaybackFailure.DECODER) {
                                temporaryResolutionCapped = true
                                val builder = exo.trackSelectionParameters.buildUpon().setMaxVideoSize(1920, 1080)
                                if (builder is androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder) {
                                    builder.setExceedVideoConstraintsIfNecessary(false)
                                }
                                exo.trackSelectionParameters = builder.build()
                                log(active, "auto-recover capping video to 1080p fallback for channel=${channel.name}")
                            }
                            selectInternal(channel, preserveRecoveryState = true)
                        }
                        return@launch
                    }
                    status = PlaybackStatus.ERROR
                    error = effectiveStage.displayMessage(channel.name, isMultiScreen)
                    log(active, "status=ERROR stage=$effectiveStage exception=${failure.javaClass.simpleName} causes=$causes")
                }
            }
        } else status = PlaybackStatus.IDLE
    }
    override fun pause(paused: Boolean) { if (released) return; requestedPause = paused; exo.playWhenReady = !paused }
    override fun mute(muted: Boolean) {
        if (released) return
        exo.volume = if (muted) 0f else 1f
    }
    override fun retry() { if (!released) select(channel) }
    override fun syncLive() {
        if (released) return
        seekToDefaultPosition()
        play()
    }
    private fun applyLiveCushionIfTooClose() {
        if (released || channel?.video == true) return
        val liveOffset = exo.currentLiveOffset.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: if (exo.duration > 0L && exo.duration > exo.currentPosition) exo.duration - exo.currentPosition else 0L
        if (exo.isCurrentMediaItemLive && liveOffset in 1L..23_999L && exo.isCurrentMediaItemSeekable && exo.currentPosition > 22_000L) {
            val targetPos = (exo.currentPosition - 22_000L).coerceAtLeast(0L)
            exo.seekTo(targetPos)
        }
    }
    fun seekToDefaultPosition() {
        if (released) return
        hasAppliedInitialLiveCushion = false
        exo.seekToDefaultPosition()
        applyLiveCushionIfTooClose()
    }
    fun play() {
        if (!released) {
            requestedPause = false
            exo.play()
        }
    }
    fun seekBackwardInLiveWindow() {
        if (released) return
        val target = (exo.currentPosition - 15_000L).coerceAtLeast(0L)
        exo.seekTo(target)
        requestedPause = false
        exo.play()
    }
    override fun release() {
        if (released) return
        released = true; attempt = null; cueGate.advance(null); captions = emptyList()
        recoveryJob?.cancel(); recoveryJob = null
        hasStartedPlayback = false; hasAppliedInitialLiveCushion = false; autoRecoveryCount = 0
        temporaryResolutionCapped = false
        cueListener?.let(exo::removeAnalyticsListener); cueListener = null
        audioTracks = emptyList(); textTracks = emptyList(); technicalInfo = StreamTechnicalInfo()
        sourceScope.cancel(); exo.release()
    }
}

@Composable
internal fun SampleVideo(channel: PreviewChannel?, paused: Boolean, muted: Boolean, retry: Int, format: String, modifier: Modifier, onPlayed: (com.omer.belestiviclone.shared.catalog.OpaqueId) -> Unit = {}, preferences: PlaybackPreferences = PlaybackPreferences(), onQuality: (String) -> Unit = {}, captionBottomInsetPx: Int = 0, onTechnicalInfo: (StreamTechnicalInfo) -> Unit = {}, onPlaybackStatus: (PlaybackStatus, String?) -> Unit = { _, _ -> }, isMultiScreen: Boolean = false, liveSync: Int = 0, seekBack: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val played by rememberUpdatedState(onPlayed)
    val player = remember(isMultiScreen) { AndroidPreviewPlayer(context.applicationContext, isMultiScreen) { played(it) } }
    val currentPaused by rememberUpdatedState(paused)
    LaunchedEffect(channel, retry) { player.preferences(preferences); player.mute(muted); player.select(channel); player.pause(paused) }
    LaunchedEffect(liveSync) {
        if (liveSync > 0) {
            player.seekToDefaultPosition()
            player.play()
        }
    }
    LaunchedEffect(seekBack) {
        if (seekBack > 0) {
            player.seekBackwardInLiveWindow()
        }
    }
    LaunchedEffect(player, channel, paused) {
        while (true) {
            onTechnicalInfo(player.pollLiveMetrics())
            kotlinx.coroutines.delay(500L)
        }
    }
    LaunchedEffect(preferences) { player.preferences(preferences) }
    LaunchedEffect(player.status, player.error) { onPlaybackStatus(player.status, player.error) }
    SideEffect { onQuality(player.qualityDescription); onTechnicalInfo(player.technicalInfo); onPlaybackStatus(player.status, player.error) }
    LaunchedEffect(paused, muted) { player.pause(paused); player.mute(muted) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (preferences.backgroundAudio != "Açık") player.pause(true)
            }
            if (event == Lifecycle.Event.ON_START) {
                if (!currentPaused) player.pause(false)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.release() }
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        VideoTexture(player, format, Modifier.fillMaxSize())
        CaptionOverlay(
            if (TrackLanguage.subtitle(preferences.subtitle).mode in listOf(LanguageMode.OFF,LanguageMode.UNKNOWN)) emptyList() else player.captions,
            Modifier.fillMaxSize(),
            { _, _ -> },
            captionBottomInsetPx,
            subtitleSize = preferences.subtitleSize,
            subtitleColor = preferences.subtitleColor,
            subtitleBgOpacity = preferences.subtitleBgOpacity,
        )
        val err = player.error
        if (isMultiScreen && err != null) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicText(
                    "Yayın açılamadı",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                BasicText(
                    "OK: Tekrar dene veya kanal değiştir",
                    style = TextStyle(
                        color = Color(0xFFD0D7DE),
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                BasicText(
                    err,
                    style = TextStyle(
                        color = Color(0xFF94A2B7),
                        fontSize = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        } else if (err != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clickable { player.select(channel) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(
                    err,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                BasicText(
                    "(Tekrar denemek için dokunun veya OK basın)",
                    style = TextStyle(
                        color = Color(0xFFD0D7DE),
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        } else {
            val text = when (player.status) { PlaybackStatus.LOADING -> "${channel?.name.orEmpty()} yükleniyor…"; PlaybackStatus.ENDED -> "${channel?.name.orEmpty()} bitti · Yeniden dene"; else -> "" }
            if (text.isNotEmpty()) {
                BasicText(text, Modifier.align(Alignment.Center).padding(16.dp), style = TextStyle(color = Color.White, fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
    }
}

/** Texture sizing is independent from media requests and attempts. */
@Composable
private fun VideoTexture(player: AndroidPreviewPlayer, format: String, modifier: Modifier) {
    val exo = player.exo
    val context = androidx.compose.ui.platform.LocalContext.current
    val texture = remember(exo) { TextureView(context).also { it.isOpaque = false } }
    var size by remember(exo) { mutableStateOf(exo.videoSize) }
    val currentSize by rememberUpdatedState(size)
    val currentFormat by rememberUpdatedState(format)
    fun resize(view: TextureView) {
        val video = currentSize
        val rect = videoRect(view.width, view.height, video.width, video.height,
            video.pixelWidthHeightRatio, 0, currentFormat) ?: run { view.alpha = 0f; return }
        view.alpha = 1f // Media3 1.11.1 VideoSize constructors normalize deprecated rotation to zero.
        val matrix = android.graphics.Matrix()
        matrix.setScale(rect.width / view.width, rect.height / view.height, view.width / 2f, view.height / 2f)
        view.setTransform(matrix)
    }
    LaunchedEffect(format, size) { resize(texture) }
    DisposableEffect(exo, texture) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { size = videoSize }
        }
        val layout = android.view.View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> resize(view as TextureView) }
        exo.addListener(listener); texture.addOnLayoutChangeListener(layout)
        exo.setVideoTextureView(texture)
        onDispose {
            exo.removeListener(listener); texture.removeOnLayoutChangeListener(layout)
            exo.clearVideoTextureView(texture)
        }
    }
    AndroidView(factory = { texture }, update = { resize(it) }, modifier = modifier)
}
