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
    @Test fun cyclePersistsWithoutOpeningDialogOrChangingPlaybackIdentity() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        s.paused = true; s.muted = true; s.retry = 4
        val before = s.data; val selected = s.selected; val uri = playbackUri(s.channel)
        for (expected in listOf("Sığdır (Fit)", "Tam Ekran (Kes)", "Orijinal")) {
            s.cycleLiveFormat(); yield()
            assertEquals(expected, s.data.pref("format")); assertEquals(expected, store.value.pref("format"))
            assertNull(s.dialog); assertEquals(selected, s.selected); assertEquals(uri, playbackUri(s.channel))
            assertTrue(s.paused); assertTrue(s.muted); assertEquals(4, s.retry)
            assertEquals(before.channels, s.data.channels); assertEquals(before.favorites, s.data.favorites)
            assertEquals(before.history, s.data.history)
        }
        assertEquals(3, store.writes)
    }
    @Test fun liveFormatCannotChangeBehindSettingsOrAnotherModal() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load()
        s.page = "settings"; s.cycleLiveFormat(); yield(); assertEquals(0, store.writes)
        s.page = "live"; s.dialog = "format"; s.cycleLiveFormat(); yield()
        assertEquals(0, store.writes); assertEquals("format", s.dialog)
        assertEquals("Orijinal", s.data.pref("format"))
    }
}
