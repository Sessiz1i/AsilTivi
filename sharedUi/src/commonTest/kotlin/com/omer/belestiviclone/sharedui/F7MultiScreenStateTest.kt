package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class F7MultiScreenStateTest {
    private class FakeStore : PreviewStore {
        var value = PreviewData()
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) {
            value = data
            writes++
        }
    }

    @Test
    fun allFourLayoutModesSetSlotCountsAndClampSelection() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)
        assertEquals("multi", state.page)
        assertEquals(MultiLayoutMode.TWO_SIDE_BY_SIDE, state.multi?.mode)
        assertEquals(2, state.multi?.count)

        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, state.multi?.mode)
        assertEquals(3, state.multi?.count)
        state.multi!!.selected = 2

        state.startMulti(MultiLayoutMode.ONE_BIG_THREE_SMALL)
        assertEquals(MultiLayoutMode.ONE_BIG_THREE_SMALL, state.multi?.mode)
        assertEquals(4, state.multi?.count)
        state.multi!!.selected = 3

        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)
        assertEquals(0, state.multi?.selected)

        state.startMulti(MultiLayoutMode.FOUR_EQUAL)
        assertEquals(MultiLayoutMode.FOUR_EQUAL, state.multi?.mode)
        assertEquals(4, state.multi?.count)
    }

    @Test
    fun multiChannelPickerFiltersByCategoryAndSearchQueryAndAssignsSlot() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()
        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)

        // Open picker for slot 1 (second screen)
        state.openMultiChannelPicker(1)
        assertEquals("assign", state.dialog)
        assertEquals(1, state.multi?.selected)

        // Filter by category "Haber"
        state.chooseMultiPickerCategory("Haber")
        assertEquals("Haber", state.multiPickerCategory)
        assertEquals("", state.multiPickerQuery)
        val haberChannels = state.multiPickerChannels
        assertEquals(2, haberChannels.size)
        assertTrue(haberChannels.all { "Haber" in it.groups })

        // Filter by search query "ntv"
        state.multiPickerQuery = "ntv"
        val searched = state.multiPickerChannels
        assertEquals(1, searched.size)
        assertEquals("NTV", searched.single().name)

        // Assign channel to slot 1
        val ntvId = searched.single().id
        state.assignMultiChannel(ntvId)
        yield()
        assertNull(state.dialog)
        assertEquals(ntvId, state.multi?.slots?.get(1))
        assertEquals(ntvId, state.lastMultiLayout?.slots?.get(1))
    }

    @Test
    fun saveLoadAndDeleteNamedMultiLayoutsPersistInStore() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        val trt1 = PreviewFixture.channels.first { it.name == "TRT 1" }.id
        val ahaber = PreviewFixture.channels.first { it.name == "A Haber" }.id
        val ntv = PreviewFixture.channels.first { it.name == "NTV" }.id

        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)
        state.multi!!.slots[0] = trt1
        state.multi!!.slots[1] = ahaber
        state.multi!!.slots[2] = ntv

        state.openSaveMultiLayoutDialog()
        assertEquals("saveMultiLayout", state.dialog)
        state.formName = "Haber Paketi"
        state.saveCurrentMultiLayout()
        yield()

        assertNull(state.dialog)
        assertEquals(1, state.savedMultiLayouts.size)
        val saved = state.savedMultiLayouts.single()
        assertEquals("Haber Paketi", saved.name)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, saved.mode)
        assertEquals(listOf(trt1, ahaber, ntv, null), saved.slots)

        // Verify persisted in Room/Store preferences
        val reloadedState = PreviewState(store, this)
        reloadedState.load()
        assertEquals(1, reloadedState.savedMultiLayouts.size)
        assertNotNull(reloadedState.lastMultiLayout)

        // Restore saved layout from live page in one touch
        reloadedState.loadSavedMultiLayout(reloadedState.savedMultiLayouts.single())
        assertEquals("multi", reloadedState.page)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, reloadedState.multi?.mode)
        assertEquals(ahaber, reloadedState.multi?.slots?.get(1))
        assertEquals(ntv, reloadedState.multi?.slots?.get(2))

        // Delete saved layout
        reloadedState.deleteSavedMultiLayout("Haber Paketi")
        yield()
        assertTrue(reloadedState.savedMultiLayouts.isEmpty())
    }

    @Test
    fun slotMenuOpensOnFilledSlotAndExecutesAllSevenRows() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        assertEquals(
            listOf(
                "Kanal Değiştir",
                "Tam Ekran",
                "Tekrar Dene",
                "Görüntü Oranını Değiştir",
                "Bu Yayını Kapat",
                "Ekran Düzenini Değiştir",
                "Bu Düzeni Kaydet",
            ),
            MultiSlotMenuItems
        )

        val trt1 = PreviewFixture.channels.first { it.name == "TRT 1" }
        val atv = PreviewFixture.channels.first { it.name == "ATV" }
        state.select(trt1.id)
        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)

        // Slot 0 dolu (TRT 1) -> activateMultiSlot "slotMenu" açmalı
        state.activateMultiSlot(0)
        assertEquals("slotMenu", state.dialog)
        assertEquals("1. pencere · TRT 1", state.multiSlotMenuTitle)

        // Slot 1 boş -> activateMultiSlot "assign" (Kanal Seçici) açmalı
        state.activateMultiSlot(1)
        assertEquals("assign", state.dialog)

        // Slot 1'e ATV ata ve tekrar activateMultiSlot çağır -> "2. pencere · ATV"
        state.assignMultiChannel(atv.id)
        yield()
        state.activateMultiSlot(1)
        assertEquals("slotMenu", state.dialog)
        assertEquals("2. pencere · ATV", state.multiSlotMenuTitle)

        // 1. Kanal Değiştir
        state.executeMultiSlotMenuAction("Kanal Değiştir")
        assertEquals("assign", state.dialog)

        // 3. Tekrar Dene
        state.activateMultiSlot(1)
        val beforeRetry = state.multi!!.retries[1]
        state.executeMultiSlotMenuAction("Tekrar Dene")
        assertNull(state.dialog)
        assertEquals(beforeRetry + 1, state.multi!!.retries[1])

        // 4. Görüntü Oranını Değiştir
        state.activateMultiSlot(1)
        assertEquals("Otomatik (Orijinal)", state.multi!!.slotFormat(1))
        state.executeMultiSlotMenuAction("Görüntü Oranını Değiştir")
        assertNull(state.dialog)
        assertEquals("Ekrana yay", state.multi!!.slotFormat(1))
        assertEquals("✅ 2. pencere: Ekrana yay", state.bottomToastMessage)

        // 6. Ekran Düzenini Değiştir
        state.activateMultiSlot(1)
        state.executeMultiSlotMenuAction("Ekran Düzenini Değiştir")
        assertEquals("layout", state.dialog)

        // 7. Bu Düzeni Kaydet
        state.activateMultiSlot(1)
        state.executeMultiSlotMenuAction("Bu Düzeni Kaydet")
        assertEquals("saveMultiLayout", state.dialog)

        // 5. Bu Yayını Kapat
        state.activateMultiSlot(1)
        state.executeMultiSlotMenuAction("Bu Yayını Kapat")
        yield()
        assertNull(state.dialog)
        assertNull(state.multi!!.slots[1])

        // 2. Tam Ekran (Slot 0: TRT 1 veya Slot 1'e tekrar ATV atayıp Tam Ekran)
        state.openMultiChannelPicker(1)
        state.assignMultiChannel(atv.id)
        yield()
        state.activateMultiSlot(1)
        val zapBefore = state.zapCardRevision
        state.executeMultiSlotMenuAction("Tam Ekran")
        assertEquals("live", state.page)
        assertNull(state.multi)
        assertNull(state.dialog)
        assertEquals(atv.id, state.selected)
        assertTrue(state.zapCardRevision > zapBefore)
        assertEquals("✅ 📺 ATV tam ekranda", state.bottomToastMessage)
        assertEquals(ToastTone.Success, state.bottomToastTone)
    }

    @Test
    fun backFromMultiScreenRestoresPreviousSingleScreenChannel() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        val trt1 = PreviewFixture.channels.first { it.name == "TRT 1" }
        val showTv = PreviewFixture.channels.first { it.name == "Show TV" }
        val ntv = PreviewFixture.channels.first { it.name == "NTV" }

        // Çoklu ekrana girmeden önce izlenen kanal: Show TV
        state.select(showTv.id)
        assertEquals(showTv.id, state.selected)

        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)
        // 2. slota NTV ata ve odağı 2. slota bırak
        state.openMultiChannelPicker(1)
        state.assignMultiChannel(ntv.id)
        yield()
        assertEquals(1, state.multi?.selected)
        // 1. slotu bile değiştirsek (TRT 1)
        state.openMultiChannelPicker(0)
        state.assignMultiChannel(trt1.id)
        yield()
        state.multi!!.selected = 1

        // BACK ile çoklu ekrandan çıkıldığında önceki kanal (Show TV) korunmalı
        state.back()
        assertEquals("live", state.page)
        assertNull(state.multi)
        assertEquals(showTv.id, state.selected)
    }

    @Test
    fun multiPickerExcludesRadioChannelsAndNormalizesCategories() = runBlocking {
        val tv1 = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 1), "TRT 1", "Ulusal", "logo_trt1", groups = listOf("Ulusal"))
        val tvWorld = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 2), "TRT Türk", "Ulusal - Yurt Dışı", "logo_trt1", groups = listOf("Ulusal - Yurt Dışı"))
        val tvEconomy = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 3), "Bloomberg HT", "Ekonomi", "logo_ntv", groups = listOf("Ekonomi"))
        val tvSpor1 = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 4), "TRT Spor", "SPOR", "logo_trt1", groups = listOf("SPOR"))
        val tvSpor2 = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 5), "A Spor", "Spor", "logo_ahaber", groups = listOf("Spor"))
        val radio1 = PreviewChannel(com.omer.belestiviclone.shared.catalog.OpaqueId(1, 10), "TRT FM", "Radyo", "logo_trt1", isRadio = true, groups = listOf("Radyo"))

        val store = FakeStore().also {
            it.value = PreviewData(
                channels = listOf(tv1, tvWorld, tvEconomy, tvSpor1, tvSpor2, radio1),
                favorites = listOf(tv1.id, radio1.id),
                history = listOf(radio1.id, tv1.id),
                categories = listOf("Ulusal - Yurt Dışı", "Ekonomi", "SPOR", "Spor", "Ulusal", "Radyo")
            )
        }
        val state = PreviewState(store, this)
        state.load()
        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)

        val cats = state.multiPickerCategories
        // Radyo ve ham M3U kategorileri görünmemeli, normalize kategoriler olmalı
        assertFalse(cats.any { it.equals("Radyo", ignoreCase = true) })
        assertFalse("Ulusal - Yurt Dışı" in cats)
        assertFalse("Ekonomi" in cats)
        assertEquals(1, cats.count { it.equals("Spor", ignoreCase = true) })
        assertTrue("Ulusal" in cats)
        assertTrue("Haber" in cats)
        assertTrue("DÜNYA" in cats)

        // Hiçbir sekmede (Tümü, Favoriler, Son İzlenenler dahil) radyo istasyonu çıkmamalı
        for (cat in listOf("Tümü", "Favoriler", "Son İzlenenler", "Ulusal", "Haber", "DÜNYA")) {
            state.chooseMultiPickerCategory(cat)
            assertTrue(state.multiPickerChannels.none { it.isAudioOrRadio }, "Radyo kanal seçicide görünmemeli: $cat")
        }

        // Radyo kanalı doğrudan atanmaya çalışılsa bile reddedilmeli
        state.multi!!.selected = 1
        state.assignMultiChannel(radio1.id)
        assertNull(state.multi!!.slots[1])
    }
}
