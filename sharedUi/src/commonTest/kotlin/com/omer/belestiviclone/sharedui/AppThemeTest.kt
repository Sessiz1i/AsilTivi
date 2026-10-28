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
        assertTrue(classic.isCardFlat)
        assertFalse(classic.categoryTabPill)

        val legacy = AppThemes.get("Beleş TiVi Klasik")
        assertEquals("Klasik", legacy.name)
        assertEquals(Color(0xFFFF6B00), legacy.accentColor)

        val modern = AppThemes.get("Modern")
        assertEquals("Modern", modern.name)
        assertEquals(Color(0xFF00BCD4), modern.accentColor)
        assertEquals(Color(0xFF80DEEA), modern.headingTitleColor)
        assertFalse(modern.isCardFlat)
        assertTrue(modern.categoryTabPill)

        val gold = AppThemes.get("Altın")
        assertEquals("Altın", gold.name)
        assertEquals(Color(0xFFFFC107), gold.accentColor)
        assertEquals(Color(0xFFFFD54F), gold.headingTitleColor)
        assertFalse(gold.isCardFlat)
        assertTrue(gold.categoryTabPill)

        val cinema = AppThemes.get("Sinema")
        assertEquals("Sinema", cinema.name)
        assertEquals(Color(0xFFE50914), cinema.accentColor)
        assertEquals(Color(0xFFFF5252), cinema.headingTitleColor)
        assertFalse(cinema.isCardFlat)
        assertTrue(cinema.categoryTabPill)
    }
}
