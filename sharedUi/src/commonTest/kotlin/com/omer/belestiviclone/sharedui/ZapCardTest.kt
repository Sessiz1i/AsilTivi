package com.omer.belestiviclone.sharedui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class ZapCardTest {
    private class Store : PreviewStore {
        override suspend fun load() = PreviewData()
        override suspend fun save(data: PreviewData) {}
    }

    @Test fun stepChannelKeepsPanelClosedAndTriggersZapCard() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Tümü"; s.panel = false
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[0].id
        val before = s.zapCardRevision
        s.stepChannel(1)
        assertEquals(channels[1].id, s.selected)
        assertFalse(s.panel)
        assertEquals(before + 1, s.zapCardRevision)
        s.stepChannel(1)
        assertEquals(before + 2, s.zapCardRevision, "Kart açıkken yeni değişim revizyonu artırıp süreyi sıfırlamalı")
    }

    @Test fun remoteStepWhilePanelOpenClosesPanelAndShowsCard() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Tümü"; s.panel = true
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[0].id
        val before = s.zapCardRevision
        s.stepChannel(-1)
        assertFalse(s.panel)
        assertEquals(before + 1, s.zapCardRevision)
    }

    @Test fun listArrowStepKeepsPanelOpenWithoutCard() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Tümü"; s.panel = true
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[0].id
        val before = s.zapCardRevision
        s.stepChannel(1, keepPanel = true)
        assertEquals(channels[1].id, s.selected)
        assertTrue(s.panel)
        assertEquals(before, s.zapCardRevision)
    }

    @Test fun selectFromOpenPanelClosesPanelAndTriggersCard() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Tümü"; s.panel = true
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[0].id
        val before = s.zapCardRevision
        s.select(channels[2].id)
        assertEquals(channels[2].id, s.selected)
        assertFalse(s.panel)
        assertEquals(before + 1, s.zapCardRevision)
    }

    @Test fun zapCardNotTriggeredOutsideLivePage() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "settings"
        val channels = s.data.filtered("Tümü", "")
        val before = s.zapCardRevision
        s.select(channels[1].id)
        assertEquals(before, s.zapCardRevision)
    }

    @Test fun modelWithoutEpgShowsEmptyStateAndHidesBadge() {
        val ch = PreviewFixture.channels.first()
        val m = zapCardModel(ch, 3, EpgNowNext(), StreamTechnicalInfo(), 0L)
        assertFalse(m.hasEpg)
        assertNull(m.qualityBadge)
        assertEquals("", m.nowTitle); assertEquals("", m.nowRange); assertEquals("", m.nextTitle)
        assertEquals(0f, m.progress)
        assertEquals(ch.name, m.channelName); assertEquals(3, m.channelNumber)
        assertEquals("03:00", m.clock) // epoch 0 → Türkiye UTC+3
        assertEquals("Yayın akışı bilgisi yok", ZapCardNoEpgText)
    }

    @Test fun modelWithEpgAndTechnicalInfo() {
        val hour = 3_600_000L
        val now = EpgProgramme("trt", "Gece Bakışı", startEpochMs = 20 * hour, endEpochMs = 22 * hour)
        val next = EpgProgramme("trt", "Gün Sonu", startEpochMs = 22 * hour, endEpochMs = 23 * hour)
        val m = zapCardModel(null, 1, EpgNowNext("trt", now, next), StreamTechnicalInfo(width = 1920, height = 1080), 21 * hour)
        assertTrue(m.hasEpg)
        assertEquals("1080p FHD", m.qualityBadge)
        assertEquals("Gece Bakışı", m.nowTitle)
        assertEquals("23:00-01:00", m.nowRange)
        assertEquals(0.5f, m.progress, 0.001f)
        assertEquals("Gün Sonu", m.nextTitle)
        assertEquals("00:00", m.clock)
        assertEquals("ikon_tv", m.logo)
    }

    @Test fun zapCardPositionAndSizePreferencesMapToAlignmentScaleAndWidth() {
        assertEquals(Alignment.BottomStart, zapCardAlignment("Sol"))
        assertEquals(Alignment.BottomCenter, zapCardAlignment("Orta"))
        assertEquals(Alignment.BottomEnd, zapCardAlignment("Sağ"))
        assertEquals(0.76f, zapCardScale("Küçük"), 0.001f)
        assertEquals(1.0f, zapCardScale("Normal"), 0.001f)
        assertEquals(1.25f, zapCardScale("Büyük"), 0.001f)
        assertTrue(zapCardWidthDp("Küçük", 900f) < zapCardWidthDp("Normal", 900f))
        assertTrue(zapCardWidthDp("Büyük", 900f) > zapCardWidthDp("Normal", 900f))
    }

    @Test fun numberPadInputBuffersDigitsMatchesChannelAndCommitsWithZapCard() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.panel = false
        val channels = s.data.filtered("Tümü", "").filter { !it.isAudioOrRadio }
        assertTrue(channels.size >= 3)
        // 1 basıldığında "ⓘ 1 › TRT 1"
        s.inputChannelDigit(1, autoCommitAfterDelay = false)
        assertEquals("1", s.numberInputBuffer)
        assertEquals("ⓘ 1 › ${channels[0].name}", s.numberInputLabel)
        // Eşleşmeyen numara girildiğinde "ⓘ 99 › ..."
        s.clearNumberInput()
        s.inputChannelDigit(9, autoCommitAfterDelay = false)
        s.inputChannelDigit(9, autoCommitAfterDelay = false)
        assertEquals("99", s.numberInputBuffer)
        assertEquals("ⓘ 99 › ...", s.numberInputLabel)
        val beforeUnmatched = s.selected
        s.commitNumberInput()
        assertEquals(beforeUnmatched, s.selected, "Eşleşmeyen numara kanalı değiştirmemeli")
        assertEquals("", s.numberInputBuffer)
        // Geçerli numara (3) girilip onaylandığında 3. kanala geçer ve ZapCard açılır
        val beforeRev = s.zapCardRevision
        s.inputChannelDigit(3, autoCommitAfterDelay = false)
        assertEquals("ⓘ 3 › ${channels[2].name}", s.numberInputLabel)
        s.commitNumberInput()
        assertEquals(channels[2].id, s.selected)
        assertEquals(beforeRev + 1, s.zapCardRevision)
        assertFalse(s.panel)
    }

    @Test fun liveControlLabelsReflectPauseAndBehindLiveEdgeStates() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"
        assertEquals("Canlı" to "⏸ Duraklat", liveControlLabels(s.paused, s.isBehindLiveEdge))
        s.togglePause()
        assertTrue(s.paused)
        assertEquals("|| Yayın Durduruldu" to "▶ Oynat", liveControlLabels(s.paused, s.isBehindLiveEdge))
        s.seekInLiveWindow(0.5f)
        assertFalse(s.paused)
        assertTrue(s.isBehindLiveEdge)
        assertEquals("● ⏩ Canlıya Dön" to "⏸ Duraklat", liveControlLabels(s.paused, s.isBehindLiveEdge))
        s.syncLive()
        assertFalse(s.isBehindLiveEdge)
        assertEquals("Canlı" to "⏸ Duraklat", liveControlLabels(s.paused, s.isBehindLiveEdge))
    }
}
