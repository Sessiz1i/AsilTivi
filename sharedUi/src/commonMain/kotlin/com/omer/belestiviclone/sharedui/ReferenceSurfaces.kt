package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

internal val LocalModalLayer = staticCompositionLocalOf { false }
private val referenceCard = Color(0xFF13233D)

/** Uses the app's content rectangle and scaled density, never screenshot pixels as Android dp. */
@Composable
internal fun ReferenceOverlay(s: PreviewState, title: String, quality: Boolean = false, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    CompositionLocalProvider(LocalModalLayer provides true) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (quality) 0f else .60f))
            .pointerInput(quality) { detectTapGestures { point ->
                // T6's undimmed quality popup leaves the settings close target available.
                if (quality && point.x in 100.dp.toPx()..140.dp.toPx() && point.y in 20.dp.toPx()..55.dp.toPx()) {
                    s.dismiss(); s.back()
                } else s.dismiss()
            } }
            .semantics {
                paneTitle = title
                customActions = listOf(CustomAccessibilityAction("Kapat") { s.dismiss(); true }) +
                    if (quality) listOf(CustomAccessibilityAction("Ayarlardan çık") { s.dismiss(); s.back(); true }) else emptyList()
            }, content = content)
    }
}

@Composable
internal fun OptionPicker(s: PreviewState, kind: String, values: List<String>, title: String) {
    val quality = kind == "quality"
    val selected = remember(kind) { FocusRequester() }
    ReferenceOverlay(s, title, quality) {
        val width = if (quality) maxWidth * (256f / 855f) else 470.dp.coerceAtMost(maxWidth - 32.dp)
        val height = when (kind) { "list" -> 144.dp; "audio", "subtitle" -> 413.dp; "quality" -> 211.dp; else -> 188.dp }
        val placement = if (quality) Modifier.offset(x = maxWidth * (560f / 855f), y = maxHeight * (135f / 481f))
            else Modifier.align(Alignment.Center)
        Column(placement.width(width).heightIn(max = maxHeight - 12.dp).height(height)
            .background(referenceCard, RoundedCornerShape(if (quality) 10.dp else 0.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = if (quality) 17.dp else 19.dp).focusGroup()) {
            Label(title, 9, Color(0xFF94A2B7), bold = true, lineHeight = 16f, letterSpacing = if (quality) 0f else 1f)
            Spacer(Modifier.height(if (quality) 5.dp else 7.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.forEachIndexed { index, value ->
                    val chosen = s.data.pref(kind) == value
                    Action(value, Modifier.fillMaxWidth().height(if (quality) 41.5.dp else 44.5.dp)
                        .then(if (chosen || (s.data.pref(kind) !in values && index == 0)) Modifier.focusRequester(selected) else Modifier),
                        tint = Color.Transparent, radius = 0.dp, strokeWidth = 0.dp, focusColor = Color(0xFFE67E22), enabled = !s.busy,
                        padding = PaddingValues(horizontal = 2.dp), content = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                Label(value, 12, Color(0xFFE9EEF5), bold = true)
                                if (chosen) Label("✓", 20, Color(0xFFE67E22), if (quality) Modifier.align(Alignment.CenterEnd).padding(end = 5.dp) else Modifier.offset(x = 184.dp))
                            }
                        }) { s.pref(kind, value) }
                }
            }
        }
        LaunchedEffect(kind) { selected.requestFocus() }
    }
}

@Composable
internal fun LayoutChooser(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val first = remember { FocusRequester() }
    ReferenceOverlay(s, "Çoklu Ekran Düzeni") {
        Box(Modifier.align(Alignment.Center).width(470.dp.coerceAtMost(maxWidth - 32.dp)).height(370.dp.coerceAtMost(maxHeight - 12.dp))
            .background(Color(0xFF15253E)).pointerInput(Unit) { detectTapGestures { } }.focusGroup()) {
            Label("Çoklu Ekran Düzeni", 15, modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp), bold = true, lineHeight = 20f)
            Label("Kaç ekranı aynı anda izlemek istersin?", 10, Color(0xFF9AA8BC), Modifier.align(Alignment.TopCenter).padding(top = 50.dp))
            listOf(2, 4).forEach { count ->
                Action("$count Ekran", Modifier.align(Alignment.TopCenter).offset(y = if (count == 2) 102.dp else 220.dp).width(300.dp).height(if (count == 2) 83.dp else 116.dp)
                    .then(if (count == 2) Modifier.focusRequester(first) else Modifier), tint = Color.Transparent, strokeWidth = 0.dp, radius = 0.dp, padding = PaddingValues(0.dp), content = {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        image(if (count == 2) "ikon_iki" else "ikon_dort", Modifier.size(68.dp, if (count == 2) 33.dp else 66.dp))
                        Spacer(Modifier.height(10.dp)); Label("$count Ekran", 12, bold = true, lineHeight = 16f)
                        Spacer(Modifier.height(5.dp)); Label(if (count == 2) "Yan yana izleme · Orta performans" else "Izgara düzeni · Yüksek performans", 9, Color(0xFF78869D), lineHeight = 12f)
                    }
                }) { s.startMulti(count) }
            }
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }
}

@Composable
internal fun MultiWarning(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val cancel = remember { FocusRequester() }
    ReferenceOverlay(s, "Performans Uyarısı") {
        Column(Modifier.align(Alignment.Center).width(470.dp.coerceAtMost(maxWidth - 32.dp))) {
            Box(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF15253E))
                .pointerInput(Unit) { detectTapGestures { } }.focusGroup()) {
                Column(Modifier.fillMaxWidth().padding(top = 29.dp, start = 22.dp, end = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    image("ikon_uyari", Modifier.size(44.dp)); Spacer(Modifier.height(17.dp))
                    Label("Performans Uyarısı", 17, bold = true, lineHeight = 20f)
                    Spacer(Modifier.height(14.dp))
                    Label("Çoklu ekran modu cihazınızın işlemcisini ve internetinizi yoğun kullanır.", 11, Color(0xFF9AA8BC), lineHeight = 13f, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Label("Eski veya düşük donanımlı cihazlarda donma yaşanabilir. Devam etmek istiyor\n musunuz?", 11, Color(0xFF9AA8BC), lineHeight = 13f, textAlign = TextAlign.Center)
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 19.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("İPTAL", Modifier.weight(1f).height(38.dp).focusRequester(cancel), tint = Color.Transparent, strokeWidth = 0.dp, content = { Label("İPTAL", 11, Color(0xFFAAB7CA), bold = true) }, click = s::dismiss)
                    Action("DEVAM ET", Modifier.weight(1f).height(38.dp), tint = Color(0xFFED8017), radius = 10.dp, strokeWidth = 0.dp, content = { Label("DEVAM ET", 11, bold = true) }) { s.dialog = "layout" }
                }
            }
        }
        LaunchedEffect(Unit) { cancel.requestFocus() }
    }
}

@Composable
internal fun SettingsGroup(title: String) {
    Row(Modifier.height(26.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(3.dp).height(16.dp).background(Color(0xFFE67E22)))
        Label(title, 10, Color(0xFF94A2B7), Modifier.padding(start = 10.dp), bold = true, letterSpacing = 1f)
    }
}

@Composable
internal fun CategoryManagement(s: PreviewState, image: @Composable (String, Modifier) -> Unit, modifier: Modifier) {
    val back = remember { FocusRequester() }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 19.dp)) {
        Action("Kanallar Ve Listeler › KATEGORİ YÖNETİMİ · Geri", Modifier.fillMaxWidth().height(45.dp).focusRequester(back), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 20.dp), content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Label("←", 12, Color(0xFFE67E22)); Label("Kanallar Ve Listeler ›", 10, Color(0xFF69798F), bold = true); Label("KATEGORİ YÖNETİMİ", 10, bold = true)
            }
        }, click = s::dismiss)
        Spacer(Modifier.height(32.dp)); SettingsGroup("HIZLI AKSİYONLAR")
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Action("TÜMÜNÜ GÖSTER", Modifier.weight(1f).height(36.dp), tint = Color(0xFF0ABB91), radius = 8.dp, strokeWidth = 0.dp, enabled = !s.busy, content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { image("ikon_show", Modifier.size(12.dp)); Label("TÜMÜNÜ GÖSTER", 10, bold = true) }
            }) { s.showCategories(true) }
            Action("TÜMÜNÜ GİZLE", Modifier.weight(1f).height(36.dp), tint = Color(0xFFF13D40), radius = 8.dp, strokeWidth = 0.dp, enabled = !s.busy, content = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) { image("ikon_hide", Modifier.size(12.dp)); Label("TÜMÜNÜ GİZLE", 10, bold = true) }
            }) { s.showCategories(false) }
        }
        Spacer(Modifier.height(25.dp)); SettingsGroup("KATEGORİLER (${s.data.categories.size})")
        s.data.categories.forEachIndexed { index, category ->
            key(category) {
                val hidden = category in s.data.hiddenCategories
                Row(Modifier.fillMaxWidth().height(52.dp).background(referenceCard, RoundedCornerShape(10.dp)).padding(start = 13.dp, end = 19.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Box(Modifier.size(26.dp).background(Color(0xFF1A2D4F), CircleShape), contentAlignment = Alignment.Center) { Label("${index + 1}", 10, bold = true) }
                    Label(category, 11, if (hidden) Color(0xFF62718A) else Color.White, Modifier.weight(1f), bold = true, maxLines = 1, decoration = if (hidden) TextDecoration.LineThrough else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Action("$category ${if (hidden) "göster" else "gizle"}", Modifier.size(35.dp, 33.dp), tint = Color(0xFF1A2D4F), radius = 8.dp, strokeWidth = 0.dp, enabled = !s.busy, content = { Label(if (hidden) "⊘" else "✓", 19, if (hidden) Color(0xFFFF3D68) else Color.White) }) { s.toggleCategory(category) }
                        Action("$category yukarı taşı", Modifier.size(35.dp, 33.dp), tint = Color(0xFF1A2D4F), radius = 8.dp, strokeWidth = 0.dp, enabled = index > 0 && !s.busy, content = { Label("▲", 11, if (index > 0) Color.White else Color(0xFF4A5B74)) }) { s.moveCategory(category, -1) }
                        Action("$category aşağı taşı", Modifier.size(35.dp, 33.dp), tint = Color(0xFF1A2D4F), radius = 8.dp, strokeWidth = 0.dp, enabled = index < s.data.categories.lastIndex && !s.busy, content = { Label("▼", 11, if (index < s.data.categories.lastIndex) Color.White else Color(0xFF4A5B74)) }) { s.moveCategory(category, 1) }
                    }
                }
                Spacer(Modifier.height(1.dp))
            }
        }
    }
    LaunchedEffect(Unit) { back.requestFocus() }
}
