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
import com.omer.belestiviclone.shared.preview.MultiLayoutMode
import com.omer.belestiviclone.shared.preview.PreviewCodec
import com.omer.belestiviclone.shared.preview.SavedMultiLayout
import com.omer.belestiviclone.shared.preview.isVideoFormatSelected

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
    val format = kind == "format"
    val selected = remember(kind) { FocusRequester() }
    ReferenceOverlay(s, title, quality) {
        val width = if (quality) maxWidth * (256f / 855f) else 470.dp.coerceAtMost(maxWidth - 32.dp)
        val height = when (kind) {
            "list" -> 144.dp
            "format" -> 368.dp
            "audio", "subtitle" -> 413.dp
            "quality" -> 211.dp
            "sleepTimer" -> 340.dp
            "subtitleBgOpacity" -> 270.dp
            else -> (values.size * 45 + 50).dp.coerceIn(144.dp, 400.dp)
        }
        val placement = if (quality) Modifier.offset(x = maxWidth * (560f / 855f), y = maxHeight * (135f / 481f))
            else Modifier.align(Alignment.Center)
        Column(placement.width(width).heightIn(max = maxHeight - 12.dp).height(height)
            .background(referenceCard, RoundedCornerShape(if (quality || format) 10.dp else 0.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = if (quality) 17.dp else 19.dp).focusGroup()) {
            Label(title, 9, Color(0xFF94A2B7), bold = true, lineHeight = 16f, letterSpacing = if (quality) 0f else 1f)
            Spacer(Modifier.height(if (quality) 5.dp else 7.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                values.forEachIndexed { index, value ->
                    val chosen = if (format) isVideoFormatSelected(s.data.pref(kind), value) else s.data.pref(kind) == value
                    Action(value, Modifier.fillMaxWidth().height(if (quality) 41.5.dp else 44.5.dp)
                        .then(if (chosen || (!format && s.data.pref(kind) !in values && index == 0)) Modifier.focusRequester(selected) else Modifier),
                        tint = Color.Transparent, radius = 0.dp, strokeWidth = 0.dp, focusColor = Color(0xFFE67E22), enabled = !s.busy,
                        padding = PaddingValues(horizontal = 2.dp), content = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                Label(value, 12, Color(0xFFE9EEF5), bold = true)
                                if (chosen) Label("✓", 20, Color(0xFFE67E22), Modifier.align(Alignment.CenterEnd).padding(end = 5.dp))
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
    val savedCount = s.savedMultiLayouts.size + if (s.lastMultiLayout != null) 1 else 0
    ReferenceOverlay(s, "Çoklu Ekran Düzeni") {
        Column(
            Modifier.align(Alignment.Center)
                .width(460.dp.coerceAtMost(maxWidth - 32.dp))
                .heightIn(max = maxHeight - 16.dp)
                .background(Color(0xFF12233B), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF283D5C), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 22.dp, vertical = 18.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Label("Çoklu Ekran Düzeni", 16, bold = true, lineHeight = 20f)
            Label("Kaç ekranı aynı anda izlemek istersin?", 10, Color(0xFF9AA8BC))
            Spacer(Modifier.height(2.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MultiLayoutMode.entries.forEachIndexed { idx, mode ->
                    val isCurrent = s.multi?.mode == mode
                    Action(
                        label = mode.title,
                        modifier = Modifier.fillMaxWidth().height(50.dp).then(if (idx == 0) Modifier.focusRequester(first) else Modifier),
                        selected = isCurrent,
                        tint = Color(0xFF192E4D),
                        selectedTint = Color(0xFF274570),
                        radius = 10.dp,
                        stroke = if (isCurrent) Color(0xFFF1C40F) else Color(0xFF2B4263),
                        strokeWidth = 1.dp,
                        focusColor = Color(0xFFF1C40F),
                        padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        content = {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Label(mode.title, 13, Color.White, bold = true)
                                    Label(mode.subtitle, 9, Color(0xFF94A2B7), maxLines = 1)
                                }
                                Box(
                                    Modifier.background(Color(0xFF23395B), RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF425E86), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Label("${mode.slotCount} EKRAN", 9, Color(0xFFFFD735), bold = true)
                                }
                            }
                        }
                    ) { s.startMulti(mode) }
                }
                Action(
                    label = "Kayıtlı Düzenler",
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    tint = Color(0xFF192E4D),
                    radius = 10.dp,
                    stroke = Color(0xFF2B4263),
                    strokeWidth = 1.dp,
                    focusColor = Color(0xFFF1C40F),
                    padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    content = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Label("Kayıtlı Düzenler", 13, Color.White, bold = true)
                                Label(
                                    if (savedCount > 0) "${s.savedMultiLayouts.size} kayıtlı paket · Son Çoklu Ekran hazır"
                                    else "Favori çoklu ekran paketlerini kaydet ve tek dokunuşla aç",
                                    9,
                                    Color(0xFF94A2B7),
                                    maxLines = 1
                                )
                            }
                            Box(
                                Modifier.background(Color(0xFF3A2E1E), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFE67E22), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Label(if (savedCount > 0) "$savedCount KAYIT" else "PAKETLER", 9, Color(0xFFF39C12), bold = true)
                            }
                        }
                    }
                ) { s.dialog = "savedLayouts" }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                Action(
                    label = "Vazgeç",
                    modifier = Modifier.height(34.dp),
                    tint = Color.Transparent,
                    strokeWidth = 0.dp,
                    focusColor = Color(0xFFF1C40F),
                    padding = PaddingValues(horizontal = 14.dp),
                    content = { Label("Vazgeç", 12, Color(0xFFE67E22), bold = true) },
                    click = s::dismiss
                )
            }
        }
        LaunchedEffect(Unit) { first.requestFocus() }
    }
}

@Composable
internal fun MultiChannelPicker(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val first = remember { FocusRequester() }
    val m = s.multi ?: return
    val slotNumber = m.selected + 1
    val currentId = m.slots.getOrNull(m.selected)
    val categories = s.multiPickerCategories
    val filteredChannels = s.multiPickerChannels
    ReferenceOverlay(s, "Kanal Seç · Ekran $slotNumber") {
        Column(
            Modifier.align(Alignment.Center)
                .width(560.dp.coerceAtMost(maxWidth - 24.dp))
                .height(430.dp.coerceAtMost(maxHeight - 16.dp))
                .background(Color(0xFF102038), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2B4263), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Label("Ekran $slotNumber İçin Kanal Seç", 15, Color.White, bold = true)
                    Label("${s.multiPickerCategory} · ${filteredChannels.size} kanal", 9, Color(0xFF94A2B7))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentId != null) {
                        Action(
                            label = "Paneli Kapat",
                            modifier = Modifier.height(30.dp),
                            tint = Color(0xFF3E1E24),
                            radius = 8.dp,
                            stroke = Color(0xFF8E3440),
                            focusColor = Color(0xFFF1C40F),
                            padding = PaddingValues(horizontal = 10.dp),
                            content = { Label("Paneli Boşalt", 10, Color(0xFFFF8A8A), bold = true) }
                        ) { s.assignMultiChannel(null) }
                    }
                    Action(
                        label = "Kapat",
                        modifier = Modifier.size(30.dp),
                        tint = Color(0xFF1A2D4F),
                        radius = 8.dp,
                        strokeWidth = 0.dp,
                        focusColor = Color(0xFFF1C40F),
                        content = { Label("×", 14, Color.White, bold = true) },
                        click = s::dismiss
                    )
                }
            }

            // Hızlı Arama Çubuğu
            Row(
                Modifier.fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF182C49), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF2E496E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                image("kategori_ara", Modifier.size(14.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = s.multiPickerQuery,
                    onValueChange = { s.multiPickerQuery = it },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Kanal ara" },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE67E22)),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (s.multiPickerQuery.isEmpty()) Label("Kanal adına göre hızlı ara…", 11, Color(0xFF7B8FA8))
                            inner()
                        }
                    }
                )
                if (s.multiPickerQuery.isNotEmpty()) {
                    Action(
                        label = "Aramayı Temizle",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Transparent,
                        strokeWidth = 0.dp,
                        padding = PaddingValues(0.dp),
                        content = { Label("×", 13, Color(0xFF94A2B7), bold = true) }
                    ) { s.multiPickerQuery = "" }
                }
            }

            // Kategori Filtresi Sekmeleri
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    val chosen = s.multiPickerCategory == cat
                    Action(
                        label = cat,
                        modifier = Modifier.height(30.dp).then(if (index == 0 && filteredChannels.isEmpty()) Modifier.focusRequester(first) else Modifier),
                        selected = chosen,
                        tint = Color(0xFF182C49),
                        selectedTint = Color(0xFFE67E22),
                        radius = 8.dp,
                        stroke = if (chosen) Color(0xFFF1C40F) else Color(0xFF2B4263),
                        focusColor = Color(0xFFF1C40F),
                        padding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                        content = { Label(cat, 10, Color.White, bold = true) }
                    ) { s.chooseMultiPickerCategory(cat) }
                }
            }

            // Kanal Listesi
            if (filteredChannels.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Label("Bu filtrede eşleşen kanal bulunamadı.", 12, Color(0xFF8A9AB0))
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredChannels.size, key = { idx -> PreviewCodec.id(filteredChannels[idx].id) }) { idx ->
                        val ch = filteredChannels[idx]
                        val isAssignedHere = ch.id == currentId
                        val assignedOtherSlot = m.slots.take(m.count).indexOfFirst { it == ch.id }.takeIf { it >= 0 && it != m.selected }
                        Action(
                            label = ch.name,
                            modifier = Modifier.fillMaxWidth().height(42.dp).then(if (idx == 0) Modifier.focusRequester(first) else Modifier),
                            selected = isAssignedHere,
                            tint = Color(0xFF162944),
                            selectedTint = Color(0xFF284875),
                            radius = 8.dp,
                            stroke = if (isAssignedHere) Color(0xFFF1C40F) else Color(0xFF223858),
                            focusColor = Color(0xFFF1C40F),
                            padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            content = {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    image(ch.logo, Modifier.size(28.dp))
                                    Column(Modifier.weight(1f)) {
                                        Label(ch.name, 12, Color.White, bold = true, maxLines = 1)
                                        Label(ch.category, 9, Color(0xFF86C9EF), maxLines = 1)
                                    }
                                    if (isAssignedHere) {
                                        Label("AKTİF", 9, Color(0xFFFFD735), bold = true)
                                    } else if (assignedOtherSlot != null) {
                                        Label("Ekran ${assignedOtherSlot + 1}", 9, Color(0xFF94A2B7))
                                    }
                                }
                            }
                        ) { s.assignMultiChannel(ch.id) }
                    }
                }
            }
        }
        LaunchedEffect(s.multiPickerCategory) { runCatching { first.requestFocus() } }
    }
}

@Composable
internal fun SavedLayoutsModal(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val first = remember { FocusRequester() }
    val savedList = s.savedMultiLayouts
    val lastLayout = s.lastMultiLayout
    ReferenceOverlay(s, "Kayıtlı Düzenler") {
        Column(
            Modifier.align(Alignment.Center)
                .width(480.dp.coerceAtMost(maxWidth - 32.dp))
                .heightIn(max = maxHeight - 16.dp)
                .background(Color(0xFF12233B), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2B4263), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Label("Kayıtlı Çoklu Ekran Düzenleri", 15, Color.White, bold = true)
                    Label("Hazır kombinasyonları tek dokunuşla geri yükleyin", 9, Color(0xFF94A2B7))
                }
                if (s.multi != null) {
                    Action(
                        label = "+ Mevcut Düzeni Kaydet",
                        modifier = Modifier.height(32.dp).focusRequester(first),
                        tint = Color(0xFFE67E22),
                        radius = 8.dp,
                        strokeWidth = 0.dp,
                        focusColor = Color(0xFFF1C40F),
                        padding = PaddingValues(horizontal = 10.dp),
                        content = { Label("+ Mevcut Düzeni Kaydet", 10, Color.White, bold = true) }
                    ) { s.openSaveMultiLayoutDialog() }
                }
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (lastLayout != null) {
                    SavedLayoutRow(
                        s = s,
                        saved = lastLayout.copy(name = "Son Çoklu Ekran"),
                        badge = "SON OTURUM",
                        deletable = false,
                        modifier = if (s.multi == null) Modifier.focusRequester(first) else Modifier
                    )
                }
                if (savedList.isEmpty() && lastLayout == null) {
                    Box(
                        Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF172B47), RoundedCornerShape(10.dp)).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Label("Henüz kayıtlı bir çoklu ekran paketi yok.\nÇoklu ekran açıkken üstteki 'Düzeni Kaydet' düğmesiyle paket oluşturabilirsiniz.", 10, Color(0xFF94A2B7), textAlign = TextAlign.Center)
                    }
                } else {
                    savedList.forEachIndexed { idx, item ->
                        SavedLayoutRow(
                            s = s,
                            saved = item,
                            badge = item.mode.title,
                            deletable = true,
                            modifier = if (s.multi == null && lastLayout == null && idx == 0) Modifier.focusRequester(first) else Modifier
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Action(
                    label = "← Düzen Seçimine Dön",
                    modifier = Modifier.height(34.dp).then(if (s.multi == null && lastLayout == null && savedList.isEmpty()) Modifier.focusRequester(first) else Modifier),
                    tint = Color(0xFF192E4D),
                    radius = 8.dp,
                    strokeWidth = 0.dp,
                    focusColor = Color(0xFFF1C40F),
                    padding = PaddingValues(horizontal = 12.dp),
                    content = { Label("← Düzen Seçimine Dön", 10, Color(0xFF86C9EF), bold = true) }
                ) { s.dialog = "layout" }
                Action(
                    label = "Kapat",
                    modifier = Modifier.height(34.dp),
                    tint = Color.Transparent,
                    strokeWidth = 0.dp,
                    focusColor = Color(0xFFF1C40F),
                    padding = PaddingValues(horizontal = 12.dp),
                    content = { Label("Kapat", 11, Color(0xFFE67E22), bold = true) },
                    click = s::dismiss
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun SavedLayoutRow(
    s: PreviewState,
    saved: SavedMultiLayout,
    badge: String,
    deletable: Boolean,
    modifier: Modifier = Modifier,
) {
    val channelNames = saved.slots.take(saved.mode.slotCount).mapNotNull { id ->
        id?.let { s.data.channels.find { ch -> ch.id == it }?.name }
    }
    val summary = if (channelNames.isEmpty()) "Kanal atanmamış" else channelNames.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF182C49), RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF284266), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Action(
            label = "${saved.name} Yükle",
            modifier = modifier.weight(1f).height(44.dp),
            tint = Color.Transparent,
            strokeWidth = 0.dp,
            focusColor = Color(0xFFF1C40F),
            padding = PaddingValues(horizontal = 4.dp),
            content = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(saved.name, 12, Color.White, bold = true, maxLines = 1)
                        Box(Modifier.background(Color(0xFF243B5E), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Label(badge, 8, Color(0xFFFFD735), bold = true)
                        }
                    }
                    Label(summary, 9, Color(0xFF94A2B7), maxLines = 1)
                }
            }
        ) { s.loadSavedMultiLayout(saved) }
        if (deletable) {
            Action(
                label = "${saved.name} Sil",
                modifier = Modifier.size(34.dp),
                tint = Color(0xFF3A1D24),
                radius = 8.dp,
                strokeWidth = 0.dp,
                focusColor = Color(0xFFF1C40F),
                content = { Label("🗑", 11, Color(0xFFFF8A8A)) }
            ) { s.deleteSavedMultiLayout(saved.name) }
        }
    }
}

@Composable
internal fun SaveMultiLayoutDialog(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    val saveBtn = remember { FocusRequester() }
    ReferenceOverlay(s, "Çoklu Ekran Düzenini Kaydet") {
        Column(
            Modifier.align(Alignment.Center)
                .width(430.dp.coerceAtMost(maxWidth - 32.dp))
                .background(Color(0xFF12233B), RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2B4263), RoundedCornerShape(14.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label("Çoklu Ekran Düzenini Kaydet", 15, Color.White, bold = true)
            Label("Bu kombinasyona bir paket adı verin veya hazır şablon seçin:", 10, Color(0xFF94A2B7))
            Field(
                value = s.formName,
                label = "PAKET / DÜZEN ADI",
                icon = "ikon_kaydet",
                image = image,
                placeholder = "Örn: Spor Paketi, Haber Paketi",
                change = { s.formName = it }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Spor Paketi", "Haber Paketi", "Ulusal Paket").forEach { preset ->
                    Action(
                        label = preset,
                        modifier = Modifier.weight(1f).height(30.dp),
                        tint = Color(0xFF1B3152),
                        radius = 7.dp,
                        strokeWidth = 0.dp,
                        focusColor = Color(0xFFF1C40F),
                        content = { Label(preset, 9, Color(0xFF86C9EF), bold = true) }
                    ) { s.formName = preset }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(
                    label = "İPTAL",
                    modifier = Modifier.weight(1f).height(38.dp),
                    tint = Color(0xFF1A2D4F),
                    radius = 9.dp,
                    strokeWidth = 0.dp,
                    focusColor = Color(0xFFF1C40F),
                    content = { Label("İPTAL", 11, Color(0xFF9AA8BC), bold = true) },
                    click = s::dismiss
                )
                Action(
                    label = "KAYDET",
                    modifier = Modifier.weight(1f).height(38.dp).focusRequester(saveBtn),
                    tint = Color(0xFFE67E22),
                    radius = 9.dp,
                    strokeWidth = 0.dp,
                    focusColor = Color(0xFFF1C40F),
                    content = { Label("KAYDET", 11, Color.White, bold = true) }
                ) { s.saveCurrentMultiLayout(s.formName) }
            }
        }
        LaunchedEffect(Unit) { runCatching { saveBtn.requestFocus() } }
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
