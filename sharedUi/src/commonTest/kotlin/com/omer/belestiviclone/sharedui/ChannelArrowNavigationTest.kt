package com.omer.belestiviclone.sharedui

import androidx.compose.ui.input.key.Key
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class ChannelArrowNavigationTest {
    private class Store : PreviewStore {
        val original = PreviewData()
        var writes = 0
        override suspend fun load() = original
        override suspend fun save(data: PreviewData) { writes++ }
    }
    @Test fun horizontalArrowsSelectImmediatelyWithoutOkAndKeepPanelAndData() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"; s.panel = true; s.category = "Tümü"
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[0].id; s.paused = true
        s.stepChannel(channelArrowDelta(false, Key.DirectionRight)!!)
        assertEquals(channels[1].id, s.selected); assertFalse(s.paused); assertTrue(s.panel); assertNull(s.dialog)
        s.stepChannel(channelArrowDelta(false, Key.DirectionLeft)!!)
        assertEquals(channels[0].id, s.selected); assertEquals(store.original, s.data); assertEquals(0, store.writes)
        assertNull(channelArrowDelta(false, Key.DirectionUp)); assertNull(channelArrowDelta(false, Key.DirectionDown))
        assertNull(channelArrowDelta(false, Key.Enter))
    }
    @Test fun verticalArrowsFollowVisibleChannelOrderWhileMenusAndSearchStayBlocked() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"; s.panel = true; s.category = "Tümü"
        val channels = s.data.filtered("Tümü", ""); s.selected = channels[1].id
        s.stepChannel(channelArrowDelta(true, Key.DirectionDown)!!); assertEquals(channels[2].id, s.selected)
        s.stepChannel(channelArrowDelta(true, Key.DirectionUp)!!); assertEquals(channels[1].id, s.selected)
        s.dialog = "channel"; s.stepChannel(1); assertEquals(channels[1].id, s.selected)
        s.dialog = null; s.searching = true; s.stepChannel(1); assertEquals(channels[1].id, s.selected)
        assertNull(channelArrowDelta(true, Key.DirectionLeft)); assertNull(channelArrowDelta(true, Key.DirectionRight))
        assertEquals(0, store.writes)
    }
}
