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
import com.omer.belestiviclone.sharedui.PreviewApp
import com.omer.belestiviclone.sharedui.PreviewState

open class MainActivity : ComponentActivity() {
    private var renderTrace: RenderTrace? = null
    protected open fun r30Store(session: String): PreviewStore = error("R30 debug route required")
    protected open fun r39Store(session: String): PreviewStore = error("R39 debug route required")
    protected open suspend fun r39Ready(state: PreviewState) = Unit
    protected open fun r40Store(session: String): PreviewStore = error("R40 debug route required")
    protected open suspend fun r40Ready(state: PreviewState) = Unit
    protected open fun r41Store(session: String): PreviewStore = error("R41 debug route required")
    protected open suspend fun r41Ready(state: PreviewState) = Unit
    protected open fun r42Store(session: String): PreviewStore = error("R42 debug route required")
    protected open suspend fun r42Ready(state: PreviewState) = Unit
    private var previewState: PreviewState? = null
    private val navigationPress = NavigationPress()
    private var focusTarget = "none"
    private val layoutRecords = mutableMapOf<String, String>()
    private val navigationKeys = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)

    private fun immersive() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        renderTrace?.event("window-focus", detail = "focused=$hasFocus")
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
        val command = tvNavigationCommand(event.keyCode)
        if (s != null && command != null && event.action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val ime = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            val eligible = command.eligible(s.normalLiveControls, s.panel, ime)
            val result = navigationPress.event(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount > 0, eligible)
            if (result.perform) {
                if (command.delta == 0) s.revealLiveControls() else s.stepChannel(command.delta)
                if (BuildConfig.DEBUG) android.util.Log.i("BelesNav", "action=${if (command.delta == 0) "reveal" else "channel-step"} delta=${command.delta} selected=${PreviewCodec.id(s.selected)} panel=${s.panel}")
            }
            if (result.consumed) return true
        }
        return false
    }

    private var r38GuardRejected = false
    override fun onDestroy() {
        super.onDestroy()
        if (r38GuardRejected && BuildConfig.DEBUG) android.util.Log.i("BelesR38B", "guard-onDestroy storesOpened=0")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val r42Session = intent.getStringExtra("isolatedProfile")
        val r42Marked = R42Entry.marked(r42Session,intent.action,intent.extras?.keySet().orEmpty(),javaClass.name)
        val r42 = if (r42Marked) R42Entry.admit(BuildConfig.DEBUG,intent.getStringExtra("candidate"),r42Session,
            intent.action,intent.getStringExtra("r42Action"),intent.getStringExtra("database"),intent.getIntExtra("key",-1),
            javaClass.name,savedInstanceState != null) else null
        if (r42Marked && r42 == null) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR42","entry-rejected storesOpened=0")
            finish(); return
        }
        val r41Session = intent.getStringExtra("isolatedProfile")
        val r41Marked = R41Entry.marked(r41Session,intent.action,intent.extras?.keySet().orEmpty(),javaClass.name)
        val r41 = if (r41Marked) R41Entry.admit(BuildConfig.DEBUG,intent.getStringExtra("candidate"),r41Session,
            intent.action,intent.getStringExtra("r41Action"),intent.getStringExtra("database"),intent.getIntExtra("key",-1),
            javaClass.name,savedInstanceState != null) else null
        if (r41Marked && r41 == null) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR41","entry-rejected storesOpened=0")
            finish(); return
        }
        val r40Session = intent.getStringExtra("isolatedProfile")
        val r40Marked = R40Entry.marked(r40Session, intent.action, intent.extras?.keySet().orEmpty(), javaClass.name)
        val r40 = if (r40Marked) R40Entry.admit(BuildConfig.DEBUG, intent.getStringExtra("candidate"), r40Session,
            intent.action, intent.getStringExtra("r40Action"), intent.getStringExtra("database"), intent.getIntExtra("key", -1),
            javaClass.name, savedInstanceState != null) else null
        if (r40Marked && r40 == null) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR40", "entry-rejected storesOpened=0")
            finish(); return
        }
        val r39Session = intent.getStringExtra("isolatedProfile")
        val r39Marked = R39Entry.marked(r39Session, intent.action, intent.extras?.keySet().orEmpty(), javaClass.name)
        val r39 = if (r39Marked) R39Entry.admit(BuildConfig.DEBUG, intent.getStringExtra("candidate"), r39Session,
            intent.action, intent.getStringExtra("r39Action"), intent.getStringExtra("database"),
            intent.getIntExtra("key", -1), javaClass.name, savedInstanceState != null) else null
        if (r39Marked && r39 == null) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR39", "entry-rejected storesOpened=0")
            finish(); return
        }
        if (r39 == null && r40 == null && r41 == null && r42 == null && MainEntryGuard.marked(intent.getStringExtra("isolatedProfile"), intent.action, intent.extras?.keySet().orEmpty())) {
            r38GuardRejected = true
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR38B", "guard-rejected storesOpened=0 finish-request")
            finish(); return
        }
        val sessionExtra = intent.getStringExtra("isolatedProfile")
        if (R38Entry.marked(sessionExtra, intent.action, intent.hasExtra("r38Action"))) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR38", "entry-rejected storesOpened=0")
            finish(); return
        }
        if (!R33Entry.isolatedExtraAllowed(BuildConfig.DEBUG, intent.hasExtra("isolatedProfile"), sessionExtra)) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR33", "invalid-isolated-extra storesOpened=0")
            finish(); return
        }
        if (R33Entry.marked(sessionExtra, intent.action, intent.hasExtra("r33Action")) &&
            !R33Entry.allowed(BuildConfig.DEBUG, sessionExtra, intent.action, intent.hasExtra("r33Action"))) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR33", "entry-rejected storesOpened=0")
            finish(); return
        }
        val r30Marked = R30Entry.marked(sessionExtra, intent.action, intent.hasExtra("r30Action"), javaClass.name)
        val r30 = if (r30Marked) R30Entry.admit(BuildConfig.DEBUG, sessionExtra, intent.action,
            intent.getStringExtra("r30Action"), javaClass.name) else null
        if (r30Marked && r30 == null) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesRender", "event=entry-rejected storesOpened=0")
            finish(); return
        }
        val traceSession = if (BuildConfig.DEBUG) sessionExtra?.takeIf { it.matches(Regex("toplu-[a-z0-9-]{1,48}")) } else null
        if (BuildConfig.DEBUG) renderTrace = RenderTrace(this, traceSession, traceSession?.let { "$it.db" } ?: "belestivi_preview_v1.db")
        renderTrace?.event("activity-create", detail = "restored=${savedInstanceState != null}")
        if (BuildConfig.DEBUG) lifecycle.addObserver(LifecycleEventObserver { _, event -> renderTrace?.event("activity-$event") })
        // The public Window callback preserves Android/Compose dispatch for every unowned event.
        val activityCallback = window.callback
        window.callback = object : android.view.Window.Callback by activityCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handleNavigationKey(event) || activityCallback.dispatchKeyEvent(event)
        }
        enableEdgeToEdge()
        immersive()
        window.decorView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (BuildConfig.DEBUG) {
                val insets = ViewCompat.getRootWindowInsets(view)
                val detail = "view=${view.width}x${view.height} bars=${insets?.getInsets(WindowInsetsCompat.Type.systemBars())} cutout=${insets?.getInsets(WindowInsetsCompat.Type.displayCutout())} ime=${insets?.getInsets(WindowInsetsCompat.Type.ime())} barsVisible=${insets?.isVisible(WindowInsetsCompat.Type.systemBars())}"
                if (layoutRecords.put("insets", detail) != detail) android.util.Log.i("BelesWindow", detail)
            }
        }
        val testProfile = if (BuildConfig.DEBUG) intent.getStringExtra("isolatedProfile")?.takeIf { it.matches(Regex("toplu-[a-z0-9-]{1,48}")) } else null
        // TV and this isolated TV test session keep their landscape viewport across SAF activities.
        // Phone sessions retain their existing sensor/responsive orientation behavior.
        if (testProfile != null || resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        renderTrace?.event("orientation-selected")
        val databaseName = testProfile?.let { "$it.db" } ?: "belestivi_preview_v1.db"
        val store: PreviewStore = if (r42 != null) try { r42Store(r42) } catch (failure: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR42","event=HOLD normalStoreOpened=false type=${failure.javaClass.simpleName}")
            finish(); return
        } else if (r41 != null) try { r41Store(r41) } catch (failure: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.i("BelesR41","event=HOLD normalStoreOpened=false type=${failure.javaClass.simpleName}")
            finish(); return
        } else if (r40 != null) r40Store(r40) else if (r39 != null) r39Store(r39) else if (r30 != null) r30Store(r30) else AndroidPreviewStore(applicationContext, databaseName = databaseName)
        val catalog = if (r30 == null && r39 == null && r40 == null && r41 == null && r42 == null) AndroidPublicCatalog(applicationContext, testProfile?.let { "$it-catalog" } ?: "public-trlist-catalog-v1") else null
        val pilotAction = r21PilotAction(BuildConfig.DEBUG, testProfile, intent.getStringExtra("r21Action"))
            ?: r22PilotAction(BuildConfig.DEBUG, testProfile, intent.getStringExtra("r22Action"))
        if (BuildConfig.DEBUG) android.util.Log.i("BelesTransition", "session=${testProfile ?: "none"} manifestProvided=${pilotAction != null} realProfileMigration=false")
        val pilotRequest: (suspend () -> PilotRequest)? = pilotAction?.let { action ->
            { androidR21Pilot(applicationContext, testProfile!!, catalog!!, action == "rollback", action == "legacy-exit") }
        }
        val publicStore = if (r30 != null || r39 != null || r40 != null || r41 != null || r42 != null) store else AndroidPreviewStore(applicationContext, key = 2, databaseName = databaseName, pilot = pilotRequest) {
            val channels = catalog!!.load()
            PreviewData(favorites = emptyList(), history = emptyList(), categories = channels.flatMap { it.groups }.distinct(),
                preferences = store.load().preferences, sources = listOf("Özel M3U · trlist · 8 Ekim 2026"), channels = channels, profileId = PublicM3u.profile)
        }
        setContent {
            val scope = rememberCoroutineScope()
            val state = remember { PreviewState(store, scope, publicStore, AndroidSourceLoader(applicationContext)) }
            var captionReservedBottomPx by remember { mutableIntStateOf(0) }
            DocumentBridge(state)
            SideEffect { previewState = state }
            LaunchedEffect(state.loaded) { if (r39 != null && state.loaded) r39Ready(state) }
            LaunchedEffect(state.loaded) { if (r40 != null && state.loaded) r40Ready(state) }
            LaunchedEffect(state.loaded) { if (r41 != null && state.loaded) r41Ready(state) }
            LaunchedEffect(state.loaded) { if (r42 != null && state.loaded) r42Ready(state) }
            LaunchedEffect(state.data, state.selected, state.busy, state.page) {
                if (state.loaded) renderTrace?.event("preview-state", detail = "testID=${PreviewCodec.id(state.selected)} manualPause=${state.paused} startup=${if (r30 != null) state.data.pref("startup") else "redacted"} history=${if (r30 != null) state.data.pref("history") else "redacted"} format=${state.data.pref("format")} route=${state.page}")
                if (BuildConfig.DEBUG && state.loaded) android.util.Log.i("BelesSource", "testProfile=${testProfile ?: "none"} route=${state.page} busy=${state.busy} active=${state.data.library?.active?.value?.let(PreviewCodec::id) ?: "legacy"} kind=${state.data.library?.main?.kind} records=${state.savedSources.size} activeCount=${state.activeCount} favorites=${state.data.favorites.size} history=${state.data.history.size} selected=${PreviewCodec.id(state.selected)}")
                if (BuildConfig.DEBUG && state.loaded) {
                    fun digest(values: List<String>) = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(values.joinToString("\n").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                    val active = state.data.channels.filter { it.available }
                    android.util.Log.i("BelesLogoBinding", "channels=${active.size} idsSha256=${digest(active.map { PreviewCodec.id(it.id) }.sorted())} uriSetSha256=${digest(active.mapNotNull { playbackUri(it) }.distinct().sorted())} logoSetSha256=${digest(active.map { it.logo }.distinct().sorted())} favoritesSha256=${digest(state.data.favorites.map(PreviewCodec::id))} historySha256=${digest(state.data.history.map(PreviewCodec::id))}")
                }
            }
            LaunchedEffect(state.exitRequested) { if (state.exitRequested) finish() }
            BackHandler(enabled = state.page != "home" || state.dialog != null, onBack = state::back)
            PreviewApp(state, appVersionName = BuildConfig.VERSION_NAME,
                onFocusTarget = { focusTarget = it; if (BuildConfig.DEBUG) android.util.Log.i("BelesNav", "focus=$it route=${state.page} dialog=${state.dialog}") },
                onNavigationKey = { observeKey(it.nativeKeyEvent, "compose") },
                onLayoutBounds = { part, rect ->
                    if(part == "channel-info") captionReservedBottomPx=(window.decorView.height-rect.top.toInt()+8).coerceAtLeast(0)
                    if (BuildConfig.DEBUG) {
                    val detail = "part=$part rect=$rect route=${state.page} panel=${state.panel}"
                    if (layoutRecords.put(part, detail) != detail) android.util.Log.i("BelesWindow", detail)
                } },
                platformKeySignal = { KeyPressSignal(it.nativeKeyEvent.isLongPress, it.nativeKeyEvent.repeatCount > 0) }, image = { name, modifier ->
                PreviewImage(name, modifier)
            }, video = { channel, paused, muted, retry, format, preferences, onQuality, modifier ->
                SampleVideo(channel, paused, muted, retry, format, modifier, state::played, renderTrace, preferences, onQuality,
                    captionBottomInsetPx=if(state.page == "live" && state.info) captionReservedBottomPx else 0)
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
class AndroidPreviewPlayer(private val context: android.content.Context, private val trace: RenderTrace? = null, private val onPlayed: (com.omer.belestiviclone.shared.catalog.OpaqueId) -> Unit = {}) : PreviewPlayer {
    val exo = ExoPlayer.Builder(context).build()
    private val tracePlayer = trace?.let {
        TracePlayerMetadata.capture(RenderTrace.identity(exo)) {
            val loop = exo.applicationLooper
            loop.thread.id to RenderTrace.identity(loop)
        }
    }
    override var status by mutableStateOf(PlaybackStatus.IDLE); private set
    override var error by mutableStateOf<String?>(null); private set
    private var channel: PreviewChannel? = null
    private var attempt: PlaybackAttempt? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var released = false
    private val r42Observed = BuildConfig.DEBUG && trace?.session in R42Entry.sessions
    private var r42Frames = 0L
    private var r42FirstFrameNs = 0L
    private var r42SelectedNs = 0L
    internal fun r42Snapshot(): org.json.JSONObject {
        check(!released && android.os.Looper.myLooper() == exo.applicationLooper)
        return org.json.JSONObject().put("monoNs",android.os.SystemClock.elapsedRealtimeNanos())
            .put("player",RenderTrace.identity(exo)).put("attempt",attempt?.id).put("mediaId",exo.currentMediaItem?.mediaId)
            .put("channelId",channel?.id?.let(PreviewCodec::id)).put("ready",exo.playbackState == Player.STATE_READY)
            .put("playing",exo.isPlaying).put("positionMs",exo.currentPosition).put("frames",r42Frames)
            .put("gain",exo.volume.toDouble()).put("videoDecoder",decoder).put("audioDecoder",audioDecoder)
            .put("selectedNs",r42SelectedNs).put("firstFrameNs",r42FirstFrameNs)
            .put("cueGeneration",cueOwner?.generation).put("cueCount",captions.size)
            .put("quality",playbackPreferences.quality).put("audio",playbackPreferences.audio).put("text",playbackPreferences.subtitle)
            .put("error",status == PlaybackStatus.ERROR).apply {
                exo.videoFormat?.let { put("videoInput",formatJson(it)) }
                exo.audioFormat?.let { put("audioInput",formatJson(it).put("language",it.language).put("channels",it.channelCount).put("sampleRate",it.sampleRate)) }
                channel?.let { ch -> playbackUri(ch)?.let { put("uriSha256",r40Hash(it)) };ch.definition?.let { put("fullDefinitionSha256",r40FullHash(it)) } }
            }
    }
    private val sourceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val probeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var sourceJob: Job? = null
    private var requestedPause = false
    private var playbackPreferences by mutableStateOf(PlaybackPreferences())
    private var qualityObservation by mutableStateOf(VideoQualityObservation())
    val qualityDescription get() = qualityObservation.description(playbackPreferences) + "\nSes: " +
        languageStatus(playbackPreferences.audio,TrackLanguage.audio(playbackPreferences.audio),audioTracks) + "\nAltyazı: " +
        languageStatus(playbackPreferences.subtitle,TrackLanguage.subtitle(playbackPreferences.subtitle),textTracks,text=true)
    private var audioTracks by mutableStateOf(emptyList<LanguageTrack>())
    private var textTracks by mutableStateOf(emptyList<LanguageTrack>())
    var captions by mutableStateOf(emptyList<androidx.media3.common.text.Cue>()); private set
    private val cueGate = CueOwnerGate()
    private var cueOwner: CueOwner? = null
    private var cueListener: AnalyticsListener? = null
    private var audioInput: androidx.media3.common.Format? = null
    private var audioDecoder = "unobserved"
    private var cueCapturedNs = 0L
    private var cueObservedNs = 0L
    private var lastUiCueKey = ""
    private fun resetCueObserver() {
        val old=cueOwner
        cueListener?.let(exo::removeAnalyticsListener)
        captions=emptyList();cueCapturedNs=0;cueObservedNs=0;lastUiCueKey=""
        cueOwner=cueGate.advance(attempt?.id)
        val captured=cueOwner
        val installedMs=android.os.SystemClock.elapsedRealtime()
        cueListener = if (captured == null) null else object : AnalyticsListener {
            override fun onCues(eventTime: AnalyticsListener.EventTime, cueGroup: androidx.media3.common.text.CueGroup) {
                val capturedNs=android.os.SystemClock.elapsedRealtimeNanos()
                if (eventTime.realtimeMs < installedMs) return
                val active=observedAttempt(eventTime) ?: return
                val target=subtitleTrack(TrackLanguage.subtitle(playbackPreferences.subtitle),textTracks) ?: return
                if (textTracks.none { it.supported && it.selected && it.group == target.group && it.index == target.index }) return
                receiveCues(captured,active.id,cueGroup.cues.toList(),capturedNs,cueGroup.presentationTimeUs)
            }
        }.also(exo::addAnalyticsListener)
        if (BuildConfig.DEBUG && trace?.session in R41Entry.sessions && old != null) {
            // Deliberate debug-only queued stale cue. It must never reach the overlay.
            val payload=listOf(androidx.media3.common.text.Cue.Builder().setText("R41 STALE FAULT").build())
            val capturedNs=android.os.SystemClock.elapsedRealtimeNanos()
            handler.postDelayed({ receiveCues(old,old.attempt,payload,capturedNs,0,true) },500)
        }
    }
    private fun receiveCues(owner: CueOwner, mediaId: String, cues: List<androidx.media3.common.text.Cue>, capturedNs: Long,
        presentationUs: Long, injected: Boolean = false) {
        if (released) return // No Player getters after release, including diagnostic paths.
        val enabled=TrackLanguage.subtitle(playbackPreferences.subtitle).mode !in listOf(LanguageMode.OFF,LanguageMode.UNKNOWN) &&
            C.TRACK_TYPE_TEXT !in exo.trackSelectionParameters.disabledTrackTypes
        if (!cueGate.accepts(owner,mediaId,enabled) || attempt?.accepts(mediaId) != true) {
            trackEvent(if(injected) "forced-stale-cue-rejected" else "stale-cue-rejected",org.json.JSONObject()
                .put("callbackAttempt",mediaId).put("callbackGeneration",owner.generation));return
        }
        captions=cues.toList();cueCapturedNs=capturedNs;cueObservedNs=android.os.SystemClock.elapsedRealtimeNanos()
        trackEvent("cue-payload",org.json.JSONObject().put("callbackCapturedNs",capturedNs).put("mainDispatchObservedNs",cueObservedNs)
            .put("presentationTimeUs",presentationUs).put("cueCount",cues.size)
            .put("textLength",cues.sumOf { it.text?.length ?: 0 }).put("bitmapCount",cues.count { it.bitmap != null }))
    }
    internal fun cueUiObserved(count: Int, unsupported: Int) {
        val key="${cueOwner}:$cueObservedNs:$count:$unsupported"
        if (released || lastUiCueKey == key) return
        lastUiCueKey=key
        trackEvent("cue-ui",org.json.JSONObject().put("uiCueObservedNs",android.os.SystemClock.elapsedRealtimeNanos())
            .put("callbackCapturedNs",cueCapturedNs).put("mainDispatchObservedNs",cueObservedNs)
            .put("visibleCueCount",count).put("unsupportedCueCount",unsupported))
    }
    private fun trackEvent(name: String, value: org.json.JSONObject = org.json.JSONObject()) {
        if (!BuildConfig.DEBUG || released || (trace?.session !in R41Entry.sessions && !r42Observed)) return
        val p=exo.trackSelectionParameters
        fun rows(values: List<LanguageTrack>) = org.json.JSONArray().also { a -> values.forEach {
            a.put(org.json.JSONObject().put("group",it.group).put("index",it.index).put("id",it.id).put("language",it.language)
                .put("supported",it.supported).put("selected",it.selected).put("role",it.role).put("default",it.default)) } }
        value.put("event",name).put("monoNs",android.os.SystemClock.elapsedRealtimeNanos()).put("player",RenderTrace.identity(exo))
            .put("attempt",attempt?.id).put("mediaId",exo.currentMediaItem?.mediaId).put("generation",cueOwner?.generation)
            .put("audioRequest",playbackPreferences.audio).put("textRequest",playbackPreferences.subtitle)
            .put("audioLanguages",p.preferredAudioLanguages.joinToString()).put("textLanguages",p.preferredTextLanguages.joinToString())
            .put("textDisabled",C.TRACK_TYPE_TEXT in p.disabledTrackTypes).put("audio",rows(audioTracks)).put("text",rows(textTracks))
            .put("positionMs",exo.currentPosition).put("playing",exo.isPlaying).put("bufferedMs",exo.bufferedPosition)
            .put("audioDecoder",audioDecoder).put("audioInputId",audioInput?.id).put("audioInputLanguage",audioInput?.language)
            .put("maxWidth",p.maxVideoWidth).put("maxHeight",p.maxVideoHeight)
            .put("exceedVideoConstraints",(exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector).parameters.exceedVideoConstraintsIfNecessary)
        android.util.Log.i("BelesTracks","${trace?.immutableContext()} state=$value")
    }
    private fun applyTextParameters() {
        val selector=exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector
        val next=LanguageTrackSelection.text(selector.parameters,playbackPreferences.subtitle,exo.currentTracks)
        if (next != selector.parameters) exo.trackSelectionParameters=next
    }
    private var decoder = "unobserved"
    private var qualitySequence = 0
    private var preferencesApplied = false
    internal fun qualityEvent(name: String, extra: org.json.JSONObject = org.json.JSONObject()) {
        if (!BuildConfig.DEBUG || released) return
        check(android.os.Looper.myLooper() == exo.applicationLooper)
        val params = exo.trackSelectionParameters
        val selectorParams = (exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector).parameters
        extra.put("event", name).put("monoNs", android.os.SystemClock.elapsedRealtimeNanos())
            .put("player", RenderTrace.identity(exo)).put("playerSeq", tracePlayer?.sequence)
            .put("attempt", attempt?.id).put("request", playbackPreferences.quality).put("requestSequence", qualitySequence)
            .put("maxWidth", params.maxVideoWidth).put("maxHeight", params.maxVideoHeight)
            .put("exceedVideoConstraintsIfNecessary", selectorParams.exceedVideoConstraintsIfNecessary)
            .put("audioTextSha256", r40Hash(PreviewCodec.pack(listOf(params.preferredAudioLanguages.joinToString(), params.preferredTextLanguages.joinToString(),
                params.preferredAudioRoleFlags.toString(), params.preferredTextRoleFlags.toString(), params.maxAudioBitrate.toString(),
                params.maxAudioChannelCount.toString(), params.selectUndeterminedTextLanguage.toString(), params.disabledTrackTypes.toString()))))
            .put("applicationThread", true).put("decoder", decoder).put("positionMs", exo.currentPosition)
            .put("playing", exo.isPlaying).put("mediaId", exo.currentMediaItem?.mediaId)
        channel?.let { ch ->
            extra.put("channelId", PreviewCodec.id(ch.id))
            playbackUri(ch)?.let { extra.put("uriSha256", r40Hash(it)) }
            ch.definition?.let { extra.put("fullDefinitionSha256", r40FullHash(it)) }
        }
        android.util.Log.i("BelesQuality", "${trace?.immutableContext() ?: "session=normal"} state=$extra")
    }
    private fun formatJson(format: androidx.media3.common.Format) = org.json.JSONObject()
        .put("width", format.width).put("height", format.height).put("bitrate", format.bitrate)
        .put("codec", format.codecs).put("mime", format.sampleMimeType)
    override fun preferences(value: PlaybackPreferences) {
        if (released) return
        check(android.os.Looper.myLooper() == exo.applicationLooper)
        if (preferencesApplied && value == playbackPreferences) return
        preferencesApplied = true
        val previous=playbackPreferences
        playbackPreferences = value; qualitySequence++
        qualityEvent("request")
        val selector = exo.trackSelector as androidx.media3.exoplayer.trackselection.DefaultTrackSelector
        if (previous.quality != value.quality || qualitySequence == 1)
            exo.trackSelectionParameters = VideoQualitySelection.parameters(selector.parameters, value)
        if (previous.audio != value.audio || qualitySequence == 1)
            exo.trackSelectionParameters = LanguageTrackSelection.audio(selector.parameters,value.audio)
        if (previous.subtitle != value.subtitle || qualitySequence == 1) { resetCueObserver();applyTextParameters() }
        qualityEvent("parameters-active")
        trackEvent("parameters-active")
    }
    private val r39 = if (BuildConfig.DEBUG && trace?.session in R39Entry.sessions)
        R39Diagnostics(exo, requireNotNull(trace).immutableContext(), requireNotNull(tracePlayer)) else null
    internal fun trace(name: String, texture: TextureView? = null, detail: String = "") {
        trace?.event(name, tracePlayer, texture, "released=$released requestedPause=$requestedPause attempt=${attempt?.id ?: "none"} $detail")
        if (texture != null) r39?.surface(name, RenderTrace.identity(texture))
    }
    private val positionObserver = object : Runnable {
        override fun run() {
            if (released) return
            val active = attempt
            if (active != null && active.accepts(exo.currentMediaItem?.mediaId) && exo.isPlaying) {
                log(active, "position positionMs=${exo.currentPosition} playing=true")
                trackEvent("position")
            }
            handler.postDelayed(this, 3000)
        }
    }

    private fun log(active: PlaybackAttempt, detail: String) {
        android.util.Log.i("BelesPreview", "${active.logContext} $detail")
        trace("playback", detail = "${active.logContext} $detail")
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
        return attempt?.takeIf { it.accepts(mediaId) }.also {
            if (it == null) trace("stale-callback-ignored", detail = "callbackAttempt=$mediaId")
        }
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
        if (r42Observed) R42Observation.add(this)
        trace("player-create")
        if (BuildConfig.DEBUG) exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                trace("playWhenReady-changed", detail = "value=$playWhenReady reason=$reason")
            }
        })
        handler.postDelayed(positionObserver, 3000)
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                observedAttempt(eventTime)?.let { decoder = decoderName; log(it, "video-decoder=$decoderName initMs=$initializationDurationMs"); qualityEvent("decoder") }
            }
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                observedAttempt(eventTime) ?: return
                qualityObservation = qualityObservation.copy(input = VideoTrackFormat(format.width, format.height, format.bitrate, format.codecs ?: format.sampleMimeType))
                qualityEvent("input-format", formatJson(format))
            }
            override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: androidx.media3.common.Tracks) {
                observedAttempt(eventTime) ?: return
                val formats = mutableListOf<VideoTrackFormat>()
                val raw = org.json.JSONArray()
                tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
                    for (index in 0 until group.length) {
                        val f = group.getTrackFormat(index)
                        val supported = group.isTrackSupported(index)
                        if (supported) formats += VideoTrackFormat(f.width, f.height, f.bitrate, f.codecs ?: f.sampleMimeType)
                        raw.put(formatJson(f).put("supported", supported).put("selected", group.isTrackSelected(index)))
                    }
                }
                qualityObservation = qualityObservation.copy(tracks = formats, unsupported = raw.length() > 0 && formats.isEmpty())
                qualityEvent("tracks", org.json.JSONObject().put("variants", raw))
                audioTracks=LanguageTrackSelection.tracks(tracks,C.TRACK_TYPE_AUDIO)
                textTracks=LanguageTrackSelection.tracks(tracks,C.TRACK_TYPE_TEXT)
                applyTextParameters()
                trackEvent("tracks")
            }
            override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                observedAttempt(eventTime)?.let { audioDecoder=decoderName;log(it, "audio-decoder=$decoderName initMs=$initializationDurationMs");trackEvent("audio-decoder") }
            }
            override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                observedAttempt(eventTime) ?: return
                audioInput=format;trackEvent("audio-input",org.json.JSONObject().put("callbackCapturedNs",android.os.SystemClock.elapsedRealtimeNanos())
                    .put("codec",format.codecs).put("mime",format.sampleMimeType).put("role",format.roleFlags))
            }
            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
                val active = observedAttempt(eventTime) ?: return
                if (status == PlaybackStatus.ERROR && playbackState == Player.STATE_IDLE) return
                status = when (playbackState) { Player.STATE_BUFFERING -> PlaybackStatus.LOADING; Player.STATE_READY -> PlaybackStatus.READY; Player.STATE_ENDED -> PlaybackStatus.ENDED; else -> PlaybackStatus.IDLE }
                log(active, "status=$status positionMs=${exo.currentPosition}")
                trackEvent("playback-state")
            }
            override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                val active = observedAttempt(eventTime) ?: return
                log(active, "audio-position-advancing positionMs=${exo.currentPosition} muted=${exo.volume == 0f}")
                if (exo.volume > 0f) channel?.id?.let(onPlayed)
            }
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                val active = observedAttempt(eventTime) ?: return
                if (r42Observed && r42FirstFrameNs == 0L) r42FirstFrameNs = android.os.SystemClock.elapsedRealtimeNanos()
                log(active, "first-frame positionMs=${exo.currentPosition}")
                qualityEvent("first-frame")
                if (exo.volume > 0f) channel?.id?.let(onPlayed)
            }
            override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, retryCount: Int) {
                val active = observedAttempt(eventTime) ?: return
                log(active, "load-start stage=${loadStage(data)} task=${info.loadTaskId} retryCount=$retryCount")
            }
            override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
                val active = observedAttempt(eventTime) ?: return
                log(active, "load-complete stage=${loadStage(data)} task=${info.loadTaskId} bytes=${info.bytesLoaded}")
            }
            override fun onLoadError(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, failure: java.io.IOException, wasCanceled: Boolean) {
                val active = observedAttempt(eventTime) ?: return
                log(active, "load-error stage=${loadStage(data)} task=${info.loadTaskId} exception=${failure.javaClass.simpleName} causes=${causeClasses(failure)} http=${httpStatus(failure) ?: "unobserved"} canceled=$wasCanceled")
            }
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, value: PlaybackException) {
                val active = observedAttempt(eventTime) ?: return
                val http = httpStatus(value)
                val stage = PlaybackFailure.classify(value.errorCode, http)
                status = PlaybackStatus.ERROR
                error = "${channel?.name.orEmpty()} · ${stage.message}"
                log(active, "status=ERROR stage=$stage code=${value.errorCodeName} codeNumber=${value.errorCode} exception=${value.javaClass.simpleName} causes=${causeClasses(value)} http=${http ?: "unobserved"}")
            }
        })
    }
    override fun select(channel: PreviewChannel?) {
        if (released) return
        trace("select-begin", detail = "testID=${channel?.id?.let(PreviewCodec::id)}")
        sourceJob?.cancel()
        attempt = null // Ignore stop/clear and any callbacks from the previous media item.
        resetCueObserver();audioTracks=emptyList();textTracks=emptyList();audioInput=null;audioDecoder="unobserved"
        this.channel = channel; error = null
        r42Frames = 0; r42FirstFrameNs = 0; r42SelectedNs = android.os.SystemClock.elapsedRealtimeNanos()
        qualityObservation = VideoQualityObservation(); decoder = "unobserved"
        exo.stop(); exo.clearMediaItems()
        status = PlaybackStatus.IDLE
        val uri = playbackUri(channel)
        if (uri != null) {
            val profile = if (channel?.definition != null) "public:${PreviewCodec.id(PublicM3u.profile)}" else "demo"
            val active = PlaybackAttempt.create(profile, PreviewCodec.id(channel!!.id), uri)
            attempt = active
            resetCueObserver()
            qualityEvent("select")
            if (BuildConfig.DEBUG && (trace?.session in R40Entry.sessions || trace?.session in R41Entry.sessions || r42Observed)) {
                // Capture the attempt before crossing threads. No live Player getter on the renderer.
                exo.setVideoFrameMetadataListener { pts, _, f, _ ->
                    val sample = formatJson(f).put("presentationTimeUs", pts).put("callbackCapturedNs",android.os.SystemClock.elapsedRealtimeNanos())
                    handler.post { if (!released && attempt?.accepts(active.id) == true) { if (r42Observed) r42Frames++; qualityEvent("frame-input", sample) } }
                }
            }
            r39?.select(active.id, android.os.SystemClock.elapsedRealtimeNanos())
            log(active, "select")
            sourceJob = sourceScope.launch {
                var stage = PlaybackFailure.HEADERS
                try {
                    val headers = channel.definition?.headers.orEmpty()
                    require(headers.map { it.name.lowercase() }.distinct().size == headers.size) { "Repeated headers unsupported" }
                    val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers.associate { it.name to it.value })
                    var mime = channel.definition?.mimeType
                    if (StreamType.needsProbe(uri, mime)) {
                        status = PlaybackStatus.LOADING
                        mime = withContext(probeDispatcher) {
                            // One bounded normal GET; original URI and stored headers are used again for playback.
                            val probe = DefaultHttpDataSource.Factory().setConnectTimeoutMs(3000).setReadTimeoutMs(3000)
                                .setDefaultRequestProperties(headers.associate { it.name to it.value }).createDataSource()
                            try {
                                probe.open(DataSpec.Builder().setUri(uri).build())
                                val bytes = ByteArray(1024)
                                val count = probe.read(bytes, 0, bytes.size).coerceAtLeast(0)
                                val type = probe.responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
                                StreamType.fromResponse(type, bytes.copyOf(count))
                            } catch (failure: java.io.IOException) {
                                null // Normal Media3 load reports the authoritative playback failure.
                            } finally { probe.close() }
                        }
                    }
                    if (released || attempt?.accepts(active.id) != true) { trace("stale-source-ignored", detail = "callbackAttempt=${active.id}"); return@launch }
                    stage = PlaybackFailure.MEDIA_SOURCE
                    val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
                    val item = MediaItem.Builder().setUri(uri).setMimeType(mime).setMediaId(active.id).build()
                    val source = factory.createMediaSource(item)
                    log(active, "media-source-created type=${source.javaClass.simpleName} inferredHls=${mime == StreamType.HLS}")
                    exo.setMediaSource(source)
                    stage = PlaybackFailure.PLAYER
                    trace("prepare-begin"); exo.prepare(); trace("playWhenReady-set", detail = "value=${!requestedPause}"); exo.playWhenReady = !requestedPause
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (released || attempt?.accepts(active.id) != true) return@launch
                    status = PlaybackStatus.ERROR
                    error = "${channel.name} · ${stage.message}"
                    log(active, "status=ERROR stage=$stage exception=${failure.javaClass.simpleName} causes=${causeClasses(failure)}")
                }
            }
        } else status = PlaybackStatus.IDLE
    }
    override fun pause(paused: Boolean) { if(released)return;trace("pause", detail = "paused=$paused"); requestedPause = paused; exo.playWhenReady = !paused }
    override fun mute(muted: Boolean) {
        if (released) return
        exo.volume = if (muted) 0f else 1f
        attempt?.let { log(it, "volume=${exo.volume}") }
    }
    override fun retry() { if(!released)select(channel) }
    override fun release() { if(released)return;trace("release-begin"); attempt?.let { log(it, "release") }; r39?.close(); if(r42Observed)R42Observation.remove(this); released = true; attempt = null; cueGate.advance(null);captions=emptyList();cueListener?.let(exo::removeAnalyticsListener);cueListener=null;audioTracks=emptyList();textTracks=emptyList();audioInput=null;sourceScope.cancel(); handler.removeCallbacks(positionObserver); exo.release(); trace("release-end") }
}

@Composable
internal fun SampleVideo(channel: PreviewChannel?, paused: Boolean, muted: Boolean, retry: Int, format: String, modifier: Modifier, onPlayed: (com.omer.belestiviclone.shared.catalog.OpaqueId) -> Unit = {}, trace: RenderTrace? = null, preferences: PlaybackPreferences = PlaybackPreferences(), onQuality: (String) -> Unit = {}, captionBottomInsetPx: Int = 0) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val played by rememberUpdatedState(onPlayed)
    val player = remember { AndroidPreviewPlayer(context.applicationContext, trace) { played(it) } }
    val currentPaused by rememberUpdatedState(paused)
    LaunchedEffect(channel, retry) { player.preferences(preferences); player.mute(muted); player.select(channel); player.pause(paused) }
    LaunchedEffect(preferences) { player.preferences(preferences) }
    SideEffect { onQuality(player.qualityDescription) }
    LaunchedEffect(paused, muted) { player.pause(paused); player.mute(muted) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            player.trace("sample-$event", detail = "manualPause=$currentPaused format=$format")
            if (event == Lifecycle.Event.ON_STOP) player.pause(true)
            if (event == Lifecycle.Event.ON_START) player.pause(currentPaused)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.release() }
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        VideoTexture(player, format, Modifier.fillMaxSize())
        CaptionOverlay(if (TrackLanguage.subtitle(preferences.subtitle).mode in listOf(LanguageMode.OFF,LanguageMode.UNKNOWN)) emptyList() else player.captions,Modifier.fillMaxSize(),player::cueUiObserved,captionBottomInsetPx)
        val text = player.error ?: when (player.status) { PlaybackStatus.LOADING -> "${channel?.name.orEmpty()} yükleniyor…"; PlaybackStatus.ENDED -> "${channel?.name.orEmpty()} bitti · Yeniden dene"; else -> "" }
        if (text.isNotEmpty()) {
            BasicText(text, Modifier.align(Alignment.Center).padding(16.dp), style = TextStyle(color = Color.White, fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        }
    }
}

/** Texture sizing is independent from media requests, attempts and lifecycle diagnostics. */
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
        player.trace("resize", view, "format=$currentFormat video=${video.width}x${video.height} pixelRatio=${video.pixelWidthHeightRatio}")
        val rect = videoRect(view.width, view.height, video.width, video.height,
            video.pixelWidthHeightRatio, 0, currentFormat) ?: run { view.alpha = 0f; return }
        view.alpha = 1f // Media3 1.11.1 VideoSize constructors normalize deprecated rotation to zero.
        val matrix = android.graphics.Matrix()
        matrix.setScale(rect.width / view.width, rect.height / view.height, view.width / 2f, view.height / 2f)
        view.setTransform(matrix)
        android.util.Log.i("BelesGeometry", "view=${view.width}x${view.height} video=${video.width}x${video.height} pixelRatio=${video.pixelWidthHeightRatio} rotation=0 format=$currentFormat rect=$rect")
    }
    DisposableEffect(exo, texture) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { size = videoSize }
        }
        val layout = android.view.View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> resize(view as TextureView) }
        val attach = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { player.trace("attach", texture) }
            override fun onViewDetachedFromWindow(v: android.view.View) { player.trace("detach", texture) }
        }
        if (BuildConfig.DEBUG) texture.addOnAttachStateChangeListener(attach)
        exo.addListener(listener); texture.addOnLayoutChangeListener(layout)
        player.trace("bind-begin", texture); exo.setVideoTextureView(texture); player.trace("bind-end", texture)
        onDispose {
            exo.removeListener(listener); texture.removeOnLayoutChangeListener(layout)
            player.trace("clear-begin", texture); exo.clearVideoTextureView(texture); player.trace("clear-end", texture)
            if (BuildConfig.DEBUG) texture.removeOnAttachStateChangeListener(attach)
        }
    }
    AndroidView(factory = { texture }, update = { resize(it) }, modifier = modifier)
}
