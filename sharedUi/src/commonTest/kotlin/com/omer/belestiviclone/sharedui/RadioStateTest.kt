package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
    fun backInRadioModeFirstClosesPanelThenOpensExitConfirmationDialog() = runBlocking {
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
        assertNull(s.dialog)

        // İkinci back (overlay kapalıyken): Çıkış onay diyaloğu açılır (v4.1 A52 / B28 davranışı)
        s.back()
        assertTrue(s.isRadioMode)
        assertEquals("exit", s.dialog)

        // Üçüncü back: Çıkış diyaloğunu kapatır, radyo modunda kalır
        s.back()
        assertNull(s.dialog)
        assertTrue(s.isRadioMode)

        // Üst bardaki TV Modu butonu (exitRadioMode) ise doğrudan Canlı TV moduna döndürür
        s.exitRadioMode()
        assertFalse(s.isRadioMode)
        assertEquals(tv1.id, s.selected)
    }

    @Test
    fun radioScreenDimmingAndWakeUpByTouchOrKeyConsumesAction() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val radio1 = PreviewChannel(OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))
        val radio2 = PreviewChannel(OpaqueId(1, 11), "Kral FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        val data = PreviewData(channels = listOf(tv1, radio1, radio2))
        val s = PreviewState(DummyStore(data), this)
        s.load()
        s.enterRadioMode()
        assertFalse(s.radioScreenDimmed)

        // "☾ Ekranı karart" basıldığında ekran kararır ve overlay kapanır
        s.dimRadioScreen()
        assertTrue(s.radioScreenDimmed)
        assertFalse(s.panel)

        // Karartılmışken BACK basıldığında karartma kapanır, çıkış diyaloğu AÇILMAZ
        s.back()
        assertFalse(s.radioScreenDimmed)
        assertNull(s.dialog)
        assertTrue(s.isRadioMode)

        // Tekrar karart ve yön tuşu (stepChannel) ile uyandır -> istasyon değişmez, sadece karartma kalkar
        s.dimRadioScreen()
        assertTrue(s.radioScreenDimmed)
        s.stepChannel(1)
        assertFalse(s.radioScreenDimmed)
        assertEquals(radio1.id, s.selected)

        // Tekrar karart ve OK (revealLiveControls) ile uyandır -> sadece karartma kalkar
        s.dimRadioScreen()
        assertTrue(s.radioScreenDimmed)
        s.revealLiveControls()
        assertFalse(s.radioScreenDimmed)
        assertFalse(s.panel)
    }

    @Test
    fun coldStartAfterClosingOnRadioStationOpensLastTvChannelInLiveMode() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val tv2 = PreviewChannel(OpaqueId(1, 2), "ATV", "Ulusal", "logo_atv")
        val radio1 = PreviewChannel(OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        var savedData = PreviewData(channels = listOf(tv1, tv2, radio1))
        val store = object : PreviewStore {
            override suspend fun load() = savedData
            override suspend fun save(data: PreviewData) { savedData = data }
        }

        val firstSession = PreviewState(store, this)
        firstSession.load()
        firstSession.select(tv2.id)
        firstSession.played(tv2.id); yield()
        firstSession.enterRadioMode()
        firstSession.select(radio1.id)
        firstSession.played(radio1.id); yield()

        // Son oynatılan (history.first()) radyo istasyonu oldu
        assertEquals(radio1.id, savedData.history.first())
        assertEquals(PreviewCodec.id(tv2.id), savedData.preferences["lastTvChannel"])

        // Uygulama yeniden (soğuk) başlatıldığında Canlı TV modunda tv2 açılmalı, radyo açılmamalı
        val coldStartSession = PreviewState(store, this)
        coldStartSession.load()
        assertFalse(coldStartSession.isRadioMode)
        assertEquals(tv2.id, coldStartSession.selected)
    }

    @Test
    fun defaultRadioFixtureHasValidPlaybackDefinitionsAndPlaybackUriWorksInRadioMode() = runBlocking {
        assertEquals(12, PreviewFixture.radioChannels.size)
        for (station in PreviewFixture.radioChannels) {
            assertTrue(station.isRadio)
            assertTrue(station.isAudioOrRadio)
            assertNotNull(station.definition, "${station.name} oynatma tanımı boş olmamalı")
            assertNotNull(playbackUri(station), "${station.name} playbackUri null olmamalı")
            assertTrue(station.logo.startsWith("https://") || station.logo.startsWith("http://"), "${station.name} gerçek logo URL'sine sahip olmalı")
        }

        // Yalnızca TV kanalı içeren bir katalogda Radyo moduna geçildiğinde PreviewFixture.radioChannels devreye girer
        // ve s.channel null dönmez, "Bu kaydın oynatma tanımı desteklenmiyor." hatası oluşmaz.
        val tvOnlyData = PreviewData(
            channels = listOf(PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1", definition = com.omer.belestiviclone.shared.catalog.PlaybackDefinition(com.omer.belestiviclone.shared.catalog.Address.fromForm("https://tv-trt1.medya.trt.com.tr/master.m3u8")))),
            profileId = PublicM3u.profile
        )
        val s = PreviewState(DummyStore(tvOnlyData), this)
        s.load()
        assertEquals("TiviListesi", s.profileName)

        s.enterRadioMode()
        assertTrue(s.isRadioMode)
        assertNotNull(s.channel, "Radyo modunda s.channel PreviewFixture.radioChannels üzerinden çözümlenmeli")
        assertEquals("TRT FM", s.channel?.name)
        assertNotNull(playbackUri(s.channel), "Seçili radyo istasyonunun playbackUri değeri geçerli olmalı")
        assertFalse(s.notice.contains("desteklenmiyor"), "Radyo istasyonu seçildiğinde desteklenmiyor uyarısı çıkmamalı")
    }

    @Test
    fun radioPlaybackStatusAndErrorResetOnStationSwitch() = runBlocking {
        val s = PreviewState(DummyStore(), this)
        s.load()
        s.enterRadioMode()
        assertEquals(PlaybackStatus.LOADING, s.playbackStatus)
        assertNull(s.playbackError)

        s.playbackStatus = PlaybackStatus.ERROR
        s.playbackError = "Bağlantı hatası"
        assertEquals(PlaybackStatus.ERROR, s.playbackStatus)
        assertEquals("Bağlantı hatası", s.playbackError)

        val secondStation = PreviewFixture.radioChannels[1]
        s.select(secondStation.id)
        assertEquals("Kral FM", s.channel?.name)
        assertEquals(PlaybackStatus.LOADING, s.playbackStatus)
        assertNull(s.playbackError)
    }

    @Test
    fun radioFavoriteToggleAndFilteringWorkForFixtureAndCatalogStations() = runBlocking {
        // Yalnızca TV kanalı içeren bir katalogda (TiviListe.m3u gibi) radyo istasyonları PreviewFixture.radioChannels'dan gelir
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        var savedData = PreviewData(channels = listOf(tv1), favorites = listOf(tv1.id))
        val store = object : PreviewStore {
            override suspend fun load() = savedData
            override suspend fun save(data: PreviewData) { savedData = data }
        }

        val s = PreviewState(store, this)
        s.load()
        s.enterRadioMode()

        assertEquals("Tümü", s.radioCategory)
        assertEquals(12, s.filteredRadioChannels.size)
        assertTrue(s.favoriteRadioChannels.isEmpty())

        val trtFm = PreviewFixture.radioChannels[0] // 101: TRT FM
        val kralFm = PreviewFixture.radioChannels[1] // 102: Kral FM
        assertFalse(s.isRadioFavorite(trtFm.id))

        // TRT FM ve Kral FM'i favorilere ekle
        val beforeRev = s.panelInteractionRevision
        s.toggleFavoriteWithToast(trtFm.id)
        yield()
        assertTrue(s.isRadioFavorite(trtFm.id))
        assertEquals(1, s.favoriteRadioChannels.size)
        assertEquals("TRT FM", s.favoriteRadioChannels.first().name)
        assertEquals("✅ ⭐ TRT FM favorilere eklendi", s.bottomToastMessage)
        assertEquals(ToastTone.Success, s.bottomToastTone)
        assertTrue(s.panelInteractionRevision > beforeRev)

        s.toggleFavoriteWithToast(kralFm.id)
        yield()
        assertTrue(s.isRadioFavorite(kralFm.id))
        assertEquals(2, s.favoriteRadioChannels.size)

        // Favoriler sekmesine geç
        s.radioCategory = "Favoriler"
        assertEquals(listOf(trtFm.id, kralFm.id), s.filteredRadioChannels.map { it.id })

        // Favoriler sekmesinde stepChannel sadece favori radyo istasyonları arasında gezinir
        s.select(trtFm.id)
        s.stepChannel(1)
        assertEquals(kralFm.id, s.selected)
        s.stepChannel(1)
        assertEquals(trtFm.id, s.selected) // 3. istasyona (Power FM) geçmez, başa sarar

        // TRT FM'i favorilerden çıkar
        s.toggleFavoriteWithToast(trtFm.id)
        yield()
        assertFalse(s.isRadioFavorite(trtFm.id))
        assertEquals(1, s.favoriteRadioChannels.size)
        assertEquals("⭐ TRT FM favorilerden çıkarıldı", s.bottomToastMessage)
        assertEquals(ToastTone.Warning, s.bottomToastTone)
    }

    @Test
    fun radioAndTvFavoriteReorderingWorksWithinRespectiveGroupsEvenWhenInterleaved() = runBlocking {
        val tv1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1")
        val tv2 = PreviewChannel(OpaqueId(1, 2), "ATV", "Ulusal", "logo_atv")
        val tv3 = PreviewChannel(OpaqueId(1, 3), "Show TV", "Ulusal", "logo_show")

        val r1 = PreviewFixture.radioChannels[0] // TRT FM
        val r2 = PreviewFixture.radioChannels[1] // Kral FM
        val r3 = PreviewFixture.radioChannels[2] // Power FM

        // TV ve Radyo favorileri karışık sırada dizilmiş olsun: [tv1, r1, tv2, r2, tv3, r3]
        var savedData = PreviewData(
            channels = listOf(tv1, tv2, tv3),
            favorites = listOf(tv1.id, r1.id, tv2.id, r2.id, tv3.id, r3.id)
        )
        val store = object : PreviewStore {
            override suspend fun load() = savedData
            override suspend fun save(data: PreviewData) { savedData = data }
        }

        val s = PreviewState(store, this)
        s.load()

        // 1. Radyo modunda favori sıralama
        s.enterRadioMode()
        assertEquals(listOf(r1.id, r2.id, r3.id), s.favoriteRadioChannels.map { it.id })

        // r2 (Kral FM) için sıralama başlat ve 1 adım sola (r1'in önüne) taşı
        s.target = r2.id
        s.moveStart()
        assertEquals("Favoriler", s.radioCategory)
        assertEquals(r2.id, s.moving)

        s.moveBy(-1)
        // Henüz commit edilmeden (draft/moving sırasında) favoriteRadioChannels anlık güncellenmeli!
        assertEquals(listOf(r2.id, r1.id, r3.id), s.favoriteRadioChannels.map { it.id })
        // TV favorilerinin sırası bozulmamalı
        assertEquals(listOf(tv1.id, tv2.id, tv3.id), s.visibleData.filtered("FAVORİ TV", "").map { it.id })

        s.moveFinish()
        yield()
        assertNull(s.moving)
        assertNull(s.draft)
        assertEquals(listOf(r2.id, r1.id, r3.id), s.favoriteRadioChannels.map { it.id })
        assertEquals("✅ Favori sıralaması güncellendi", s.bottomToastMessage)

        // r3 (Power FM) istasyonunu En Başa Taşı
        s.moveFavoriteToStart(r3.id)
        yield()
        assertEquals(listOf(r3.id, r2.id, r1.id), s.favoriteRadioChannels.map { it.id })
        assertEquals(listOf(tv1.id, tv2.id, tv3.id), s.visibleData.filtered("FAVORİ TV", "").map { it.id })

        // r3 (Power FM) istasyonunu En Sona Taşı
        s.moveFavoriteToEnd(r3.id)
        yield()
        assertEquals(listOf(r2.id, r1.id, r3.id), s.favoriteRadioChannels.map { it.id })

        // 2. Canlı TV modunda (FAVORİ TV) favori sıralama
        s.exitRadioMode()
        s.category = "FAVORİ TV"
        assertEquals(listOf(tv1.id, tv2.id, tv3.id), s.visibleData.filtered("FAVORİ TV", "").map { it.id })

        // tv3 (Show TV) kanalını 1 adım sola taşı (arada r2 olsa bile doğrudan tv2'nin önüne geçmeli!)
        s.target = tv3.id
        s.moveStart()
        s.moveBy(-1)
        assertEquals(listOf(tv1.id, tv3.id, tv2.id), s.visibleData.filtered("FAVORİ TV", "").map { it.id })
        s.moveFinish()
        yield()
        assertEquals(listOf(tv1.id, tv3.id, tv2.id), s.data.filtered("FAVORİ TV", "").map { it.id })
        // Radyo favorilerinin sırası bozulmamalı
        assertEquals(listOf(r2.id, r1.id, r3.id), s.favoriteRadioChannels.map { it.id })

        // tv2 kanalını En Başa Taşı
        s.moveFavoriteToStart(tv2.id)
        yield()
        assertEquals(listOf(tv2.id, tv1.id, tv3.id), s.data.filtered("FAVORİ TV", "").map { it.id })
        assertEquals(listOf(r2.id, r1.id, r3.id), s.favoriteRadioChannels.map { it.id })
    }
}
