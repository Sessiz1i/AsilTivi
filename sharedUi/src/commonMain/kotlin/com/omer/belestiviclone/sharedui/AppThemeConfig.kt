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
    val seekbarColor: Color = Color(0xFFE53935)
)

object AppThemes {
    val supported = listOf("Klasik", "Modern", "Altın", "Sinema")

    fun get(themeName: String): ThemeConfig {
        val normalized = if (themeName == "Beleş TiVi Klasik" || themeName.isBlank()) "Klasik" else themeName
        return when (normalized) {
            "Modern" -> ThemeConfig(
                name = "Modern",
                accentColor = Color(0xFF00BCD4),
                headingTitleColor = Color(0xFF80DEEA),
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
                seekbarColor = Color(0xFF00BCD4)
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
                seekbarColor = Color(0xFFFFC107)
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
                seekbarColor = Color(0xFFE50914)
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
                seekbarColor = Color(0xFFE53935)
            )
        }
    }
}
