package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class ThemeConfig(
    val name: String,
    val accentColor: Color,
    val headingTitleColor: Color,
    val headingSubtitleColor: Color = Color.LightGray,
    val cardBackground: Color,
    val cardShape: Shape,
    val cardSelectedStroke: Color,
    val cardSelectedStrokeWidthDp: Float = 2f,
    val isCardFlat: Boolean = false,
    val categoryTabPill: Boolean = false,
    val categoryTabBg: Color? = null,
    val categoryTabSelectedStroke: Color,
    val backgroundTint: Color = Color.Black,
    val seekbarColor: Color = Color(0xFFE53935),
    val settingsBg: Color = Color(0xFF0A1628),
    val settingsPanelBg: Color = Color(0xFF0F1F3A),
    val settingsCardBg: Color = Color(0xFF13233D),
    val settingsCardBorder: Color = Color(0xFF283D5C),
    val settingsMutedText: Color = Color(0xFF8A9AB0),
    val settingsAccent: Color = Color(0xFFE67E22),
    val settingsSlider: Color = Color(0xFF80CBC4),
    val settingsFocusStroke: Color = Color(0xFFE67E22),
    val settingsFocusFill: Color = Color(0xFF2A4A7F),
    val focusFill: Color = settingsFocusFill,
    val settingsSelectedTabBg: Color = Color(0xFF1A2D4F),
    val settingsBottomBarBg: Color = Color(0xFF0A1424)
)

object AppThemes {
    val supported = listOf("Klasik", "Modern", "Altın", "Sinema")

    fun get(themeName: String): ThemeConfig {
        val normalized = if (themeName == "Beleş TiVi Klasik" || themeName.isBlank()) "Klasik" else themeName
        return when (normalized) {
            "Modern" -> ThemeConfig(
                name = "Modern",
                accentColor = Color(0xFF00BCD4),
                headingTitleColor = Color(0xFF67E8F9),
                headingSubtitleColor = Color.White.copy(alpha = 0.85f),
                cardBackground = Color(0xFF13233D),
                cardShape = RoundedCornerShape(8.dp),
                cardSelectedStroke = Color(0xFF00BCD4),
                cardSelectedStrokeWidthDp = 2f,
                isCardFlat = false,
                categoryTabPill = true,
                categoryTabBg = Color(0xFF0F1F38),
                categoryTabSelectedStroke = Color(0xFF00BCD4),
                backgroundTint = Color(0xFF0B1422),
                seekbarColor = Color(0xFF00BCD4),
                settingsBg = Color(0xFF090F1D),
                settingsPanelBg = Color(0xFF0D1626),
                settingsCardBg = Color(0xFF131F32),
                settingsCardBorder = Color(0xFF223654),
                settingsMutedText = Color(0xFF8A9AB0),
                settingsAccent = Color(0xFF67E8F9),
                settingsSlider = Color(0xFF67E8F9),
                settingsFocusStroke = Color(0xFF67E8F9),
                settingsFocusFill = Color(0xFF19394D),
                settingsSelectedTabBg = Color(0xFF1A2A40),
                settingsBottomBarBg = Color(0xFF0A111C)
            )
            "Altın" -> ThemeConfig(
                name = "Altın",
                accentColor = Color(0xFFFFC107),
                headingTitleColor = Color(0xFFFFD54F),
                headingSubtitleColor = Color(0xFFFFECB3),
                cardBackground = Color(0xFF262013),
                cardShape = RoundedCornerShape(8.dp),
                cardSelectedStroke = Color(0xFFFFC107),
                cardSelectedStrokeWidthDp = 2f,
                isCardFlat = false,
                categoryTabPill = true,
                categoryTabBg = Color(0xFF231C10),
                categoryTabSelectedStroke = Color(0xFFFFC107),
                backgroundTint = Color(0xFF18150E),
                seekbarColor = Color(0xFFFFC107),
                settingsBg = Color(0xFF141416),
                settingsPanelBg = Color(0xFF1C1C1F),
                settingsCardBg = Color(0xFF242428),
                settingsCardBorder = Color(0xFF3E341D),
                settingsMutedText = Color(0xFFB0A58A),
                settingsAccent = Color(0xFFFFC107),
                settingsSlider = Color(0xFFFFC107),
                settingsFocusStroke = Color(0xFFFFC107),
                settingsFocusFill = Color(0xFF423214),
                settingsSelectedTabBg = Color(0xFF362B16),
                settingsBottomBarBg = Color(0xFF141417)
            )
            "Sinema" -> ThemeConfig(
                name = "Sinema",
                accentColor = Color(0xFFE50914),
                headingTitleColor = Color(0xFFFF5252),
                headingSubtitleColor = Color.White.copy(alpha = 0.85f),
                cardBackground = Color(0xFF231215),
                cardShape = RoundedCornerShape(8.dp),
                cardSelectedStroke = Color(0xFFE50914),
                cardSelectedStrokeWidthDp = 2f,
                isCardFlat = false,
                categoryTabPill = true,
                categoryTabBg = Color(0xFF200E11),
                categoryTabSelectedStroke = Color(0xFFE50914),
                backgroundTint = Color(0xFF160D0F),
                seekbarColor = Color(0xFFE50914),
                settingsBg = Color(0xFF0A0A0C),
                settingsPanelBg = Color(0xFF121216),
                settingsCardBg = Color(0xFF1A1A20),
                settingsCardBorder = Color(0xFF381E22),
                settingsMutedText = Color(0xFFA89295),
                settingsAccent = Color(0xFFE50914),
                settingsSlider = Color(0xFFE50914),
                settingsFocusStroke = Color(0xFFE50914),
                settingsFocusFill = Color(0xFF3D1014),
                settingsSelectedTabBg = Color(0xFF381318),
                settingsBottomBarBg = Color(0xFF0D0D10)
            )
            else -> ThemeConfig( // "Klasik"
                name = "Klasik",
                accentColor = Color(0xFFFF6B00),
                headingTitleColor = Color(0xFFFFEB3B),
                headingSubtitleColor = Color.LightGray,
                cardBackground = Color.Transparent,
                cardShape = RoundedCornerShape(0.dp),
                cardSelectedStroke = Color(0xFFFF6B00),
                cardSelectedStrokeWidthDp = 2f,
                isCardFlat = true,
                categoryTabPill = false,
                categoryTabBg = null,
                categoryTabSelectedStroke = Color(0xFFFF6B00),
                backgroundTint = Color.Black,
                seekbarColor = Color(0xFFE53935),
                settingsBg = Color(0xFF0A1628),
                settingsPanelBg = Color(0xFF0F1F3A),
                settingsCardBg = Color(0xFF13233D),
                settingsCardBorder = Color(0xFF283D5C),
                settingsMutedText = Color(0xFF8A9AB0),
                settingsAccent = Color(0xFFE67E22),
                settingsSlider = Color(0xFF80CBC4),
                settingsFocusStroke = Color(0xFFE67E22),
                settingsFocusFill = Color(0xFF2A4A7F),
                settingsSelectedTabBg = Color(0xFF1A2D4F),
                settingsBottomBarBg = Color(0xFF0A1424)
            )
        }
    }
}
