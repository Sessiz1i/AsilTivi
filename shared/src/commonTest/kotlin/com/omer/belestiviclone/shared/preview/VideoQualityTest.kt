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
    @Test fun streamTechnicalInfoFormatsResolutionFpsCodecsAudioAndBitrate() {
        val info = StreamTechnicalInfo(
            width = 1920,
            height = 1080,
            frameRate = 50f,
            videoCodec = "avc1.640028",
            videoMimeType = "video/avc",
            videoBitrate = 2_400_000,
            audioCodec = "mp4a.40.2",
            audioMimeType = "audio/mp4a-latm",
            audioChannels = 2,
            audioSampleRate = 48_000,
            audioBitrate = 128_000,
        )
        assertEquals("1080p FHD", info.resolutionBadge)
        assertEquals("1920x1080 (1080p)", info.resolutionLabel)
        assertEquals("50 fps", info.fpsLabel)
        assertEquals("H.264 / AVC", info.videoCodecLabel)
        assertEquals("AAC, 2 kanal, 48 kHz", info.audioFormatLabel)
        assertEquals("2.5 Mbps (2528 kbps)", info.bitrateLabel)

        val hevcFallback = StreamTechnicalInfo(
            width = 1280,
            height = 720,
            frameRate = 25f,
            videoMimeType = "video/hevc",
            measuredBitrate = 1_500_000,
        )
        assertEquals("720p HD", hevcFallback.resolutionBadge)
        assertEquals("1280x720 (720p)", hevcFallback.resolutionLabel)
        assertEquals("25 fps", hevcFallback.fpsLabel)
        assertEquals("H.265 / HEVC", hevcFallback.videoCodecLabel)
        assertEquals("1.5 Mbps (1500 kbps)", hevcFallback.bitrateLabel)

        assertEquals(7, LiveFormatOptions.size)
        assertTrue(isVideoFormatSelected("Orijinal", "Otomatik (Orijinal)"))
        assertTrue(isVideoFormatSelected("Orijinal", "Otomatik (Orijinal yayın oranı)"))
        assertTrue(isVideoFormatSelected("Sinema 2.39:1", "2.39:1 (Anamorfik sinema)"))
        assertTrue(isVideoFormatSelected("2.39:1 (Anamorfik sinema)", "Sinema 2.39:1"))
    }
    @Test fun streamTechnicalInfoFormatsThreeV41CommaDecimalLines() {
        val info = StreamTechnicalInfo(
            width = 1920,
            height = 1080,
            frameRate = 25f,
            videoCodec = "avc1.640028",
            videoMimeType = "video/avc",
            videoBitrate = 4_700_000,
            audioCodec = "mp4a.40.2",
            audioMimeType = "audio/mp4a-latm",
            audioChannels = 2,
            audioSampleRate = 48_000,
            audioLanguage = "tr",
            bufferedDurationMs = 12_400L,
            liveOffsetMs = 18_200L,
            isPlaying = true,
        )
        assertEquals("1920 × 1080 · 25 FPS · 4,7 Mbps · H.264", info.v41VideoLine)
        assertEquals("Türkçe · AAC · 2 ch · 48,0 kHz", info.v41AudioLine)
        assertEquals("Oynatılıyor · Tampon: 12,4 s · Gecikme: 18,2 s", info.v41PlaybackLine(paused = false))
        assertEquals("Duraklatıldı · Tampon: 12,4 s · Gecikme: 18,2 s", info.v41PlaybackLine(paused = true))
    }
}
