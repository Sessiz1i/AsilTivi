package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class LiveFormatTest {
    private class Store : PreviewStore {
        var value = PreviewData()
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) { value = data; writes++ }
    }
    @Test fun cyclePersistsAllSevenAspectRatioModesWithoutChangingPlaybackIdentity() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        s.paused = true; s.muted = true; s.retry = 4
        val before = s.data; val selected = s.selected; val uri = playbackUri(s.channel)
        val expectedOrder = LiveFormatOptions.drop(1) + LiveFormatOptions.first()
        assertEquals(7, expectedOrder.size)
        for (expected in expectedOrder) {
            s.cycleLiveFormat(); yield()
            assertEquals(expected, s.data.pref("format")); assertEquals(expected, store.value.pref("format"))
            assertNull(s.dialog); assertEquals(selected, s.selected); assertEquals(uri, playbackUri(s.channel))
            assertTrue(s.paused); assertTrue(s.muted); assertEquals(4, s.retry)
            assertEquals(before.channels, s.data.channels); assertEquals(before.favorites, s.data.favorites)
            assertEquals(before.history, s.data.history)
        }
        assertEquals(7, store.writes)
    }
    @Test fun formatMenuOpensSelectsAndPersistsAcrossChannelSwitchAndReopen() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        s.openFormatMenu()
        assertEquals("format", s.dialog)
        s.pref("format", "2.39:1 (Anamorfik sinema)"); yield()
        assertNull(s.dialog)
        assertEquals("2.39:1 (Anamorfik sinema)", s.data.pref("format"))
        s.stepChannel(1)
        assertEquals("2.39:1 (Anamorfik sinema)", s.data.pref("format"))
        val reopened = PreviewState(store, this); reopened.load()
        assertEquals("2.39:1 (Anamorfik sinema)", reopened.data.pref("format"))
    }
    @Test fun infoPanelTogglesClosesOnBackAndResetsTechnicalInfoOnChannelSwitch() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        assertFalse(s.info)
        s.toggleInfo()
        assertTrue(s.info)
        s.technicalInfo = StreamTechnicalInfo(width = 1920, height = 1080, frameRate = 50f, videoCodec = "avc1", audioCodec = "mp4a.40.2", audioChannels = 2, audioSampleRate = 48000, videoBitrate = 3_500_000)
        assertEquals(1, s.channelNumber)
        s.back()
        assertFalse(s.info)
        assertTrue(s.panel)
        s.stepChannel(1)
        assertEquals(StreamTechnicalInfo(), s.technicalInfo)
        assertEquals(2, s.channelNumber)
    }
    @Test fun liveFormatCannotChangeBehindSettingsOrAnotherModal() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load()
        s.page = "settings"; s.cycleLiveFormat(); yield(); assertEquals(0, store.writes)
        s.page = "live"; s.dialog = "format"; s.cycleLiveFormat(); yield()
        assertEquals(0, store.writes); assertEquals("format", s.dialog)
        assertEquals("Orijinal", s.data.pref("format"))
    }
}
