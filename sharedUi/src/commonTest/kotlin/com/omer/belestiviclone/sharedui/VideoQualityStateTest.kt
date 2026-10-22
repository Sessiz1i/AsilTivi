package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class VideoQualityStateTest {
    private class Store : PreviewStore {
        var value=PreviewData();var fail=false
        override suspend fun load()=value
        override suspend fun save(data:PreviewData) { check(!fail);value=data }
    }
    @Test fun livePreferenceChangesOnlyQualityAndSettingsReturnKeepsChannelAttemptTrigger()=runBlocking {
        val store=Store();val state=PreviewState(store,this);state.load();state.page="live"
        val before=state.data;val selected=state.selected;val retry=state.retry
        state.pref("quality","360p");yield()
        assertEquals(before.withPref("quality","360p"),state.data)
        assertEquals(selected,state.selected);assertEquals(retry,state.retry);assertEquals("live",state.page)
        state.settings();state.pref("quality","1080p FHD");yield();state.back()
        assertEquals("live",state.page);assertEquals(selected,state.selected);assertEquals(retry,state.retry)
        val reopened=PreviewState(store,this);reopened.load()
        assertEquals("1080p FHD",reopened.data.pref("quality"))
        assertEquals(before.channels,reopened.data.channels)
    }
    @Test fun failedWriteAndUnknownPreferenceDoNotNormalizeOrLeakToAnotherProfile()=runBlocking {
        val a=Store();val b=Store();val state=PreviewState(a,this);state.load()
        a.fail=true;state.pref("quality","360p");yield()
        assertEquals("720p HD",state.data.pref("quality"))
        a.fail=false;state.pref("quality","legacy-unknown");yield()
        val reopened=PreviewState(a,this);reopened.load()
        assertEquals("legacy-unknown",reopened.data.pref("quality"));assertNull(PlaybackPreferences(reopened.data.pref("quality")).cap)
        assertEquals("720p HD",b.value.pref("quality"))
    }
}
