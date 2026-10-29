package com.omer.belestiviclone.sharedui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omer.belestiviclone.shared.preview.PlaybackStatus
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.preview.PreviewCodec

private val amber = Color(0xFFE67E22)
private val card = Color(0xFF13233D)

private val radioTips = listOf(
    "Beleş TiVi'ye hoş geldin. Keyifli dinlemeler!",
    "Ekranı karart, müziğin tadını çıkar.",
    "Sevdiğin istasyonu basılı tutarak favorilerine ekleyebilirsin.",
)

@Composable
fun RadioScreen(
    s: PreviewState,
    image: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    if (s.radioScreenDimmed) {
        val dimFocusRequester = remember { FocusRequester() }
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(dimFocusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        s.wakeRadioScreen()
                    }
                    true
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        s.wakeRadioScreen()
                    }
                }
                .semantics { contentDescription = "Ekran karartıldı" }
        )
        LaunchedEffect(Unit) { runCatching { dimFocusRequester.requestFocus() } }
        return
    }

    val theme = remember(s.data.pref("theme")) { AppThemes.get(s.data.pref("theme")) }
    val boundsObserver = LocalBoundsObserver.current
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            nowEpochMs = System.currentTimeMillis()
        }
    }

    val timeLabel = remember(nowEpochMs / 60_000) { formatTime(nowEpochMs) }
    val dateLabel = remember(nowEpochMs / 60_000) { formatDate(nowEpochMs) }
    val tipText = remember(nowEpochMs / 6_000) {
        val idx = ((nowEpochMs / 6_000) % radioTips.size).toInt().coerceAtLeast(0)
        radioTips[idx]
    }
    val currentStation = s.channel

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!s.panel) {
                        s.panel = true
                    }
                    s.touchPanelInteraction()
                }
            }
    ) {
        val radioBg = s.data.pref("radioBg")
        when (radioBg) {
            "Koyu Neon" -> {
                // Koyu Neon: Siber mor ve gece mavisi zengin degrade + neon yatay ışıma
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF2E0854), Color(0xFF0D0B24), Color(0xFF050510)),
                                radius = 1200f
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x3367E8F9),
                                    Color(0x00000000),
                                    Color(0x449333EA)
                                )
                            )
                        )
                )
            }
            "Klasik Ahşap" -> {
                // Klasik Ahşap: Sıcak maun, ceviz ve kehribar ahşap tonları + radyal sıcak ışıma
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2B1408),
                                    Color(0xFF45220E),
                                    Color(0xFF1E0C04)
                                )
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x26FFB300), Color(0x00000000)),
                                radius = 900f
                            )
                        )
                )
            }
            else -> {
                // Varsayılan: Orijinal Beleş TiVi 4.1 Retro Ahşap Radyo ve Neon Tabela Görseli
                image("bg_retro_radio", Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x99000000),
                                    Color(0x33000000),
                                    Color(0xAA000000)
                                )
                            )
                        )
                )
            }
        }

        // 2. Sağ Üst Saat ve Tarih (ekran_tvmod.png ve ekran_radyo_cikis.png referansına uygun)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 26.dp),
            horizontalAlignment = Alignment.End
        ) {
            Label(timeLabel, 26, Color.White, bold = true)
            Spacer(Modifier.height(2.dp))
            Label(dateLabel, 11, Color(0xFFD0D7DE), bold = false)
        }

        // 3. Sol Üst Başlık ve Üst Sağ Araç Çubuğu (Panel açıkken)
        if (s.panel) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 20.dp, start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Color(0xFF13233D), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    image(currentStation?.logo ?: "ikon_audio", Modifier.fillMaxSize())
                }
                Column {
                    Label(currentStation?.name ?: "Radyo Modu", 18, Color(0xFFFFD600), bold = true)
                    Label("Canlı Radyo Yayını · Beleş TiVi", 10, Color(0xFFB0BEC5))
                }
            }

            // Üst Sağ Butonlar (☾ Ekranı karart · 📺 TV Modu · ⚙ Ayarlar · ✕ Çıkış)
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 20.dp)
                    .onGloballyPositioned { boundsObserver("radio-top-actions", it.boundsInWindow()) }
                    .horizontalScroll(rememberScrollState())
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Action(
                    label = "☾ Ekranı karart",
                    tint = Color(0xB8000000),
                    radius = 11.dp,
                    stroke = Color(0xFF344454),
                    focusKey = "radio:action:dim",
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TopBarActionIcon("dim", color = Color.White, modifier = Modifier.size(13.dp, 14.dp))
                            Label("Ekranı karart", 11, bold = true)
                        }
                    },
                    click = s::dimRadioScreen
                )
                Action(
                    label = "TV Modu",
                    tint = Color(0xB8000000),
                    radius = 11.dp,
                    stroke = Color(0xFF344454),
                    focusKey = "radio:action:tv",
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (theme.categoryTabPill) {
                                TopBarActionIcon("ikon_tv", color = Color.White, modifier = Modifier.size(13.dp, 14.dp))
                            } else {
                                image("ikon_tv", Modifier.size(13.dp, 14.dp))
                            }
                            Label("TV Modu", 11, bold = true)
                        }
                    },
                    click = s::exitRadioMode
                )
                Action(
                    label = "Ayarlar",
                    tint = Color(0xB8000000),
                    radius = 11.dp,
                    stroke = Color(0xFF344454),
                    focusKey = "radio:action:settings",
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (theme.categoryTabPill) {
                                TopBarActionIcon("ikon_ayar", color = Color.White, modifier = Modifier.size(13.dp, 14.dp))
                            } else {
                                image("ikon_ayar", Modifier.size(13.dp, 14.dp))
                            }
                            Label("Ayarlar", 11, bold = true)
                        }
                    },
                    click = s::settings
                )
                Action(
                    label = "Çıkış",
                    tint = Color(0xB8000000),
                    radius = 11.dp,
                    stroke = Color(0xFF344454),
                    focusKey = "radio:action:exit",
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (theme.categoryTabPill) {
                                TopBarActionIcon("ikon_cikis", color = Color.White, modifier = Modifier.size(13.dp, 14.dp))
                            } else {
                                image("ikon_cikis", Modifier.size(13.dp, 14.dp))
                            }
                            Label("Çıkış", 11, bold = true)
                        }
                    },
                    click = { s.dialog = "exit" }
                )
            }
        }

        // 4. Alt-Orta Canlı Radyo Bilgi Kartı (BELEŞ ▶ RADYO neon tabelasıyla çakışmaz)
        val isError = s.playbackStatus == PlaybackStatus.ERROR || s.playbackError != null
        val isLoading = !isError && !s.paused && (s.playbackStatus == PlaybackStatus.LOADING || s.playbackStatus == PlaybackStatus.IDLE)
        val isLivePlaying = !isError && !s.paused && s.playbackStatus == PlaybackStatus.READY

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (s.panel) 142.dp else 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Şık Cam/Glass İstasyon Kartı
            Row(
                modifier = Modifier
                    .background(Color(0xD908111E), RoundedCornerShape(14.dp))
                    .border(1.dp, if (isError) Color(0xFFE65100) else Color(0xFF283A4E), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // İstasyon Logosu
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF13233D), RoundedCornerShape(10.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    image(currentStation?.logo ?: "ikon_audio", Modifier.fillMaxSize())
                }

                // İstasyon Adı ve Alt Bilgi
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Label(currentStation?.name ?: "Radyo İstasyonu", 20, Color.White, bold = true)

                        // Durum Rozeti (⏳ YÜKLENİYOR… / ● CANLI / ⚠️ YAYIN YOK / DURAKLADI)
                        val badgeBg = when {
                            isError -> Color(0xFFD84315)
                            s.paused -> Color(0xFF555555)
                            isLoading -> Color(0xFFD97706)
                            else -> Color(0xFFD32F2F)
                        }
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            when {
                                isError -> Label("⚠️ YAYIN YOK", 9, Color.White, bold = true)
                                s.paused -> Label("DURAKLADI", 9, Color.White, bold = true)
                                isLoading -> Label("⏳ YÜKLENİYOR…", 9, Color.White, bold = true)
                                else -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(5.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                        Label("CANLI", 9, Color.White, bold = true)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val subtitleText = when {
                            isError -> "Yayın bağlantısı kurulamadı."
                            s.paused -> "Yayın duraklatıldı. Oynatmak için dokunun."
                            isLoading -> "${currentStation?.name ?: "İstasyon"} yayına bağlanıyor…"
                            else -> tipText
                        }
                        Label(
                            subtitleText,
                            11,
                            if (isError) Color(0xFFFFAB91) else Color(0xFFB0BEC5)
                        )
                        if (!s.paused) {
                            EqualizerWaveAnimation(isLive = isLivePlaying)
                        }
                    }
                }
            }

            if (isError) {
                Row(
                    modifier = Modifier
                        .background(Color(0xEB260E12), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE65100), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Label(
                        "⚠️ Bu radyo istasyonunun yayınına şu an ulaşılamıyor. Yeniden deneyin veya başka bir istasyon seçin.",
                        11,
                        Color(0xFFFFE0B2),
                        bold = true
                    )
                    Action(
                        label = "Yeniden Dene",
                        tint = Color(0xFFE65100),
                        radius = 8.dp,
                        stroke = Color(0xFFFFB74D),
                        padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        content = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PlayerRetryIcon(color = Color.White, modifier = Modifier.size(11.dp))
                                Label("Yeniden Dene", 10, Color.White, bold = true)
                            }
                        }
                    ) { s.retry++ }
                }
            }
        }

        // 5. Alt Kısım: İstasyon Seçici Şerit ve Kontroller
        if (s.panel) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xEB060D17))
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Başlık Satırı ve Kontroller
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TopBarActionIcon("ikon_audio", color = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                        Label("RADYO İSTASYONLARI (${s.radioChannels.size})", 12, Color(0xFFFFD600), bold = true)
                    }

                    // Oynat/Duraklat / Ses Aç/Kapat Butonları
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action(
                            label = if (s.paused) "Oynat" else "Duraklat",
                            tint = Color(0xB8000000),
                            radius = 8.dp,
                            stroke = Color(0xFF344454),
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PlayerPlayPauseIcon(isPaused = s.paused, color = Color.White, modifier = Modifier.size(11.dp))
                                    Label(if (s.paused) "Oynat" else "Duraklat", 10, bold = true)
                                }
                            }
                        ) { s.paused = !s.paused }

                        Action(
                            label = if (s.muted) "Sesi Aç" else "Sessiz",
                            tint = Color(0xB8000000),
                            radius = 8.dp,
                            stroke = Color(0xFF344454),
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PlayerAudioIcon(isMuted = s.muted, color = Color.White, modifier = Modifier.size(12.dp))
                                    Label(if (s.muted) "Sesi Aç" else "Sessiz", 10, bold = true)
                                }
                            }
                        ) { s.muted = !s.muted }

                        Action(
                            label = "Yeniden Dene",
                            tint = Color(0xB8000000),
                            radius = 8.dp,
                            stroke = Color(0xFF344454),
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PlayerRetryIcon(color = Color.White, modifier = Modifier.size(11.dp))
                                    Label("Yenile", 10, bold = true)
                                }
                            }
                        ) { s.retry++ }
                    }
                }

                // Yatay Kaydırılabilir Radyo İstasyon Kartları (11-radyo.png referansına uygun)
                val listState = rememberLazyListState()
                val radioList = s.radioChannels

                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) {
                        s.touchPanelInteraction()
                    }
                }

                LaunchedEffect(s.selected) {
                    val index = radioList.indexOfFirst { it.id == s.selected }
                    if (index >= 0) {
                        listState.animateScrollToItem((index - 2).coerceAtLeast(0))
                    }
                }

                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(radioList, key = { PreviewCodec.id(it.id) }) { station ->
                        val isSelected = station.id == s.selected
                        RadioStationCard(
                            station = station,
                            isSelected = isSelected,
                            image = image,
                            onClick = {
                                s.touchPanelInteraction()
                                s.select(station.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioStationCard(
    station: PreviewChannel,
    isSelected: Boolean,
    image: @Composable (String, Modifier) -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) amber else Color(0xFF283A4E)
    val bgColor = if (isSelected) Color(0x99A75F19) else Color(0xDD0D1B2A)

    Action(
        label = station.name,
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        selected = isSelected,
        tint = bgColor,
        stroke = borderColor,
        strokeWidth = if (isSelected) 2.dp else 1.dp,
        radius = 10.dp,
        padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        focusKey = "radio:station:${PreviewCodec.id(station.id)}",
        content = {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    image(station.logo.ifEmpty { "ikon_audio" }, Modifier.fillMaxSize())
                }
                Label(
                    value = station.name,
                    size = 9,
                    color = if (isSelected) Color(0xFFFFD600) else Color.White,
                    bold = isSelected,
                    maxLines = 1
                )
            }
        },
        click = onClick
    )
}

/** Canlı müzik dalgası/ekolayzır barları animasyonu */
@Composable
private fun EqualizerWaveAnimation(isLive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse)
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 12f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse)
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse)
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse)
    )

    Row(
        modifier = Modifier.height(18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val bars = listOf(bar1, bar2, bar3, bar4)
        bars.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (isLive) h.dp else 4.dp)
                    .background(amber, RoundedCornerShape(1.dp))
            )
        }
    }
}

/** Basit multiplatform saat formatı (HH:mm) */
private fun formatTime(epochMs: Long): String {
    val totalSeconds = epochMs / 1000
    // UTC+3 Türkiye saati
    val turkeySeconds = totalSeconds + 3 * 3600
    val secondsOfDay = (turkeySeconds % 86400 + 86400) % 86400
    val hours = (secondsOfDay / 3600).toInt()
    val minutes = ((secondsOfDay % 3600) / 60).toInt()
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/** Basit multiplatform tarih formatı (ör. 23 Ekim Cuma) */
private fun formatDate(epochMs: Long): String {
    val totalDays = (epochMs + 3 * 3600 * 1000) / (86400 * 1000)
    // 1 Ocak 1970 Perşembe (4)
    val dayOfWeek = ((totalDays + 4) % 7).toInt()
    val daysTr = listOf("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")

    // Gün ve ay hesaplama
    var z = totalDays + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1024 + doe / 1460 - doe / 36524) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9

    val monthsTr = listOf(
        "", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )

    val monthName = monthsTr.getOrElse(m) { "" }
    val dayName = daysTr.getOrElse(dayOfWeek) { "" }
    return "$d $monthName $dayName"
}
