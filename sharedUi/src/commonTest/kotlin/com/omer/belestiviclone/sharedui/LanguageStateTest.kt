package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class LanguageStateTest {
    private class Store: PreviewStore {
        var data=PreviewData();var fail=false
        override suspend fun load()=data
        override suspend fun save(data:PreviewData) { check(!fail);this.data=data }
    }
    @Test fun languageWritesDoNotRetrySelectNormalizeOrChangeQualityAndSurviveOwnReopen()=runBlocking {
        val store=Store();val state=PreviewState(store,this);state.load();state.page="live"
        val before=state.data;val id=state.selected;val retry=state.retry
        state.pref("audio","Deutsch");yield();state.pref("subtitle","English");yield()
        assertEquals(before.withPref("audio","Deutsch").withPref("subtitle","English"),state.data)
        assertEquals(id,state.selected);assertEquals(retry,state.retry);assertEquals("720p HD",state.data.pref("quality"))
        state.settings();state.pref("subtitle","Otomatik (İlk Bulunan)");yield();state.back()
        assertEquals("live",state.page);assertEquals(id,state.selected);assertEquals(retry,state.retry)
        val reopened=PreviewState(store,this);reopened.load();assertEquals(state.data,reopened.data)
    }
    @Test fun failedWriteKeepsOldLanguageAndUnknownIsPreservedInOnlyItsProfile()=runBlocking {
        val a=Store();val b=Store();val state=PreviewState(a,this);state.load()
        a.fail=true;state.pref("subtitle","English");yield();assertEquals("Kapalı",state.data.pref("subtitle"))
        assertTrue(state.notice.contains("Kaydedilemedi"))
        a.fail=false;state.pref("audio","legacy-audio");yield();state.pref("subtitle","legacy-text");yield()
        val reopened=PreviewState(a,this);reopened.load()
        assertEquals("legacy-audio",reopened.data.pref("audio"));assertEquals("legacy-text",reopened.data.pref("subtitle"))
        assertEquals("Türkçe",b.data.pref("audio"));assertEquals("Kapalı",b.data.pref("subtitle"))
        assertEquals(LanguageMode.UNKNOWN,TrackLanguage.subtitle(reopened.data.pref("subtitle")).mode)
    }
}
