package com.omer.belestiviclone.sharedui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppThemeTest {
    @Test
    fun supportedThemesIncludeFourThemes() {
        assertEquals(listOf("Klasik", "Modern", "Altın", "Sinema"), AppThemes.supported)
    }

    @Test
    fun themeLookupResolvesColorsAndBackwardCompatibility() {
        val classic = AppThemes.get("Klasik")
        assertEquals("Klasik", classic.name)
        assertEquals(Color(0xFFFF6B00), classic.accentColor)
        assertEquals(Color(0xFFFFEB3B), classic.headingTitleColor)
        assertEquals(Color(0xFFE53935), classic.seekbarColor)
        assertTrue(classic.isCardFlat)
        assertFalse(classic.categoryTabPill)

        val legacy = AppThemes.get("Beleş TiVi Klasik")
        assertEquals("Klasik", legacy.name)
        assertEquals(Color(0xFFFF6B00), legacy.accentColor)
        assertEquals(Color(0xFFE53935), legacy.seekbarColor)

        val modern = AppThemes.get("Modern")
        assertEquals("Modern", modern.name)
        assertEquals(Color(0xFF00BCD4), modern.accentColor)
        assertEquals(Color(0xFF67E8F9), modern.headingTitleColor)
        assertEquals(Color(0xFF00BCD4), modern.seekbarColor)
        assertFalse(modern.isCardFlat)
        assertTrue(modern.categoryTabPill)
        assertEquals(Color(0xFF090F1D), modern.settingsBg)
        assertEquals(Color(0xFF67E8F9), modern.settingsAccent)
        assertEquals(Color(0xFF67E8F9), modern.settingsFocusStroke)
        assertEquals(Color(0xFF19394D), modern.settingsFocusFill)

        val gold = AppThemes.get("Altın")
        assertEquals("Altın", gold.name)
        assertEquals(Color(0xFFFFC107), gold.accentColor)
        assertEquals(Color(0xFFFFD54F), gold.headingTitleColor)
        assertEquals(Color(0xFFFFC107), gold.seekbarColor)
        assertFalse(gold.isCardFlat)
        assertTrue(gold.categoryTabPill)
        assertEquals(Color(0xFF141416), gold.settingsBg)
        assertEquals(Color(0xFFFFC107), gold.settingsAccent)
        assertEquals(Color(0xFFFFC107), gold.settingsFocusStroke)
        assertEquals(Color(0xFF423214), gold.settingsFocusFill)

        val cinema = AppThemes.get("Sinema")
        assertEquals("Sinema", cinema.name)
        assertEquals(Color(0xFFE50914), cinema.accentColor)
        assertEquals(Color(0xFFFF5252), cinema.headingTitleColor)
        assertEquals(Color(0xFFE50914), cinema.seekbarColor)
        assertFalse(cinema.isCardFlat)
        assertTrue(cinema.categoryTabPill)
        assertEquals(Color(0xFF0A0A0C), cinema.settingsBg)
        assertEquals(Color(0xFFE50914), cinema.settingsAccent)
        assertEquals(Color(0xFFE50914), cinema.settingsFocusStroke)
        assertEquals(Color(0xFF3D1014), cinema.settingsFocusFill)
        assertEquals(Color(0xFF3D1014), cinema.focusFill)
        assertEquals(Color(0xFF223654), modern.settingsCardBorder)
        assertEquals(Color(0xFF8A9AB0), modern.settingsMutedText)
    }
}
