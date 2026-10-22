package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.EpgLoader
import com.omer.belestiviclone.shared.preview.PreviewData
import com.omer.belestiviclone.shared.preview.PreviewStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpgStateTest {

    private class MemoryPreviewStore(var value: PreviewData = PreviewData()) : PreviewStore {
        override suspend fun load(): PreviewData = value
        override suspend fun save(data: PreviewData) { value = data }
    }

    @Test
    fun defaultSampleEpgProvidesNowAndNextProgrammesForNationalChannels(): Unit = runBlocking {
        val store = MemoryPreviewStore()
        val state = PreviewState(store, this)
        state.load()

        val trt1 = state.data.channels.first { it.name.contains("TRT 1") }
        val nowNext = state.epgNowNext(trt1)
        assertTrue(nowNext.now != null)
        assertTrue(nowNext.now!!.title.isNotBlank())
        assertTrue(nowNext.next != null)
    }

    @Test
    fun epgSourceFormValidatesAndSavesCustomXmltvUrl(): Unit = runBlocking {
        val customXml = """
            <tv>
              <channel id="trt1.tr"><display-name>TRT 1</display-name></channel>
              <programme start="20200101000000 +0300" stop="20990101000000 +0300" channel="trt1.tr">
                <title>Özel XMLTV Yayını</title>
                <desc>Canlı Rehber Testi</desc>
              </programme>
            </tv>
        """.trimIndent()
        val loader = object : EpgLoader {
            override suspend fun loadSampleXml(): String = customXml
            override suspend fun fetchXml(url: String): String = customXml
        }
        val store = MemoryPreviewStore()
        val state = PreviewState(store, this, epgLoader = loader)
        state.load()

        state.sourceForm("epg")
        assertEquals("epg", state.dialog)

        state.formAddress = "gecersiz-adres"
        state.saveEpgUrl()
        assertEquals("Geçerli bir HTTP/HTTPS adresi girin.", state.notice)

        state.formAddress = "https://ornek.com/epg.xml"
        state.saveEpgUrl()
        yield()
        val trt1 = state.data.channels.first { it.name.contains("TRT 1") }
        val nowNext = state.epgNowNext(trt1)
        assertEquals("Özel XMLTV Yayını", nowNext.now?.title)
        assertEquals("https://ornek.com/epg.xml", state.data.pref("epgUrl"))
        assertNull(state.dialog)
    }
}
