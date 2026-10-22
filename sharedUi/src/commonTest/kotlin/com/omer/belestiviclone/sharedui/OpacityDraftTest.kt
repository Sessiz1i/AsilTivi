package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class OpacityDraftTest {
    private class Store : PreviewStore {
        var value = PreviewData(); var writes = 0; var fail = false
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) { if (fail) error("write failure"); value = data; writes++ }
    }
    @Test fun continuousDraftClampsAndBackCancelsWithoutLeavingAppearance() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.settings(); s.section = 1
        val before = s.data
        s.updateOpacity(-30); assertEquals(0, s.opacityValue)
        s.updateOpacity(140); assertEquals(100, s.opacityValue)
        s.updateOpacity(43); assertEquals(43, s.opacityValue); assertEquals(before, s.data)
        s.back(); assertEquals(72, s.opacityValue); assertEquals("settings", s.page); assertEquals(1, s.section)
        assertEquals(0, store.writes); assertEquals(before, store.value)
    }
    @Test fun releaseCommitsOnceAndUntouchedValueDoesNotWrite() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val before = s.data
        s.updateOpacity(72); s.commitOpacity(); yield(); assertEquals(0, store.writes)
        s.updateOpacity(0); s.updateOpacity(100); s.commitOpacity(); yield()
        assertEquals(1, store.writes); assertEquals(before.withPref("opacity", "100"), s.data)
        assertNull(s.opacityDraft); s.commitOpacity(); yield(); assertEquals(1, store.writes)
    }
    @Test fun failedWritePreservesStoredDataAndDraftCanBeCancelled() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val before = s.data; store.fail = true
        s.updateOpacity(17); s.commitOpacity(); yield()
        assertEquals(before, s.data); assertEquals(before, store.value); assertEquals(0, store.writes)
        assertEquals(17, s.opacityValue); s.cancelOpacity(); assertEquals(72, s.opacityValue)
    }
    @Test fun publicSliderNeverWritesDemoPreferencesOrFavorites() = runBlocking {
        val demo = Store(); val public = Store().also { it.value = it.value.copy(profileId = PublicM3u.profile) }
        val s = PreviewState(demo, this, public); s.load(); val before = demo.value
        s.updateOpacity(39); s.commitOpacity(); yield()
        assertEquals(before, demo.value); assertEquals(0, demo.writes); assertEquals("39", public.value.pref("opacity"))
        s.switchProfile(false); assertEquals(72, s.opacityValue)
    }
}
