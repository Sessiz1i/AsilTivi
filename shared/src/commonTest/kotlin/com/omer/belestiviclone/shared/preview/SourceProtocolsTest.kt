package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SourceProtocolsTest {
    private var serial = 0L
    private val ops = SourceOperations { OpaqueId(950, ++serial) }
    private val base = PreviewData(
        channels = emptyList(),
        favorites = emptyList(),
        history = emptyList(),
        categories = emptyList(),
        profileId = PublicM3u.profile,
    )

    @Test
    fun xtreamHandshakeCategoriesAndStreamsConvertToPreviewChannelsAndSurviveCodec() = runBlocking {
        val loader = object : SourceLoader {
            override suspend fun defaultText() = "#EXTM3U\n"
            override suspend fun fetch(address: String): String = when {
                "action=get_live_categories" in address -> """
                    [
                      {"category_id":"10","category_name":"Ulusal"},
                      {"category_id":"20","category_name":"Spor"}
                    ]
                """.trimIndent()
                "action=get_live_streams" in address -> """
                    [
                      {
                        "num":1,
                        "name":"TRT 1 HD",
                        "stream_id":101,
                        "stream_icon":"https://img.test/trt1.png",
                        "epg_channel_id":"TRT1.tr",
                        "category_id":"10"
                      },
                      {
                        "num":2,
                        "name":"TRT Spor HD",
                        "stream_id":202,
                        "stream_icon":"https://img.test/trtspor.png",
                        "epg_channel_id":"TRTSpor.tr",
                        "category_id":"20"
                      }
                    ]
                """.trimIndent()
                "player_api.php" in address -> """
                    {
                      "user_info": {
                        "username": "kambak",
                        "auth": 1,
                        "status": "Active",
                        "exp_date": "1893456000",
                        "allowed_output_formats": ["m3u8", "ts"]
                      }
                    }
                """.trimIndent()
                else -> error("unexpected url: $address")
            }
        }

        val (account, m3u) = XtreamCodesClient.connectAndBuildM3u(loader, "http://xtream.test:8080/", "kambak", "secret123")
        assertEquals("kambak", account.username)
        assertEquals("Active", account.status)
        assertTrue(m3u.startsWith("#EXTM3U"))

        val saved = ops.saveXtream(base, "Xtream Test", "http://xtream.test:8080/", "kambak", m3u)
        assertEquals(SourceKind.XTREAM, saved.library!!.main.kind)
        assertFalse("secret123" in saved.library!!.main.address)

        val channels = saved.filtered("Tümü", "")
        assertEquals(2, channels.size)
        assertEquals("TRT 1 HD", channels[0].name)
        assertEquals("Ulusal", channels[0].category)
        assertEquals("TRT1.tr", channels[0].providerId)
        assertEquals("http://xtream.test:8080/live/kambak/secret123/101.m3u8", playbackUri(channels[0]))

        val reopened = LibraryCodec.decode(LibraryCodec.encode(saved), saved.copy(channels = emptyList(), library = null))
        assertEquals(saved, reopened)
    }

    @Test
    fun xtreamFallbackToGetPhpM3uPlusAndAuthErrorsWork() = runBlocking {
        val loader = object : SourceLoader {
            override suspend fun defaultText() = "#EXTM3U\n"
            override suspend fun fetch(address: String): String = when {
                "username=bad" in address -> """{"user_info":{"auth":0,"status":"Disabled"}}"""
                "username=expired" in address -> """{"user_info":{"auth":1,"status":"Expired"}}"""
                "action=get_live_streams" in address -> "[]"
                "get.php" in address -> "#EXTM3U\n#EXTINF:-1 tvg-id=\"Show.tr\" group-title=\"Ulusal\",Show TV\nhttp://xtream.test:8080/live/demo/pass/55.ts\n"
                else -> """{"user_info":{"username":"demo","auth":"1","status":"Active","allowed_output_formats":["ts"]}}"""
            }
        }

        val badErr = assertFailsWith<SourceProblem> {
            XtreamCodesClient.connectAndBuildM3u(loader, "http://xtream.test:8080", "bad", "pass")
        }
        assertEquals("xtream-expired", badErr.code)

        val expErr = assertFailsWith<SourceProblem> {
            XtreamCodesClient.connectAndBuildM3u(loader, "http://xtream.test:8080", "expired", "pass")
        }
        assertEquals("xtream-expired", expErr.code)

        val (_, m3u) = XtreamCodesClient.connectAndBuildM3u(loader, "http://xtream.test:8080", "demo", "pass")
        val saved = ops.saveXtream(base, "", "http://xtream.test:8080", "demo", m3u)
        assertEquals(1, saved.filtered("Tümü", "").size)
        assertEquals("Show.tr", saved.filtered("Tümü", "").single().providerId)
    }

    @Test
    fun stalkerPortalHandshakeGenresAndChannelsProtocolWork() = runBlocking {
        val capturedHeaders = mutableListOf<Map<String, String>>()
        val loader = object : SourceLoader {
            override suspend fun defaultText() = "#EXTM3U\n"
            override suspend fun fetch(address: String): String = error("should call fetch with headers")
            override suspend fun fetch(address: String, headers: Map<String, String>): String {
                capturedHeaders += headers
                return when {
                    "action=handshake" in address -> """{"js":{"token":"STK_TOKEN_99","random":"42"}}"""
                    "action=get_genres" in address -> """{"js":[{"id":"1","title":"Ulusal"},{"id":"2","title":"Haber"}]}"""
                    "action=get_all_channels" in address -> """
                        {
                          "js": {
                            "total_items": 2,
                            "data": [
                              {
                                "id": "10",
                                "name": "ATV HD",
                                "number": "2",
                                "tv_genre_id": "1",
                                "cmd": "ffrt http://localhost/ch/101.m3u8",
                                "logo": "/logos/atv.png",
                                "xmltv_id": "ATV.tr"
                              },
                              {
                                "id": "20",
                                "name": "NTV HD",
                                "number": "3",
                                "tv_genre_id": "2",
                                "cmd": "ffmpeg http://stream.stalker.test:8080/live/ntv.m3u8",
                                "logo": "https://img.test/ntv.png",
                                "xmltv_id": "NTV.tr"
                              }
                            ]
                          }
                        }
                    """.trimIndent()
                    else -> error("unexpected stalker url: $address")
                }
            }
        }

        assertFailsWith<SourceProblem> { StalkerPortalClient.normalizeMac("invalid-mac") }
        assertEquals("00:1A:79:AB:CD:EF", StalkerPortalClient.normalizeMac("00-1a-79-ab-cd-ef"))

        val (session, m3u) = StalkerPortalClient.connectAndBuildM3u(loader, "http://portal.test:8080/c/", "00:1A:79:AB:CD:EF")
        assertEquals("STK_TOKEN_99", session.token)
        assertEquals("http://portal.test:8080/stalker_portal/server/load.php", session.loadPhpUrl)
        assertTrue(capturedHeaders.first()["Cookie"]!!.contains("mac=00:1A:79:AB:CD:EF"))
        assertEquals("Bearer STK_TOKEN_99", capturedHeaders.last()["Authorization"])

        val saved = ops.saveStalker(base, "MAG Portal", "http://portal.test:8080/c/", "00:1A:79:AB:CD:EF", m3u)
        assertEquals(SourceKind.STALKER, saved.library!!.main.kind)
        val channels = saved.filtered("Tümü", "")
        assertEquals(2, channels.size)
        assertEquals("http://portal.test:8080/ch/101.m3u8", playbackUri(channels[0]))
        assertEquals("http://stream.stalker.test:8080/live/ntv.m3u8", playbackUri(channels[1]))
    }

    @Test
    fun countryListsPresetsMergeRemoveAndStandaloneSourceWork() {
        val start = ops.default(base, CountryListsCatalog.byCode("TR")!!.builtinM3u)
        val initialCount = start.filtered("Tümü", "").size
        assertTrue(initialCount >= 8)

        val withKktcAndAz = ops.applyCountryLists(start, listOf("KKTC", "AZ"))
        assertTrue(withKktcAndAz.filtered("KKTC", "").isNotEmpty())
        assertTrue(withKktcAndAz.filtered("AZERBAYCAN", "").isNotEmpty())
        assertTrue(withKktcAndAz.filtered("Tümü", "").size > initialCount)

        val removedKktc = ops.applyCountryLists(withKktcAndAz, listOf("AZ"))
        assertTrue(removedKktc.filtered("KKTC", "").isEmpty())
        assertTrue(removedKktc.filtered("AZERBAYCAN", "").isNotEmpty())

        val kktcOnly = ops.saveCountry(base, CountryListsCatalog.byCode("KKTC")!!)
        assertEquals(SourceKind.COUNTRY, kktcOnly.library!!.main.kind)
        assertTrue(kktcOnly.filtered("KKTC", "").isNotEmpty())
    }
}
