package com.omer.belestiviclone.sharedui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.preview.PreviewCodec

private val amber = Color(0xFFE67E22)
private val card = Color(0xFF13233D)

@Composable
fun RadioScreen(
    s: PreviewState,
    image: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
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
    val currentStation = s.channel

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    s.panel = !s.panel
                }
            }
    ) {
        // 1. Orijinal Beleş TiVi 4.1 Retro Radyo Arka Planı
        image("bg_retro_radio", Modifier.fillMaxSize())

        // Hafif koyu degrade katman: Metin ve kartların net okunması için
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x99000000),
                            Color(0x44000000),
                            Color(0xAA000000)
                        )
                    )
                )
        )

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

        // 3. Sol Üst Başlık (Panel açıkken)
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

            // Üst Sağ Butonlar (TV Modu, Çoklu Ekran, Görüntü, Bilgi, Ayarlar, Çıkış)
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 20.dp)
                    .onGloballyPositioned { boundsObserver("radio-top-actions", it.boundsInWindow()) }
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Action(
                    label = "TV Modu",
                    tint = Color(0xB8000000),
                    radius = 11.dp,
                    stroke = Color(0xFF344454),
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            image("ikon_tv", Modifier.size(13.dp, 14.dp))
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
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            image("ikon_ayar", Modifier.size(13.dp, 14.dp))
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
                    content = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            image("ikon_cikis", Modifier.size(13.dp, 14.dp))
                            Label("Çıkış", 11, bold = true)
                        }
                    },
                    click = { s.dialog = "exit" }
                )
            }
        }

        // 4. Orta Canlı Radyo Bilgi Kartı (Referans ekran_radyo_cikis.png ve ekran_tvmod.png)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = if (s.panel) 55.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Retro Radyo Üzerinde BELEŞ RADYO Logosu
            image("logo_radio", Modifier.size(140.dp, 110.dp))

            // Şık Cam/Glass İstasyon Kartı
            Row(
                modifier = Modifier
                    .background(Color(0xD908111E), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF283A4E), RoundedCornerShape(14.dp))
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

                        // CANLI Rozeti
                        Box(
                            modifier = Modifier
                                .background(if (s.paused) Color(0xFF555555) else Color(0xFFD32F2F), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Label(if (s.paused) "DURAKLADI" else "CANLI", 9, Color.White, bold = true)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Label(
                            if (s.paused) "Yayın duraklatıldı. Oynatmak için dokunun." else "Günün temposuna kısa bir müzik molası.",
                            11,
                            Color(0xFFB0BEC5)
                        )
                        if (!s.paused) {
                            EqualizerWaveAnimation(isLive = true)
                        }
                    }
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
                        image("ikon_audio", Modifier.size(16.dp))
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
                                    image(if (s.paused) "ikon_canli" else "ikon_duraklat", Modifier.size(12.dp))
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
                                    image("ikon_ses", Modifier.size(12.dp))
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
                                    image("ikon_canli", Modifier.size(12.dp))
                                    Label("Yenile", 10, bold = true)
                                }
                            }
                        ) { s.retry++ }
                    }
                }

                // Yatay Kaydırılabilir Radyo İstasyon Kartları (11-radyo.png referansına uygun)
                val listState = rememberLazyListState()
                val radioList = s.radioChannels

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
                            onClick = { s.select(station.id) }
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
