package com.omer.belestiviclone

import kotlin.test.*

class VideoGeometryTest {
    @Test fun fitTwoPanelsPreservesAspectAndCentersLetterbox() {
        val r = videoRect(800, 864, 1920, 1080, 1f, 0, "Sığdır (Fit)")!!
        assertEquals(800f, r.width, .01f); assertEquals(450f, r.height, .01f)
        assertEquals(0f, r.left, .01f); assertEquals(207f, r.top, .01f)
        assertEquals(16f / 9, r.width / r.height, .001f)
    }
    @Test fun cropFillsWithoutStretchingAndPanelNaturalKeepsDecodedPixels() {
        val crop = videoRect(800, 864, 1920, 1080, 1f, 0, "Tam Ekran (Kes)")!!
        assertEquals(864f, crop.height, .01f); assertTrue(crop.left < 0)
        assertEquals(16f / 9, crop.width / crop.height, .001f)
        val original = videoRect(800, 864, 1920, 1080, 1f, 0, "Orijinal (Panel)")!!
        assertEquals(1920f, original.width); assertEquals(1080f, original.height)
        assertEquals(-560f, original.left); assertEquals(-108f, original.top)
    }
    @Test fun originalDownscalesLargeVideoToViewportWithoutCuttingEdges() {
        val r = videoRect(1600, 900, 1920, 1080, 1f, 0, "Orijinal")!!
        assertEquals(VideoRect(1600f, 900f, 0f, 0f), r)
    }
    @Test fun originalAlsoUpscalesSmallVideoAndLetterboxesDifferentDisplayAspect() {
        assertEquals(VideoRect(1600f, 900f, 0f, 0f), videoRect(1600, 900, 1280, 720, 1f, 0, "Orijinal"))
        val r = videoRect(1600, 900, 720, 576, 16f / 15, 0, "Orijinal")!!
        assertEquals(1200f, r.width, .01f); assertEquals(900f, r.height, .01f)
        assertEquals(200f, r.left, .01f); assertEquals(0f, r.top, .01f)
    }
    @Test fun nonSquarePixelsAndUnappliedRotationUseDisplayAspect() {
        val r = videoRect(1000, 1000, 720, 576, 16f / 15, 90, "Sığdır (Fit)")!!
        assertEquals(.75f, r.width / r.height, .001f)
        assertEquals(1000f, r.height, .01f)
    }
    @Test fun unknownOrInvalidSizesNeverCreateAnInvalidTransform() {
        assertNull(videoRect(0, 100, 1920, 1080, 1f, 0, "Orijinal"))
        assertNull(videoRect(100, 100, 0, 0, 1f, 0, "Sığdır (Fit)"))
        assertNull(videoRect(100, 100, 720, 576, Float.NaN, 0, "Sığdır (Fit)"))
        assertNull(videoRect(100, 100, 720, 576, 0f, 0, "Sığdır (Fit)"))
    }
}
