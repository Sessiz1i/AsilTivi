package com.omer.belestiviclone.sharedui

import kotlin.test.*

class HeadingPlacementTest {
    @Test fun recordedFitGeometrySeparatesWholeHeadingAndSidebar() {
        val unit = 1.5f * 1.1975052f
        val measuredHeight = 68f / unit
        val p = headingPlacement("Sığdır (Fit)", 1600f / unit, 864f / unit, measuredHeight)
        assertTrue(p.shifted)
        // Historical mark bottom=117. Use the earliest visible TV ink, not text baseline.
        val earliestInk = 82f + (p.top - 22f) * unit
        assertTrue(earliestInk - 117f >= 6f)
        val wholeBottom = 36f + p.top * unit + 68f
        assertTrue(36f + LiveSidebarTop * unit - wholeBottom >= 7f)
    }
    @Test fun originalAndCropKeepRecordedPlacement() {
        for (format in listOf("Orijinal", "Tam Ekran (Kes)")) {
            assertEquals(HeadingPlacement(22f, false), headingPlacement(format, 890f, 481f, 38f))
        }
    }
    @Test fun narrowViewportCannotShiftIntoTopActions() {
        for (width in listOf(600f, 700f, 760f)) {
            assertFalse(headingPlacement("Sığdır (Fit)", width, 481f, 38f).shifted)
        }
        assertTrue(headingPlacement("Sığdır (Fit)", 800f, 481f, 38f).shifted)
    }
    @Test fun wrappedSubtitleOrLargeFontDoesNotMoveIntoSidebar() {
        // Measured heights are inputs: long names remain one ellipsized row; subtitle can wrap.
        for (height in listOf(39f, 46f, 57f, 76f)) {
            val p = headingPlacement("Sığdır (Fit)", 890f, 481f, height)
            assertEquals(HeadingPlacement(22f, false), p)
        }
    }
    @Test fun scaledViewportsAndHeightBoundaryKeepPanelClearance() {
        for (width in listOf(800f, 855f, 890.7f, 1000f)) {
            for (height in listOf(24f, 33f, 37.8f, 38f)) {
                val p = headingPlacement("Sığdır (Fit)", width, 481f, height)
                assertTrue(p.shifted)
                assertTrue(p.top + height <= LiveSidebarTop - 4f)
            }
        }
        assertFalse(headingPlacement("Sığdır (Fit)", 890f, 481f, 38.01f).shifted)
    }
    @Test fun portraitIsUnchanged() {
        assertEquals(HeadingPlacement(22f, false), headingPlacement("Sığdır (Fit)", 481f, 855f, 38f))
    }
}
