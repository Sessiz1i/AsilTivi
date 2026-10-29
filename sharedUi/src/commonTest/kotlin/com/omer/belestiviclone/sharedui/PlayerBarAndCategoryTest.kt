package com.omer.belestiviclone.sharedui

import androidx.compose.ui.graphics.Color
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.CategoryNormalizer
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.preview.PreviewData
import com.omer.belestiviclone.shared.preview.PreviewFixture
import com.omer.belestiviclone.shared.preview.PreviewStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerBarAndCategoryTest {

    private class FakeStore(var value: PreviewData = PreviewData()) : PreviewStore {
        override suspend fun load(): PreviewData = value
        override suspend fun save(data: PreviewData) { value = data }
    }

    @Test
    fun appThemeProvidesCorrectSeekbarColorsForEachTheme() {
        assertEquals(Color(0xFFE53935), AppThemes.get("Klasik").seekbarColor)
        assertEquals(Color(0xFF00BCD4), AppThemes.get("Modern").seekbarColor)
        assertEquals(Color(0xFFFFC107), AppThemes.get("Altın").seekbarColor)
        assertEquals(Color(0xFFE50914), AppThemes.get("Sinema").seekbarColor)
    }

    @Test
    fun filteredSupportsBothFavoriTvAndFavoriler() {
        val testChannel1 = PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", "logo1")
        val testChannel2 = PreviewChannel(OpaqueId(1, 2), "NTV", "Haber", "logo2")
        val data = PreviewData(
            channels = listOf(testChannel1, testChannel2),
            favorites = listOf(testChannel1.id)
        )

        val byFavoriler = data.filtered("Favoriler", "")
        val byFavoriTv = data.filtered("FAVORİ TV", "")

        assertEquals(1, byFavoriler.size)
        assertEquals("TRT 1", byFavoriler.first().name)
        assertEquals(byFavoriler, byFavoriTv)
    }

    @Test
    fun filteredSupportsBothAzAndAzerbaycan() {
        val azChannel = PreviewChannel(OpaqueId(1, 10), "İctimai TV", "AZERBAYCAN", "logo_az", groups = listOf("AZERBAYCAN"))
        val data = PreviewData(channels = listOf(azChannel))

        val byAz = data.filtered("AZ", "")
        val byAzerbaycan = data.filtered("AZERBAYCAN", "")

        assertEquals(1, byAz.size)
        assertEquals("İctimai TV", byAz.first().name)
        assertEquals(byAz, byAzerbaycan)
    }

    @Test
    fun filteredSupportsBothTumuVariants() {
        val channels = listOf(
            PreviewChannel(OpaqueId(1, 1), "Kanal 1", "Ulusal", ""),
            PreviewChannel(OpaqueId(1, 2), "Kanal 2", "Haber", "")
        )
        val data = PreviewData(channels = channels)

        val tumuLower = data.filtered("Tümü", "")
        val tumuUpper = data.filtered("TÜMÜ", "")

        assertEquals(2, tumuLower.size)
        assertEquals(tumuLower, tumuUpper)
    }

    @Test
    fun categoryNormalizerDeduplicatesCaseInsensitiveSporAndAliases() {
        val raw = listOf("Ulusal", "Haber", "SPOR", "Spor", "spor", "Kıbrıs", "KKTC", "AZ", "AZERBAYCAN", "Radyo")
        val dedup = CategoryNormalizer.deduplicateCategories(raw, normalizeAliases = true)

        assertEquals(1, dedup.count { CategoryNormalizer.matches(it, "SPOR") })
        assertEquals(1, dedup.count { CategoryNormalizer.matches(it, "KKTC") })
        assertEquals(1, dedup.count { CategoryNormalizer.matches(it, "AZERBAYCAN") })
        assertTrue("SPOR" in dedup)
        assertFalse("Spor" in dedup)
        assertTrue("KKTC" in dedup)
        assertTrue("AZERBAYCAN" in dedup)
    }

    @Test
    fun categoriesListUsesAzerbaycanKktcDunya247AndPlacesRadyoAtEnd() = runBlocking {
        val chlist = listOf(
            PreviewChannel(OpaqueId(1, 1), "TRT 1", "Ulusal", ""),
            PreviewChannel(OpaqueId(1, 2), "A Spor", "SPOR", ""),
            PreviewChannel(OpaqueId(1, 3), "HT Spor", "Spor", ""),
            PreviewChannel(OpaqueId(1, 4), "BRT 1", "Kıbrıs", ""),
            PreviewChannel(OpaqueId(1, 5), "İctimai TV", "AZERBAYCAN", ""),
            PreviewChannel(OpaqueId(1, 6), "TRT Türk", "Ulusal - Yurt Dışı", ""),
            PreviewChannel(OpaqueId(1, 7), "Yeşilçam 7/24", "7/24 Youtube", ""),
            PreviewChannel(OpaqueId(1, 8), "TRT FM", "Radyo", "", isRadio = true)
        )
        val data = PreviewData(
            channels = chlist,
            categories = listOf("Ulusal", "SPOR", "Spor", "Kıbrıs", "AZERBAYCAN", "Ulusal - Yurt Dışı", "7/24 Youtube", "Radyo")
        )
        val state = PreviewState(FakeStore(data), this)
        state.load()

        val barCats = categories(state)
        assertTrue("AZERBAYCAN" in barCats)
        assertFalse("AZ" in barCats)
        assertTrue("KKTC" in barCats)
        assertFalse("Kıbrıs" in barCats)
        assertTrue("DÜNYA" in barCats)
        assertTrue("7/24 DİZİ/FİLM" in barCats)
        assertEquals("RADYO", barCats.last())

        val kktcIdx = barCats.indexOf("KKTC")
        val azIdx = barCats.indexOf("AZERBAYCAN")
        val dunyaIdx = barCats.indexOf("DÜNYA")
        val diziIdx = barCats.indexOf("7/24 DİZİ/FİLM")
        val radyoIdx = barCats.indexOf("RADYO")
        assertTrue(kktcIdx < azIdx && azIdx < dunyaIdx && dunyaIdx < diziIdx && diziIdx < radyoIdx)

        assertEquals(2, state.data.filtered("SPOR", "").size)
        assertEquals(2, state.data.filtered("Spor", "").size)
        assertEquals(1, state.data.filtered("KKTC", "").size)
        assertEquals(1, state.data.filtered("DÜNYA", "").size)
        assertEquals(1, state.data.filtered("7/24 DİZİ/FİLM", "").size)
        assertEquals(1, state.managedCategories.count { it.equals("SPOR", ignoreCase = true) })
    }

    @Test
    fun chooseCategoryWithFocusFirstCardIncrementsRevision() = runBlocking {
        val state = PreviewState(FakeStore(), this)
        state.load()
        val before = state.categoryFirstCardFocusRevision

        state.chooseCategory("Haber", focusFirstCard = false)
        assertEquals("Haber", state.category)
        assertEquals(before, state.categoryFirstCardFocusRevision)

        state.chooseCategory("Ulusal", focusFirstCard = true)
        assertEquals("Ulusal", state.category)
        assertEquals(before + 1, state.categoryFirstCardFocusRevision)
    }

    @Test
    fun bottomToastOnFavoriteAddRemoveHideAndAppearanceSettings() = runBlocking {
        val state = PreviewState(FakeStore(PreviewData(favorites = emptyList())), this)
        state.load()
        val trt1 = state.data.channels.first { it.name == "TRT 1" }

        state.target = trt1.id
        state.toggleFavoriteWithToast(trt1.id)
        yield()
        assertTrue(trt1.id in state.data.favorites)
        assertEquals("✅ ⭐ TRT 1 favorilere eklendi", state.bottomToastMessage)
        assertEquals(ToastTone.Success, state.bottomToastTone)

        state.target = trt1.id
        state.toggleFavoriteWithToast(trt1.id)
        yield()
        assertFalse(trt1.id in state.data.favorites)
        assertEquals("⭐ TRT 1 favorilerden çıkarıldı", state.bottomToastMessage)
        assertEquals(ToastTone.Warning, state.bottomToastTone)

        state.target = trt1.id
        state.hideChannelWithToast(trt1.id)
        yield()
        assertTrue(trt1.id in state.data.hidden)
        assertEquals("Kanal gizlendi: TRT 1", state.bottomToastMessage)
        assertEquals(ToastTone.Warning, state.bottomToastTone)

        state.pref("list", "Dikey (Sidebar)")
        yield()
        assertEquals("✅ Kanal listesi: Dikey", state.bottomToastMessage)
        assertEquals(ToastTone.Success, state.bottomToastTone)

        state.pref("list", "Yatay (Alt)")
        yield()
        assertEquals("✅ Kanal listesi: Yatay", state.bottomToastMessage)
        assertEquals(ToastTone.Success, state.bottomToastTone)

        state.pref("size", "Büyük")
        yield()
        assertEquals("✅ Banner boyutu güncellendi", state.bottomToastMessage)
        assertEquals(ToastTone.Success, state.bottomToastTone)

        state.clearBottomToast()
        assertNull(state.bottomToastMessage)
    }
}
