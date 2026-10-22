package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class RemoteNavigationTest {
    private class Store(val value: PreviewData = PreviewData()) : PreviewStore {
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) { writes++ }
    }
    @Test fun favoriteOrderHiddenCandidatesAndBothWrapDirectionsUseNormalSelection() = runBlocking {
        val ids = PreviewFixture.channels.take(4).map { it.id }
        val store = Store(PreviewData().copy(favorites = listOf(ids[2], ids[0], ids[3], ids[1]), hidden = setOf(ids[0])))
        val s = PreviewState(store, this); s.load(); s.page = "live"; s.category = "Favoriler"; s.selected = ids[2]; s.panel = false; s.paused = true
        s.stepChannel(1); assertEquals(ids[3], s.selected); assertFalse(s.paused)
        s.stepChannel(1); assertEquals(ids[1], s.selected)
        s.stepChannel(1); assertEquals(ids[2], s.selected)
        s.stepChannel(-1); assertEquals(ids[1], s.selected)
        assertFalse(s.panel); assertFalse(s.liveHeadingVisible)
        assertEquals(store.value, s.data); assertEquals(0, store.writes); assertEquals(0, s.retry)
    }
    @Test fun absentEmptyAndSingleCandidateNeverInventOrRestartAChannel() = runBlocking {
        val ids = PreviewFixture.channels.take(3).map { it.id }
        val store = Store(PreviewData().copy(favorites = listOf(ids[1])))
        val s = PreviewState(store, this); s.load(); s.page = "live"; s.category = "Favoriler"
        s.selected = ids[0]; s.stepChannel(1); assertEquals(ids[1], s.selected)
        s.selected = ids[2]; s.stepChannel(-1); assertEquals(ids[1], s.selected)
        s.paused = true; s.stepChannel(1); assertTrue(s.paused); assertEquals(0, s.retry)
        s.category = "missing-category"; s.stepChannel(-1); assertEquals(ids[1], s.selected); assertTrue(s.paused)
        assertEquals(store.value, s.data); assertEquals(0, store.writes)
    }
    @Test fun categoryOrderAndAbsentSelectionChooseFirstOrLastVisibleItem() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Tümü"
        val list = s.data.filtered(s.category, "")
        s.selected = list.last().id; s.stepChannel(1); assertEquals(list.first().id, s.selected)
        s.stepChannel(-1); assertEquals(list.last().id, s.selected)
        s.category = list.first().groups.first()
        val category = s.data.filtered(s.category, "")
        s.selected = com.omer.belestiviclone.shared.catalog.OpaqueId(-999, -999)
        s.stepChannel(-1); assertEquals(category.last().id, s.selected)
        s.selected = com.omer.belestiviclone.shared.catalog.OpaqueId(-999, -999)
        s.stepChannel(1); assertEquals(category.first().id, s.selected)
    }
    @Test fun otherRoutesSearchFormsModalAndEditingDoNotZapBackgroundPlayback() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); val selected = s.selected
        for (page in listOf("home", "settings", "multi")) { s.page = page; s.stepChannel(1); assertEquals(selected, s.selected) }
        s.page = "live"
        for (dialog in listOf("format", "channel", "m3u", "categories")) { s.dialog = dialog; s.stepChannel(1); assertEquals(selected, s.selected) }
        s.dialog = null; s.searching = true; s.stepChannel(1); assertEquals(selected, s.selected)
        s.searching = false; s.query = "TRT"; s.stepChannel(1); assertEquals(selected, s.selected)
        s.query = ""; s.moving = selected; s.stepChannel(1); assertEquals(selected, s.selected)
        s.moving = null; s.draft = s.data; s.stepChannel(1); assertEquals(selected, s.selected)
    }
    @Test fun backRevealAndModalCancellationPreservePlaybackAndData() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        s.paused = true; s.muted = true; s.retry = 3; val selected = s.selected; val data = s.data; val uri = playbackUri(s.channel)
        s.searching = true; s.query = "TRT"; s.dialog = "format"
        s.back(); assertNull(s.dialog); assertTrue(s.searching); assertTrue(s.panel)
        s.back(); assertFalse(s.searching); assertTrue(s.panel)
        s.back(); assertFalse(s.panel); assertFalse(s.liveHeadingVisible)
        s.revealLiveControls(); assertTrue(s.panel); assertTrue(s.liveHeadingVisible)
        s.back(); s.back(); assertEquals("home", s.page)
        s.revealLiveControls(); assertEquals("home", s.page); assertFalse(s.liveHeadingVisible)
        assertEquals(selected, s.selected); assertEquals(uri, playbackUri(s.channel)); assertEquals(data, s.data)
        assertTrue(s.paused); assertTrue(s.muted); assertEquals(3, s.retry); assertEquals(0, store.writes)
    }
    @Test fun entryFocusUsesMountedPreferredCardAndModalOpenerKeepsIdentity() {
        val registry = FocusRegistry<Any>(); val close = registry.requester("close") { Any() }
        val live = registry.requester("home:live") { Any() }
        assertSame(live, registry.entryTarget("home:live", listOf(close, live)))
        assertSame(close, registry.entryTarget("home:live", listOf(close)))
        assertNull(registry.entryTarget("home:live", emptyList()))
        assertSame(live, registry.returnTarget(live, listOf(close, live)))
    }
}
