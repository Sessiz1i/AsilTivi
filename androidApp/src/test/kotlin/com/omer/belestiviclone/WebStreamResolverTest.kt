package com.omer.belestiviclone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebStreamResolverTest {
    @Test
    fun detectsWebPageUrlsVersusDirectStreams() {
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.cnnturk.com/canli-yayin"))
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.atv.com.tr/canli-yayin"))
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.kanald.com.tr/canli-yayin"))
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.showtv.com.tr/canli-yayin"))
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.startv.com.tr/canli-yayin"))
        assertTrue(WebStreamResolver.isWebPageUrl("https://www.cnbce.com/canli-yayin"))

        assertFalse(WebStreamResolver.isWebPageUrl("https://trt.daioncdn.net/trt-1/master.m3u8?app=clean"))
        assertFalse(WebStreamResolver.isWebPageUrl("https://dogus.daioncdn.net/ntv/ntv.m3u8?app=ntv_web"))
        assertFalse(WebStreamResolver.isWebPageUrl("https://sunar.impresents.workers.dev/xezertv"))
    }

    @Test
    fun extractsCnnTurkContentIdAndParsesDemirorenMediaJson() {
        val sampleHtml = """
            <html>
            <script>
                const contentId = "62d6814670380e2cdc7c124c";
            </script>
            </html>
        """.trimIndent()
        assertEquals("62d6814670380e2cdc7c124c", WebStreamResolver.extractCnnTurkContentId(sampleHtml))

        val attrHtml = """<div data-id="5f8b1234567890abcdef1234" class="player"></div>"""
        assertEquals("5f8b1234567890abcdef1234", WebStreamResolver.extractCnnTurkContentId(attrHtml))
        assertEquals(WebStreamResolver.DEFAULT_CNN_TURK_CONTENT_ID, WebStreamResolver.extractCnnTurkContentId("<html></html>"))

        val mediaJson = """
            {
              "Media": {
                "Link": {
                  "DefaultServiceUrl": "https:\/\/live.duhnet.tv",
                  "SecurePath": "\/S2\/HLS_LIVE\/cnnturknp\/playlist.m3u8?\u0026live=true\u0026app=com.cnnturk\u0026st=abc\u0026e=123"
                }
              }
            }
        """.trimIndent()
        assertEquals(
            "https://live.duhnet.tv/S2/HLS_LIVE/cnnturknp/playlist.m3u8?&live=true&app=com.cnnturk&st=abc&e=123",
            WebStreamResolver.parseDemirorenMediaJson(mediaJson)
        )
    }

    @Test
    fun parsesTurkuvazDygCnbceAndGenericHtmlStreams() {
        val turkuvazJson = """{"Success":true,"Url":"https:\/\/trkvz.daioncdn.net\/atv\/atv.m3u8?ce=3\u0026app=d1ce\u0026st=tok"}"""
        assertEquals(
            "https://trkvz.daioncdn.net/atv/atv.m3u8?ce=3&app=d1ce&st=tok",
            WebStreamResolver.parseTurkuvazSecureJson(turkuvazJson, "https://fallback.example/atv.m3u8")
        )

        val dygJs = """var cfg={Startv:{development:"https://dev.m3u8",production:"https://dogus-live.daioncdn.net/startv/startv.m3u8"},Ntv:{development:"https://dev2.m3u8",production:"https://dogus-live.daioncdn.net/ntv/ntv.m3u8"}};"""
        assertEquals(
            "https://dogus-live.daioncdn.net/startv/startv.m3u8",
            WebStreamResolver.parseDygPlayerBuildJs(dygJs, "Startv")
        )
        assertEquals(
            "https://dogus-live.daioncdn.net/ntv/ntv.m3u8",
            WebStreamResolver.parseDygPlayerBuildJs(dygJs, "Ntv")
        )

        val cnbceJson = """{"source":"https:\/\/hnpsechtsc.turknet.ercdn.net\/xpnvudnlsv\/cnbc-e\/cnbc-e.m3u8"}"""
        assertEquals(
            "https://hnpsechtsc.turknet.ercdn.net/xpnvudnlsv/cnbc-e/cnbc-e.m3u8",
            WebStreamResolver.parseCnbceSourceJson(cnbceJson)
        )

        val kanaldHtml = """<script>var stream = "https:\/\/demiroren.daioncdn.net\/kanald\/kanald.m3u8?app=kanald_web\u0026ce=3";</script>"""
        assertEquals(
            "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web&ce=3",
            WebStreamResolver.extractFirstM3u8FromHtml(kanaldHtml)
        )
    }
}
