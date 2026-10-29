package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlinx.coroutines.*
import kotlin.test.*

class SourceTransactionTest {
    private val content = "#EXTM3U\n#EXTINF:-1 group-title=\"Test\",Test\nhttps://media.test/a.m3u8\n"
    private class Store : PreviewStore {
        var value = PreviewData(channels = emptyList(), categories = emptyList(), favorites = emptyList(), history = emptyList())
        var fail = false
        var writes = 0
        var entering: CompletableDeferred<Unit>? = null
        var finish: CompletableDeferred<Unit>? = null
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) {
            entering?.complete(Unit); finish?.await()
            if (fail) error("disk full")
            value = data; writes++
        }
    }
    private suspend fun settle(state: PreviewState) { withTimeout(5000) { while (state.busy) delay(1) }; yield() }
    private fun form(state: PreviewState, name: String, url: String = "https://list.test/a") { state.sourceForm("m3u"); state.formName = name; state.formAddress = url; state.saveSource() }
    @Test fun canceledUncooperativeRequestCannotOverwriteNewerSelectionOrPersistHalfRecord() = runBlocking {
        val started = CompletableDeferred<Unit>(); val late = CompletableDeferred<Unit>(); val oldReturned = CompletableDeferred<Unit>()
        val loader = object : SourceLoader {
            override suspend fun defaultText() = content
            override suspend fun fetch(address: String): String {
                if (address.endsWith("old")) withContext(NonCancellable) { started.complete(Unit); late.await(); oldReturned.complete(Unit) }
                return content
            }
        }
        val store = Store(); val state = PreviewState(store, this, sourceLoader = loader); state.load()
        form(state, "Old", "https://list.test/old"); started.await(); state.cancelSource()
        form(state, "New", "https://list.test/new"); settle(state)
        val beforeLate = state.data; late.complete(Unit); oldReturned.await(); delay(20)
        assertEquals("New", state.profileName); assertEquals(beforeLate, state.data); assertEquals(beforeLate, store.value); assertEquals(1, store.writes)
    }
    @Test fun atomicWriteFailureKeepsPreviousCatalogFavoriteAndRecordThenRetrySucceeds() = runBlocking {
        val store = Store(); val loader = object : SourceLoader { override suspend fun fetch(address: String) = content; override suspend fun defaultText() = content }
        val state = PreviewState(store, this, sourceLoader = loader); state.load(); form(state, "One"); settle(state)
        state.commit(state.data.toggleFavorite(state.data.channels.first().id)); settle(state)
        val before = state.data; store.fail = true; form(state, "Two", "https://list.test/b"); settle(state)
        assertEquals(before, state.data); assertEquals(before, store.value); assertEquals("Two", state.formName)
        store.fail = false; state.saveSource(); settle(state); assertEquals(2, state.savedSources.size)
    }
    @Test fun commitPointRejectsCancellationAndFinishesStoreAndPublicationTogether() = runBlocking {
        val store = Store(); val loader = object : SourceLoader { override suspend fun fetch(address: String) = content; override suspend fun defaultText() = content }
        val state = PreviewState(store, this, sourceLoader = loader); state.load()
        store.entering = CompletableDeferred(); store.finish = CompletableDeferred()
        form(state, "Saved"); store.entering!!.await(); assertTrue(state.committingSource)
        state.cancelSource(); state.back(); assertTrue(state.busy)
        store.finish!!.complete(Unit); settle(state)
        assertEquals(store.value, state.data); assertEquals("Saved", state.profileName); assertEquals(1, store.writes)
    }
    @Test fun editBackPreservesOriginalRecordAndRenameReopenRetainsIdentity() = runBlocking {
        val store = Store(); val loader = object : SourceLoader { override suspend fun fetch(address: String) = content; override suspend fun defaultText() = content }
        val state = PreviewState(store, this, sourceLoader = loader); state.load(); form(state, "Original"); settle(state)
        val before = state.data
        state.sourceForm("m3u", 0); assertEquals("https://list.test/a", state.formAddress); state.formName = "Unsaved"; state.back()
        assertEquals(before, state.data); assertEquals(before, store.value)
        state.sourceForm("m3u", 0); state.formName = "Renamed"; state.saveSource(); settle(state)
        val reopened = PreviewState(store, this, sourceLoader = loader); reopened.load()
        assertEquals("Renamed", reopened.savedSources.single().name); assertEquals(before.library!!.active, reopened.savedSources.single().id)
    }
    @Test fun historyOffDoesNotRecordPlayedEventsAndStartupUsesSeparateLastChannel() = runBlocking {
        val store = Store(); store.value = PreviewData().withPref("history", "Kapalı")
        val state = PreviewState(store, this); state.load(); val before = state.data.history; val id = PreviewFixture.channels.last().id
        state.select(id); state.played(id); settle(state)
        assertEquals(before, state.data.history); assertEquals(PreviewCodec.id(id), state.data.preferences["lastChannel"])
        state.pref("history", "Açık"); settle(state); assertEquals(before, state.data.history)
        state.played(id); settle(state); assertEquals(id, state.data.history.first())
        val reopened = PreviewState(store, this); reopened.load()
        assertEquals(id, reopened.selected)
        // This fixture card has no playback definition, so it is not presented as live playback.
        assertEquals(id, reopened.data.startup(id))
    }
    @Test fun multiSettingsReturnPreservesAssignmentsAndBackUsesSelectedOrEntry() = runBlocking {
        val state = PreviewState(Store(), this); state.load(); state.page = "live"; val entry = state.selected; state.startMulti(2)
        val session = state.multi!!; session.slots[1] = OpaqueId(44, 2); session.selected = 1
        state.settings(); state.back(); assertEquals("multi", state.page); assertSame(session, state.multi)
        state.back(); settle(state); assertNull(state.multi); assertEquals(entry, state.selected); assertEquals("Orijinal", state.data.pref("format"))
    }
}
