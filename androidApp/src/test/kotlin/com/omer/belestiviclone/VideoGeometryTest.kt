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
    @Test fun sevenAspectRatioModesComputeExpectedViewportGeometry() {
        val autoRect = videoRect(1920, 1080, 720, 576, 16f / 15f, 0, "Otomatik (Orijinal yayın oranı)")!!
        assertEquals(1440f, autoRect.width, .01f)
        assertEquals(1080f, autoRect.height, .01f)
        assertEquals(240f, autoRect.left, .01f)

        val stretchRect = videoRect(1920, 1080, 720, 576, 1f, 0, "Ekrana Yay (Stretch / Fill)")!!
        assertEquals(VideoRect(1920f, 1080f, 0f, 0f), stretchRect)

        val zoomRect = videoRect(1920, 1080, 720, 576, 16f / 15f, 0, "Yakınlaştır (Zoom / Crop)")!!
        assertEquals(1920f, zoomRect.width, .01f)
        assertEquals(1440f, zoomRect.height, .01f)
        assertEquals(-180f, zoomRect.top, .01f)

        val r16x9 = videoRect(1920, 1080, 720, 576, 1f, 0, "16:9 (Geniş ekran)")!!
        assertEquals(16f / 9f, r16x9.width / r16x9.height, .001f)
        assertEquals(1920f, r16x9.width, .01f)
        assertEquals(1080f, r16x9.height, .01f)

        val r4x3 = videoRect(1920, 1080, 1920, 1080, 1f, 0, "4:3 (Klasik ekran)")!!
        assertEquals(4f / 3f, r4x3.width / r4x3.height, .001f)
        assertEquals(1440f, r4x3.width, .01f)
        assertEquals(1080f, r4x3.height, .01f)
        assertEquals(240f, r4x3.left, .01f)

        val r185 = videoRect(1920, 1080, 1920, 1080, 1f, 0, "1.85:1 (Geniş sinema)")!!
        assertEquals(1.85f, r185.width / r185.height, .001f)
        assertEquals(1920f, r185.width, .01f)
        assertTrue(r185.height < 1080f && r185.top > 0f)

        val r239 = videoRect(1920, 1080, 1920, 1080, 1f, 0, "2.39:1 (Anamorfik sinema)")!!
        assertEquals(2.39f, r239.width / r239.height, .001f)
        assertEquals(1920f, r239.width, .01f)
        assertTrue(r239.height < r185.height && r239.top > r185.top)
    }
}
