package com.omer.belestiviclone.sharedui

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
}
