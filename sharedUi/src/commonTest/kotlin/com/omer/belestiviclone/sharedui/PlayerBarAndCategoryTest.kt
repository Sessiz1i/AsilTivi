package com.omer.belestiviclone.sharedui

import androidx.compose.ui.graphics.Color
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.preview.PreviewData
import com.omer.belestiviclone.shared.preview.PreviewFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerBarAndCategoryTest {

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
}
