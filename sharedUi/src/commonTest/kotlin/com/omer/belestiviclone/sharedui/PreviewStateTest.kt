package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

/** Fake-store tests cover UI transitions, not Android SQLite persistence. */
class PreviewStateTest {
    @Test fun directClearWithFavoritesClosesPanelAndKeepsFavoriteOrderAndHistoryPreference() = runBlocking {
        for (history in listOf("Açık", "Kapalı")) {
            val store = Store().also { it.value = it.value.withPref("history", history) }
            val s = PreviewState(store, this); s.load(); s.page = "live"; s.category = "Son İzlenenler"
            s.selected = PreviewFixture.channels.last().id
            val before = s.data
            s.clearHistory(); yield()
            assertFalse(s.panel)
            assertEquals("Favoriler", s.category); assertEquals(before.favorites.first(), s.selected)
            assertEquals(before.copy(history = emptyList()), s.data); assertEquals(s.data, store.value)
            assertFalse(s.returnAfterClear); assertNull(s.dialog)
        }
    }
    @Test fun directClearWithoutFavoritesClosesPanelAndPreservesPlayback() = runBlocking {
        val store = Store().also { it.value = it.value.copy(favorites = emptyList()) }
        val s = PreviewState(store, this); s.load(); s.page = "live"; s.category = "Son İzlenenler"
        s.selected = PreviewFixture.channels.last().id; s.paused = true; s.muted = true
        val playing = s.selected
        s.clearHistory(); yield()
        assertFalse(s.panel); assertEquals("Tümü", s.category); assertEquals(playing, s.selected)
        assertTrue(s.paused); assertTrue(s.muted); assertEquals(0, s.retry)
        assertTrue(s.data.favorites.isEmpty()); assertTrue(s.data.history.isEmpty())
    }
    @Test fun directClearDropsClosedSearchAndCategoryLayersSoBackHasNoHiddenConsumer() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.category = "Son İzlenenler"
        s.searching = true; s.query = "trt"; s.categoryMenu = true
        s.clearHistory(); yield()
        assertFalse(s.searching); assertEquals("", s.query); assertFalse(s.categoryMenu); assertFalse(s.panel)
        s.back(); assertEquals("exit", s.dialog); assertEquals("live", s.page)
        s.back(); assertNull(s.dialog); assertEquals("live", s.page)
    }
    @Test fun failedDirectClearPreservesHistoryListChannelPanelAndLayers() = runBlocking {
        val store = Store().also { it.fail = true }
        val s = PreviewState(store, this); s.load(); s.page = "live"; s.category = "Son İzlenenler"
        s.searching = true; s.query = "trt"; s.categoryMenu = true; s.paused = true
        val before = s.data; val playing = s.selected
        s.clearHistory(); yield()
        assertEquals(before, s.data); assertEquals(before, store.value); assertEquals(0, store.writes)
        assertEquals("Son İzlenenler", s.category); assertEquals(playing, s.selected); assertTrue(s.panel)
        assertTrue(s.searching); assertEquals("trt", s.query); assertTrue(s.categoryMenu); assertTrue(s.paused)
        assertFalse(s.returnAfterClear); assertTrue(s.notice.contains("Kaydedilemedi"))
    }
    @Test fun settingsClearStaysInApplicationUntilExitThenUsesFavoriteReturn() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
        s.category = "Son İzlenenler"; s.selected = PreviewFixture.channels.last().id
        s.settings(); s.section = 3; s.dialog = "clear"
        val before = s.data; val playing = s.selected
        s.clearHistory(); yield()
        assertEquals("settings", s.page); assertEquals(3, s.section); assertNull(s.dialog)
        assertTrue(s.panel); assertTrue(s.returnAfterClear); assertEquals(playing, s.selected)
        assertEquals("Son İzlenenler", s.category); assertEquals(before.copy(history = emptyList()), s.data)
        s.back(); assertEquals("live", s.page); assertFalse(s.panel); assertEquals("Favoriler", s.category)
        assertEquals(before.favorites.first(), s.selected); assertFalse(s.returnAfterClear)
    }
    @Test fun settingsClearWithoutFavoritesKeepsPlaybackOnLaterExit() = runBlocking {
        val store = Store().also { it.value = it.value.copy(favorites = emptyList()).withPref("history", "Kapalı") }
        val s = PreviewState(store, this); s.load(); s.page = "live"; val playing = s.selected
        s.settings(); s.section = 3; s.dialog = "clear"; s.clearHistory(); yield()
        assertEquals("settings", s.page); assertEquals(3, s.section); assertNull(s.dialog)
        assertTrue(s.panel); assertEquals("Kapalı", s.data.pref("history")); assertTrue(s.data.history.isEmpty())
        s.back(); assertEquals("live", s.page); assertFalse(s.panel); assertEquals("Tümü", s.category)
        assertEquals(playing, s.selected); assertTrue(s.data.history.isEmpty())
    }
    @Test fun settingsClearCancelAndModalBackDoNotClearOrApplyDirectReturn() = runBlocking {
        for (useBack in listOf(false, true)) {
            val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"
            s.category = "Son İzlenenler"; val before = s.data; val playing = s.selected
            s.settings(); s.section = 3; s.dialog = "clear"
            if (useBack) s.back() else s.dismiss()
            assertEquals("settings", s.page); assertEquals(3, s.section); assertNull(s.dialog); assertTrue(s.panel)
            assertEquals(before, s.data); assertEquals(0, store.writes); assertFalse(s.returnAfterClear)
            s.back(); assertEquals("live", s.page); assertEquals("Son İzlenenler", s.category)
            assertEquals(playing, s.selected); assertEquals(before, s.data); assertFalse(s.panel)
        }
    }
    @Test fun settingsOpenedFromSearchDoesNotNeedAnExtraBack() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"
        s.searching = true; s.query = "trt"; s.categoryMenu = true
        s.settings(); s.back()
        assertEquals("live", s.page); assertFalse(s.searching); assertEquals("", s.query); assertFalse(s.categoryMenu)
    }
    @Test fun multiOpenedFromSearchReturnsToLiveOnOneBack() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.searching = true; s.query = "trt"
        s.startMulti(4); s.back(); yield()
        assertEquals("live", s.page); assertFalse(s.searching); assertEquals("", s.query); assertNull(s.multi)
    }
    @Test fun translationKeepsStoredValuesAndTranslatesSelectedChoice() {
        assertEquals("List position  ›  Vertical (Sidebar)", translated("Liste Konumu  ›  Dikey (Sidebar)", true))
        assertEquals("Small  ✓", translated("Küçük  ✓", true))
        assertEquals("Küçük", translated("Küçük", false))
    }
    @Test fun removingTargetFocusesNearestRemainingFavoriteWithoutPlayingIt() = runBlocking {
        val s = PreviewState(Store(), this); s.load()
        val playing = s.selected; val removed = s.data.favorites[2]; val next = s.data.favorites[3]
        s.target = removed; s.dialog = "favorite"; s.channelChange(s.data.toggleFavorite(removed)); yield()
        assertEquals(next, s.target); assertEquals(playing, s.selected); assertNull(s.dialog)
    }
    private class Store : PreviewStore {
        var value = PreviewData()
        var fail = false
        var failLoad = false
        var writes = 0
        override suspend fun load(): PreviewData { check(!failLoad); return value }
        override suspend fun save(data: PreviewData) { check(!fail); value = data; writes++ }
    }
    @Test fun modalBackOnlyClosesModal() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "settings"; s.dialog = "size"
        s.back(); assertNull(s.dialog); assertEquals("settings", s.page)
    }
    @Test fun settingsReturnsToEntryAndHidesLiveControls() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; s.settings(); s.back()
        assertEquals("live", s.page); assertFalse(s.panel)
    }
    @Test fun searchBackClearsQueryWithoutChangingPlayingChannel() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.page = "live"; val id = s.selected
        s.searching = true; s.query = "trt"; s.back()
        assertFalse(s.searching); assertEquals("", s.query); assertEquals(id, s.selected); assertTrue(s.panel)
    }
    @Test fun categorySwitchDropsOldQuery() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); s.query = "trt"; s.categoryMenu = true
        s.chooseCategory("Haber"); assertEquals("", s.query); assertFalse(s.categoryMenu)
    }
    @Test fun moveCancelRestoresOriginalAndDoesNotWrite() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load()
        val before = s.data.favorites; s.target = before.last(); s.moveStart(); s.moveBy(-3)
        assertNotEquals(before, s.visibleData.favorites); s.back()
        assertEquals(before, s.visibleData.favorites); assertEquals(0, store.writes)
    }
    @Test fun successfulMoveCommitsOnceAndDoesNotPlayTarget() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val playing = s.selected
        s.target = s.data.favorites.last(); s.moveStart(); s.moveBy(-4); s.moveFinish(); yield()
        assertEquals(s.target, s.data.favorites.first()); assertEquals(1, store.writes); assertEquals(playing, s.selected)
    }
    @Test fun failedMoveKeepsStoredAndVisibleOrder() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val before = s.data
        store.fail = true; s.target = s.data.favorites.last(); s.moveStart(); s.moveBy(-4); s.moveFinish(); yield()
        assertEquals(before, s.data); assertEquals(before, store.value); assertNull(s.draft); assertNull(s.moving)
        assertTrue(s.notice.contains("Kaydedilemedi"))
    }
    @Test fun failedMembershipDoesNotCloseMenuOrClaimSuccess() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val before = s.data
        store.fail = true; s.dialog = "channel"; s.commit(s.data.toggleFavorite(s.selected)) { s.dismiss() }; yield()
        assertEquals(before, s.data); assertEquals("channel", s.dialog)
    }
    @Test fun historyOffPreservesFixtureAndMovesOnlyList() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); val history = s.data.history; val playing = s.selected
        s.category = "Son İzlenenler"; s.pref("history", "Kapalı"); yield()
        assertEquals(history, s.data.history); assertEquals("Tümü", s.category); assertEquals(playing, s.selected)
    }
    @Test fun selectingChannelsDoesNotInventSuccessfulWatchHistory() = runBlocking {
        val s = PreviewState(Store(), this); s.load(); val history = s.data.history
        s.select(PreviewFixture.channels.last().id); assertEquals(history, s.data.history)
    }
    @Test fun clearHistoryWithEmptyFavoritesPreservesPlayback() = runBlocking {
        val store = Store().also { it.value = it.value.copy(favorites = emptyList()) }
        val s = PreviewState(store, this); s.load(); s.page = "live"; val id = s.selected
        s.clearHistory(); yield(); assertTrue(s.data.history.isEmpty()); assertEquals("Tümü", s.category); assertEquals(id, s.selected)
    }
    @Test fun unavailableSourceLoaderDoesNotSaveFakeRecordAndBackDropsSecretDraft() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load()
        s.sourceForm("m3u", 0); s.formName = "Yeni örnek"; s.saveSource(); yield()
        assertEquals(listOf("Örnek kayıt"), s.data.sources)
        s.sourceForm("xtream"); s.formPassword = "fixture-secret"; s.back(); assertEquals("", s.formPassword); assertEquals(0, store.writes)
    }
    @Test fun failedLoadUsesExplicitSessionWithoutOverwritingDatabase() = runBlocking {
        val store = Store().also { it.failLoad = true }; val s = PreviewState(store, this); s.load()
        assertTrue(s.sessionOnly); assertTrue(s.storage.contains("oturum"))
        s.commit(s.data.copy(favorites = emptyList())); yield(); assertTrue(s.data.favorites.isEmpty()); assertEquals(0, store.writes)
    }
    @Test fun directLiveStartupAndExitConfirmationFlow() = runBlocking {
        val s = PreviewState(Store(), this)
        assertEquals("live", s.page)
        s.load()
        assertEquals("live", s.page)
        assertTrue(s.panel)
        s.back()
        assertFalse(s.panel)
        assertNull(s.dialog)
        assertEquals("live", s.page)
        s.back()
        assertEquals("exit", s.dialog)
        assertEquals("live", s.page)
        s.back()
        assertNull(s.dialog)
        assertEquals("live", s.page)
        assertFalse(s.exitRequested)
    }
    @Test fun sleepTimerPreferenceAndCountdownStopsPlaybackAndRequestsExit() = runBlocking {
        val s = PreviewState(Store(), this)
        s.load()
        s.pref("sleepTimer", "15 dk")
        yield()
        assertEquals("15 dk", s.data.pref("sleepTimer"))
        assertEquals(15 * 60, s.sleepTimerRemainingSeconds)
        s.pref("sleepTimer", "Kapalı")
        yield()
        assertNull(s.sleepTimerRemainingSeconds)
    }
    @Test fun channelChangeRevealsLivePanelWhenOnLivePage() = runBlocking {
        val s = PreviewState(Store(), this)
        s.load()
        s.page = "live"
        s.panel = false
        val nextId = s.data.channels[1].id
        s.select(nextId)
        assertTrue(s.panel)

        s.panel = false
        s.stepChannel(1)
        assertTrue(s.panel)
    }
}
