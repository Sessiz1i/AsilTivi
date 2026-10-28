@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omer.belestiviclone.shared.preview.*
import kotlin.math.abs
import kotlin.time.TimeSource

private val amber = Color(0xFFE67E22)
private val card = Color(0xFF13233D)
private val ink = Color(0xFF0A1628)
private class FocusReturn {
    val registry = FocusRegistry<FocusRequester>()
    var blockBackground by mutableStateOf(false)
    var capture = true
    var focused: FocusRequester? = null
    var restore: FocusRequester? = null
    val active = mutableListOf<FocusRequester>()
}
private val LocalFocusReturn = staticCompositionLocalOf<FocusReturn?> { null }
private val LocalKeySignal = staticCompositionLocalOf<(KeyEvent) -> KeyPressSignal> { { KeyPressSignal() } }
private val LocalFocusObserver = staticCompositionLocalOf<(String) -> Unit> { {} }
private val LocalNavigationObserver = staticCompositionLocalOf<(KeyEvent) -> Unit> { {} }
internal val LocalBoundsObserver = staticCompositionLocalOf<(String, Rect) -> Unit> { { _, _ -> } }
private val colors = listOf(Color(0xFFB68B08), Color(0xFF007D9E), Color(0xFF5A6C80), Color(0xFFB1AFA7), Color(0xFFAF4546), Color(0xFF37875B), Color(0xFF466BAB))
@Composable
internal fun Label(value: String, size: Int = 12, color: Color = Color.White, modifier: Modifier = Modifier, bold: Boolean = false, lineHeight: Float = size * 1.333f, letterSpacing: Float = 0f, maxLines: Int = Int.MAX_VALUE, textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Unspecified, decoration: androidx.compose.ui.text.style.TextDecoration? = null) {
    BasicText(presentationText(value, LocalLanguage.current), modifier, style = TextStyle(color = color, fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = letterSpacing.sp, textAlign = textAlign, textDecoration = decoration, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Key repeat is consumed; long OK never produces an additional short action. */
@Composable
internal fun Action(label: String, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, tint: Color = card, selectedTint: Color = Color(0xB3A75F19), radius: Dp = 10.dp, brush: Brush? = null, stroke: Color = Color(0xFF34404A), strokeWidth: Dp = 1.dp, focusColor: Color = Color.White, horizontalFocus: Boolean = false, channelFocus: Boolean = false, padding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp), long: (() -> Unit)? = null, double: (() -> Unit)? = null, extraKey: ((KeyEvent) -> Boolean)? = null, content: (@Composable () -> Unit)? = null, focusKey: String? = null, returnFocusKey: String? = null, onFocused: (() -> Unit)? = null, click: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val gate = remember { OkGesture() }
    val clock = remember { TimeSource.Monotonic.markNow() }
    val returns = LocalFocusReturn.current
    val requester = remember(returns, focusKey) { focusKey?.let { returns?.registry?.requester(it) { FocusRequester() } } ?: FocusRequester() }
    val inModal = LocalModalLayer.current
    val canInteract = enabled && (returns?.blockBackground != true || inModal)
    val keySignal = LocalKeySignal.current
    val focusObserver = LocalFocusObserver.current
    val navigationObserver = LocalNavigationObserver.current
    val accessibilityLabel = presentationText(label, LocalLanguage.current)
    DisposableEffect(requester) {
        returns?.active?.add(requester)
        onDispose { returns?.active?.remove(requester); if (returns?.focused == requester) returns.focused = null }
    }
    fun invoke(action: () -> Unit) {
        if (returns?.capture == true) returns.restore = returnFocusKey?.let { returns.registry.requester(it) { FocusRequester() } } ?: requester
        action()
    }
    Box(modifier.defaultMinSize(minWidth = 29.dp, minHeight = 29.dp)
        .focusRequester(requester).focusProperties { canFocus = canInteract }.semantics { contentDescription = accessibilityLabel }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) { returns?.focused = requester; focusObserver(focusKey ?: "action:${label.hashCode()}"); onFocused?.invoke() }
            else { gate.reset(); if (returns?.focused == requester) { returns.focused = null; focusObserver("none") } }
        }
        .onPreviewKeyEvent { e ->
            navigationObserver(e)
            if (extraKey?.invoke(e) == true) true
            else if (canInteract && e.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                val now = clock.elapsedNow().inWholeMilliseconds
                val signal = keySignal(e)
                val action = when (e.type) { KeyEventType.KeyDown -> gate.down(now, long != null, signal.longPress, signal.repeat); KeyEventType.KeyUp -> gate.up(now, long != null); else -> OkAction.NONE }
                when (action) { OkAction.SHORT -> invoke(click); OkAction.LONG -> long?.let { invoke(it) }; OkAction.NONE -> Unit }
                true
            } else false
        }
        .then(if (brush != null && !selected) Modifier.background(brush, RoundedCornerShape(radius)) else Modifier.background(if (selected) selectedTint else if (focused && channelFocus) Color(0x668A531B) else tint, RoundedCornerShape(radius)))
        .then(if (strokeWidth > 0.dp || (focused && !horizontalFocus && !channelFocus)) Modifier.border(if (focused && !horizontalFocus && !channelFocus) 2.dp else strokeWidth, if (focused && !horizontalFocus && !channelFocus) focusColor else stroke, RoundedCornerShape(radius)) else Modifier)
        .drawWithContent {
            drawContent()
            if (focused && horizontalFocus && !channelFocus) {
                val edge = 1.dp.toPx()
                drawLine(Color.White, Offset(0f, edge / 2), Offset(size.width, edge / 2), edge)
                drawLine(focusColor, Offset(0f, edge * 2), Offset(size.width, edge * 2), edge * 2)
                drawLine(focusColor, Offset(0f, size.height - edge * 2), Offset(size.width, size.height - edge * 2), edge * 2)
                drawLine(Color.White, Offset(0f, size.height - edge / 2), Offset(size.width, size.height - edge / 2), edge)
            }
        }
        .combinedClickable(enabled = canInteract, role = Role.Button, onLongClick = long?.let { { invoke(it) } }, onDoubleClick = double?.let { { invoke(it) } }, onClick = { invoke(click) })
        .padding(padding), contentAlignment = Alignment.Center) {
        if (content != null) content() else Label(label, color = if (enabled) Color.White else Color.Gray)
    }
}
@Composable
internal fun Field(value: String, label: String, icon: String, image: @Composable (String, Modifier) -> Unit, password: Boolean = false, modifier: Modifier = Modifier, placeholder: String = "", change: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().height(78.dp).background(card, RoundedCornerShape(10.dp)).padding(horizontal = 19.dp, vertical = 12.dp)) {
        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            image(icon, Modifier.size(18.dp, 19.dp)); Label(label, 9, Color(0xFF94A2B7), bold = true, letterSpacing = .8f)
        }
        Spacer(Modifier.height(7.dp))
        BasicTextField(value, change, Modifier.fillMaxWidth().height(24.dp).onFocusChanged { focused = it.isFocused }.semantics { contentDescription = label }, textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp), cursorBrush = SolidColor(amber), singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None, decorationBox = { input ->
            Box { if (value.isEmpty()) Label(placeholder, 12, Color(0xFF69798F), lineHeight = 18f); input() }
        })
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(if (focused) amber else Color(0xFF223858)))
    }
}
@Composable
fun PreviewApp(s: PreviewState, appVersionName: String, image: @Composable (String, Modifier) -> Unit, video: @Composable (PreviewChannel?, Boolean, Boolean, Int, String, PlaybackPreferences, (String) -> Unit, Modifier) -> Unit, platformKeySignal: (KeyEvent) -> KeyPressSignal = { KeyPressSignal() }, onFocusTarget: (String) -> Unit = {}, onNavigationKey: (KeyEvent) -> Unit = {}, onLayoutBounds: (String, Rect) -> Unit = { _, _ -> }) {
    val returns = remember { FocusReturn() }
    var restorePending by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    returns.capture = s.dialog == null
    returns.blockBackground = s.dialog != null && s.dialog !in sourceFormKinds && s.dialog != "categories"
    LaunchedEffect(s.loaded, s.page, s.panel, s.dialog, windowFocused) {
        if (s.dialog != null) restorePending = true
        else if (s.loaded && windowFocused) {
            withFrameNanos { }
            if (!s.searching) {
                val requester = if (restorePending) returns.registry.returnTarget(returns.restore, returns.active)
                    else if (returns.focused !in returns.active) {
                        val key = when (s.page) {
                            "home" -> "home:live"
                            "settings" -> "settings:section:${s.section}"
                            "multi" -> "multi:slot:${s.multi?.selected ?: 0}"
                            else -> "channel:${PreviewCodec.id(s.selected)}"
                        }
                        returns.registry.entryTarget(key, returns.active)
                    } else null
                runCatching { requester?.requestFocus() }
            }
            restorePending = false
        }
    }
    LaunchedEffect(s) { s.load() }
    CompositionLocalProvider(LocalFocusReturn provides returns, LocalEnglish provides (s.data.pref("language") == "English"), LocalLanguage provides s.data.pref("language"), LocalKeySignal provides platformKeySignal, LocalFocusObserver provides onFocusTarget, LocalNavigationObserver provides onNavigationKey, LocalBoundsObserver provides onLayoutBounds) {
    Box(Modifier.fillMaxSize().onGloballyPositioned { onLayoutBounds("window-root", it.boundsInWindow()) }.background(Color.Black).safeDrawingPadding()) {
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { onLayoutBounds("safe-root", it.boundsInWindow()) }) {
    val density = LocalDensity.current
    val scale = minOf(maxWidth.value / 855f, maxHeight.value / 481f)
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
    Box(Modifier.fillMaxSize()) {
        if (!s.loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(s.storage) }
        else when (s.page) {
            "settings" -> Settings(s, image, appVersionName)
            "multi" -> Multi(s, image, video)
            else -> Live(s, image, video)
        }
        if (s.notice.isNotEmpty()) {
            val current = s.notice
            LaunchedEffect(current) { kotlinx.coroutines.delay(5500); if (s.notice == current) s.notice = "" }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).widthIn(max = 540.dp).background(Color(0xEE20354C), RoundedCornerShape(12.dp)).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) { Label(s.notice, 14) }
        }
        if (s.dialog != null) CompositionLocalProvider(LocalModalLayer provides true) { Modal(s, image) }
    }
    }
    }
    }
    }
}
@Composable
private fun Home(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF0C0C0C))))) {
        val portrait = maxWidth < maxHeight
        val logoHeight = if (portrait) 84.dp else maxHeight * (84f / 482)
        val entries = listOf(Triple("CANLI TV", "ev_tv", Color(0xFF184B6C)), Triple("FİLMLER", "ev_film", Color(0xFF8A251B)), Triple("Diziler", "ev_dizi", Color(0xFF4A2B58)))
        if (!portrait) {
            image("beles_tivi_plus", Modifier.align(Alignment.TopCenter).offset(y = maxHeight * .075f).height(logoHeight).width(maxWidth * .174f))
            Row(Modifier.offset(x = maxWidth * .034f, y = maxHeight * .357f).fillMaxWidth(.932f).height(maxHeight * .284f), horizontalArrangement = Arrangement.spacedBy(maxWidth * .023f)) {
                entries.forEachIndexed { i, e -> HomeCard(s, image, e, i, Modifier.weight(1f).fillMaxHeight()) }
            }
            Label("${s.profileName} · ${s.activeCount} içerik", 9, Color.Gray, Modifier.align(Alignment.BottomStart).padding(15.dp))
            Action(s.text("Ayarlar", "Settings"), Modifier.offset(x = maxWidth * (708f / 857), y = maxHeight * (415f / 482)).width(maxWidth * (122f / 857)).height(maxHeight * (41f / 482)), brush = Brush.verticalGradient(listOf(Color(0xFF101A25), Color(0xFF040810))), radius = 23.dp, content = {
                Row(verticalAlignment = Alignment.CenterVertically) { image("ikon_ayar", Modifier.size(20.dp)); Label("Ayarlar", 12, bold = true) }
            }, click = s::settings)
        } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            image("beles_tivi_plus", Modifier.height(logoHeight).width(150.dp))
            Label(s.text("Önizleme · 12 örnek kanal", "Preview · 12 sample channels"), 13, Color(0xFFB6BDCA))
            if (portrait) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { entries.forEachIndexed { i, e -> HomeCard(s, image, e, i, Modifier.fillMaxWidth().height(120.dp)) } }
            else Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) { entries.forEachIndexed { i, e -> HomeCard(s, image, e, i, Modifier.weight(1f).heightIn(min = 120.dp, max = 180.dp)) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label(s.storage, 12, Color(0xFFAEB8C8), Modifier.weight(1f))
                Action(s.text("Ayarlar", "Settings"), click = s::settings)
            }
        }
        }
        Action(s.text("ÇIKIŞ", "EXIT"), Modifier.offset(x = maxWidth * (763f / 857), y = maxHeight * (24f / 482)).width(maxWidth * (72f / 857)).height(maxHeight * (30f / 482)), radius = 0.dp, tint = Color(0xFFD8222A), strokeWidth = 0.dp, content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { image("ev_cikis", Modifier.size(12.dp, 15.dp)); Label("ÇIKIŞ", 12, bold = true) }
        }) { s.exitRequested = true }
    }
}
@Composable
private fun HomeCard(s: PreviewState, image: @Composable (String, Modifier) -> Unit, entry: Triple<String, String, Color>, index: Int, modifier: Modifier) {
    val start = listOf(Color(0xFF0D2332), Color(0xFF4E090C), Color(0xFF26132D))[index]
    Action(entry.first, modifier, brush = Brush.linearGradient(listOf(start, entry.third)), stroke = Color(0xFF34343B), strokeWidth = 2.dp, radius = 25.dp, padding = PaddingValues(0.dp), focusKey = if (index == 0) "home:live" else "home:$index", content = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            image(entry.second, Modifier.align(Alignment.TopCenter).offset(y = maxHeight * (31f / 137)).size(43.dp, 45.dp))
            Row(Modifier.align(Alignment.TopCenter).offset(y = maxHeight * (87f / 137)), verticalAlignment = Alignment.CenterVertically) {
                if (index == 2) image("ev_dizi_kucuk", Modifier.size(17.dp, 22.dp))
                Label(if (s.data.pref("language") == "English") listOf("LIVE TV", "MOVIES", "Series")[index] else entry.first, 17, bold = true)
            }
        }
    }) { if (index == 0) { s.page = "live"; s.panel = true } else s.notice = s.text("Bu içerik ilk önizleme kapsamında değil.", "This content is outside the first preview.") }
}
@Composable
private fun Live(s: PreviewState, image: @Composable (String, Modifier) -> Unit, video: @Composable (PreviewChannel?, Boolean, Boolean, Int, String, PlaybackPreferences, (String) -> Unit, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vertical = s.data.pref("list").startsWith("Dikey") || maxHeight > maxWidth
        val panelHeight = if (s.searching) 160.dp else 120.dp
        val opacity = s.data.pref("opacity").toFloat() / 100f
        var qualityInfo by remember(s.selected, s.retry) { mutableStateOf("") }
        var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(s.selected, s.info, s.panel, s.epgRevision) {
            nowEpochMs = System.currentTimeMillis()
            s.epgCache.evictExpired(nowEpochMs)
            var ticks = 0
            while (s.info || s.panel) {
                kotlinx.coroutines.delay(1000L)
                nowEpochMs = System.currentTimeMillis()
                ticks++
                if (ticks >= 15) {
                    ticks = 0
                    s.epgCache.evictExpired(nowEpochMs)
                }
            }
        }
        val playbackPrefs = PlaybackPreferences(s.data.pref("quality"), s.data.pref("audio"), s.data.pref("subtitle"), s.data.pref("subtitleSize"), s.data.pref("subtitleColor"), s.data.pref("subtitleBgOpacity"), s.data.pref("backgroundAudio"))
        video(s.channel, s.paused, s.muted, s.retry, s.data.pref("format"), playbackPrefs, { qualityInfo = it }, Modifier.fillMaxSize())
        if (s.isRadioMode) {
            RadioScreen(s, image, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        if (playbackUri(s.channel) == null) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { image(s.channel?.logo ?: "ikon_tv", Modifier.size(70.dp)); Label(if (s.channel?.available == false) s.message("Kanal etkin kaynaklarda erişilemiyor", "Channel unavailable in active sources", "Kanal in aktiven Quellen nicht verfügbar") else if (s.isPublic) "Oynatma tanımı desteklenmiyor" else "Yayın bağlantısı yok", 11) }
        }
        LaunchedEffect(s.panel, s.selected, s.query, s.categoryMenu, s.moving, s.dialog, s.info) {
            if (s.panel && s.page == "live" && s.dialog == null && !s.searching && s.moving == null && !s.categoryMenu && !s.info) {
                kotlinx.coroutines.delay(5000L)
                s.panel = false
            }
        }
        val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { s.panel = true } })
        val boundsObserver = LocalBoundsObserver.current
        val currentEpg = remember(s.channel, nowEpochMs, s.epgRevision) { s.epgNowNext(s.channel, nowEpochMs) }
        val headingNow = currentEpg.now?.let { "Şimdi: ${it.title} (${it.timeRangeLabel()})" } ?: "Şimdi: EPG bilgisi yok"
        val headingNext = currentEpg.next?.let { "Sonra: ${it.title} (${it.timeRangeLabel()})" }
        if (s.liveHeadingVisible) LiveHeading(s.channel?.name ?: "", s.channel?.logo ?: "ikon_tv", headingNow, image,
            Modifier.liveHeadingPlacement(s.data.pref("format"), maxWidth.value, maxHeight.value).widthIn(max = maxWidth * .55f),
            nextSubtitle = headingNext, titleColor = theme.headingTitleColor, includeLogo = false, backdrop = true, leadingChannelLogo = true, onBounds = { part, rect -> boundsObserver("heading:$part", rect) })
        if (s.panel) {
            Row(Modifier.align(Alignment.TopEnd).padding(top = 21.dp, end = 18.dp).onGloballyPositioned { boundsObserver("top-actions", it.boundsInWindow()) }.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconAction("Radyo", "ikon_audio", image, click = s::enterRadioMode)
                IconAction("Çoklu Ekran", "ikon_coklu", image) { s.dialog = "warning" }
                IconAction("Görüntü", "ikon_format", image, click = s::openFormatMenu)
                IconAction("Bilgi", "ikon_saat", image, click = s::toggleInfo)
                IconAction("Ayarlar", "ikon_ayar", image, click = s::settings)
                IconAction("Çıkış", "ikon_cikis", image) { s.dialog = "exit" }
            }
            if (vertical) {
                val panelWidth = minOf(320.dp, maxWidth * if (maxWidth > maxHeight) (192f / 855f) else .52f)
                val top = LiveSidebarTop.dp
                val listHeight = (maxHeight - top - if (s.searching) 114.dp else 74.dp).coerceAtLeast(80.dp)
                Column(Modifier.onGloballyPositioned { boundsObserver("vertical-panel", it.boundsInWindow()) }.offset(x = if (s.categoryMenu) 135.dp else 13.dp, y = top).width(panelWidth).height(listHeight).background(Color.Black.copy(alpha = opacity))) {
                    Channels(s, image, true, Modifier.fillMaxSize())
                }
                if (s.categoryMenu) LazyColumn(Modifier.offset(13.dp, top).width(122.dp).height(listHeight).background(Color.Black.copy(alpha = opacity)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { CategoryTab("Ara", s, image, true) { s.searching = true; s.categoryMenu = false } }
                    items(categories(s)) { cat -> CategoryTab(cat, s, image, true) { handleChooseCategory(s, cat) } }
                    item { CategoryTab("ÇOKLU EKRAN", s, image, true) { s.categoryAction("multi") } }
                    item { CategoryTab("AYARLAR", s, image, true) { s.categoryAction("settings") } }
                }
                Row(Modifier.offset(13.dp, top + listHeight).width(panelWidth).height(30.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val currentCategoryLabel = when {
                        s.category.equals("Favoriler", ignoreCase = true) || s.category.equals("FAVORİ TV", ignoreCase = true) -> "FAVORİ TV"
                        s.category.equals("Tümü", ignoreCase = true) || s.category.equals("TÜMÜ", ignoreCase = true) -> "TÜMÜ"
                        else -> s.category
                    }
                    Action(currentCategoryLabel, Modifier.weight(1f), tint = Color(0xB3000000), radius = 0.dp, focusKey = "live:category") { s.categoryMenu = !s.categoryMenu }
                    IconAction("Ara", "kategori_ara", image) { s.searching = true }
                }
                if (s.searching) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 19.dp, vertical = 20.dp)) { Search(s) }
            } else Column(Modifier.align(Alignment.BottomCenter).onGloballyPositioned { boundsObserver("horizontal-panel", it.boundsInWindow()) }.padding(bottom = 11.dp).fillMaxWidth().height(panelHeight).background(Color.Black.copy(alpha = opacity))) {
                LazyRow(Modifier.height(44.dp).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(if (theme.categoryTabPill) 6.dp else (-8).dp)) {
                    item { CategoryTab("Ara", s, image, false) { s.searching = true } }
                    items(categories(s)) { cat -> CategoryTab(cat, s, image, false) { handleChooseCategory(s, cat) } }
                }
                Channels(s, image, false, Modifier.fillMaxWidth().height(68.dp))
                if (s.searching) Search(s)
            }
            val liveProgress = currentEpg.liveProgress(nowEpochMs, s.channelSessionStartMs)
            val playerTimeText = liveProgress.elapsedLabel
            val playerProgressFraction = liveProgress.progress

            val barStartPadding = if (vertical) {
                val pWidth = minOf(320.dp, maxWidth * if (maxWidth > maxHeight) (192f / 855f) else .52f)
                val panelRightEdge = (if (s.categoryMenu) 135.dp else 13.dp) + pWidth
                (panelRightEdge + 14.dp).coerceAtMost(maxWidth * 0.45f)
            } else {
                24.dp
            }

            Row(
                Modifier
                    .align(if (vertical) Alignment.BottomEnd else Alignment.BottomCenter)
                    .padding(
                        start = barStartPadding,
                        end = 24.dp,
                        bottom = if (vertical) 24.dp else panelHeight + 20.dp
                    )
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color(0xCC090F1A), RoundedCornerShape(19.dp))
                    .border(1.dp, Color(0x33446080), RoundedCornerShape(19.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Label(playerTimeText, 11, Color(0xFFE2E8F0), bold = true)

                PlayerSeekBar(
                    progress = playerProgressFraction,
                    color = theme.seekbarColor,
                    modifier = Modifier.weight(1f)
                )

                PlayerActionButton(
                    label = "Canlı",
                    iconContent = { Box(Modifier.size(7.dp).background(Color(0xFFE53935), CircleShape)) },
                    click = {
                        s.paused = false
                        s.syncLive()
                    }
                )

                val isMuted = s.muted
                PlayerActionButton(
                    label = if (isMuted) "Sessiz" else "Ses Açık",
                    iconContent = { BasicText(if (isMuted) "🔇" else "🔊", style = TextStyle(fontSize = 11.sp)) },
                    click = { s.muted = !s.muted }
                )

                val isPaused = s.paused
                PlayerActionButton(
                    label = if (isPaused) "Oynat" else "Duraklat",
                    iconContent = { BasicText(if (isPaused) "▶" else "⏸", style = TextStyle(fontSize = 10.sp, color = Color.White)) },
                    click = { s.paused = !s.paused }
                )
            }
        }
        if (s.info) {
            LaunchedEffect(s.info, s.selected) {
                kotlinx.coroutines.delay(10_000)
                s.info = false
            }
            val align = when (s.data.pref("position")) { "Sol" -> Alignment.BottomStart; "Sağ" -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
            val factor = when (s.data.pref("size")) { "Küçük" -> .75f; "Büyük" -> 1.25f; else -> 1f }
            ChannelInfoPanel(
                s = s,
                qualityInfo = qualityInfo,
                nowEpochMs = nowEpochMs,
                factor = factor,
                image = image,
                modifier = Modifier
                    .align(align)
                    .onGloballyPositioned { boundsObserver("channel-info", it.boundsInWindow()) }
                    .padding(bottom = if (s.panel && !vertical) panelHeight + 18.dp else 20.dp, start = 16.dp, end = 16.dp)
                    .width((435.dp * factor).coerceAtMost(maxWidth - 32.dp))
            )
        }
        if (s.moving != null) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp).background(Color(0xFF243F66), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF647B9A), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            image("ikon_tasima_bilgi", Modifier.size(35.dp, 17.dp)); Label("Yönlerle taşı · OK uygula · Geri vazgeç", 11, bold = true)
        }
    }
}
@Composable
private fun ChannelInfoPanel(s: PreviewState, qualityInfo: String, nowEpochMs: Long, factor: Float, image: @Composable (String, Modifier) -> Unit, modifier: Modifier) {
    val t = s.technicalInfo
    val epg = remember(s.channel, nowEpochMs, s.epgRevision) { s.epgNowNext(s.channel, nowEpochMs) }
    val nowProg = epg.now
    val nextProg = epg.next
    val programTitle = nowProg?.title ?: t.streamTitle ?: "Program Bilgisi Bulunamadı"
    val timeRangeText = nowProg?.timeRangeLabel() ?: "--:-- - --:--"
    val progressFraction = nowProg?.progressAt(nowEpochMs) ?: if (t.streamTitle != null) 0.5f else 0.0f
    val progressPercentText = nowProg?.let { "%${it.progressPercentAt(nowEpochMs)}" }
    val nextText = nextProg?.let { "${it.title} (${it.timeRangeLabel()})" } ?: "Program Bilgisi Bulunamadı"
    Column(
        modifier
            .background(Color(0xE608111E), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2B3E58), RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { s.info = false } }
            .padding((12 * factor).dp),
        verticalArrangement = Arrangement.spacedBy((8 * factor).dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((12 * factor).dp)
        ) {
            Box(
                Modifier
                    .size((56 * factor).dp)
                    .background(Color(0xFF13233D), RoundedCornerShape(10.dp))
                    .padding((6 * factor).dp),
                contentAlignment = Alignment.Center
            ) {
                image(s.channel?.logo ?: "ikon_tv", Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * factor).dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((6 * factor).dp)
                ) {
                    Box(
                        Modifier
                            .background(Color(0xFF1E3557), RoundedCornerShape(5.dp))
                            .padding(horizontal = (6 * factor).dp, vertical = (2 * factor).dp)
                    ) {
                        Label("#${s.channelNumber}", (10 * factor).toInt(), Color(0xFFFFD735), bold = true)
                    }
                    Label(s.channel?.name ?: "", (16 * factor).toInt(), Color.White, Modifier.weight(1f), bold = true, maxLines = 1)
                    if (!s.channel?.category.isNullOrEmpty()) {
                        Label(s.channel?.category.orEmpty(), (9 * factor).toInt(), Color(0xFF86C9EF), bold = true, maxLines = 1)
                    }
                    Box(
                        Modifier
                            .background(amber, RoundedCornerShape(5.dp))
                            .padding(horizontal = (6 * factor).dp, vertical = (2 * factor).dp)
                    ) {
                        Label(t.resolutionBadge, (9 * factor).toInt(), Color.White, bold = true)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Label("Şimdi: $programTitle", (11 * factor).toInt(), Color(0xFFFFD735), Modifier.weight(1f), bold = true, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy((4 * factor).dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(timeRangeText, (9 * factor).toInt(), Color(0xFFB0BEC5))
                        if (progressPercentText != null) {
                            Label(progressPercentText, (9 * factor).toInt(), Color(0xFFFFD735), bold = true)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((3 * factor).dp)
                        .background(Color(0xFF223651), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFFFFD735), RoundedCornerShape(2.dp))
                    )
                }
                if (!nowProg?.desc.isNullOrBlank()) {
                    Label(nowProg?.desc.orEmpty(), (9 * factor).toInt(), Color(0xFFCBD5E1), maxLines = 1)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Label("Sonra: $nextText", (10 * factor).toInt(), Color(0xFF86C9EF), Modifier.weight(1f), maxLines = 1)
                    Label("Oran: ${s.data.pref("format").substringBefore(" (")}", (9 * factor).toInt(), Color(0xFF94A2B7))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        Column(verticalArrangement = Arrangement.spacedBy((3 * factor).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("Çözünürlük: ${t.resolutionLabel}", (9 * factor).toInt(), Color(0xFFE2E8F0), bold = true)
                Label("Kare Hızı: ${t.fpsLabel}", (9 * factor).toInt(), Color(0xFFE2E8F0), bold = true)
                Label("Video Codec: ${t.videoCodecLabel}", (9 * factor).toInt(), Color(0xFFE2E8F0), bold = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label("Ses Formatı: ${t.audioFormatLabel}", (9 * factor).toInt(), Color(0xFFB8C7DC))
                Label("Bitrate: ${t.bitrateLabel}", (9 * factor).toInt(), Color(0xFFFFD735), bold = true)
            }
            if (qualityInfo.isNotBlank()) {
                val extraLine = qualityInfo.lineSequence().drop(2).joinToString(" · ")
                if (extraLine.isNotBlank()) {
                    Label(extraLine, (8 * factor).toInt(), Color(0xFF8A9AB0), maxLines = 1)
                }
            }
        }
    }
}

@Composable private fun IconAction(label: String, icon: String, image: @Composable (String, Modifier) -> Unit, width: Dp? = null, visibleLabel: String = label, click: () -> Unit) {
    Action(label, Modifier.height(29.dp).then(if (width != null) Modifier.width(width) else Modifier), tint = Color(0xB8000000), radius = 11.dp, stroke = Color(0xFF344454), content = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { image(icon, Modifier.size(13.dp, 14.dp)); Label(visibleLabel, 11, bold = true) } }, click = click)
}
private fun handleChooseCategory(s: PreviewState, cat: String) {
    val targetCategory = when {
        cat.equals("FAVORİ TV", ignoreCase = true) || cat.equals("Favoriler", ignoreCase = true) -> "Favoriler"
        cat.equals("TÜMÜ", ignoreCase = true) || cat.equals("Tümü", ignoreCase = true) -> "Tümü"
        else -> cat
    }
    s.chooseCategory(targetCategory)
}

@Composable
private fun PlayerSeekBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(20.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(1.5.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(3.dp)
                .background(color, RoundedCornerShape(1.5.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(clamped)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                        .border(0.5.dp, Color(0x66000000), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(label: String, iconContent: @Composable () -> Unit, modifier: Modifier = Modifier, click: () -> Unit) {
    Action(
        label = label,
        modifier = modifier.height(26.dp),
        tint = Color(0x2EFFFFFF),
        radius = 13.dp,
        stroke = Color(0x3DFFFFFF),
        strokeWidth = 1.dp,
        padding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                iconContent()
                Label(label, 10, Color.White, bold = true)
            }
        },
        click = click
    )
}

private fun categorySymbol(cat: String) = when (cat.uppercase()) {
    "ARA" -> "🔍"
    "FAVORİ TV", "FAVORILER", "FAVORİLER" -> "🏆"
    "TÜMÜ", "TUMU" -> "⊞"
    "ULUSAL" -> "🇹🇷"
    "HABER" -> "📰"
    "SPOR" -> "⚽"
    "ÇOCUK", "COCUK" -> "🪁"
    "BELGESEL" -> "🌍"
    "MÜZİK", "MUZIK" -> "🎵"
    "DİNİ", "DINI" -> "🕌"
    "YEREL" -> "📍"
    "KKTC" -> "🇨🇾"
    "AZ", "AZERBAYCAN" -> "🇦🇿"
    "ALMANYA / AVRUPA" -> "🇩🇪"
    "DÜNYA", "DUNYA" -> "🌐"
    "EKONOMİ", "EKONOMI" -> "📈"
    "KURUMLAR" -> "🏛️"
    "DİĞER", "DIGER" -> "📺"
    "ÇOKLU EKRAN", "AYARLAR" -> null
    else -> if (cat.startsWith("7/24")) "🎬" else "📺"
}

@Composable private fun CategoryTab(cat: String, s: PreviewState, image: @Composable (String, Modifier) -> Unit, vertical: Boolean, click: () -> Unit) {
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val shades = when (cat.uppercase()) {
        "ÇOKLU EKRAN" -> listOf(Color(0xFFDA6800), Color(0xFFF4A10B))
        "AYARLAR" -> listOf(Color(0xFF292A2A), Color(0xFF070707))
        "ARA" -> listOf(Color(0xFF247675))
        "FAVORİ TV", "FAVORILER", "FAVORİLER" -> listOf(Color(0xFFB8860B), Color(0xFFE5A910))
        "TÜMÜ", "TUMU" -> listOf(Color(0xFF30495C))
        "ULUSAL" -> listOf(Color(0xFFFAFAFA))
        "HABER" -> listOf(Color(0xFF315D77))
        "SPOR" -> listOf(Color(0xFF1A9B67))
        "ÇOCUK", "COCUK" -> listOf(Color(0xFF7A4FAC), Color(0xFF568AD1))
        "BELGESEL" -> listOf(Color(0xFF455B65))
        "MÜZİK", "MUZIK" -> listOf(Color(0xFFDC4835), Color(0xFFE99A18))
        "DİNİ", "DINI" -> listOf(Color(0xFF1BAE91))
        "YEREL" -> listOf(Color(0xFF87918F))
        "KKTC" -> listOf(Color(0xFFC0392B), Color(0xFFE74C3C))
        "AZ", "AZERBAYCAN" -> listOf(Color(0xFF007A87), Color(0xFF96253F))
        "ALMANYA / AVRUPA" -> listOf(Color(0xFF2C3E50), Color(0xFFD35400))
        "DÜNYA", "DUNYA" -> listOf(Color(0xFF1565C0), Color(0xFF00ACC1))
        "7/24 YOUTUBE" -> listOf(Color(0xFFE0AF0D), Color(0xFFBB7714))
        else -> listOf(Color(0xFF2684A6), Color(0xFF18B59F))
    }
    val shape = if (vertical) RoundedCornerShape(0.dp)
        else if (theme.categoryTabPill) RoundedCornerShape(16.dp)
        else GenericShape { size, _ -> val slant = size.height * .466f; moveTo(slant, 0f); lineTo(size.width, 0f); lineTo(size.width - slant, size.height); lineTo(0f, size.height); close() }
    val tabBrush = Brush.linearGradient(if (shades.size == 1) shades + shades else shades)

    val isFav = cat.equals("FAVORİ TV", ignoreCase = true) || cat.equals("Favoriler", ignoreCase = true)
    val isUlusal = cat.equals("Ulusal", ignoreCase = true)
    val isSelected = s.category == cat ||
        (isFav && (s.category == "Favoriler" || s.category == "FAVORİ TV")) ||
        (cat.equals("TÜMÜ", ignoreCase = true) && (s.category == "Tümü" || s.category == "TÜMÜ")) ||
        (cat.equals("AZ", ignoreCase = true) && (s.category == "AZ" || s.category == "AZERBAYCAN")) ||
        cat.equals(s.category, ignoreCase = true)

    val favBorder = if (isFav && !theme.categoryTabPill) Color(0xFFFFD700) else Color.Transparent
    val selection = if (isSelected) {
        Modifier.border(if (theme.categoryTabPill) 1.5.dp else 2.dp, theme.categoryTabSelectedStroke, shape)
    } else if (favBorder != Color.Transparent) {
        Modifier.border(1.5.dp, favBorder, shape)
    } else {
        Modifier
    }

    val pillBrush = when (theme.name) {
        "Modern" -> Brush.linearGradient(listOf(Color(0xFF102138), Color(0xFF162D4A)))
        "Altın" -> Brush.linearGradient(listOf(Color(0xFF282012), Color(0xFF382D18)))
        "Sinema" -> Brush.linearGradient(listOf(Color(0xFF241114), Color(0xFF38191E)))
        else -> null
    }

    val background = if (vertical && cat in listOf("ÇOKLU EKRAN", "AYARLAR")) Modifier.drawWithCache {
        val radians = 110f * kotlin.math.PI.toFloat() / 180f
        val direction = Offset(kotlin.math.sin(radians), -kotlin.math.cos(radians))
        val halfLength = (size.width * abs(direction.x) + size.height * abs(direction.y)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val gradient = Brush.linearGradient(shades, start = center - direction * halfLength, end = center + direction * halfLength)
        onDrawBehind { drawRect(gradient) }
    } else if (!vertical && theme.categoryTabPill && pillBrush != null) {
        Modifier.background(pillBrush, shape)
    } else Modifier.background(tabBrush, shape)

    val language = LocalLanguage.current
    val label = presentationText(cat, language).let { if (language == "Türkçe") it.replace('i', 'İ').replace('ı', 'I').uppercase() else it.uppercase() }
    val horizontalPadding = if (vertical) 9.dp else if (theme.categoryTabPill) 12.dp else 14.dp
    val textColor = if (isUlusal) {
        Color(0xFFE00010)
    } else if (isFav && !theme.categoryTabPill) {
        Color(0xFFFFD700)
    } else {
        Color.White
    }

    Action(cat, (if (vertical) Modifier.fillMaxWidth().heightIn(min = 30.dp) else Modifier.height(32.dp)).clip(shape).then(background).then(selection), tint = Color.Transparent, radius = 0.dp, strokeWidth = 0.dp, returnFocusKey = if (cat in listOf("ÇOKLU EKRAN", "AYARLAR")) "live:category" else null, padding = PaddingValues(horizontal = horizontalPadding, vertical = 3.dp), content = {
        Box(if (vertical) Modifier.fillMaxWidth() else Modifier) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val symbol = categorySymbol(cat)
                if (symbol != null) BasicText(symbol, Modifier.clearAndSetSemantics { }, style = TextStyle(fontSize = 12.sp))
                else image(if (cat == "ÇOKLU EKRAN") "ikon_coklu" else "ikon_ayar", Modifier.size(12.dp, 13.dp))
                Label(label, 10, textColor, bold = true, maxLines = if (vertical) Int.MAX_VALUE else 1)
            }
        }
    }, click = click)
}

private fun categories(s: PreviewState): List<String> {
    val template = listOf("FAVORİ TV", "TÜMÜ", "Ulusal", "Haber", "Spor", "Çocuk", "Belgesel", "Müzik", "Dini", "Yerel", "KKTC", "AZ")
    val available = s.data.categories.filter { it !in s.data.hiddenCategories && s.data.channels.any { ch -> ch.available && it in ch.groups } }
    val result = mutableListOf<String>()
    for (item in template) {
        if (item == "FAVORİ TV" || item == "TÜMÜ") {
            result.add(item)
        } else {
            val matched = available.any { it.equals(item, ignoreCase = true) || (item == "AZ" && it.equals("AZERBAYCAN", ignoreCase = true)) }
            val channelMatched = s.data.channels.any { ch ->
                ch.available && (ch.groups.any { g -> g.equals(item, ignoreCase = true) || (item == "AZ" && g.equals("AZERBAYCAN", ignoreCase = true)) } ||
                    ch.category.equals(item, ignoreCase = true) || (item == "AZ" && ch.category.equals("AZERBAYCAN", ignoreCase = true)))
            }
            if (matched || channelMatched) {
                result.add(item)
            }
        }
    }
    for (cat in available) {
        val already = template.any { it.equals(cat, ignoreCase = true) || (it == "AZ" && cat.equals("AZERBAYCAN", ignoreCase = true)) }
        if (!already && !cat.equals("Favoriler", true) && !cat.equals("Son İzlenenler", true) && !cat.equals("Tümü", true)) {
            result.add(cat)
        }
    }
    return result
}
@Composable private fun Search(s: PreviewState) {
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    Row(Modifier.fillMaxWidth().height(35.dp).background(Color(0x45D3D3D3)), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(s.query, { s.query = it }, Modifier.weight(1f).focusRequester(focus).padding(horizontal = 10.dp).semantics { contentDescription = "Kategoride ara" }, textStyle = TextStyle(color = Color.White, fontSize = 12.sp), singleLine = true, cursorBrush = SolidColor(amber), decorationBox = { field -> if (s.query.isEmpty()) Label("Kategoride ara", 10, Color(0xFFC3C6CA)); field() })
        Action("×", tint = Color.Transparent, strokeWidth = 0.dp) { s.query = "" }
    }
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
}
@Composable
private fun Channels(s: PreviewState, image: @Composable (String, Modifier) -> Unit, vertical: Boolean, modifier: Modifier) {
    val values = s.visibleData.filtered(s.category, s.query)
    val listState = key(vertical) { rememberLazyListState() }
    LaunchedEffect(s.selected, values) {
        val index = values.indexOfFirst { it.id == s.selected }
        if (!s.searching && s.moving == null && index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 44.dp.toPx() }
    val verticalModifier = if (s.moving == null) modifier.pointerInput(s.moving, swipeThreshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f }, onDragEnd = { if (distance >= swipeThreshold) s.categoryMenu = true }) { change, amount -> change.consume(); distance += amount }
    } else modifier
    if (vertical) LazyColumn(verticalModifier.focusGroup(), state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) { items(values, key = { PreviewCodec.id(it.id) }) { ChannelCard(s, it, image, true) } }
    else LazyRow(modifier.focusGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(values, key = { PreviewCodec.id(it.id) }) { ChannelCard(s, it, image, false) } }
}
@Composable
private fun ChannelCard(s: PreviewState, ch: PreviewChannel, image: @Composable (String, Modifier) -> Unit, vertical: Boolean) {
    val requester = remember { FocusRequester() }
    val keySignal = LocalKeySignal.current
    val number = if (s.category == "Favoriler" && !s.searching) "${s.visibleData.favorites.indexOf(ch.id) + 1}. " else ""
    val isMoving = s.moving == ch.id
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val isSelected = ch.id == s.selected || isMoving
    val strokeColor = if (isMoving) Color(0xFFFFCF37) else theme.cardSelectedStroke
    val strokeWidth = if (isSelected) theme.cardSelectedStrokeWidthDp.dp else 0.dp
    val cardRadius = if (theme.isCardFlat) 0.dp else 8.dp
    val cardTint = if (theme.isCardFlat) Color.Transparent else theme.cardBackground

    var drag by remember { mutableStateOf(0f) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(s.dialog, s.moving, windowFocused, s.searching) { if (windowFocused && s.dialog == null && !s.searching && s.target == ch.id) requester.requestFocus() }
    LaunchedEffect(s.selected) {
        if (windowFocused && s.dialog == null && !s.searching && s.selected == ch.id) requester.requestFocus()
    }
    Action(
        number + ch.name,
        (if (vertical) Modifier.fillMaxWidth().height(38.5.dp) else Modifier.width(69.dp).height(68.dp)).focusRequester(requester)
            .then(if (isMoving) Modifier.pointerInput(ch.id, vertical) { detectDragGestures(onDragStart = { drag = 0f }, onDragEnd = { s.moveFinish() }, onDragCancel = { s.draft = null; s.moving = null }) { change, amount -> change.consume(); drag += if (vertical) amount.y else amount.x; if (abs(drag) > 45) { s.moveBy(if (drag > 0) 1 else -1); drag = 0f } } } else Modifier),
        focusKey = "channel:${PreviewCodec.id(ch.id)}",
        selected = isSelected,
        tint = cardTint,
        radius = cardRadius,
        strokeWidth = strokeWidth,
        stroke = strokeColor,
        focusColor = if (isMoving) Color(0xFFFFCF37) else if (vertical) theme.accentColor else Color.White,
        horizontalFocus = !vertical && !isMoving,
        channelFocus = !isMoving,
        padding = PaddingValues(0.dp),
        long = { s.target = ch.id; s.dialog = if (s.category == "Favoriler") "favorite" else "channel" },
        extraKey = { e ->
            val delta = channelArrowDelta(vertical, e.key)
            if (isMoving && e.type == KeyEventType.KeyDown && e.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) { s.moveBy(if (e.key in listOf(Key.DirectionUp, Key.DirectionLeft)) -1 else 1); true }
            else if (!isMoving && delta != null && s.panel && s.dialog == null) {
                if (e.type == KeyEventType.KeyDown && !keySignal(e).repeat && s.normalLiveControls) s.stepChannel(delta)
                true
            }
            else if (vertical && !isMoving && e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) { s.categoryMenu = true; true } else false
        },
        content = {
            if (vertical) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(if (ch.id == s.selected) theme.accentColor else Color.Transparent))
                    image(ch.logo, Modifier.size(30.dp))
                    BasicText(
                        number + ch.name,
                        Modifier.weight(1f),
                        style = TextStyle(color = if (ch.id == s.selected) Color.White else Color(0xFFDEDEDE), fontSize = 10.sp, fontWeight = if (ch.id == s.selected) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ch.id in s.data.favorites) image("yildiz", Modifier.size(10.dp))
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    image(ch.logo, Modifier.align(Alignment.TopCenter).offset(y = 5.dp).size(52.dp, 40.dp))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
                        BasicText(
                            number + ch.name,
                            style = TextStyle(color = if (ch.id == s.selected) Color.White else Color(0xFFE8E8E8), fontSize = 8.sp, fontWeight = if (ch.id == s.selected) FontWeight.Bold else FontWeight.Normal),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (ch.id in s.data.favorites) {
                        image("yildiz", Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 5.dp).size(10.dp))
                    }
                }
            }
        }
    ) { if (isMoving) s.moveFinish() else { requester.requestFocus(); s.select(ch.id) } }
}

private val sections = listOf("Kanallar Ve Listeler", "Görünüm", "Oynatma", "Uygulama", "Yedekleme Ve Aktarım", "Yardım Ve Hakkında")
private val sectionsEn = listOf("Channels And Lists", "Appearance", "Playback", "Application", "Backup And Transfer", "Help And About")
private val choices = mapOf(
    "list" to listOf("Yatay (Alt)", "Dikey (Sidebar)"),
    "position" to listOf("Sol", "Orta", "Sağ"),
    "size" to listOf("Küçük", "Normal", "Büyük"),
    "language" to listOf("Türkçe", "English", "Deutsch"),
    "quality" to listOf("360p", "480p", "720p HD", "1080p FHD", "4K UHD"),
    "format" to LiveFormatOptions,
    "audio" to listOf("Türkçe", "English", "Deutsch", "Français", "Español", "العربية", "Русский", "Auto"),
    "subtitle" to listOf("Kapalı", "Türkçe", "English", "Deutsch", "Français", "Español", "العربية", "Otomatik (İlk Bulunan)"),
    "theme" to listOf("Klasik", "Modern", "Altın", "Sinema"),
    "sleepTimer" to listOf("Kapalı", "15 dk", "30 dk", "45 dk", "60 dk", "90 dk", "120 dk"),
    "subtitleSize" to listOf("Küçük", "Normal", "Büyük", "Çok Büyük"),
    "subtitleColor" to listOf("Beyaz", "Sarı", "Yeşil", "Camgöbeği"),
    "subtitleBgOpacity" to listOf("%0 (Saydam)", "%25", "%50", "%75", "%100 (Siyah)"),
)
private val titles = mapOf(
    "list" to "Liste Konumu",
    "opacity" to "Liste Şeffaflığı",
    "position" to "Kanal Bilgisi Konum",
    "size" to "Kanal Bilgisi Boyut",
    "language" to "Uygulama Dili",
    "quality" to "Görüntü Kalitesi",
    "format" to "Varsayılan Görüntü Formatı",
    "audio" to "Tercih Edilen Ses Dili",
    "subtitle" to "Altyazı Varsayılanı",
    "theme" to "Tema Seçimi",
    "sleepTimer" to "Uyku Zamanlayıcısı",
    "subtitleSize" to "Altyazı Boyutu",
    "subtitleColor" to "Altyazı Rengi",
    "subtitleBgOpacity" to "Altyazı Arka Plan Opaklığı",
)
private val sectionIcons = listOf("ikon_kaynak", "ikon_gorunum", "ikon_oynatma", "ikon_app", "ikon_export", "ikon_hakkinda")
private val settingIcons = mapOf(
    "list" to "ikon_list",
    "opacity" to "ikon_opacity",
    "position" to "ikon_position",
    "size" to "ikon_size",
    "language" to "ikon_language",
    "quality" to "ikon_quality",
    "format" to "ikon_format",
    "audio" to "ikon_ses",
    "subtitle" to "ikon_subtitle",
    "theme" to "ikon_gorunum",
    "sleepTimer" to "ikon_saat",
    "subtitleSize" to "ikon_size",
    "subtitleColor" to "ikon_gorunum",
    "subtitleBgOpacity" to "ikon_opacity",
)
@Composable private fun Settings(s: PreviewState, image: @Composable (String, Modifier) -> Unit, appVersionName: String) {
    val contentScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().background(ink)) {
        val compact = maxWidth < 600.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) Column(Modifier.width(144.dp).fillMaxHeight().background(Color(0xFF0F1F3A))) {
                Row(Modifier.height(74.dp).padding(horizontal = 19.dp), verticalAlignment = Alignment.CenterVertically) { Label("Ayarlar", 18, modifier = Modifier.weight(1f), bold = true); Action("×", tint = Color.Transparent, strokeWidth = 0.dp, click = s::back) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    sections.forEachIndexed { i, title -> Action(title, Modifier.fillMaxWidth().height(47.dp), focusKey = "settings:section:$i", tint = Color.Transparent, selected = s.section == i, selectedTint = Color(0xFF1A2D4F), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 9.dp), content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { image(sectionIcons[i], Modifier.size(22.dp, 23.dp)); Label(s.text(title, sectionsEn[i]), 11, modifier = Modifier.weight(1f), bold = true, lineHeight = 14f, maxLines = 2) }
                    }) { s.dismiss(); s.cancelOpacity(); s.section = i } }
                }
                Column(Modifier.padding(13.dp)) { Label("Beleş Tivi Clone", 9, Color(0xFF62718A)); Label("← GERİ DÖN", 9, Color(0xFF62718A), Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (compact) LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(6) { i -> Action(sections[i], focusKey = "settings:section:$i", selected = s.section == i) { s.section = i } }; item { Action("×", click = s::back) } }
                if (s.dialog in sourceFormKinds || s.dialog == "categories") CompositionLocalProvider(LocalModalLayer provides true) {
                    if (s.dialog == "categories") CategoryManagement(s, image, Modifier.weight(1f)) else SourceForm(s, image, Modifier.weight(1f))
                } else Column(Modifier.weight(1f).verticalScroll(contentScroll).padding(horizontal = 16.dp, vertical = 38.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (s.section != 1) Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(3.dp).height(16.dp).background(amber)); Label(settingsSectionHeading(s.section), 10, Color(0xFF94A2B7), Modifier.padding(start = 10.dp), true) }
                    when (s.section) {
                        0 -> {
                            SettingsGroup("LİSTEM")
                            SettingTile("Aktif Listem", "${s.activeSourceKindBadge} · ${s.activeCount} içerik", "ikon_tv", image, s.profileName) { s.sourceForm("activeSources") }
                            SettingTile("Ek Liste", s.data.library?.extra?.name ?: "Ana kaynağa bağlı tek ek liste", "ikon_list", image) { s.sourceForm("supplement") }
                            SettingTile("Listeyi Güncelle", "Son başarılı snapshot korunur", "ikon_canli", image) { s.refreshSource() }
                            SettingTile("Varsayılana Dön", "Klonun trlist kaynağı; favoriler korunur", "ikon_tv", image) { s.dialog = "defaultSource" }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("KENDİ LİSTENİ EKLE")
                            SettingTile("M3U URL", "Özel liste adresi ile bağlan", "ikon_url", image) { s.sourceForm("m3u") }
                            SettingTile("Xtream Codes", "Sunucu bilgileri ile bağlantı", "ikon_xtream", image) { s.sourceForm("xtream") }
                            SettingTile("Stalker / MAC", "Portal adresi ve kayıtlı MAC ile bağlan", "ikon_sunucu", image) { s.sourceForm("stalker") }
                            SettingTile("Dosyadan Yükle", "M3U dosyası seç", "ikon_dosya", image) { s.dialog = "file" }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("ÜLKE LİSTELERİ")
                            SettingTile("Ülke Listeleri", s.countryListsSubtitle, "kategori_dunya", image) { s.sourceForm("countries") }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("EPG (PROGRAM REHBERİ)")
                            SettingTile(
                                "XMLTV EPG URL",
                                s.data.pref("epgUrl").ifBlank { "Yerleşik örnek EPG (epg_sample.xml) aktif" },
                                "ikon_saat",
                                image
                            ) { s.sourceForm("epg") }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("KANAL DÜZENİ")
                            SettingTile("Kategorileri Yönet", "Sıralama ve görünürlük", "ikon_category", image) { s.dialog = "categories" }
                        }
                        1 -> {
                            SettingsGroup("UYGULAMA TEMASI"); SettingCard(s, "theme", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("KANAL LİSTESİ"); SettingCard(s, "list", image); SettingCard(s, "opacity", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("KANAL BİLGİSİ"); SettingCard(s, "position", image); SettingCard(s, "size", image)
                        }
                        2 -> {
                            SettingsGroup("ZAMANLAYICI"); SettingCard(s, "sleepTimer", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("VİDEO"); SettingCard(s, "quality", image); SettingCard(s, "format", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("SES & ALTYAZI")
                            SettingCard(s, "audio", image); SettingCard(s, "subtitle", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("ALTYAZI GÖRÜNÜMÜ")
                            SettingCard(s, "subtitleSize", image)
                            SettingCard(s, "subtitleColor", image)
                            SettingCard(s, "subtitleBgOpacity", image)
                            SubtitlePreviewBox(s)
                            Label(s.message("Kalite bir üst sınırdır. Bilgi alanında işlenen format ve gerçek ses/altyazı seçimi gösterilir; kaynakta olmayan dil uygulanamaz.", "Quality is a ceiling. Info shows the input format and actual audio/subtitle selection; unavailable languages cannot be applied.", "Qualität ist eine Obergrenze. Info zeigt das Eingabeformat und die tatsächliche Ton-/Untertitelauswahl; fehlende Sprachen sind nicht verfügbar."), 10, Color(0xFF69798F), Modifier.padding(top = 12.dp))
                        }
                        3 -> {
                            SettingsGroup("DİL")
                            SettingCard(s, "language", image)
                            Spacer(Modifier.height(20.dp)); SettingsGroup("BAŞLATMA & DAVRANIŞ")
                            SettingTile("Favorilerden Başlat", "Açılışta doğrudan favori kanallardan birini aç", "kategori_favoriler", image, s.data.pref("startupFavorites"), true) { s.pref("startupFavorites", if (s.data.pref("startupFavorites") == "Açık") "Kapalı" else "Açık") }
                            SettingTile("Açılışta Son İzlenen Kanala Git", "Uygulama açılış tercihi", "ikon_startup", image, s.data.pref("startup"), true) { s.pref("startup", if (s.data.pref("startup") == "Açık") "Kapalı" else "Açık") }
                            SettingTile("Arka Planda Sesi Sürdür", "Uygulama arka plandayken ses çalmaya devam eder", "ikon_ses", image, s.data.pref("backgroundAudio"), true) { s.pref("backgroundAudio", if (s.data.pref("backgroundAudio") == "Açık") "Kapalı" else "Açık") }
                            SettingTile("Resim İçinde Resim (PiP)", "Home veya arka plana geçişte otomatik PiP", "ikon_coklu", image, s.data.pref("autoPip"), true) { s.pref("autoPip", if (s.data.pref("autoPip") == "Açık") "Kapalı" else "Açık") }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("GEÇMİŞ")
                            SettingTile("İzleme Geçmişi", "Son izlenen kanalları göster", "kategori_gecmis", image, s.data.pref("history"), true) { s.pref("history", if (s.data.pref("history") == "Açık") "Kapalı" else "Açık") }
                            SettingTile("İzleme Geçmişini Temizle", "${s.data.history.size} kayıt", "ikon_cop", image, danger = true) { s.dialog = "clear" }
                        }
                        4 -> {
                            SettingsGroup("TELEFONDAN AKTAR")
                            SettingTile("Telefondan Aktar", "Yerel ağ üzerinden M3U / Xtream / Stalker gönder", "ikon_export", image) { s.openWebTransfer() }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("FAVORİLER")
                            SettingTile("Favorileri Dışa Aktar", "Favori listesini yedekle", "ikon_export", image) { s.dialog = "export" }
                            SettingTile("Favorileri İçe Aktar", "Yedekten geri yükle", "ikon_import", image) { s.dialog = "import" }
                        }
                        5 -> {
                            SettingsGroup("UYGULAMA")
                            SettingTile("Beleş TiVi Clone", "Android & Google TV · $appVersionName", "ikon_hakkinda", image) { }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("AĞ VE YAYIN KONTROLÜ")
                            SettingTile("Bağlantıyı Kontrol Et", "Ağ, DNS ve canlı yayın erişim testi", "ikon_canli", image) { s.openNetworkDiagnostics() }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("DESTEK")
                            SettingTile("Destek", "Yardım ve iletişim", "ikon_contact", image) { s.notice = "Destek ve iletişim kanalları yakında aktif olacaktır." }
                            SettingTile("Telegram", "Topluluk", "ikon_telegram", image) { s.notice = "Telegram topluluk grubu yakında eklenecektir." }
                            Spacer(Modifier.height(20.dp)); SettingsGroup("DİĞER İŞLEMLER")
                            SettingTile("Önbelleği Temizle", "Liste güncellemeden ayrı işlem", "ikon_cache", image) { s.dialog = "cache" }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(51.dp).background(Color(0xFF0F1F3A)).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(8.dp).background(Color(0xFF0CBA91))); Label(s.profileName, 10, bold = true); Label("${s.activeCount} içerik", 10, Color(0xFF94A1B5)); Spacer(Modifier.weight(1f)); Label(if (s.busy) "İşlem sürüyor…" else "Yerel kayıt", 10, Color(0xFF94A1B5))
                }
            }
        }
    }
}
@Composable private fun SettingTile(title: String, subtitle: String, icon: String, image: @Composable (String, Modifier) -> Unit, value: String = "", toggle: Boolean = false, danger: Boolean = false, visibleTitle: String = title, click: () -> Unit) {
    Action(title, Modifier.fillMaxWidth().height(58.dp), radius = 10.dp, strokeWidth = 0.dp, focusKey = "settings:$title", padding = PaddingValues(horizontal = 20.dp), content = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            image(icon, Modifier.size(21.dp, 23.dp))
            Column(Modifier.weight(1f)) { Label(visibleTitle, 12, if (danger) Color(0xFFF44747) else Color.White, bold = true, lineHeight = 18f, maxLines = 1); if (subtitle.isNotEmpty()) Label(subtitle, 10, Color(0xFF69798F), lineHeight = 16f, maxLines = 1) }
            if (toggle) Box(Modifier.defaultMinSize(minWidth = 55.dp).height(28.dp).background(if (value == "Açık") Color(0xFF0ABB91) else Color(0xFF68798F), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Label("• $value", 10, bold = true) }
            else { if (value.isNotEmpty()) Label(value, 10, amber, bold = true); Label("›", 12, Color(0xFF60728E)) }
        }
    }, click = click)
}
@Composable private fun SettingCard(s: PreviewState, key: String, image: @Composable (String, Modifier) -> Unit) {
    if (key == "opacity") OpacityControl(s, image)
    else SettingTile(titles.getValue(key), "", settingIcons.getValue(key), image, s.data.pref(key), visibleTitle = when (key) { "position" -> "Konum"; "size" -> "Boyut"; "subtitleSize" -> "Boyut"; "subtitleColor" -> "Renk"; "subtitleBgOpacity" -> "Arka Plan Opaklığı"; "sleepTimer" -> "Süre"; else -> titles.getValue(key) }) { s.dialog = key }
}

@Composable
private fun SubtitlePreviewBox(s: PreviewState) {
    val sizePref = s.data.pref("subtitleSize")
    val colorPref = s.data.pref("subtitleColor")
    val opacityPref = s.data.pref("subtitleBgOpacity")

    val textColor = when (colorPref) {
        "Sarı" -> Color(0xFFFFEB3B)
        "Yeşil" -> Color(0xFF4CAF50)
        "Camgöbeği" -> Color(0xFF00E5FF)
        else -> Color.White
    }
    val fontSize = when (sizePref) {
        "Küçük" -> 11.sp
        "Büyük" -> 17.sp
        "Çok Büyük" -> 21.sp
        else -> 14.sp
    }
    val bgAlpha = when (opacityPref) {
        "%0", "%0 (Saydam)" -> 0.0f
        "%25" -> 0.25f
        "%50" -> 0.50f
        "%100", "%100 (Siyah)" -> 1.0f
        else -> 0.75f
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFF0D1726), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF223651), RoundedCornerShape(10.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("Canlı Altyazı Önizlemesi", 10, Color(0xFF86C9EF), bold = true)
            Label("$sizePref · $colorPref · $opacityPref", 9, Color(0xFF69798F))
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(54.dp).background(Color(0xFF05080E), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.background(Color.Black.copy(alpha = bgAlpha), RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    "Beleş TiVi Canlı Yayın Örnek Altyazı",
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif
                    )
                )
            }
        }
    }
}

@Composable
private fun ChannelMenu(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val ch = s.data.channels.find { it.id == s.target } ?: return
    val first = remember { FocusRequester() }
    val favorite = s.dialog == "favorite"
    val contentDensity = LocalDensity.current
    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
        Column(Modifier.width(240.5.dp).background(Color(0xF5121212), RoundedCornerShape(13.dp)).border(1.dp, Color(0xFF414141), RoundedCornerShape(13.dp)).clip(RoundedCornerShape(13.dp)).focusGroup().semantics { paneTitle = ch.name }) {
            Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                image(ch.logo, Modifier.size(29.dp, 30.dp)); Column(Modifier.weight(1f)) { Label(ch.name, 11, bold = true); Label(ch.category, 9, Color(0xFF9A9A9A)) }
            }
            @Composable fun row(label: String, icon: String, modifier: Modifier = Modifier, dismiss: Boolean = false, click: () -> Unit) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF303030)))
                Action(label, modifier.fillMaxWidth().height(if (dismiss) 33.dp else 44.5.dp), enabled = !s.busy, tint = Color.Transparent, radius = 0.dp, strokeWidth = 0.dp, focusColor = Color(0xFFFFD600), horizontalFocus = true, padding = PaddingValues(horizontal = if (dismiss) 5.dp else 15.dp), content = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (dismiss) Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(9.dp)) {
                        if (icon.startsWith(":")) Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { Label(icon.drop(1), 22, Color(0xFFB8B8B8)) }
                        else image(icon, Modifier.size(if (dismiss) 9.dp else 26.dp))
                        Label(label, 10, if (dismiss) Color(0xFF818181) else Color.White, if (dismiss) Modifier else Modifier.weight(1f), bold = !dismiss)
                        if (!dismiss) Label("›", 15, Color(0xFF555555))
                    }
                }, click = click)
            }
            row(if (ch.id in s.data.favorites) "Favorilerden kaldır" else "Favorilere ekle", if (ch.id in s.data.favorites) "ikon_menu_favoriden_cikar" else "ikon_menu_favori", Modifier.focusRequester(first)) { s.channelChange(s.data.toggleFavorite(ch.id)) }
            if (favorite) {
                row("Sırasını değiştir…", ":↔", click = s::moveStart)
                row("En başa taşı", ":⇤") { s.commit(s.data.move(ch.id, 0)) { s.dismiss() } }
                row("En sona taşı", ":⇥") { s.commit(s.data.move(ch.id, s.data.favorites.lastIndex)) { s.dismiss() } }
            } else {
                row("Kanalı gizle", "ikon_menu_gizle") { s.channelChange(s.data.copy(hidden = s.data.hidden + ch.id)) }
                row("Gizlenen kanalları geri getir", "ikon_menu_gerigetir") { s.commit(s.data.copy(hidden = emptySet())) { s.dismiss() } }
            }
            row("Vazgeç", "ikon_menu_vazgec", dismiss = true, click = s::dismiss)
        }
        LaunchedEffect(Unit) { first.requestFocus() }
        }
    }
}
@Composable
private fun Confirmation(s: PreviewState, image: @Composable (String, Modifier) -> Unit, kind: String) {
    val contentDensity = LocalDensity.current
    val cancel = remember { FocusRequester() }
    val warning = kind == "warning"
    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Box(Modifier.width(470.dp).height(if (warning) 250.dp else 140.dp).background(card).padding(19.dp).focusGroup()) {
                if (warning) Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    image("ikon_uyari", Modifier.size(44.dp)); Spacer(Modifier.height(17.dp)); Label("Performans Uyarısı", 17, bold = true)
                    Spacer(Modifier.height(14.dp)); Label("Çoklu ekran cihazınızı ve internetinizi yoğun kullanır. Düşük donanımlı cihazlarda donma yaşanabilir.", 11, Color(0xFF9AA8BC))
                } else {
                    Label(if (kind == "clear") "Geçmişi Temizle?" else "Önbelleği Temizle?", 16, bold = true)
                    Label(if (kind == "clear") "${s.data.history.size} geçmiş kaydı silinecek. Favoriler korunur." else "Yalnız indirilen logo önbelleği temizlenir. Kaynaklar ve tercihler korunur.", 11, Color(0xFF9AA8BC), Modifier.offset(y = 31.dp))
                }
                Row(Modifier.align(Alignment.BottomEnd).then(if (warning) Modifier.fillMaxWidth() else Modifier), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("İPTAL", (if (warning) Modifier.weight(1f) else Modifier.width(73.dp)).height(if (warning) 38.dp else 40.dp).focusRequester(cancel), tint = Color.Transparent, radius = 9.dp, strokeWidth = 0.dp, content = { Label("İPTAL", 11, Color(0xFF9AA8BC), bold = true) }, click = s::dismiss)
                    val label = when (kind) { "clear" -> "EVET, SİL"; "cache" -> "TEMİZLE"; else -> "DEVAM ET" }
                    Action(label, (if (warning) Modifier.weight(1f) else Modifier.width(87.dp)).height(if (warning) 38.dp else 40.dp), tint = if (warning) amber else Color.Transparent, enabled = !s.busy, radius = 9.dp, strokeWidth = 0.dp, content = { Label(label, 11, if (warning) Color.White else if (kind == "clear") Color(0xFFF44747) else amber, bold = true) }) {
                        when (kind) { "clear" -> s.clearHistory(); "warning" -> s.dialog = "layout"; else -> { s.requestDocument("cache") } }
                    }
                }
            }
            LaunchedEffect(Unit) { cancel.requestFocus() }
        }
    }
}
@Composable
private fun ExitDialog(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val stayRequester = remember { FocusRequester() }
    val contentDensity = LocalDensity.current
    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Box(Modifier.width(420.dp).background(card, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF344454), RoundedCornerShape(12.dp)).padding(22.dp).focusGroup().semantics { paneTitle = "Çıkış Onayı" }) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    image("ikon_cikis", Modifier.size(36.dp))
                    Label("Çıkmak İstiyor Musunuz?", 16, bold = true, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action(
                            label = "HAYIR, DEVAM",
                            modifier = Modifier.weight(1f).height(42.dp).focusRequester(stayRequester),
                            tint = Color(0xFF1E3557),
                            radius = 8.dp,
                            strokeWidth = 0.dp,
                            content = { Label("HAYIR, DEVAM", 11, Color.White, bold = true) },
                            click = s::dismiss
                        )
                        Action(
                            label = "EVET, ÇIKIŞ",
                            modifier = Modifier.weight(1f).height(42.dp),
                            tint = Color(0xFFD8222A),
                            radius = 8.dp,
                            strokeWidth = 0.dp,
                            content = { Label("EVET, ÇIKIŞ", 11, Color.White, bold = true) },
                            click = { s.exitRequested = true }
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { stayRequester.requestFocus() }
        }
    }
}
@Composable
private fun Modal(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val kind = s.dialog ?: return
    if (kind == "exit") { ExitDialog(s, image); return }
    if (kind == "webTransfer") { WebTransferDialog(s, image); return }
    if (kind == "networkDiagnostics") { NetworkDiagnosticsDialog(s, image); return }
    if (kind in sourceFormKinds || kind == "categories") return
    if (kind in choices) { OptionPicker(s, kind, choices.getValue(kind), if (kind == "format" && s.page == "live") "GÖRÜNTÜ ORANI" else optionHeading(kind)); return }
    if (kind == "warning") { MultiWarning(s, image); return }
    if (kind == "layout") { LayoutChooser(s, image); return }
    if (kind == "assign") { MultiChannelPicker(s, image); return }
    if (kind == "savedLayouts") { SavedLayoutsModal(s, image); return }
    if (kind == "saveMultiLayout") { SaveMultiLayoutDialog(s, image); return }
    if (kind == "favorite" || kind == "channel") { ChannelMenu(s, image); return }
    if (kind in listOf("clear", "cache", "warning")) { Confirmation(s, image, kind); return }
    val first = remember(kind) { FocusRequester() }
    val contentDensity = LocalDensity.current
    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
        Column(Modifier.padding(20.dp).widthIn(max = if (kind == "categories") 620.dp else 470.dp).fillMaxWidth().heightIn(max = 570.dp).background(card, RoundedCornerShape(if (kind == "quality") 10.dp else 0.dp)).verticalScroll(rememberScrollState()).padding(20.dp).focusGroup().semantics { paneTitle = titles[kind] ?: kind }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Action("×", Modifier.align(Alignment.End).focusRequester(first), tint = Color.Transparent, strokeWidth = 0.dp, click = s::dismiss)
            Label(titles[kind] ?: when (kind) { "sourceMenu" -> s.message("Liste İşlemleri", "List Actions", "Listenaktionen"); "deleteSource" -> s.message("Kaydı Sil?", "Delete Record?", "Eintrag löschen?"); "defaultSource" -> s.message("Varsayılana Dön?", "Restore Default?", "Standard wiederherstellen?"); "replaceExtra" -> s.message("Ek Listeyi Değiştir?", "Replace Extra List?", "Zusatzliste ersetzen?"); "removeExtra" -> s.message("Ek Listeyi Kaldır?", "Remove Extra List?", "Zusatzliste entfernen?"); "assign" -> s.message("Kanal Seç", "Choose Channel", "Kanal wählen"); "m3u" -> "M3U URL İle"; "supplement" -> "Ek Liste"; "xtream" -> "Xtream Codes"; "file" -> "Dosyadan Yükle"; "export" -> "Favorileri Dışa Aktar"; "import" -> "Favorileri İçe Aktar"; "layout" -> "Çoklu Ekran Düzeni"; "categories" -> "Kategorileri Yönet"; "sources" -> "Kayıtlı Listelerim"; else -> s.text("Önizleme", "Preview") }, if (kind in choices) 9 else 16, color = Color(0xFF94A2B7), modifier = if (kind == "layout") Modifier.align(Alignment.CenterHorizontally) else Modifier, bold = true)
            when {
                kind in choices -> choices.getValue(kind).forEach { value -> Action(value, Modifier.fillMaxWidth().height(44.5.dp), tint = Color.Transparent, strokeWidth = 0.dp, focusColor = amber, enabled = !s.busy, content = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Label(value, 12, modifier = Modifier.weight(1f), bold = true); if (s.data.pref(kind) == value) Label("✓", 20, amber) } }) { s.pref(kind, value) } }
                kind == "clear" -> { Label("${s.data.history.size} geçmiş kaydı silinecek. Favoriler korunur.", 11, Color(0xFF98A5B8), Modifier.padding(vertical = 9.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Action("İPTAL", Modifier.height(40.dp), tint = Color.Transparent, click = s::dismiss); Action("EVET, SİL", Modifier.height(40.dp), enabled = !s.busy, tint = Color.Transparent, content = { Label("EVET, SİL", 11, Color(0xFFFF4B48), bold = true) }) { s.clearHistory() } } }
                kind == "warning" -> { image("ikon_uyari", Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(44.dp)); Label("Çoklu ekran modu cihazınızın işlemcisini ve internetinizi yoğun kullanır. Eski veya düşük donanımlı cihazlarda donma yaşanabilir. Devam etmek istiyor musunuz?"); Action("İPTAL", Modifier.fillMaxWidth(), click = s::dismiss); Action("DEVAM ET", Modifier.fillMaxWidth(), tint = amber) { s.dialog = "layout" } }
                kind == "layout" -> listOf(2, 4).forEach { count -> Action("$count Ekran", Modifier.fillMaxWidth().height(if (count == 2) 106.dp else 140.dp), tint = Color.Transparent, strokeWidth = 0.dp, content = { Column(horizontalAlignment = Alignment.CenterHorizontally) { image(if (count == 2) "ikon_iki" else "ikon_dort", Modifier.size(68.dp, if (count == 2) 33.dp else 66.dp)); Label("$count Ekran", 12, bold = true) } }) { s.startMulti(count) } }
                kind == "assign" -> { Action("Paneli Kapat", Modifier.fillMaxWidth()) { s.multi?.let { it.slots[it.selected] = null; s.multiRevision++ }; s.dismiss() }; s.data.channels.filter { it.available && it.id !in s.data.hidden }.forEach { ch -> Action(ch.name, Modifier.fillMaxWidth()) { s.multi!!.slots[s.multi!!.selected] = ch.id; s.multiRevision++; s.dismiss() } } }
                kind == "categories" -> {
                    s.data.categories.forEachIndexed { i, cat -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Action(cat, Modifier.weight(1f), selected = cat !in s.data.hiddenCategories, enabled = !s.busy) { s.commit(s.data.copy(hiddenCategories = if (cat in s.data.hiddenCategories) s.data.hiddenCategories - cat else s.data.hiddenCategories + cat)) }
                        Action("↑", enabled = i > 0 && !s.busy) { val order = s.data.categories.toMutableList(); order.removeAt(i); order.add(i - 1, cat); s.commit(s.data.copy(categories = order)) }
                        Action("↓", enabled = i < s.data.categories.lastIndex && !s.busy) { val order = s.data.categories.toMutableList(); order.removeAt(i); order.add(i + 1, cat); s.commit(s.data.copy(categories = order)) }
                    } }
                    Action("Tümünü göster", Modifier.fillMaxWidth()) { s.commit(s.data.copy(hiddenCategories = emptySet())) }
                    Action("Tümünü gizle", Modifier.fillMaxWidth()) { s.commit(s.data.copy(hiddenCategories = s.data.categories.toSet())) }
                }
                kind == "sources" -> s.savedSources.forEachIndexed { i, source -> Action(source.name, Modifier.fillMaxWidth()) { s.editing = i; s.dialog = "sourceMenu" } }
                kind == "sourceMenu" -> {
                    Action(s.message("DÜZENLE", "EDIT", "BEARBEITEN"), Modifier.fillMaxWidth()) { s.sourceForm("m3u", s.editing) }
                    Action(s.message("LİSTEYİ YÜKLE", "LOAD LIST", "LISTE LADEN"), Modifier.fillMaxWidth()) { s.editing?.let(s::loadSource) }
                    Action(s.message("SİL", "DELETE", "LÖSCHEN"), Modifier.fillMaxWidth()) { s.dialog = "deleteSource" }
                }
                kind == "deleteSource" -> {
                    Label(s.message("Seçilen ana kayıt ve ona bağlı ek liste ayarı kaldırılacak. Aktif kayıt silinemez; önce başka kaynak seçin. Favoriler/geçmiş korunur.", "The selected main record and its extra list setting will be removed. Select another source before deleting an active record. Favorites/history are preserved.", "Haupteintrag und zugehörige Zusatzliste werden entfernt. Aktive Quelle zuerst wechseln. Favoriten/Verlauf bleiben erhalten."))
                    Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), click = s::dismiss)
                    Action(s.message("EVET, SİL", "YES, DELETE", "JA, LÖSCHEN"), Modifier.fillMaxWidth(), enabled = !s.busy, click = s::deleteSource)
                }
                kind == "defaultSource" -> {
                    Label(s.message("Etkin kaynak klonun trlist listesine dönecek. Kayıtlı listeler, favoriler ve tercihler korunur.", "Switch to the clone's trlist source. Saved lists, favorites and preferences are preserved.", "Zur trlist-Quelle wechseln. Listen, Favoriten und Einstellungen bleiben erhalten."))
                    Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), click = s::dismiss)
                    Action(s.message("DEVAM ET", "CONTINUE", "WEITER"), Modifier.fillMaxWidth(), enabled = !s.busy, click = s::restoreDefault)
                }
                kind == "replaceExtra" || kind == "removeExtra" -> {
                    Label(s.message("Bu ana kaynağın ek listesi değişecek/kaldırılacak. Yalnız ek listede olan yayın durur; başka yayın otomatik başlamaz. Favori ve sıra kayıtları korunur.", "Replace/remove this main source's extra list. Streams available only there stop; no other channel starts automatically. Favorites and order are preserved.", "Zusatzliste dieser Hauptquelle ersetzen/entfernen. Nur dort verfügbare Streams stoppen. Favoriten und Reihenfolge bleiben erhalten."))
                    Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth()) { s.dialog = "supplement" }
                    Action(s.message("DEVAM ET", "CONTINUE", "WEITER"), Modifier.fillMaxWidth(), enabled = !s.busy) { if (kind == "removeExtra") s.removeExtra() else { s.dialog = "supplement"; s.saveSource() } }
                }
                kind == "export" || kind == "import" -> {
                    Label(s.message("Klon favori yedeği v1: kanal/kaynak kimliği ve sıra. Mevcut favorilerin sırası korunur; eksikler sona eklenir. Farklı profil ve desteklenmeyen sürüm reddedilir. URL/parola içermez.", "Clone favorite backup v1: channel/source IDs and order. Existing order is preserved; missing favorites append. Different profiles and unsupported versions are rejected. No URL/password.", "Favoritensicherung v1: Kanal-/Quellen-IDs und Reihenfolge. Fehlende Favoriten werden angehängt. Fremde Profile/Versionen werden abgelehnt. Keine URL/Passwörter."))
                    Action(s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN"), Modifier.fillMaxWidth(), enabled = !s.busy) { s.requestDocument(kind) }
                    Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), click = s::dismiss)
                }
            }
            if (s.busy) { Label(s.message("İşlem sürüyor…", "Working…", "Vorgang läuft…"), 13, Color(0xFFFFC17C)); Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), enabled = !s.committingSource, click = s::cancelSource) }
        }
        LaunchedEffect(kind) { first.requestFocus() }
        }
    }
}
@Composable
private fun Multi(s: PreviewState, image: @Composable (String, Modifier) -> Unit, video: @Composable (PreviewChannel?, Boolean, Boolean, Int, String, PlaybackPreferences, (String) -> Unit, Modifier) -> Unit) {
    val m = s.multi ?: return
    val revision = s.multiRevision
    val multiPrefs = remember(
        s.data.pref("quality"), s.data.pref("audio"), s.data.pref("subtitle"),
        s.data.pref("subtitleSize"), s.data.pref("subtitleColor"), s.data.pref("subtitleBgOpacity"), s.data.pref("backgroundAudio")
    ) {
        PlaybackPreferences(
            s.data.pref("quality"), s.data.pref("audio"), s.data.pref("subtitle"),
            s.data.pref("subtitleSize"), s.data.pref("subtitleColor"), s.data.pref("subtitleBgOpacity"), s.data.pref("backgroundAudio")
        )
    }

    @Composable
    fun SlotCell(index: Int, modifier: Modifier) {
        val ch = s.data.channels.find { it.id == m.slots.getOrNull(index) }
        val isFocusedSlot = index == m.selected
        val borderWidth = if (isFocusedSlot) 4.dp else 1.5.dp
        val borderColor = if (isFocusedSlot) Color(0xFFFFD600) else Color(0xFF284266)
        Box(
            modifier
                .border(borderWidth, borderColor)
                .background(Color.Black)
        ) {
            if (playbackUri(ch) != null) {
                key(index, m.mode.code) {
                    video(
                        ch,
                        false,
                        s.muted || !isFocusedSlot,
                        s.retry,
                        if (m.fit[index]) "Sığdır (Fit)" else "Orijinal (Panel)",
                        multiPrefs,
                        {},
                        Modifier.fillMaxSize().padding(borderWidth)
                    )
                }
            }
            Action(
                label = ch?.name ?: "+ Kanal Ekle",
                modifier = Modifier.fillMaxSize(),
                focusKey = "multi:slot:$index",
                selected = isFocusedSlot,
                tint = Color.Transparent,
                selectedTint = Color.Transparent,
                radius = 0.dp,
                strokeWidth = 0.dp,
                focusColor = Color(0xFFFFD600),
                onFocused = {
                    if (m.selected != index) {
                        m.selected = index
                        s.multiRevision++
                    }
                },
                long = {
                    if (ch != null) {
                        m.fit[index] = !m.fit[index]
                        s.multiRevision++
                        s.notice = if (m.fit[index]) "Ekrana Sığdır" else "Orijinal"
                    }
                },
                double = {
                    if (ch != null) {
                        m.fit[index] = !m.fit[index]
                        s.multiRevision++
                        s.notice = if (m.fit[index]) "Ekrana Sığdır" else "Orijinal"
                    }
                },
                content = {
                    if (ch != null && playbackUri(ch) != null) {
                        Box(Modifier.fillMaxSize()) {
                            Row(
                                Modifier.align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .background(Color(0xB8000000), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Label("#${index + 1}", 10, if (isFocusedSlot) Color(0xFFFFD600) else Color(0xFF94A2B7), bold = true)
                                Label(ch.name, 11, Color.White, bold = true)
                                if (isFocusedSlot && !s.muted) {
                                    Label("🔊", 10, Color(0xFFFFD600))
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (ch != null) image(ch.logo, Modifier.size(32.dp)) else Label("+", 24, if (isFocusedSlot) Color(0xFFFFD600) else Color(0xFF626669), bold = true)
                            Label(ch?.name ?: "Kanal Ekle (Ekran ${index + 1})", 13, if (isFocusedSlot) Color(0xFFFFD600) else Color.White, bold = true)
                            if (ch != null) Label("Yayın kullanılamıyor · Değiştirmek için dokunun", 10, Color.LightGray)
                            else Label("Kategori ve arama ile kanal seçin", 9, Color(0xFF8A9AB0))
                        }
                    }
                }
            ) {
                if (m.selected == index) s.openMultiChannelPicker(index)
                else {
                    m.selected = index
                    s.multiRevision++
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (m.mode) {
            MultiLayoutMode.TWO_SIDE_BY_SIDE -> {
                Row(Modifier.fillMaxSize()) {
                    SlotCell(0, Modifier.weight(1f).fillMaxHeight())
                    SlotCell(1, Modifier.weight(1f).fillMaxHeight())
                }
            }
            MultiLayoutMode.ONE_BIG_TWO_SMALL -> {
                Row(Modifier.fillMaxSize()) {
                    SlotCell(0, Modifier.weight(1.85f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SlotCell(1, Modifier.weight(1f).fillMaxWidth())
                        SlotCell(2, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            MultiLayoutMode.ONE_BIG_THREE_SMALL -> {
                Row(Modifier.fillMaxSize()) {
                    SlotCell(0, Modifier.weight(2.1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        SlotCell(1, Modifier.weight(1f).fillMaxWidth())
                        SlotCell(2, Modifier.weight(1f).fillMaxWidth())
                        SlotCell(3, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            MultiLayoutMode.FOUR_EQUAL -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        SlotCell(0, Modifier.weight(1f).fillMaxHeight())
                        SlotCell(1, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        SlotCell(2, Modifier.weight(1f).fillMaxHeight())
                        SlotCell(3, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }

        Row(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconAction("Düzen", "ikon_coklu", image) { s.dialog = "layout" }
            IconAction("Kaydet", "ikon_kaydet", image, click = s::openSaveMultiLayoutDialog)
            IconAction("Paketler", "ikon_kaydedildi", image) { s.dialog = "savedLayouts" }
            IconAction("Ayarlar", "ikon_ayar", image, click = s::settings)
            Action("ÇIKIŞ", Modifier.width(68.dp).height(29.dp), tint = Color(0xFFD32B2B), radius = 9.dp, strokeWidth = 0.dp, click = s::back)
        }

        val activeName = s.data.channels.find { it.id == m.slots.getOrNull(m.selected) }?.name
        val audioStatus = if (m.slots.getOrNull(m.selected) != null && !s.muted) {
            "Ses Odağı: Ekran ${m.selected + 1}" + (activeName?.let { " ($it)" } ?: "")
        } else {
            "Sessiz"
        }
        Label(
            "${m.mode.title} · $audioStatus",
            10,
            Color(0xFFFFD600),
            Modifier.align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color(0xCC0A1628), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF2B4263), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            bold = true
        )
    }
}
