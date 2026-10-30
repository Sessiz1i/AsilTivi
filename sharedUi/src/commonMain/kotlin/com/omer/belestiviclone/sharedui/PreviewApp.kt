@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
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
internal class FocusReturn {
    val registry = FocusRegistry<FocusRequester>()
    var blockBackground by mutableStateOf(false)
    var capture = true
    var focused: FocusRequester? = null
    var restore: FocusRequester? = null
    val active = mutableListOf<FocusRequester>()
}
internal val LocalFocusReturn = staticCompositionLocalOf<FocusReturn?> { null }
internal val LocalKeySignal = staticCompositionLocalOf<(KeyEvent) -> KeyPressSignal> { { KeyPressSignal() } }
private val LocalFocusObserver = staticCompositionLocalOf<(String) -> Unit> { {} }
private val LocalNavigationObserver = staticCompositionLocalOf<(KeyEvent) -> Unit> { {} }
internal val LocalBoundsObserver = staticCompositionLocalOf<(String, Rect) -> Unit> { { _, _ -> } }
internal val LocalPanelInteraction = staticCompositionLocalOf<() -> Unit> { {} }
private val colors = listOf(Color(0xFFB68B08), Color(0xFF007D9E), Color(0xFF5A6C80), Color(0xFFB1AFA7), Color(0xFFAF4546), Color(0xFF37875B), Color(0xFF466BAB))
@Composable
internal fun Label(value: String, size: Int = 12, color: Color = Color.White, modifier: Modifier = Modifier, bold: Boolean = false, lineHeight: Float = size * 1.333f, letterSpacing: Float = 0f, maxLines: Int = Int.MAX_VALUE, textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Unspecified, decoration: androidx.compose.ui.text.style.TextDecoration? = null) {
    BasicText(presentationText(value, LocalLanguage.current), modifier, style = TextStyle(color = color, fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = letterSpacing.sp, textAlign = textAlign, textDecoration = decoration, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Key repeat is consumed; long OK never produces an additional short action. */
@Composable
internal fun Action(label: String, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, tint: Color = card, selectedTint: Color = Color(0xB3A75F19), radius: Dp = 10.dp, brush: Brush? = null, stroke: Color = Color(0xFF34404A), strokeWidth: Dp = 1.dp, focusColor: Color = Color.White, focusFill: Color? = null, horizontalFocus: Boolean = false, channelFocus: Boolean = false, padding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp), long: (() -> Unit)? = null, double: (() -> Unit)? = null, extraKey: ((KeyEvent) -> Boolean)? = null, content: (@Composable () -> Unit)? = null, focusKey: String? = null, returnFocusKey: String? = null, onFocused: (() -> Unit)? = null, keyClick: (() -> Unit)? = null, click: () -> Unit) {
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
    val panelInteraction = LocalPanelInteraction.current
    val accessibilityLabel = presentationText(label, LocalLanguage.current)
    val themeFocusStroke = LocalSettingsFocusStroke.current
    val themeFocusFill = LocalSettingsFocusFill.current
    val effectiveFocusColor = if (focusColor != Color.White) focusColor else (themeFocusStroke ?: Color.White)
    val effectiveFocusFill = focusFill ?: themeFocusFill
    DisposableEffect(requester) {
        returns?.active?.add(requester)
        onDispose { returns?.active?.remove(requester); if (returns?.focused == requester) returns.focused = null }
    }
    fun invoke(action: () -> Unit) {
        panelInteraction()
        if (returns?.capture == true) returns.restore = returnFocusKey?.let { returns.registry.requester(it) { FocusRequester() } } ?: requester
        action()
    }
    Box(modifier.defaultMinSize(minWidth = 29.dp, minHeight = 29.dp)
        .focusRequester(requester).focusProperties { canFocus = canInteract }.semantics { contentDescription = accessibilityLabel }
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) { panelInteraction(); returns?.focused = requester; focusObserver(focusKey ?: "action:${label.hashCode()}"); onFocused?.invoke() }
            else { gate.reset(); if (returns?.focused == requester) { returns.focused = null; focusObserver("none") } }
        }
        .onPreviewKeyEvent { e ->
            navigationObserver(e)
            if (e.type == KeyEventType.KeyDown) panelInteraction()
            if (extraKey?.invoke(e) == true) true
            else if (canInteract && e.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                val now = clock.elapsedNow().inWholeMilliseconds
                val signal = keySignal(e)
                val action = when (e.type) { KeyEventType.KeyDown -> gate.down(now, long != null, signal.longPress, signal.repeat); KeyEventType.KeyUp -> gate.up(now, long != null); else -> OkAction.NONE }
                when (action) { OkAction.SHORT -> invoke(keyClick ?: click); OkAction.LONG -> long?.let { invoke(it) }; OkAction.NONE -> Unit }
                true
            } else false
        }
        .then(if (brush != null && !selected) Modifier.background(brush, RoundedCornerShape(radius)) else Modifier.background(if (selected) selectedTint else if (focused && effectiveFocusFill != null && !channelFocus) effectiveFocusFill else if (focused && channelFocus) Color(0x668A531B) else tint, RoundedCornerShape(radius)))
        .then(if (strokeWidth > 0.dp || (focused && !horizontalFocus && !channelFocus)) Modifier.border(if (focused && !horizontalFocus && !channelFocus) 2.dp else strokeWidth, if (focused && !horizontalFocus && !channelFocus) effectiveFocusColor else stroke, RoundedCornerShape(radius)) else Modifier)
        .drawWithContent {
            drawContent()
            if (focused && horizontalFocus && !channelFocus) {
                val edge = 1.dp.toPx()
                drawLine(Color.White, Offset(0f, edge / 2), Offset(size.width, edge / 2), edge)
                drawLine(effectiveFocusColor, Offset(0f, edge * 2), Offset(size.width, edge * 2), edge * 2)
                drawLine(effectiveFocusColor, Offset(0f, size.height - edge * 2), Offset(size.width, size.height - edge * 2), edge * 2)
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
                            else -> if (s.isRadioMode) "radio:station:${PreviewCodec.id(s.moving ?: s.selected)}" else "channel:${PreviewCodec.id(s.moving ?: s.selected)}"
                        }
                        returns.registry.entryTarget(key, returns.active)
                    } else null
                runCatching { requester?.requestFocus() }
            }
            restorePending = false
        }
    }
    LaunchedEffect(s) { s.load() }
    CompositionLocalProvider(LocalFocusReturn provides returns, LocalEnglish provides (s.data.pref("language") == "English"), LocalLanguage provides s.data.pref("language"), LocalKeySignal provides platformKeySignal, LocalFocusObserver provides onFocusTarget, LocalNavigationObserver provides onNavigationKey, LocalBoundsObserver provides onLayoutBounds, LocalPanelInteraction provides s::touchPanelInteraction) {
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { onLayoutBounds("window-root", it.boundsInWindow()) }
            .onKeyEvent { e ->
                if (s.moving != null && s.dialog == null && s.page == "live") {
                    if (e.type == KeyEventType.KeyDown && e.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                        s.moveBy(if (e.key in listOf(Key.DirectionLeft, Key.DirectionUp)) -1 else 1)
                        true
                    } else if (e.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                        if (e.type == KeyEventType.KeyUp) s.moveFinish()
                        true
                    } else false
                } else false
            }
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { onLayoutBounds("safe-root", it.boundsInWindow()) }) {
    val density = LocalDensity.current
    val scale = minOf(maxWidth.value / 855f, maxHeight.value / 481f)
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
    Box(Modifier.fillMaxSize()) {
        if (!s.loaded) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label(s.storage) }
        else if (s.page == "multi") {
            Multi(s, image, video)
        } else {
            Live(s, image, video)
            if (s.page == "settings") {
                Settings(s, image, appVersionName)
            }
        }
        if (s.notice.isNotEmpty() && !(s.isRadioMode && s.radioScreenDimmed)) {
            val current = s.notice
            LaunchedEffect(current) { kotlinx.coroutines.delay(5500); if (s.notice == current) s.notice = "" }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).widthIn(max = 540.dp).background(Color(0xEE20354C), RoundedCornerShape(12.dp)).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) { Label(s.notice, 14) }
        }
        if (s.dialog != null) CompositionLocalProvider(LocalModalLayer provides true) { Modal(s, image) }
        val toastMsg = s.bottomToastMessage
        if (!toastMsg.isNullOrEmpty() && !(s.isRadioMode && s.radioScreenDimmed)) {
            val currentRev = s.bottomToastRevision
            LaunchedEffect(currentRev) {
                kotlinx.coroutines.delay(BottomToastDurationMs)
                if (s.bottomToastRevision == currentRev) s.clearBottomToast()
            }
            val toastBg = when (s.bottomToastTone) {
                ToastTone.Success -> Color(0xFF1F8B4C)
                ToastTone.Warning -> Color(0xFFD97706)
            }
            val toastBorder = when (s.bottomToastTone) {
                ToastTone.Success -> Color(0x884ADE80)
                ToastTone.Warning -> Color(0x88FBBF24)
            }
            val boundsObserver = LocalBoundsObserver.current
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 54.dp)
                    .onGloballyPositioned { boundsObserver("bottom-toast-pill", it.boundsInWindow()) }
                    .background(toastBg, RoundedCornerShape(50))
                    .border(1.dp, toastBorder, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 9.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Label(toastMsg, 13, Color.White, bold = true, maxLines = 1)
            }
        }
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
        if (s.page != "live") {
            return@BoxWithConstraints
        }
        LaunchedEffect(s.panel, s.selected, s.query, s.categoryMenu, s.moving, s.dialog, s.info, s.panelInteractionRevision, s.isRadioMode, s.radioCategory) {
            val canAutoDismiss = if (s.isRadioMode) {
                s.panel && s.page == "live" && s.dialog == null && s.moving == null && !s.radioScreenDimmed
            } else {
                s.panel && s.page == "live" && s.dialog == null && !s.searching && s.moving == null && !s.categoryMenu && !s.info
            }
            if (canAutoDismiss) {
                kotlinx.coroutines.delay(5000L)
                s.panel = false
            }
        }
        if (s.isRadioMode) {
            RadioScreen(s, image, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        if (playbackUri(s.channel) == null) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { image(s.channel?.logo ?: "ikon_tv", Modifier.size(70.dp)); Label(if (s.channel?.available == false) s.message("Kanal etkin kaynaklarda erişilemiyor", "Channel unavailable in active sources", "Kanal in aktiven Quellen nicht verfügbar") else if (s.isPublic) "Oynatma tanımı desteklenmiyor" else "Yayın bağlantısı yok", 11) }
        }
        LaunchedEffect(s.panel, s.category, s.selected) {
            if (s.panel) {
                val list = s.visibleData.filtered(s.category, s.query)
                s.touchedFocusChannelId = if (list.any { it.id == s.selected }) s.selected else list.firstOrNull()?.id
            }
        }
        val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (s.info || s.dialog == "format") {
                            s.info = false
                            if (s.dialog == "format") s.dialog = null
                            s.touchPanelInteraction()
                        } else {
                            if (!s.panel) {
                                s.panel = true
                            }
                            s.touchPanelInteraction()
                        }
                    }
                }
        )
        val boundsObserver = LocalBoundsObserver.current
        val currentEpg = remember(s.channel, nowEpochMs, s.epgRevision) { s.epgNowNext(s.channel, nowEpochMs) }
        val headingNow = currentEpg.now?.let { "Şimdi: ${it.title} (${it.timeRangeLabel()})" } ?: "Şimdi: EPG bilgisi yok"
        val headingNext = currentEpg.next?.let { "Sonra: ${it.title} (${it.timeRangeLabel()})" }
        if (s.liveHeadingVisible) LiveHeading(s.channel?.name ?: "", s.channel?.logo ?: "ikon_tv", headingNow, image,
            Modifier.liveHeadingPlacement(s.data.pref("format"), maxWidth.value, maxHeight.value).widthIn(max = maxWidth * .55f),
            nextSubtitle = headingNext, titleColor = theme.headingTitleColor, includeLogo = false, backdrop = true, leadingChannelLogo = true, onBounds = { part, rect -> boundsObserver("heading:$part", rect) })
        if (s.panel) {
            Row(Modifier.align(Alignment.TopEnd).padding(top = 21.dp, end = 18.dp).onGloballyPositioned { boundsObserver("top-actions", it.boundsInWindow()) }.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isMono = theme.categoryTabPill
                IconAction("Radyo", "ikon_audio", image, isMonochrome = isMono, click = s::enterRadioMode)
                IconAction("Çoklu Ekran", "ikon_coklu", image, isMonochrome = isMono) { s.dialog = "warning" }
                IconAction("Görüntü", "ikon_format", image, isMonochrome = isMono, click = s::openFormatMenu)
                IconAction("Bilgi", "ikon_saat", image, isMonochrome = isMono, click = s::toggleInfo)
                IconAction("Ayarlar", "ikon_ayar", image, isMonochrome = isMono, click = s::settings)
                IconAction("Çıkış", "ikon_cikis", image, isMonochrome = isMono) { s.dialog = "exit" }
            }
            if (vertical) {
                val panelWidth = minOf(320.dp, maxWidth * if (maxWidth > maxHeight) (192f / 855f) else .52f)
                val top = LiveSidebarTop.dp
                val listHeight = (maxHeight - top - if (s.searching) 84.dp else 24.dp).coerceAtLeast(80.dp)
                val sidebarShape = RoundedCornerShape(12.dp)
                val catListState = rememberLazyListState()
                LaunchedEffect(catListState.isScrollInProgress) {
                    if (catListState.isScrollInProgress) s.touchPanelInteraction()
                }
                LazyColumn(
                    Modifier
                        .offset(13.dp, top)
                        .width(128.dp)
                        .height(listHeight)
                        .background(Color.Black.copy(alpha = opacity), sidebarShape)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), sidebarShape)
                        .clip(sidebarShape),
                    state = catListState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { CategoryTab("Ara", s, image, true) { s.searching = true; s.categoryMenu = false } }
                    items(categories(s)) { cat -> CategoryTab(cat, s, image, true, keyClick = { handleChooseCategory(s, cat, focusFirstCard = true) }) { handleChooseCategory(s, cat, focusFirstCard = false) } }
                    item { CategoryTab("ÇOKLU EKRAN", s, image, true) { s.categoryAction("multi") } }
                    item { CategoryTab("AYARLAR", s, image, true) { s.categoryAction("settings") } }
                }
                Column(
                    Modifier
                        .onGloballyPositioned { boundsObserver("vertical-panel", it.boundsInWindow()) }
                        .offset(x = 147.dp, y = top)
                        .width(panelWidth)
                        .height(listHeight)
                        .background(Color.Black.copy(alpha = opacity), sidebarShape)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), sidebarShape)
                        .clip(sidebarShape)
                ) {
                    Channels(s, image, true, Modifier.fillMaxSize())
                }
                if (s.searching) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 19.dp, vertical = 20.dp)) { Search(s) }
            } else Column(Modifier.align(Alignment.BottomCenter).onGloballyPositioned { boundsObserver("horizontal-panel", it.boundsInWindow()) }.padding(bottom = 11.dp).fillMaxWidth().height(panelHeight).background(Color.Black.copy(alpha = opacity))) {
                val catRowState = rememberLazyListState()
                LaunchedEffect(catRowState.isScrollInProgress) {
                    if (catRowState.isScrollInProgress) s.touchPanelInteraction()
                }
                LazyRow(Modifier.height(44.dp).padding(vertical = 6.dp), state = catRowState, horizontalArrangement = Arrangement.spacedBy(if (theme.categoryTabPill) 6.dp else (-8).dp)) {
                    item { CategoryTab("Ara", s, image, false) { s.searching = true } }
                    items(categories(s)) { cat -> CategoryTab(cat, s, image, false, keyClick = { handleChooseCategory(s, cat, focusFirstCard = true) }) { handleChooseCategory(s, cat, focusFirstCard = false) } }
                }
                Channels(s, image, false, Modifier.fillMaxWidth().height(68.dp))
                if (s.searching) Search(s)
            }
            val liveProgress = currentEpg.liveProgress(nowEpochMs, s.channelSessionStartMs)
            val playerTimeText = liveProgress.elapsedLabel
            val playerProgressFraction = if (s.isBehindLiveEdge && s.seekOffsetSeconds > 0L) {
                (liveProgress.progress - 0.18f).coerceIn(0.05f, 0.85f)
            } else {
                liveProgress.progress
            }

            val barStartPadding = if (vertical) {
                val pWidth = minOf(320.dp, maxWidth * if (maxWidth > maxHeight) (192f / 855f) else .52f)
                val panelRightEdge = 147.dp + pWidth
                panelRightEdge + 18.dp
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
                    modifier = Modifier.weight(1f),
                    onSeek = { fraction -> s.seekInLiveWindow(fraction) }
                )

                val isPaused = s.paused
                val isBehind = !isPaused && s.isBehindLiveEdge
                val (liveBtnLabel, _) = liveControlLabels(isPaused, s.isBehindLiveEdge)
                val liveBtnHighlight = isPaused || isBehind
                PlayerActionButton(
                    label = liveBtnLabel,
                    textColor = if (liveBtnHighlight) Color(0xFFFFD54F) else Color.White,
                    tint = if (liveBtnHighlight) Color(0x3DFFC107) else Color(0x2EFFFFFF),
                    stroke = if (liveBtnHighlight) Color(0x99FFC107) else Color(0x3DFFFFFF),
                    iconContent = {
                        if (liveBtnHighlight) {
                            PlayerPlayPauseIcon(isPaused = true, color = Color(0xFFFFD54F), modifier = Modifier.size(9.dp))
                        } else {
                            Box(Modifier.size(7.dp).background(Color(0xFFE53935), CircleShape))
                        }
                    },
                    click = { s.syncLive() }
                )

                val isMuted = s.muted
                PlayerActionButton(
                    label = if (isMuted) "Sessiz" else "Ses Açık",
                    iconContent = {
                        PlayerAudioIcon(isMuted = isMuted, color = Color.White, modifier = Modifier.size(12.dp))
                    },
                    click = { s.muted = !s.muted }
                )

                PlayerActionButton(
                    label = if (isPaused) "Oynat" else "Duraklat",
                    textColor = if (isPaused) Color(0xFF66BB6A) else Color.White,
                    tint = if (isPaused) Color(0x3D2E7D32) else Color(0x2EFFFFFF),
                    stroke = if (isPaused) Color(0x994CAF50) else Color(0x3DFFFFFF),
                    iconContent = {
                        PlayerPlayPauseIcon(
                            isPaused = isPaused,
                            color = if (isPaused) Color(0xFF66BB6A) else Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    },
                    click = { s.togglePause() }
                )
            }
        }
        if (s.info) {
            LaunchedEffect(s.info, s.selected) {
                kotlinx.coroutines.delay(10_000)
                s.info = false
            }
            val align = zapCardAlignment(s.data.pref("position"))
            val factor = zapCardScale(s.data.pref("size"))
            ChannelInfoPanel(
                s = s,
                qualityInfo = qualityInfo,
                nowEpochMs = nowEpochMs,
                factor = factor,
                image = image,
                modifier = Modifier
                    .align(align)
                    .onGloballyPositioned { boundsObserver("channel-info", it.boundsInWindow()) }
                    .padding(bottom = if (s.panel && !vertical) panelHeight + 62.dp else 22.dp, start = 20.dp, end = 20.dp)
                    .width((440.dp * factor).coerceAtMost(maxWidth - 32.dp))
            )
        }
        var zapVisible by remember { mutableStateOf(false) }
        LaunchedEffect(s.zapCardRevision) {
            if (s.zapCardRevision > 0) {
                zapVisible = true
                kotlinx.coroutines.delay(ZapCardDurationMs)
                zapVisible = false
            }
        }
        LaunchedEffect(s.panel) { if (s.panel) zapVisible = false }
        if (zapVisible && !s.panel && s.page == "live" && s.dialog == null && s.multi == null && !s.info && s.moving == null) {
            val zapNow = remember(s.zapCardRevision, s.selected) { System.currentTimeMillis() }
            val zapModel = remember(s.zapCardRevision, s.selected, s.epgRevision, s.technicalInfo) {
                zapCardModel(s.channel, s.channelNumber, s.epgNowNext(s.channel, zapNow), s.technicalInfo, zapNow)
            }
            val posPref = s.data.pref("position")
            val sizePref = s.data.pref("size")
            val zapAlign = zapCardAlignment(posPref)
            val zapScale = zapCardScale(sizePref)
            val zapWidth = zapCardWidthDp(sizePref, maxWidth.value).dp
            ZapCard(
                model = zapModel,
                theme = theme,
                image = image,
                modifier = Modifier
                    .align(zapAlign)
                    .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                    .onGloballyPositioned { boundsObserver("zap-card", it.boundsInWindow()) }
                    .width(zapWidth),
                scale = zapScale
            )
        }
        val numLabel = s.numberInputLabel
        if (numLabel != null && s.page == "live" && s.dialog == null && s.multi == null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .onGloballyPositioned { boundsObserver("number-input-pill", it.boundsInWindow()) }
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xF01565C0), Color(0xF01E88E5))),
                        RoundedCornerShape(24.dp)
                    )
                    .border(1.5.dp, Color(0xB390CAF9), RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Label(numLabel, 15, Color.White, bold = true, maxLines = 1)
            }
        }
        if (s.moving != null) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp).background(Color(0xFF243F66), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF647B9A), RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            image("ikon_tasima_bilgi", Modifier.size(35.dp, 17.dp)); Label("Yönlerle taşı · OK uygula · Geri vazgeç", 11, bold = true)
        }
    }
}

/** Oynatıcı alt barındaki Canlı ve Oynat/Duraklat buton metinlerini saf olarak hesaplar. */
fun liveControlLabels(paused: Boolean, isBehindLiveEdge: Boolean): Pair<String, String> {
    val liveText = when {
        paused -> "|| Yayın Durduruldu"
        isBehindLiveEdge -> "● ⏩ Canlıya Dön"
        else -> "Canlı"
    }
    val playPauseText = if (paused) "▶ Oynat" else "⏸ Duraklat"
    return liveText to playPauseText
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
            .background(Color(0xEC0B1320), RoundedCornerShape((14 * factor).dp))
            .border(1.dp, Color(0xFF2E4360), RoundedCornerShape((14 * factor).dp))
            .pointerInput(Unit) { detectTapGestures { s.panel = true } }
            .padding((13 * factor).dp),
        verticalArrangement = Arrangement.spacedBy((8 * factor).dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((12 * factor).dp)
        ) {
            Box(
                Modifier
                    .size((58 * factor).dp)
                    .background(Color(0xFF13233D), RoundedCornerShape((10 * factor).dp))
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
                            .background(Color(0xFF1E3557), RoundedCornerShape((5 * factor).dp))
                            .padding(horizontal = (6 * factor).dp, vertical = (2 * factor).dp)
                    ) {
                        Label("#${s.channelNumber}", (10 * factor).toInt().coerceAtLeast(8), Color(0xFFFFD735), bold = true)
                    }
                    Label(s.channel?.name ?: "", (16 * factor).toInt().coerceAtLeast(12), Color.White, Modifier.weight(1f), bold = true, maxLines = 1)
                    if (!s.channel?.category.isNullOrEmpty()) {
                        Label(s.channel?.category.orEmpty(), (9 * factor).toInt().coerceAtLeast(8), Color(0xFF86C9EF), bold = true, maxLines = 1)
                    }
                    Box(
                        Modifier
                            .background(amber, RoundedCornerShape((5 * factor).dp))
                            .padding(horizontal = (6 * factor).dp, vertical = (2 * factor).dp)
                    ) {
                        Label(t.resolutionBadge, (9 * factor).toInt().coerceAtLeast(8), Color.White, bold = true)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Label("Şimdi: $programTitle", (11 * factor).toInt().coerceAtLeast(9), Color(0xFFFFD735), Modifier.weight(1f), bold = true, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy((4 * factor).dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(timeRangeText, (9 * factor).toInt().coerceAtLeast(8), Color(0xFFB0BEC5))
                        if (progressPercentText != null) {
                            Label(progressPercentText, (9 * factor).toInt().coerceAtLeast(8), Color(0xFFFFD735), bold = true)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((3.5f * factor).dp)
                        .background(Color(0xFF223651), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFFFFD735), RoundedCornerShape(2.dp))
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Label("Sonra: $nextText", (10 * factor).toInt().coerceAtLeast(8), Color(0xFF86C9EF), Modifier.weight(1f), maxLines = 1)
                    Label(EpgDateTime.formatHhMm(nowEpochMs), (12 * factor).toInt().coerceAtLeast(9), Color(0xFFFFD735), bold = true)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        Column(verticalArrangement = Arrangement.spacedBy((3.5f * factor).dp)) {
            Label(t.v41VideoLine, (10 * factor).toInt().coerceAtLeast(8), Color(0xFFE2E8F0), bold = true, maxLines = 1)
            Label(t.v41AudioLine, (10 * factor).toInt().coerceAtLeast(8), Color(0xFFB8C7DC), maxLines = 1)
            Label(t.v41PlaybackLine(s.paused), (10 * factor).toInt().coerceAtLeast(8), Color(0xFFFFD735), bold = true, maxLines = 1)
        }
    }
}

@Composable private fun IconAction(
    label: String,
    icon: String,
    image: @Composable (String, Modifier) -> Unit,
    width: Dp? = null,
    visibleLabel: String = label,
    isMonochrome: Boolean = false,
    iconColor: Color = Color.White,
    click: () -> Unit
) {
    Action(
        label,
        Modifier.height(29.dp).then(if (width != null) Modifier.width(width) else Modifier),
        tint = Color(0xB8000000),
        radius = 11.dp,
        stroke = Color(0xFF344454),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isMonochrome) {
                    TopBarActionIcon(icon, color = iconColor, modifier = Modifier.size(13.dp, 14.dp))
                } else {
                    image(icon, Modifier.size(13.dp, 14.dp))
                }
                Label(visibleLabel, 11, bold = true)
            }
        },
        click = click
    )
}
private fun handleChooseCategory(s: PreviewState, cat: String, focusFirstCard: Boolean = false) {
    val targetCategory = when {
        cat.equals("FAVORİ TV", ignoreCase = true) || cat.equals("Favoriler", ignoreCase = true) -> "Favoriler"
        cat.equals("TÜMÜ", ignoreCase = true) || cat.equals("Tümü", ignoreCase = true) -> "Tümü"
        else -> cat
    }
    s.chooseCategory(targetCategory, focusFirstCard = focusFirstCard)
}

@Composable
private fun PlayerSeekBar(progress: Float, color: Color, modifier: Modifier = Modifier, onSeek: ((Float) -> Unit)? = null) {
    val clamped = progress.coerceIn(0f, 1f)
    val panelInteraction = LocalPanelInteraction.current
    Box(
        modifier = modifier
            .height(20.dp)
            .fillMaxWidth()
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(onSeek) {
                        detectTapGestures { offset ->
                            panelInteraction()
                            if (size.width > 0) {
                                onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                } else Modifier
            ),
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
private fun PlayerActionButton(
    label: String,
    iconContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    tint: Color = Color(0x2EFFFFFF),
    stroke: Color = Color(0x3DFFFFFF),
    click: () -> Unit
) {
    Action(
        label = label,
        modifier = modifier.height(26.dp),
        tint = tint,
        radius = 13.dp,
        stroke = stroke,
        strokeWidth = 1.dp,
        padding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                iconContent()
                Label(label, 10, textColor, bold = true)
            }
        },
        click = click
    )
}

private fun categorySymbol(cat: String) = when (CategoryNormalizer.canonicalKey(cat)) {
    "ARA" -> "🔍"
    "FAVORİ TV" -> "🏆"
    "TÜMÜ" -> "⊞"
    "ULUSAL" -> "🇹🇷"
    "HABER" -> "📰"
    "SPOR" -> "⚽"
    "ÇOCUK" -> "🪁"
    "BELGESEL" -> "🌍"
    "MÜZİK" -> "🎵"
    "DİNİ" -> "🕌"
    "YEREL" -> "📍"
    "KKTC" -> "🇨🇾"
    "AZERBAYCAN" -> "🇦🇿"
    "ALMANYA / AVRUPA" -> "🇩🇪"
    "DÜNYA" -> "🌐"
    "7/24 DİZİ/FİLM" -> "🎬"
    "RADYO" -> "📻"
    "EKONOMİ" -> "📈"
    "KURUMLAR" -> "🏛️"
    "DİĞER" -> "📺"
    "ÇOKLU EKRAN", "AYARLAR" -> null
    else -> if (cat.startsWith("7/24")) "🎬" else "📺"
}

@Composable
private fun CategoryTabIcon(
    cat: String,
    isMonochrome: Boolean,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    if (!isMonochrome) {
        val symbol = categorySymbol(cat)
        if (symbol != null) {
            BasicText(symbol, Modifier.clearAndSetSemantics { }, style = TextStyle(fontSize = 12.sp))
        }
        return
    }

    val key = CategoryNormalizer.canonicalKey(cat)
    Canvas(modifier = modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.3f.dp.toPx()

        when (key) {
            "ARA" -> {
                val r = w * 0.30f
                val cx = w * 0.40f
                val cy = h * 0.40f
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(stroke))
                val cos45 = 0.7071f
                drawLine(
                    color,
                    start = Offset(cx + r * cos45, cy + r * cos45),
                    end = Offset(w * 0.90f, h * 0.90f),
                    strokeWidth = stroke * 1.2f,
                    cap = StrokeCap.Round
                )
            }
            "FAVORİ TV" -> {
                val cx = w / 2f
                val cy = h / 2f
                val outer = w * 0.44f
                val starPath = Path().apply {
                    moveTo(cx, cy - outer)
                    quadraticTo(cx, cy, cx + outer, cy)
                    quadraticTo(cx, cy, cx, cy + outer)
                    quadraticTo(cx, cy, cx - outer, cy)
                    quadraticTo(cx, cy, cx, cy - outer)
                    close()
                }
                drawPath(starPath, color)
            }
            "TÜMÜ" -> {
                val pad = 1.dp.toPx()
                val cell = (w - pad * 3f) / 2f
                for (r in 0..1) {
                    for (c in 0..1) {
                        drawRoundRect(
                            color,
                            topLeft = Offset(pad + c * (cell + pad), pad + r * (cell + pad)),
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                            style = Stroke(stroke * 0.9f)
                        )
                    }
                }
            }
            "ULUSAL" -> {
                val rw = w * 0.86f
                val rh = h * 0.62f
                val left = (w - rw) / 2f
                val top = h * 0.12f
                drawRoundRect(color, topLeft = Offset(left, top), size = Size(rw, rh), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(w / 2f, top + rh), Offset(w / 2f, h * 0.90f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.90f), Offset(w * 0.72f, h * 0.90f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "HABER" -> {
                drawRoundRect(color, topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.26f, h * 0.35f), Offset(w * 0.74f, h * 0.35f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.74f, h * 0.52f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.26f, h * 0.69f), Offset(w * 0.58f, h * 0.69f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }
            "SPOR" -> {
                val r = w * 0.42f
                val c = Offset(w / 2f, h / 2f)
                drawCircle(color, radius = r, center = c, style = Stroke(stroke))
                drawLine(color, Offset(w * 0.2f, h / 2f), Offset(w * 0.8f, h / 2f), strokeWidth = stroke * 0.7f)
                drawArc(color, 270f, 180f, false, topLeft = Offset(w * 0.25f, h * 0.08f), size = Size(w * 0.5f, h * 0.84f), style = Stroke(stroke * 0.7f))
            }
            "ÇOCUK" -> {
                val r = w * 0.42f
                val c = Offset(w / 2f, h / 2f)
                drawCircle(color, radius = r, center = c, style = Stroke(stroke))
                drawCircle(color, radius = 0.8.dp.toPx(), center = Offset(w * 0.36f, h * 0.38f))
                drawCircle(color, radius = 0.8.dp.toPx(), center = Offset(w * 0.64f, h * 0.38f))
                drawArc(color, 30f, 120f, false, topLeft = Offset(w * 0.30f, h * 0.44f), size = Size(w * 0.40f, h * 0.30f), style = Stroke(stroke * 0.8f, cap = StrokeCap.Round))
            }
            "BELGESEL", "DÜNYA" -> {
                val r = w * 0.42f
                val c = Offset(w / 2f, h / 2f)
                drawCircle(color, radius = r, center = c, style = Stroke(stroke))
                drawLine(color, Offset(w * 0.08f, h / 2f), Offset(w * 0.92f, h / 2f), strokeWidth = stroke * 0.7f)
                drawLine(color, Offset(w / 2f, h * 0.08f), Offset(w / 2f, h * 0.92f), strokeWidth = stroke * 0.7f)
                drawOval(color, topLeft = Offset(w * 0.26f, h * 0.08f), size = Size(w * 0.48f, h * 0.84f), style = Stroke(stroke * 0.7f))
            }
            "MÜZİK" -> {
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.32f, h * 0.76f))
                drawCircle(color, radius = 2.dp.toPx(), center = Offset(w * 0.72f, h * 0.66f))
                drawLine(color, Offset(w * 0.40f, h * 0.76f), Offset(w * 0.40f, h * 0.24f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.80f, h * 0.66f), Offset(w * 0.80f, h * 0.14f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.40f, h * 0.24f), Offset(w * 0.80f, h * 0.14f), strokeWidth = stroke * 1.5f, cap = StrokeCap.Round)
            }
            "DİNİ" -> {
                drawArc(color, 60f, 250f, false, topLeft = Offset(w * 0.08f, h * 0.08f), size = Size(w * 0.84f, h * 0.84f), style = Stroke(stroke * 1.2f, cap = StrokeCap.Round))
                drawCircle(color, radius = 1.dp.toPx(), center = Offset(w * 0.66f, h * 0.46f))
            }
            "YEREL" -> {
                val cx = w / 2f
                val cy = h * 0.38f
                drawCircle(color, radius = w * 0.30f, center = Offset(cx, cy), style = Stroke(stroke))
                drawCircle(color, radius = 1.dp.toPx(), center = Offset(cx, cy))
                val path = Path().apply {
                    moveTo(w * 0.26f, cy + h * 0.15f)
                    lineTo(cx, h * 0.90f)
                    lineTo(w * 0.74f, cy + h * 0.15f)
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "KKTC", "AZERBAYCAN", "ALMANYA / AVRUPA" -> {
                val left = w * 0.18f
                val top = h * 0.18f
                val fw = w * 0.64f
                val fh = h * 0.46f
                drawLine(color, Offset(left, top), Offset(left, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawRect(color, topLeft = Offset(left, top), size = Size(fw, fh), style = Stroke(stroke))
                drawLine(color, Offset(left, top + fh / 2f), Offset(left + fw, top + fh / 2f), strokeWidth = stroke * 0.6f)
            }
            "7/24 DİZİ/FİLM" -> {
                val left = w * 0.14f
                val top = h * 0.18f
                val fw = w * 0.72f
                val fh = h * 0.64f
                drawRoundRect(color, topLeft = Offset(left, top), size = Size(fw, fh), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(left, top + fh * 0.35f), Offset(left + fw, top + fh * 0.35f), strokeWidth = stroke)
                drawLine(color, Offset(left + fw * 0.33f, top), Offset(left + fw * 0.22f, top + fh * 0.35f), strokeWidth = stroke * 0.8f)
                drawLine(color, Offset(left + fw * 0.66f, top), Offset(left + fw * 0.55f, top + fh * 0.35f), strokeWidth = stroke * 0.8f)
            }
            "RADYO" -> {
                val left = w * 0.12f
                val top = h * 0.32f
                val rw = w * 0.76f
                val rh = h * 0.54f
                drawLine(color, Offset(left + rw * 0.2f, top), Offset(w * 0.75f, h * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawRoundRect(color, topLeft = Offset(left, top), size = Size(rw, rh), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawCircle(color, radius = rh * 0.32f, center = Offset(left + rw * 0.36f, top + rh * 0.5f), style = Stroke(stroke * 0.8f))
            }
            else -> {
                val rw = w * 0.84f
                val rh = h * 0.64f
                drawRoundRect(color, topLeft = Offset((w - rw) / 2f, h * 0.16f), size = Size(rw, rh), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
            }
        }
    }
}

@Composable private fun CategoryTab(cat: String, s: PreviewState, image: @Composable (String, Modifier) -> Unit, vertical: Boolean, keyClick: (() -> Unit)? = null, click: () -> Unit) {
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val shades = when (CategoryNormalizer.canonicalKey(cat)) {
        "ÇOKLU EKRAN" -> listOf(Color(0xFFDA6800), Color(0xFFF4A10B))
        "AYARLAR" -> listOf(Color(0xFF292A2A), Color(0xFF070707))
        "ARA" -> listOf(Color(0xFF247675))
        "FAVORİ TV" -> listOf(Color(0xFFB8860B), Color(0xFFE5A910))
        "TÜMÜ" -> listOf(Color(0xFF30495C))
        "ULUSAL" -> listOf(Color(0xFFFAFAFA))
        "HABER" -> listOf(Color(0xFF315D77))
        "SPOR" -> listOf(Color(0xFF1A9B67))
        "ÇOCUK" -> listOf(Color(0xFF7A4FAC), Color(0xFF568AD1))
        "BELGESEL" -> listOf(Color(0xFF455B65))
        "MÜZİK" -> listOf(Color(0xFFDC4835), Color(0xFFE99A18))
        "DİNİ" -> listOf(Color(0xFF1BAE91))
        "YEREL" -> listOf(Color(0xFF87918F))
        "KKTC" -> listOf(Color(0xFFC0392B), Color(0xFFE74C3C))
        "AZERBAYCAN" -> listOf(Color(0xFF007A87), Color(0xFF96253F))
        "ALMANYA / AVRUPA" -> listOf(Color(0xFF2C3E50), Color(0xFFD35400))
        "DÜNYA" -> listOf(Color(0xFF1565C0), Color(0xFF00ACC1))
        "7/24 DİZİ/FİLM" -> listOf(Color(0xFFE0AF0D), Color(0xFFBB7714))
        "RADYO" -> listOf(Color(0xFF5C6BC0), Color(0xFF3949AB))
        else -> listOf(Color(0xFF2684A6), Color(0xFF18B59F))
    }
    val shape = if (vertical) RoundedCornerShape(8.dp)
        else if (theme.categoryTabPill) RoundedCornerShape(16.dp)
        else GenericShape { size, _ -> val slant = size.height * .466f; moveTo(slant, 0f); lineTo(size.width, 0f); lineTo(size.width - slant, size.height); lineTo(0f, size.height); close() }
    val tabBrush = Brush.linearGradient(if (shades.size == 1) shades + shades else shades)

    val isFav = cat.equals("FAVORİ TV", ignoreCase = true) || cat.equals("Favoriler", ignoreCase = true)
    val isUlusal = cat.equals("Ulusal", ignoreCase = true)
    val isSelected = s.category == cat ||
        (isFav && (s.category == "Favoriler" || s.category == "FAVORİ TV")) ||
        (cat.equals("TÜMÜ", ignoreCase = true) && (s.category == "Tümü" || s.category == "TÜMÜ")) ||
        CategoryNormalizer.matches(s.category, cat)

    var isTabFocused by remember { mutableStateOf(false) }
    val favBorder = if (isFav && !theme.categoryTabPill) Color(0xFFFFD700) else Color.Transparent
    val selection = if (isTabFocused) {
        Modifier.border(2.dp, Color(0xFFFFCF37), shape)
    } else if (isSelected) {
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
    } else if (theme.categoryTabPill && pillBrush != null) {
        Modifier.background(pillBrush, shape)
    } else Modifier.background(tabBrush, shape)

    val language = LocalLanguage.current
    val label = presentationText(cat, language).let { if (language == "Türkçe") CategoryNormalizer.toTurkishUpper(it) else it.uppercase() }
    val horizontalPadding = if (vertical) 9.dp else if (theme.categoryTabPill) 12.dp else 14.dp
    val textColor = if (isUlusal && !theme.categoryTabPill) {
        Color(0xFFE00010)
    } else if (isFav && !theme.categoryTabPill) {
        Color(0xFFFFD700)
    } else {
        Color.White
    }
    val returns = LocalFocusReturn.current
    val tabFocusKey = "cat:${CategoryNormalizer.canonicalKey(cat)}"

    Action(cat, (if (vertical) Modifier.fillMaxWidth().heightIn(min = 30.dp) else Modifier.height(32.dp)).onFocusChanged { isTabFocused = it.isFocused }.clip(shape).then(background).then(selection), tint = Color.Transparent, radius = 0.dp, strokeWidth = 0.dp, focusKey = tabFocusKey, returnFocusKey = if (cat in listOf("ÇOKLU EKRAN", "AYARLAR")) tabFocusKey else null, padding = PaddingValues(horizontal = horizontalPadding, vertical = 3.dp), onFocused = { s.touchPanelInteraction() }, keyClick = keyClick, extraKey = if (vertical) { e ->
        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
            val targetId = s.touchedFocusChannelId ?: s.selected
            val req = returns?.registry?.entryTarget("channel:${PreviewCodec.id(targetId)}", returns.active)
            if (req != null) runCatching { req.requestFocus() }
            true
        } else false
    } else null, content = {
        Box(if (vertical) Modifier.fillMaxWidth() else Modifier) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cat in listOf("ÇOKLU EKRAN", "AYARLAR")) {
                    image(if (cat == "ÇOKLU EKRAN") "ikon_coklu" else "ikon_ayar", Modifier.size(12.dp, 13.dp))
                } else {
                    CategoryTabIcon(cat, isMonochrome = theme.categoryTabPill, color = textColor)
                }
                Label(label, 10, textColor, bold = true, maxLines = if (vertical) Int.MAX_VALUE else 1)
            }
        }
    }, click = click)
}

internal fun categories(s: PreviewState): List<String> {
    val template = listOf(
        "FAVORİ TV",
        "TÜMÜ",
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
        "7/24 DİZİ/FİLM"
    )
    val available = s.managedCategories.filter { cat ->
        !s.isCategoryHidden(cat) && s.data.channels.any { ch ->
            ch.available && (ch.groups.any { g -> CategoryNormalizer.matches(g, cat) } || CategoryNormalizer.matches(ch.category, cat))
        }
    }
    val result = mutableListOf<String>()
    for (item in template) {
        if (item == "FAVORİ TV" || item == "TÜMÜ") {
            result.add(item)
        } else {
            val isHidden = s.isCategoryHidden(item)
            if (!isHidden) {
                val matched = available.any { CategoryNormalizer.matches(it, item) }
                val channelMatched = s.data.channels.any { ch ->
                    ch.available && (ch.groups.any { g -> CategoryNormalizer.matches(g, item) } || CategoryNormalizer.matches(ch.category, item))
                }
                if (matched || channelMatched) {
                    result.add(item)
                }
            }
        }
    }
    var hasRadio = false
    for (cat in available) {
        if (CategoryNormalizer.matches(cat, "RADYO")) {
            hasRadio = true
            continue
        }
        val already = template.any { CategoryNormalizer.matches(cat, it) } ||
            result.any { CategoryNormalizer.matches(cat, it) }
        if (!already && !cat.equals("Favoriler", true) && !cat.equals("Son İzlenenler", true) && !cat.equals("Tümü", true)) {
            result.add(cat)
        }
    }
    val radioChannelMatched = !s.isCategoryHidden("RADYO") && s.data.channels.any { ch ->
        ch.available && (ch.groups.any { g -> CategoryNormalizer.matches(g, "RADYO") } || CategoryNormalizer.matches(ch.category, "RADYO"))
    }
    if ((hasRadio || radioChannelMatched) && result.none { CategoryNormalizer.matches(it, "RADYO") }) {
        result.add("RADYO")
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
    val returns = LocalFocusReturn.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            s.touchPanelInteraction()
        }
    }
    var lockedScroll by remember(s.moving, vertical) {
        mutableStateOf(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
    }
    LaunchedEffect(s.moving, s.draft, vertical) {
        val movingId = s.moving ?: return@LaunchedEffect
        if (listState.firstVisibleItemIndex != lockedScroll.first ||
            listState.firstVisibleItemScrollOffset != lockedScroll.second
        ) {
            listState.scrollToItem(lockedScroll.first, lockedScroll.second)
        }
        val movingIndex = values.indexOfFirst { it.id == movingId }
        val visible = listState.layoutInfo.visibleItemsInfo
        if (movingIndex >= 0 && visible.isNotEmpty() && visible.none { it.index == movingIndex }) {
            listState.scrollToItem(movingIndex)
            lockedScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }
    LaunchedEffect(s.selected, values.size, s.panel, vertical) {
        val index = values.indexOfFirst { it.id == s.selected }
        if (!s.searching && s.moving == null && index >= 0) {
            if (!vertical) {
                if (listState.layoutInfo.totalItemsCount == 0) {
                    withFrameNanos { }
                }
                val layoutInfo = listState.layoutInfo
                val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemWidth = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                if (viewportWidth > 0 && itemWidth > 0) {
                    val centerOffset = -((viewportWidth - itemWidth) / 2)
                    runCatching { listState.animateScrollToItem(index, centerOffset) }
                } else {
                    runCatching { listState.scrollToItem(index) }
                    withFrameNanos { }
                    val info2 = listState.layoutInfo
                    val vw2 = info2.viewportEndOffset - info2.viewportStartOffset
                    val iw2 = info2.visibleItemsInfo.firstOrNull()?.size ?: 0
                    if (vw2 > 0 && iw2 > 0) {
                        val centerOffset = -((vw2 - iw2) / 2)
                        runCatching { listState.scrollToItem(index, centerOffset) }
                    }
                }
            } else if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem(index)
            }
        }
    }
    LaunchedEffect(s.categoryFirstCardFocusRevision) {
        if (s.categoryFirstCardFocusRevision > 0 && values.isNotEmpty() && !s.searching && s.dialog == null) {
            val targetIndex = 0
            val targetId = values[targetIndex].id
            s.touchedFocusChannelId = targetId
            runCatching { listState.scrollToItem(targetIndex) }
            withFrameNanos { }
            withFrameNanos { }
            val key = "channel:${PreviewCodec.id(targetId)}"
            val requester = returns?.registry?.entryTarget(key, returns.active)
            runCatching { requester?.requestFocus() }
        }
    }
    val swipeThreshold = with(LocalDensity.current) { 44.dp.toPx() }
    val verticalModifier = if (s.moving == null) modifier.pointerInput(s.moving, swipeThreshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f }, onDragEnd = { if (distance >= swipeThreshold) s.categoryMenu = true }) { change, amount -> change.consume(); distance += amount }
    } else modifier
    if (vertical) LazyColumn(verticalModifier.focusGroup(), state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { items(values, key = { PreviewCodec.id(it.id) }) { ChannelCard(s, it, image, true) } }
    else LazyRow(modifier.focusGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(values, key = { PreviewCodec.id(it.id) }) { ChannelCard(s, it, image, false) } }
}
@Composable
private fun ChannelCard(s: PreviewState, ch: PreviewChannel, image: @Composable (String, Modifier) -> Unit, vertical: Boolean) {
    val returns = LocalFocusReturn.current
    val focusKey = "channel:${PreviewCodec.id(ch.id)}"
    val requester = remember(returns, focusKey) { returns?.registry?.requester(focusKey) { FocusRequester() } ?: FocusRequester() }
    var isCardFocused by remember { mutableStateOf(false) }
    val keySignal = LocalKeySignal.current
    val isFavoritesCategory = s.category.equals("Favoriler", ignoreCase = true) || s.category.equals("FAVORİ TV", ignoreCase = true)
    val number = if (vertical) {
        val idx = if (isFavoritesCategory && !s.searching) {
            s.visibleData.filtered("Favoriler", "").indexOfFirst { it.id == ch.id }
        } else {
            s.data.channels.indexOfFirst { it.id == ch.id }
        }
        if (idx >= 0) "${idx + 1}. " else ""
    } else if (isFavoritesCategory && !s.searching) {
        val favIdx = s.visibleData.filtered("Favoriler", "").indexOfFirst { it.id == ch.id }
        if (favIdx >= 0) "${favIdx + 1}. " else ""
    } else ""
    val isMoving = s.moving == ch.id
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val activeHighlightId = s.touchedFocusChannelId ?: s.selected
    val hasHighlightStroke = ch.id == activeHighlightId || isCardFocused || isMoving
    val strokeColor = if (isMoving) Color(0xFFFFCF37) else theme.cardSelectedStroke
    val strokeWidth = if (hasHighlightStroke) theme.cardSelectedStrokeWidthDp.dp else 0.dp
    val cardRadius = if (theme.isCardFlat) 0.dp else 8.dp
    val cardTint = if (theme.isCardFlat) Color.Transparent else theme.cardBackground
    val verticalSelectedTint = when (theme.name) {
        "Modern" -> Color(0xFF19394D)
        "Altın" -> Color(0xFF423214)
        "Sinema" -> Color(0xFF3D1014)
        else -> Color(0xFF6A92AF)
    }

    var cardWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var anchorWindowPos by remember { mutableStateOf(0f) }
    val cardSpanPx = with(LocalDensity.current) { (if (vertical) 38.5.dp else 69.dp).toPx() }
    val stepPx = with(LocalDensity.current) { (if (vertical) (38.5.dp + 5.dp) else (69.dp + 8.dp)).toPx() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(s.dialog, s.moving, windowFocused, s.searching) { if (windowFocused && s.dialog == null && !s.searching && s.target == ch.id) requester.requestFocus() }
    LaunchedEffect(s.selected) {
        if (windowFocused && s.dialog == null && !s.searching && s.selected == ch.id) requester.requestFocus()
    }
    LaunchedEffect(s.categoryFirstCardFocusRevision) {
        if (s.categoryFirstCardFocusRevision > 0 && windowFocused && s.dialog == null && !s.searching && s.touchedFocusChannelId == ch.id) {
            runCatching { requester.requestFocus() }
        }
    }
    Action(
        number + ch.name,
        (if (vertical) Modifier.fillMaxWidth().height(38.5.dp) else Modifier.width(69.dp).height(68.dp))
            .onFocusChanged { focusState ->
                isCardFocused = focusState.isFocused
                if (focusState.isFocused) {
                    s.touchedFocusChannelId = ch.id
                    s.touchPanelInteraction()
                }
            }
            .onGloballyPositioned { cardWindowOrigin = it.boundsInWindow().topLeft }
            .then(
                if (isMoving) Modifier.pointerInput(ch.id, vertical, stepPx, cardSpanPx) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            anchorWindowPos = if (vertical) (cardWindowOrigin.y + cardSpanPx * 0.5f) else (cardWindowOrigin.x + cardSpanPx * 0.5f)
                        },
                        onDragEnd = { s.moveFinish() },
                        onDragCancel = { s.draft = null; s.moving = null }
                    ) { change, _ ->
                        change.consume()
                        s.touchPanelInteraction()
                        val currentWindowPos = if (vertical) (cardWindowOrigin.y + change.position.y) else (cardWindowOrigin.x + change.position.x)
                        val triggerThreshold = stepPx * 0.55f
                        while (abs(currentWindowPos - anchorWindowPos) >= triggerThreshold && stepPx > 0f) {
                            val delta = currentWindowPos - anchorWindowPos
                            val dir = if (delta > 0f) 1 else -1
                            val before = s.draft?.favorites
                            s.moveBy(dir)
                            if (s.draft?.favorites != before) {
                                anchorWindowPos += dir * stepPx
                            } else {
                                break
                            }
                        }
                    }
                } else Modifier
            ),
        focusKey = focusKey,
        selected = ch.id == s.selected || isMoving,
        tint = cardTint,
        selectedTint = if (vertical) verticalSelectedTint else Color(0xB3A75F19),
        radius = cardRadius,
        strokeWidth = strokeWidth,
        stroke = strokeColor,
        focusColor = if (isMoving) Color(0xFFFFCF37) else if (vertical) theme.accentColor else Color.White,
        horizontalFocus = !vertical && !isMoving,
        channelFocus = !isMoving,
        padding = PaddingValues(0.dp),
        long = { s.target = ch.id; s.dialog = if (isFavoritesCategory) "favorite" else "channel" },
        keyClick = {
            if (isMoving) s.moveFinish()
            else {
                requester.requestFocus()
                s.selectFromList(ch.id)
            }
        },
        extraKey = { e ->
            val delta = channelArrowDelta(vertical, e.key)
            if (isMoving && e.type == KeyEventType.KeyDown && e.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) { s.moveBy(if (e.key in listOf(Key.DirectionUp, Key.DirectionLeft)) -1 else 1); true }
            else if (!isMoving && delta != null && s.panel && s.dialog == null) {
                if (e.type == KeyEventType.KeyDown && !keySignal(e).repeat && s.normalLiveControls) s.stepChannel(delta, keepPanel = true)
                true
            }
            else if (vertical && !isMoving && e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) {
                s.categoryMenu = true
                val catKey = "cat:${CategoryNormalizer.canonicalKey(s.category)}"
                val req = returns?.registry?.entryTarget(catKey, returns.active)
                if (req != null) runCatching { req.requestFocus() }
                true
            } else false
        },
        content = {
            if (vertical) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(if (ch.id == s.selected) theme.accentColor else Color.Transparent))
                    image(ch.logo, Modifier.size(30.dp))
                    BasicText(
                        number + ch.name,
                        Modifier.weight(1f),
                        style = TextStyle(color = if (ch.id == s.selected) Color.White else Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = if (ch.id == s.selected) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ch.id in s.data.favorites) {
                        Label("★", 11, Color(0xFFFFD54F), bold = true)
                    }
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
    ) {
        if (isMoving) {
            s.moveFinish()
        } else {
            s.touchedFocusChannelId = ch.id
            s.touchPanelInteraction()
            runCatching { requester.requestFocus() }
            s.selectFromList(ch.id)
        }
    }
}

private val sections = listOf("Kanallar Ve Listeler", "Görünüm", "Oynatma", "Uygulama", "Yedekleme Ve Aktarım", "Beleş TiVi +", "Yardım Ve Hakkında")
private val sectionsEn = listOf("Channels And Lists", "Appearance", "Playback", "Application", "Backup And Transfer", "Beleş TiVi Plus", "Help And About")
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
    "radioBg" to listOf("Varsayılan", "Koyu Neon", "Klasik Ahşap"),
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
    "radioBg" to "Radyo Arka Planı",
    "sleepTimer" to "Uyku Zamanlayıcısı",
    "subtitleSize" to "Altyazı Boyutu",
    "subtitleColor" to "Altyazı Rengi",
    "subtitleBgOpacity" to "Altyazı Arka Plan Opaklığı",
)
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
    "radioBg" to "ikon_ses",
    "sleepTimer" to "ikon_saat",
    "subtitleSize" to "ikon_size",
    "subtitleColor" to "ikon_gorunum",
    "subtitleBgOpacity" to "ikon_opacity",
)

@Composable
private fun SettingsTabIcon(
    index: Int,
    isSelected: Boolean,
    plusColor: Color = Color(0xFFFFB300),
    modifier: Modifier = Modifier
) {
    val defaultColor = if (isSelected) Color.White else Color(0xFFB0B0B0)
    val color = if (index == 5) plusColor else defaultColor
    val strokeWidth = 1.6f

    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height

        when (index) {
            0 -> { // KANALLAR VE LİSTELER: TV / Ekran
                val screenW = w * 0.85f
                val screenH = h * 0.58f
                val left = (w - screenW) / 2f
                val top = h * 0.12f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(screenW, screenH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = strokeWidth.dp.toPx())
                )
                val stemTop = top + screenH
                val stemBottom = stemTop + h * 0.16f
                drawLine(
                    color = color,
                    start = Offset(w / 2f, stemTop),
                    end = Offset(w / 2f, stemBottom),
                    strokeWidth = strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val baseW = w * 0.45f
                drawLine(
                    color = color,
                    start = Offset(w / 2f - baseW / 2f, stemBottom),
                    end = Offset(w / 2f + baseW / 2f, stemBottom),
                    strokeWidth = strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            1 -> { // GÖRÜNÜM: Palet / Kadran
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.42f
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth.dp.toPx())
                )
                val dotR = 1.5.dp.toPx()
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.35f, h * 0.38f))
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.65f, h * 0.38f))
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.50f, h * 0.65f))
            }
            2 -> { // OYNATMA: Oynat / Play Üçgeni
                val path = Path().apply {
                    val padX = w * 0.28f
                    val padY = h * 0.22f
                    moveTo(padX, padY)
                    lineTo(w - padX + 2.dp.toPx(), h / 2f)
                    lineTo(padX, h - padY)
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth.dp.toPx(), join = StrokeJoin.Round)
                )
            }
            3 -> { // UYGULAMA: Ayar Sürgüleri / Equalizer
                val lineStroke = strokeWidth.dp.toPx()
                val knobR = 2.2.dp.toPx()
                val y1 = h * 0.25f
                drawLine(color = color, start = Offset(w * 0.15f, y1), end = Offset(w * 0.85f, y1), strokeWidth = lineStroke, cap = StrokeCap.Round)
                drawCircle(color = color, radius = knobR, center = Offset(w * 0.40f, y1))

                val y2 = h * 0.52f
                drawLine(color = color, start = Offset(w * 0.15f, y2), end = Offset(w * 0.85f, y2), strokeWidth = lineStroke, cap = StrokeCap.Round)
                drawCircle(color = color, radius = knobR, center = Offset(w * 0.68f, y2))

                val y3 = h * 0.79f
                drawLine(color = color, start = Offset(w * 0.15f, y3), end = Offset(w * 0.85f, y3), strokeWidth = lineStroke, cap = StrokeCap.Round)
                drawCircle(color = color, radius = knobR, center = Offset(w * 0.32f, y3))
            }
            4 -> { // YEDEKLEME VE AKTARIM: Tepsi + Yukarı Ok
                val stroke = strokeWidth.dp.toPx()
                val trayPath = Path().apply {
                    val yT = h * 0.62f
                    val yB = h * 0.84f
                    val xL = w * 0.20f
                    val xR = w * 0.80f
                    moveTo(xL, yT)
                    lineTo(xL, yB)
                    lineTo(xR, yB)
                    lineTo(xR, yT)
                }
                drawPath(path = trayPath, color = color, style = Stroke(width = stroke, join = StrokeJoin.Round))
                val midX = w / 2f
                val arrowTop = h * 0.18f
                val arrowBottom = h * 0.66f
                drawLine(color = color, start = Offset(midX, arrowBottom), end = Offset(midX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
                val headW = w * 0.22f
                val headH = h * 0.20f
                drawLine(color = color, start = Offset(midX - headW, arrowTop + headH), end = Offset(midX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(midX + headW, arrowTop + headH), end = Offset(midX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            5 -> { // BELEŞ TİVİ +: 4 Köşeli Yıldız ✦
                val cx = w / 2f
                val cy = h / 2f
                val outerR = w * 0.44f
                val starPath = Path().apply {
                    moveTo(cx, cy - outerR)
                    quadraticTo(cx, cy, cx + outerR, cy)
                    quadraticTo(cx, cy, cx, cy + outerR)
                    quadraticTo(cx, cy, cx - outerR, cy)
                    quadraticTo(cx, cy, cx, cy - outerR)
                    close()
                }
                drawPath(path = starPath, color = color)
            }
            6 -> { // YARDIM VE HAKKINDA: ⓘ Bilgi
                val center = Offset(w / 2f, h / 2f)
                val radius = w * 0.42f
                drawCircle(color = color, radius = radius, center = center, style = Stroke(width = strokeWidth.dp.toPx()))
                drawCircle(color = color, radius = 1.3.dp.toPx(), center = Offset(w / 2f, h * 0.32f))
                drawLine(
                    color = color,
                    start = Offset(w / 2f, h * 0.46f),
                    end = Offset(w / 2f, h * 0.72f),
                    strokeWidth = strokeWidth.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable private fun Settings(s: PreviewState, image: @Composable (String, Modifier) -> Unit, appVersionName: String) {
    val contentScroll = rememberScrollState()
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }

    // Sekme veya alt sayfa (dialog) değiştiğinde içeriğin en başa kaydırılması (C34, C13)
    LaunchedEffect(s.section, s.dialog) {
        contentScroll.scrollTo(0)
    }

    CompositionLocalProvider(
        LocalSettingsFocusStroke provides theme.settingsFocusStroke,
        LocalSettingsFocusFill provides theme.settingsFocusFill,
        LocalSettingsThemeAccent provides theme.settingsAccent
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(theme.settingsBg)) {
            val compact = maxWidth < 600.dp
            Row(Modifier.fillMaxSize()) {
                if (!compact) Column(Modifier.width(148.dp).fillMaxHeight().background(theme.settingsPanelBg)) {
                    Row(Modifier.height(74.dp).padding(horizontal = 19.dp), verticalAlignment = Alignment.CenterVertically) { Label("Ayarlar", 18, modifier = Modifier.weight(1f), bold = true); Action("✕", tint = Color.Transparent, strokeWidth = 0.dp, click = s::back) }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        sections.forEachIndexed { i, title -> Action(
                            label = title,
                            modifier = Modifier.fillMaxWidth().height(47.dp).onFocusChanged {
                                if (it.isFocused && s.section != i) {
                                    s.dismiss()
                                    s.cancelOpacity()
                                    s.section = i
                                }
                            },
                            focusKey = "settings:section:$i",
                            tint = Color.Transparent,
                            selected = s.section == i,
                            selectedTint = theme.settingsSelectedTabBg,
                            strokeWidth = 0.dp,
                            padding = PaddingValues(horizontal = 9.dp),
                            onFocused = {
                                if (s.section != i) {
                                    s.dismiss()
                                    s.cancelOpacity()
                                    s.section = i
                                }
                            },
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SettingsTabIcon(i, s.section == i, plusColor = theme.settingsAccent)
                                    Label(
                                        s.text(title, sectionsEn[i]),
                                        11,
                                        color = if (i == 5) {
                                            if (s.section == i) theme.settingsAccent else theme.settingsAccent.copy(alpha = 0.85f)
                                        } else {
                                            if (s.section == i) Color.White else Color(0xFFB0B0B0)
                                        },
                                        modifier = Modifier.weight(1f),
                                        bold = true,
                                        lineHeight = 14f,
                                        maxLines = 2
                                    )
                                }
                            }
                        ) { s.dismiss(); s.cancelOpacity(); s.section = i } }
                    }
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Label("Beleş TiVi · v$appVersionName", 9, Color(0xFF7A89A0), bold = true)
                        Label("Kambak Yazılım © 2026", 8, Color(0xFF5A687D))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (compact) LazyRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(sections.size) { i -> Action(sections[i], focusKey = "settings:section:$i", selected = s.section == i, selectedTint = theme.settingsSelectedTabBg) { s.section = i } }; item { Action("✕", click = s::back) } }
                    if (s.dialog in sourceFormKinds || s.dialog == "categories") CompositionLocalProvider(LocalModalLayer provides true) {
                        if (s.dialog == "categories") CategoryManagement(s, image, Modifier.weight(1f)) else SourceForm(s, image, Modifier.weight(1f))
                    } else Column(Modifier.weight(1f).verticalScroll(contentScroll).padding(horizontal = 16.dp, vertical = 38.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (s.section != 1) Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(3.dp).height(16.dp).background(theme.settingsAccent)); Label(settingsSectionHeading(s.section), 10, Color(0xFF94A2B7), Modifier.padding(start = 10.dp), true) }
                        when (s.section) {
                            0 -> {
                                SettingsGroup("LİSTEM")
                                SettingTile("Aktif Listem", "Beleş TiVi Varsayılan Listesi", "activesources", image, s.profileName, iconKey = "activesources") { s.sourceForm("activeSources") }
                                SettingTile("Ek Liste Ekle", s.data.library?.extra?.name ?: "Beleş TiVi listesine ek bir liste birleştir", "supplement", image, iconKey = "supplement") { s.sourceForm("supplement") }
                                SettingTile("Listeyi Güncelle", "En güncel kanal listelerini indir", "refresh", image, iconKey = "refresh") { s.refreshSource() }
                                SettingTile("Varsayılan Listeye Geri Dön", "Varsayılan listeye geri dön", "defaultsource", image, iconKey = "defaultsource") { s.dialog = "defaultSource" }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("KENDİ LİSTENİ EKLE")
                                SettingTile("M3U URL", "Özel liste adresi ile bağlan", "m3u", image, iconKey = "m3u") { s.sourceForm("m3u") }
                                SettingTile("Xtream Codes", "Sunucu bilgileri ile bağlantı", "xtream", image, iconKey = "xtream") { s.sourceForm("xtream") }
                                SettingTile("Stalker / MAC", "Portal adresi ve kayıtlı MAC ile bağlan", "stalker", image, iconKey = "stalker") { s.sourceForm("stalker") }
                                SettingTile("Dosyadan Yükle", "M3U dosyası seç", "file", image, iconKey = "file") { s.dialog = "file" }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("ÜLKE LİSTELERİ")
                                SettingTile("Ülke Listeleri", s.countryListsSubtitle, "countries", image, iconKey = "countries") { s.sourceForm("countries") }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("EPG (PROGRAM REHBERİ)")
                                SettingTile(
                                    "XMLTV EPG URL",
                                    s.data.pref("epgUrl").ifBlank { "Yerleşik örnek EPG (epg_sample.xml) aktif" },
                                    "epg",
                                    image,
                                    iconKey = "epg"
                                ) { s.sourceForm("epg") }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("KANAL DÜZENİ")
                                SettingTile("Kategorileri Yönet", "Sıralama ve görünürlük", "categories", image, iconKey = "categories") { s.dialog = "categories" }
                            }
                            1 -> {
                                SettingsGroup("UYGULAMA TEMASI"); SettingCard(s, "theme", image)
                                SettingCard(s, "radioBg", image)
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
                                SettingTile("Favorilerden Başlat", "Açılışta doğrudan favori kanallardan birini aç", "startupfavorites", image, s.data.pref("startupFavorites"), toggle = true, iconKey = "startupfavorites") { s.pref("startupFavorites", if (s.data.pref("startupFavorites") == "Açık") "Kapalı" else "Açık") }
                                SettingTile("Açılışta Son İzlenen Kanala Git", "Uygulama açılış tercihi", "startup", image, s.data.pref("startup"), toggle = true, iconKey = "startup") { s.pref("startup", if (s.data.pref("startup") == "Açık") "Kapalı" else "Açık") }
                                SettingTile("Arka Planda Sesi Sürdür", "Uygulama arka plandayken ses çalmaya devam eder", "backgroundaudio", image, s.data.pref("backgroundAudio"), toggle = true, iconKey = "backgroundaudio") { s.pref("backgroundAudio", if (s.data.pref("backgroundAudio") == "Açık") "Kapalı" else "Açık") }
                                SettingTile("Resim İçinde Resim (PiP)", "Home veya arka plana geçişte otomatik PiP", "autopip", image, s.data.pref("autoPip"), toggle = true, iconKey = "autopip") { s.pref("autoPip", if (s.data.pref("autoPip") == "Açık") "Kapalı" else "Açık") }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("GEÇMİŞ")
                                SettingTile("İzleme Geçmişi", "Son izlenen kanalları göster", "history", image, s.data.pref("history"), toggle = true, iconKey = "history") { s.pref("history", if (s.data.pref("history") == "Açık") "Kapalı" else "Açık") }
                                SettingTile("İzleme Geçmişini Temizle", "${s.data.history.size} kayıt", "clear", image, danger = true, iconKey = "clear") { s.dialog = "clear" }
                            }
                            4 -> {
                                SettingsGroup("TELEFONDAN AKTAR")
                                SettingTile("Telefondan Aktar", "Yerel ağ üzerinden M3U / Xtream / Stalker gönder", "webtransfer", image, iconKey = "webtransfer") { s.openWebTransfer() }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("FAVORİLER")
                                SettingTile("Favorileri Dışa Aktar", "Favori listesini yedekle", "export", image, iconKey = "export") { s.dialog = "export" }
                                SettingTile("Favorileri İçe Aktar", "Yedekten geri yükle", "import", image, iconKey = "import") { s.dialog = "import" }
                            }
                            5 -> {
                                SettingsGroup("BELEŞ TİVİ +")
                                SettingPlusCard(
                                    title = "Tüm Temalar Açık",
                                    subtitle = "Altın ve Sinema temaları ücretsiz kullanımda",
                                    badge = "AÇIK",
                                    click = { s.section = 1 }
                                )
                                SettingPlusCard(
                                    title = "Gelişmiş Çoklu Ekran",
                                    subtitle = "4 ekrana kadar eşzamanlı izleme",
                                    badge = "AKTİF",
                                    click = { s.dismiss(); s.categoryAction("multi") }
                                )
                                SettingPlusCard(
                                    title = "Sıfır Reklam, Kesintisiz Canlı TV",
                                    subtitle = "Tanıtım kutusu ve reklamlar yok, tam ekran akış",
                                    badge = "SINIRSIZ",
                                    click = { s.notice = "Beleş TiVi Clone'da tüm reklamlar kaldırılmıştır; kesintisiz yayın aktiftir." }
                                )
                                SettingPlusCard(
                                    title = "Telefondan Kolay Aktarım",
                                    subtitle = "Yerel ağ üzerinden M3U / Xtream / Stalker tek tıkla transfer",
                                    badge = "ÜCRETSİZ",
                                    click = { s.openWebTransfer() }
                                )
                                SettingPlusCard(
                                    title = "Özel Kaynak ve Ülke Listeleri",
                                    subtitle = "Xtream, Stalker, M3U ve KKTC/AZ/Dünya listeleri tam açık",
                                    badge = "TAM ERİŞİM",
                                    click = { s.section = 0 }
                                )
                            }
                            6 -> {
                                SettingsGroup("UYGULAMA")
                                SettingTile("Beleş TiVi Clone", "Android & Google TV · $appVersionName", "about", image, iconKey = "about") { }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("AĞ VE YAYIN KONTROLÜ")
                                SettingTile("Bağlantıyı Kontrol Et", "Ağ, DNS ve canlı yayın erişim testi", "network", image, iconKey = "network") { s.openNetworkDiagnostics() }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("DESTEK & İLETİŞİM")
                                SettingTile("Destek", "Yardım ve iletişim", "support", image, iconKey = "support") { s.notice = "Destek ve iletişim kanalları yakında aktif olacaktır." }
                                SettingTile("Telegram Kanalı", "Topluluk ve duyurular", "telegram", image, iconKey = "telegram") { s.notice = "Telegram topluluk grubu yakında eklenecektir." }
                                Spacer(Modifier.height(20.dp)); SettingsGroup("DİĞER İŞLEMLER")
                                SettingTile("Önbelleği Temizle", "Liste güncellemeden ayrı işlem", "cache", image, iconKey = "cache") { s.dialog = "cache" }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).background(theme.settingsBottomBarBg).padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(if (theme.name == "Modern") Color(0xFF047857) else Color(0xFF0ABB91), RoundedCornerShape(2.dp)))
                            Label("Beleş TiVi Clone v$appVersionName", 10, Color.White, bold = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Label("Aktif: ${s.profileName.ifBlank { "M3U / Yerel TR Listesi" }}", 10, Color(0xFF94A2B7), bold = true)
                            Label("•", 10, Color(0xFF69798F))
                            Label("Kanal Sayısı: ${s.activeCount}", 10, Color(0xFF94A2B7), bold = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TopBarActionIcon("ikon_saat", color = Color(0xFF8A99AD), modifier = Modifier.size(11.dp))
                            Label(if (s.busy) "İşlem sürüyor…" else "Son Güncelleme: Güncel", 10, Color(0xFF8A99AD))
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun SettingTile(
    title: String,
    subtitle: String,
    icon: String,
    image: @Composable (String, Modifier) -> Unit,
    value: String = "",
    toggle: Boolean = false,
    danger: Boolean = false,
    visibleTitle: String = title,
    iconKey: String = "",
    click: () -> Unit
) {
    val themeFocusStroke = LocalSettingsFocusStroke.current ?: amber
    val themeAccent = LocalSettingsThemeAccent.current ?: amber
    val cardBg = remember(themeFocusStroke) {
        when (themeFocusStroke) {
            Color(0xFF67E8F9) -> Color(0xFF131F32)
            Color(0xFFFFC107) -> Color(0xFF242428)
            Color(0xFFE50914) -> Color(0xFF1A1A20)
            else -> Color(0xFF13233D)
        }
    }
    val effectiveKey = if (iconKey.isNotBlank()) iconKey else icon
    Action(title, Modifier.fillMaxWidth().height(58.dp), radius = 10.dp, strokeWidth = 0.dp, tint = cardBg, focusKey = "settings:$title", padding = PaddingValues(horizontal = 20.dp), content = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            SettingsRowIcon(
                key = effectiveKey,
                color = if (danger) Color(0xFFF44747) else themeAccent,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) { Label(visibleTitle, 12, if (danger) Color(0xFFF44747) else Color.White, bold = true, lineHeight = 18f, maxLines = 1); if (subtitle.isNotEmpty()) Label(subtitle, 10, Color(0xFF69798F), lineHeight = 16f, maxLines = 1) }
            if (toggle) Box(Modifier.defaultMinSize(minWidth = 55.dp).height(28.dp).background(if (value == "Açık") Color(0xFF0ABB91) else Color(0xFF68798F), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Label("• $value", 10, bold = true) }
            else { if (value.isNotEmpty()) Label(value, 10, themeAccent, bold = true); Label("→", 13, Color(0xFF60728E)) }
        }
    }, click = click)
}

@Composable
private fun SettingPlusCard(
    title: String,
    subtitle: String,
    badge: String,
    click: () -> Unit
) {
    val themeFocusStroke = LocalSettingsFocusStroke.current ?: amber
    val themeAccent = LocalSettingsThemeAccent.current ?: Color(0xFFFFB300)
    val cardBg = remember(themeFocusStroke) {
        when (themeFocusStroke) {
            Color(0xFF67E8F9) -> Color(0xFF131F32)
            Color(0xFFFFC107) -> Color(0xFF242428)
            Color(0xFFE50914) -> Color(0xFF1A1A20)
            else -> Color(0xFF13233D)
        }
    }
    Action(
        title,
        Modifier.fillMaxWidth().height(58.dp),
        radius = 10.dp,
        strokeWidth = 0.dp,
        tint = cardBg,
        focusKey = "settings:plus:$title",
        padding = PaddingValues(horizontal = 20.dp),
        content = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerR = size.width * 0.44f
                    val starPath = Path().apply {
                        moveTo(cx, cy - outerR)
                        quadraticTo(cx, cy, cx + outerR, cy)
                        quadraticTo(cx, cy, cx, cy + outerR)
                        quadraticTo(cx, cy, cx - outerR, cy)
                        quadraticTo(cx, cy, cx, cy - outerR)
                        close()
                    }
                    drawPath(path = starPath, color = themeAccent)
                }
                Column(Modifier.weight(1f)) {
                    Label(title, 12, Color.White, bold = true, lineHeight = 18f, maxLines = 1)
                    if (subtitle.isNotEmpty()) Label(subtitle, 10, Color(0xFF8CA0BA), lineHeight = 16f, maxLines = 1)
                }
                Box(
                    Modifier.background(themeAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .border(1.dp, themeAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Label(badge, 9, themeAccent, bold = true)
                }
                Label("→", 13, Color(0xFF60728E))
            }
        },
        click = click
    )
}
@Composable private fun SettingCard(s: PreviewState, key: String, image: @Composable (String, Modifier) -> Unit) {
    if (key == "opacity") OpacityControl(s, image)
    else SettingTile(titles.getValue(key), "", key, image, s.data.pref(key), iconKey = key, visibleTitle = when (key) { "position" -> "Konum"; "size" -> "Boyut"; "subtitleSize" -> "Boyut"; "subtitleColor" -> "Renk"; "subtitleBgOpacity" -> "Arka Plan Opaklığı"; "sleepTimer" -> "Süre"; else -> titles.getValue(key) }) { s.dialog = key }
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
    val ch = s.data.channels.find { it.id == s.target } ?: s.radioChannels.find { it.id == s.target } ?: return
    val first = remember { FocusRequester() }
    val favorite = s.dialog == "favorite"
    val contentDensity = LocalDensity.current
    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val menuShape = RoundedCornerShape(16.dp)
    val isFav = ch.id in s.data.favorites
    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Column(
                Modifier
                    .width(268.dp)
                    .background(theme.settingsPanelBg, menuShape)
                    .border(1.dp, theme.settingsCardBorder, menuShape)
                    .clip(menuShape)
                    .padding(10.dp)
                    .focusGroup()
                    .semantics { paneTitle = ch.name },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(theme.settingsCardBg, RoundedCornerShape(11.dp))
                        .border(1.dp, theme.settingsCardBorder.copy(alpha = 0.7f), RoundedCornerShape(11.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(Color(0xFF0A1422), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        image(ch.logo.ifEmpty { "ikon_tv" }, Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label(ch.name, 12, theme.headingTitleColor, bold = true, maxLines = 1)
                        Label(ch.category.ifEmpty { if (ch.isAudioOrRadio) "Radyo" else "TV" }, 9, theme.settingsMutedText, maxLines = 1)
                    }
                    if (isFav) {
                        Label("★", 13, Color(0xFFFFD600), bold = true)
                    }
                }
                @Composable
                fun row(label: String, iconKind: String, modifier: Modifier = Modifier, dismiss: Boolean = false, click: () -> Unit) {
                    if (dismiss) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .height(1.dp)
                                .background(theme.settingsCardBorder.copy(alpha = 0.5f))
                        )
                    }
                    Action(
                        label,
                        modifier.fillMaxWidth().height(if (dismiss) 36.dp else 42.dp),
                        enabled = !s.busy,
                        tint = Color.Transparent,
                        radius = 10.dp,
                        strokeWidth = 0.dp,
                        focusColor = theme.accentColor,
                        focusFill = theme.focusFill,
                        horizontalFocus = false,
                        padding = PaddingValues(horizontal = 10.dp),
                        content = {
                            if (dismiss) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    ChannelMenuIcon("cancel", color = theme.settingsMutedText, modifier = Modifier.size(12.dp))
                                    Label(label, 10, theme.settingsMutedText, bold = true)
                                }
                            } else {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(28.dp)
                                            .background(theme.settingsCardBg, RoundedCornerShape(7.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ChannelMenuIcon(iconKind, color = theme.accentColor, modifier = Modifier.size(15.dp))
                                    }
                                    Label(label, 11, Color.White, Modifier.weight(1f), bold = true)
                                    Label("›", 14, theme.settingsMutedText, bold = true)
                                }
                            }
                        },
                        click = click
                    )
                }
                row(
                    if (isFav) "Favorilerden kaldır" else "Favorilere ekle",
                    if (isFav) "favorite_remove" else "favorite_add",
                    Modifier.focusRequester(first)
                ) { s.toggleFavoriteWithToast(ch.id) }
                if (favorite) {
                    row("Sırasını değiştir…", "reorder", click = s::moveStart)
                    row("En başa taşı", "move_start") { s.moveFavoriteToStart(ch.id) }
                    row("En sona taşı", "move_end") { s.moveFavoriteToEnd(ch.id) }
                } else {
                    row("Kanalı gizle", "hide") { s.hideChannelWithToast(ch.id) }
                    row("Gizlenen kanalları geri getir", "unhide") { s.commit(s.data.copy(hidden = emptySet())) { s.dismiss() } }
                }
                row("Vazgeç", "cancel", dismiss = true, click = s::dismiss)
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
private fun LiveFormatStrip(s: PreviewState) {
    val selectedRequester = remember { FocusRequester() }
    val boundsObserver = LocalBoundsObserver.current
    val currentFormat = s.data.pref("format")
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { s.dismiss() } }
            .semantics { paneTitle = "GÖRÜNTÜ ORANI" }
    ) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 56.dp, start = 50.dp, end = 18.dp)
                .onGloballyPositioned { boundsObserver("format-strip", it.boundsInWindow()) }
                .background(Color(0xEB0D1829), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x664A6585), RoundedCornerShape(22.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveFormatOptions.forEachIndexed { index, option ->
                val chosen = isVideoFormatSelected(currentFormat, option)
                val isInitialFocus = chosen || (LiveFormatOptions.none { isVideoFormatSelected(currentFormat, it) } && index == 0)
                val visibleText = if (chosen) "✓ $option" else option
                Action(
                    label = option,
                    modifier = Modifier
                        .height(31.dp)
                        .then(if (isInitialFocus) Modifier.focusRequester(selectedRequester) else Modifier),
                    selected = chosen,
                    enabled = !s.busy,
                    tint = Color.Transparent,
                    selectedTint = Color(0xFF284264),
                    radius = 15.dp,
                    stroke = if (chosen) Color(0xFF6B93C0) else Color.Transparent,
                    strokeWidth = if (chosen) 1.dp else 0.dp,
                    focusColor = Color(0xFFFFB300),
                    padding = PaddingValues(horizontal = 11.dp, vertical = 5.dp),
                    extraKey = { e ->
                        if (!s.busy && e.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                            if (e.type == KeyEventType.KeyUp) {
                                s.selectLiveFormat(option, closeMenu = true)
                            }
                            true
                        } else false
                    },
                    content = {
                        Label(
                            visibleText,
                            11,
                            color = if (chosen) Color.White else Color(0xFFD6E0EE),
                            bold = true,
                            maxLines = 1
                        )
                    },
                    click = { s.selectLiveFormat(option, closeMenu = false) }
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { selectedRequester.requestFocus() } }
    }
}

@Composable
private fun SourceErrorDialog(s: PreviewState) {
    val okRequester = remember { FocusRequester() }
    val title = s.sourceErrorDialogTitle.ifBlank { "Kaynağa bağlanılamadı" }
    val msg = s.sourceErrorDialogMessage.ifBlank { "Lütfen geçerli bir M3U çalma listesi adresi girin." }
    ReferenceOverlay(s, title, false) {
        Column(
            Modifier
                .align(Alignment.Center)
                .width(420.dp.coerceAtMost(maxWidth - 32.dp))
                .background(Color(0xFF13233D), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF283D5C), RoundedCornerShape(12.dp))
                .padding(20.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("⚠️", 18)
                Label(title, 14, Color.White, bold = true)
            }
            Label(msg, 11, Color(0xFFB0BAC9), lineHeight = 16f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Action(
                    label = "Tamam",
                    modifier = Modifier.width(90.dp).height(36.dp).focusRequester(okRequester),
                    tint = Color(0xFFE67E22),
                    radius = 8.dp,
                    strokeWidth = 0.dp,
                    content = { Label("Tamam", 11, Color.White, bold = true) }
                ) {
                    s.dialog = s.lastSourceDialog ?: "m3u"
                }
            }
        }
        LaunchedEffect(Unit) { okRequester.requestFocus() }
    }
}

@Composable
private fun Modal(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val kind = s.dialog ?: return
    if (kind == "exit") { ExitDialog(s, image); return }
    if (kind == "sourceError") { SourceErrorDialog(s); return }
    if (kind == "webTransfer") { WebTransferDialog(s, image); return }
    if (kind == "networkDiagnostics") { NetworkDiagnosticsDialog(s, image); return }
    if (kind in sourceFormKinds || kind == "categories") return
    if (kind == "format" && s.page == "live") { LiveFormatStrip(s); return }
    if (kind in choices) { OptionPicker(s, kind, choices.getValue(kind), optionHeading(kind)); return }
    if (kind == "warning") { MultiWarning(s, image); return }
    if (kind == "layout") { LayoutChooser(s, image); return }
    if (kind == "slotMenu") { MultiSlotMenuModal(s); return }
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
                    Label(s.message("Etkin kaynak klonun TiviListesi listesine dönecek. Kayıtlı listeler, favoriler ve tercihler korunur.", "Switch to the clone's TiviListesi source. Saved lists, favorites and preferences are preserved.", "Zur TiviListesi-Quelle wechseln. Listen, Favoriten und Einstellungen bleiben erhalten."))
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
                        s.retry + m.retries[index],
                        m.slotFormat(index),
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
                keyClick = {
                    m.selected = index
                    s.activateMultiSlot(index)
                },
                content = {
                    if (ch != null) {
                        Box(Modifier.fillMaxSize()) {
                            if (playbackUri(ch) == null) {
                                Column(
                                    Modifier.align(Alignment.Center).padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Label("Yayın açılamadı", 13, Color.White, bold = true)
                                    Label("OK: Tekrar dene veya kanal değiştir", 10, Color(0xFFD0D7DE), bold = true)
                                }
                            }
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
                            Label("+", 24, if (isFocusedSlot) Color(0xFFFFD600) else Color(0xFF626669), bold = true)
                            Label("Kanal Ekle (Ekran ${index + 1})", 13, if (isFocusedSlot) Color(0xFFFFD600) else Color.White, bold = true)
                            Label("Kategori ve arama ile kanal seçin", 9, Color(0xFF8A9AB0))
                        }
                    }
                }
            ) {
                if (m.selected == index) s.activateMultiSlot(index)
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
