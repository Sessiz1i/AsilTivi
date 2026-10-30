package com.omer.belestiviclone.sharedui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
                .padding(top = if (s.panel) 62.dp else 22.dp, end = 24.dp),
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
                    .padding(top = 20.dp, end = 20.dp)
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
                val isCurrentFavorite = s.isRadioFavorite(currentStation?.id)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Label(currentStation?.name ?: "Radyo İstasyonu", 20, Color.White, bold = true)
                        if (isCurrentFavorite) {
                            Label("★", 16, Color(0xFFFFD600), bold = true)
                        }

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TopBarActionIcon("ikon_audio", color = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                        Label("RADYO İSTASYONLARI", 12, Color(0xFFFFD600), bold = true)
                        Spacer(Modifier.width(4.dp))
                        val isAllTab = s.radioCategory == "Tümü"
                        Action(
                            label = "Tümü (${s.radioChannels.size})",
                            selected = isAllTab,
                            tint = Color(0xB8000000),
                            selectedTint = Color(0x40FFD600),
                            radius = 8.dp,
                            stroke = if (isAllTab) Color(0xFFFFD600) else Color(0xFF344454),
                            padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            focusKey = "radio:tab:all",
                            content = {
                                Label(
                                    "Tümü (${s.radioChannels.size})",
                                    10,
                                    if (isAllTab) Color(0xFFFFD600) else Color.White,
                                    bold = true
                                )
                            }
                        ) {
                            s.draft = null
                            s.moving = null
                            s.radioCategory = "Tümü"
                            s.touchPanelInteraction()
                        }

                        val isFavTab = s.radioCategory == "Favoriler"
                        Action(
                            label = "★ Favoriler (${s.favoriteRadioChannels.size})",
                            selected = isFavTab,
                            tint = Color(0xB8000000),
                            selectedTint = Color(0x40FFD600),
                            radius = 8.dp,
                            stroke = if (isFavTab) Color(0xFFFFD600) else Color(0xFF344454),
                            padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            focusKey = "radio:tab:fav",
                            content = {
                                Label(
                                    "★ Favoriler (${s.favoriteRadioChannels.size})",
                                    10,
                                    if (isFavTab) Color(0xFFFFD600) else Color.White,
                                    bold = true
                                )
                            }
                        ) {
                            s.radioCategory = "Favoriler"
                            s.touchPanelInteraction()
                        }
                    }

                    // Favori Ekle/Çıkar / Oynat/Duraklat / Ses Aç/Kapat / Yenile Butonları
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isStationFav = s.isRadioFavorite(currentStation?.id)
                        Action(
                            label = if (isStationFav) "★ Favorilerde" else "☆ Favori Ekle",
                            selected = isStationFav,
                            tint = Color(0xB8000000),
                            selectedTint = Color(0x40FFD600),
                            radius = 8.dp,
                            stroke = if (isStationFav) Color(0xFFFFD600) else Color(0xFF344454),
                            focusKey = "radio:action:favorite",
                            content = {
                                Label(
                                    if (isStationFav) "★ Favorilerde" else "☆ Favori Ekle",
                                    10,
                                    if (isStationFav) Color(0xFFFFD600) else Color.White,
                                    bold = true
                                )
                            }
                        ) {
                            s.touchPanelInteraction()
                            currentStation?.id?.let { s.toggleFavoriteWithToast(it) }
                        }

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
                        ) {
                            s.touchPanelInteraction()
                            s.paused = !s.paused
                        }

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
                        ) {
                            s.touchPanelInteraction()
                            s.muted = !s.muted
                        }

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
                        ) {
                            s.touchPanelInteraction()
                            s.retry++
                        }
                    }
                }

                // Yatay Kaydırılabilir Radyo İstasyon Kartları (11-radyo.png referansına uygun)
                val listState = rememberLazyListState()
                val radioList = s.filteredRadioChannels

                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) {
                        s.touchPanelInteraction()
                    }
                }

                LaunchedEffect(s.selected, s.radioCategory) {
                    if (s.moving == null) {
                        val index = radioList.indexOfFirst { it.id == s.selected }
                        if (index >= 0) {
                            listState.animateScrollToItem((index - 2).coerceAtLeast(0))
                        }
                    }
                }

                var lockedScroll by remember(s.moving) {
                    mutableStateOf(listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
                }

                LaunchedEffect(s.moving, s.draft) {
                    val movingId = s.moving ?: return@LaunchedEffect
                    if (listState.firstVisibleItemIndex != lockedScroll.first ||
                        listState.firstVisibleItemScrollOffset != lockedScroll.second
                    ) {
                        listState.scrollToItem(lockedScroll.first, lockedScroll.second)
                    }
                    val movingIndex = radioList.indexOfFirst { it.id == movingId }
                    val visible = listState.layoutInfo.visibleItemsInfo
                    if (movingIndex >= 0 && visible.isNotEmpty() && visible.none { it.index == movingIndex }) {
                        listState.scrollToItem(movingIndex)
                        lockedScroll = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                }

                if (s.radioCategory == "Favoriler" && radioList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Label(
                            "Henüz favori radyo istasyonu eklenmedi. '☆ Favori Ekle' butonuyla veya istasyon kartına uzun basarak ekleyebilirsiniz.",
                            11,
                            Color(0xFFB0BEC5),
                            bold = true
                        )
                    }
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(radioList, key = { _, st -> PreviewCodec.id(st.id) }) { idx, station ->
                            val isSelected = station.id == s.selected
                            val isFav = s.isRadioFavorite(station.id)
                            val isMoving = s.moving == station.id
                            val rankNumber = if (s.radioCategory == "Favoriler") idx + 1 else null
                            RadioStationCard(
                                s = s,
                                station = station,
                                rankNumber = rankNumber,
                                isSelected = isSelected,
                                isFavorite = isFav,
                                isMoving = isMoving,
                                image = image,
                                onLongPress = {
                                    s.touchPanelInteraction()
                                    if (s.radioCategory == "Favoriler") {
                                        s.target = station.id
                                        s.dialog = "favorite"
                                    } else {
                                        s.toggleFavoriteWithToast(station.id)
                                    }
                                },
                                onClick = {
                                    s.selectFromList(station.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (s.moving != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 25.dp)
                    .background(Color(0xFF243F66), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF647B9A), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                image("ikon_tasima_bilgi", Modifier.size(35.dp, 17.dp))
                Label("Yönlerle taşı · OK uygula · Geri vazgeç", 11, bold = true)
            }
        }
    }
}

@Composable
private fun RadioStationCard(
    s: PreviewState,
    station: PreviewChannel,
    rankNumber: Int? = null,
    isSelected: Boolean,
    isFavorite: Boolean,
    isMoving: Boolean = false,
    image: @Composable (String, Modifier) -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    val returns = LocalFocusReturn.current
    val focusKey = "radio:station:${PreviewCodec.id(station.id)}"
    val requester = remember(returns, focusKey) {
        returns?.registry?.requester(focusKey) { FocusRequester() } ?: FocusRequester()
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var cardWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var anchorWindowPos by remember { mutableStateOf(0f) }
    val cardSpanPx = with(LocalDensity.current) { 88.dp.toPx() }
    val stepPx = with(LocalDensity.current) { (88.dp + 8.dp).toPx() }

    LaunchedEffect(s.dialog, s.moving, s.target, windowFocused) {
        if (windowFocused && s.dialog == null && (s.target == station.id || s.moving == station.id)) {
            runCatching { requester.requestFocus() }
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }

    val borderColor = when {
        isMoving -> Color(0xFFFFCF37)
        isSelected -> amber
        else -> Color(0xFF283A4E)
    }
    val bgColor = when {
        isMoving -> Color(0xCCA77919)
        isSelected -> Color(0x99A75F19)
        else -> Color(0xDD0D1B2A)
    }
    val displayTitle = if (rankNumber != null) "$rankNumber. ${station.name}" else station.name

    Action(
        label = displayTitle,
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .onGloballyPositioned { cardWindowOrigin = it.boundsInWindow().topLeft }
            .then(
                if (isMoving) Modifier.pointerInput(station.id, stepPx, cardSpanPx) {
                    detectDragGestures(
                        onDragStart = { _ ->
                            anchorWindowPos = cardWindowOrigin.x + cardSpanPx * 0.5f
                        },
                        onDragEnd = { s.moveFinish() },
                        onDragCancel = { s.draft = null; s.moving = null }
                    ) { change, _ ->
                        change.consume()
                        s.touchPanelInteraction()
                        val currentWindowPos = cardWindowOrigin.x + change.position.x
                        val triggerThreshold = stepPx * 0.55f
                        while (kotlin.math.abs(currentWindowPos - anchorWindowPos) >= triggerThreshold && stepPx > 0f) {
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
        selected = isSelected || isMoving,
        tint = bgColor,
        stroke = borderColor,
        strokeWidth = if (isMoving) 2.5.dp else if (isSelected) 2.dp else 1.dp,
        focusColor = if (isMoving) Color(0xFFFFCF37) else Color.White,
        radius = 10.dp,
        padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        long = onLongPress,
        keyClick = {
            if (isMoving) s.moveFinish()
            else {
                runCatching { requester.requestFocus() }
                onClick()
            }
        },
        extraKey = { e ->
            if (isMoving && e.type == KeyEventType.KeyDown && e.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                s.moveBy(if (e.key in listOf(Key.DirectionLeft, Key.DirectionUp)) -1 else 1)
                true
            } else false
        },
        focusKey = focusKey,
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        value = displayTitle,
                        size = 9,
                        color = if (isMoving || isSelected) Color(0xFFFFD600) else Color.White,
                        bold = isSelected || isMoving,
                        maxLines = 1
                    )
                }
                if (isFavorite) {
                    Label(
                        value = "★",
                        size = 10,
                        color = Color(0xFFFFD600),
                        bold = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 0.dp, end = 2.dp)
                    )
                }
            }
        },
        click = {
            if (isMoving) {
                s.moveFinish()
            } else {
                s.touchPanelInteraction()
                runCatching { requester.requestFocus() }
                onClick()
            }
        }
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
