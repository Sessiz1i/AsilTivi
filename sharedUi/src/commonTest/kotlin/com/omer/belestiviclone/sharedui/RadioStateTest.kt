package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RadioStateTest {
    private class DummyStore(val value: PreviewData = PreviewData()) : PreviewStore {
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) {}
    }

    @Test
    fun radioChannelDetectionAndFlagsWorkCorrectly() {
        val tvChannel = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        assertFalse(tvChannel.isRadio)
        assertFalse(tvChannel.isAudioOrRadio)

        val radioChannelByGroup = PreviewChannel(OpaqueId(1, 2), "TRT FM", "Radyo", "logo_trt1", groups = listOf("Radyo"))
        assertTrue(radioChannelByGroup.isAudioOrRadio)

        val radioChannelByFlag = PreviewChannel(OpaqueId(1, 3), "Kral FM", "Müzik", "logo_trt1", isRadio = true)
        assertTrue(radioChannelByFlag.isRadio)
        assertTrue(radioChannelByFlag.isAudioOrRadio)
    }

    @Test
    fun enterAndExitRadioModePreservesLastTvAndRadioChannels() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val tv2 = PreviewChannel(OpaqueId(1, 2), "ATV", "Ulusal", "logo_atv")
        val radio1 = PreviewChannel(OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))
        val radio2 = PreviewChannel(OpaqueId(1, 11), "Kral FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        val data = PreviewData(channels = listOf(tv1, tv2, radio1, radio2))
        val s = PreviewState(DummyStore(data), this)
        s.load()

        s.select(tv2.id)
        assertEquals(tv2.id, s.selected)
        assertFalse(s.isRadioMode)

        // Radyo moduna gir
        s.enterRadioMode()
        assertTrue(s.isRadioMode)
        assertTrue(s.panel)
        assertEquals(tv2.id, s.lastTvChannel)
        assertEquals(radio1.id, s.selected) // İlk radyo kanalı seçildi

        // İkinci radyoyu seç
        s.select(radio2.id)
        assertEquals(radio2.id, s.selected)

        // TV moduna geri dön
        s.exitRadioMode()
        assertFalse(s.isRadioMode)
        assertEquals(radio2.id, s.lastRadioChannel) // Son dinlenen radyo kaydedildi
        assertEquals(tv2.id, s.selected) // Son izlenen TV kanalına geri dönüldü

        // Tekrar radyo moduna gir (toggle ile)
        s.toggleRadioMode()
        assertTrue(s.isRadioMode)
        assertEquals(radio2.id, s.selected) // Son dinlenen radyo istasyonundan devam etti
    }

    @Test
    fun stepChannelInRadioModeOnlyCyclesThroughRadioChannels() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val radio1 = PreviewChannel(OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))
        val radio2 = PreviewChannel(OpaqueId(1, 11), "Kral FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))
        val radio3 = PreviewChannel(OpaqueId(1, 12), "Power FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        val data = PreviewData(channels = listOf(tv1, radio1, radio2, radio3))
        val s = PreviewState(DummyStore(data), this)
        s.load()

        s.enterRadioMode()
        s.select(radio1.id)

        // İleri adım (delta = 1)
        s.stepChannel(1)
        assertEquals(radio2.id, s.selected)

        s.stepChannel(1)
        assertEquals(radio3.id, s.selected)

        // Döngüsel sarma (son radyodan ilk radyoya döner, TV kanalına asla gitmez)
        s.stepChannel(1)
        assertEquals(radio1.id, s.selected)

        // Geri adım (delta = -1)
        s.stepChannel(-1)
        assertEquals(radio3.id, s.selected)
    }

    @Test
    fun backInRadioModeFirstClosesPanelThenExitsToTvMode() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val radio1 = PreviewChannel(OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        val data = PreviewData(channels = listOf(tv1, radio1))
        val s = PreviewState(DummyStore(data), this)
        s.load()

        s.select(tv1.id)
        s.enterRadioMode()
        assertTrue(s.isRadioMode)
        assertTrue(s.panel)

        // İlk back: Panel kapanır, radyo modu devam eder
        s.back()
        assertTrue(s.isRadioMode)
        assertFalse(s.panel)

        // İkinci back: TV moduna geri döner
        s.back()
        assertFalse(s.isRadioMode)
        assertEquals(tv1.id, s.selected)

        // Üçüncü back: TV modunda çıkış onay diyaloğu açılır
        s.back()
        assertEquals("exit", s.dialog)
    }
}
