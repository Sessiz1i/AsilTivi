package com.omer.belestiviclone.shared.preview

import kotlin.test.*

class VideoQualityTest {
    @Test fun ceilingAndDefaultPreserveExistingLabels() {
        assertEquals("720p HD", PlaybackPreferences().quality)
        assertEquals(listOf(360,480,720,1080,2160), listOf("360p","480p","720p HD","1080p FHD","4K UHD").map { PlaybackPreferences(it).cap!!.height })
        assertTrue(PlaybackPreferences("480p").cap!!.contains(854,480))
        assertFalse(PlaybackPreferences("360p").cap!!.contains(854,480))
        assertNull(PlaybackPreferences("legacy-unknown").cap)
    }
    @Test fun missingVariantAndBufferedOldInputNeverClaimApplied() {
        val hd=VideoTrackFormat(1920,1080,4_000_000,"avc1")
        val sd=VideoTrackFormat(854,480,1_000_000,"avc1")
        val pending=VideoQualityObservation(hd,listOf(sd,hd))
        assertTrue(pending.description(PlaybackPreferences("480p")).contains("geçiş henüz doğrulanmadı"))
        assertTrue(pending.description(PlaybackPreferences("360p")).contains("desteklenen track yok"))
        assertTrue(pending.description(PlaybackPreferences("4K UHD")).contains("seviye kaynakta yok"))
        assertTrue(VideoQualityObservation().description(PlaybackPreferences()).contains("henüz ölçülmedi"))
        assertTrue(VideoQualityObservation().description(PlaybackPreferences("old")).contains("bilinmeyen tercih"))
        assertTrue(VideoQualityObservation(unsupported=true).description(PlaybackPreferences()).contains("bu cihazda desteklenmiyor"))
    }
}
