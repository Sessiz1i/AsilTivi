package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpgModelTest {

    @Test
    fun parsesXmltvAndIso8601TimestampsInTurkeyTimezone() {
        val epochXmltv = EpgDateTime.parseEpochMs("20261023200000 +0300")
        val epochIso = EpgDateTime.parseEpochMs("2026-10-23T20:00:00+03:00")
        val epochUtc = EpgDateTime.parseEpochMs("2026-10-23T17:00:00Z")

        assertNotNull(epochXmltv)
        assertEquals(epochXmltv, epochIso)
        assertEquals(epochXmltv, epochUtc)
        assertEquals("20:00", EpgDateTime.formatHhMm(epochXmltv))
    }

    @Test
    fun streamParserExtractsChannelsAndProgrammesWithXmlEntities() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="Test">
              <channel id="trt1.tr">
                <display-name>TRT 1</display-name>
                <display-name>TRT 1 HD</display-name>
              </channel>
              <programme start="20261023200000 +0300" stop="20261023223000 +0300" channel="trt1.tr">
                <title lang="tr">Gönül Dağı &amp; Özel</title>
                <desc lang="tr">Anadolu öyküsü &quot;Yeni Bölüm&quot;</desc>
              </programme>
              <programme start="20261023223000 +0300" stop="20261024001500 +0300" channel="trt1.tr">
                <title lang="tr">Gece Haberleri</title>
                <desc lang="tr">Günün özeti</desc>
              </programme>
            </tv>
        """.trimIndent()

        val result = XmltvStreamParser.parseLines(sampleXml.lineSequence())
        assertEquals(1, result.channels.size)
        assertEquals("trt1.tr", result.channels.first().id)
        assertEquals(listOf("TRT 1", "TRT 1 HD"), result.channels.first().displayNames)

        assertEquals(2, result.programmes.size)
        val first = result.programmes[0]
        assertEquals("trt1.tr", first.channelId)
        assertEquals("Gönül Dağı & Özel", first.title)
        assertEquals("Anadolu öyküsü \"Yeni Bölüm\"", first.desc)
        assertEquals("20:00 - 22:30", first.timeRangeLabel())
    }

    @Test
    fun channelMatchingPrioritizesTvgIdThenTvgNameThenNormalizedTurkishName() {
        val xml = """
            <tv>
              <channel id="id.exact.tr"><display-name>Alakasız İsim</display-name></channel>
              <channel id="id.tvgname.tr"><display-name>Özel TVG Adı</display-name></channel>
              <channel id="id.normalized.tr"><display-name>ÇIĞLIK ŞÖLENİ</display-name></channel>
              <programme start="20261023120000 +0300" stop="20261023140000 +0300" channel="id.exact.tr">
                <title>TVG ID Yayını</title>
              </programme>
              <programme start="20261023120000 +0300" stop="20261023140000 +0300" channel="id.tvgname.tr">
                <title>TVG Name Yayını</title>
              </programme>
              <programme start="20261023120000 +0300" stop="20261023140000 +0300" channel="id.normalized.tr">
                <title>Normalize İsim Yayını</title>
              </programme>
            </tv>
        """.trimIndent()

        val cache = EpgCacheManager()
        val nowMs = EpgDateTime.parseEpochMs("20261023130000 +0300")!!
        cache.loadXml(xml, nowEpochMs = nowMs, rebaseSampleIfOutdated = false)

        val chByTvgId = PreviewChannel(
            id = OpaqueId(1L, 1L),
            name = "ÇIĞLIK ŞÖLENİ",
            category = "Ulusal",
            logo = "ikon_tv",
            providerId = "id.exact.tr",
            tvgName = "Özel TVG Adı"
        )
        assertEquals("TVG ID Yayını", cache.nowNext(chByTvgId, nowMs).now?.title)

        val chByTvgName = PreviewChannel(
            id = OpaqueId(2L, 2L),
            name = "ÇIĞLIK ŞÖLENİ",
            category = "Ulusal",
            logo = "ikon_tv",
            providerId = "olmayan.id",
            tvgName = "Özel TVG Adı"
        )
        assertEquals("TVG Name Yayını", cache.nowNext(chByTvgName, nowMs).now?.title)

        val chByNormalizedName = PreviewChannel(
            id = OpaqueId(3L, 3L),
            name = "ciglik soleni hd tr",
            category = "Ulusal",
            logo = "ikon_tv",
            providerId = "",
            tvgName = ""
        )
        assertEquals("Normalize İsim Yayını", cache.nowNext(chByNormalizedName, nowMs).now?.title)

        val unmatched = PreviewChannel(
            id = OpaqueId(4L, 4L),
            name = "Bilinmeyen Uzay Kanalı",
            category = "Diğer",
            logo = "ikon_tv"
        )
        val unmatchedResult = cache.nowNext(unmatched, nowMs)
        assertNull(unmatchedResult.now)
        assertNull(unmatchedResult.next)
    }

    @Test
    fun cacheEvictsExpiredProgrammesAndComputesLiveProgressAccurately() {
        val xml = """
            <tv>
              <channel id="kanald.tr"><display-name>Kanal D</display-name></channel>
              <programme start="20261023180000 +0300" stop="20261023190000 +0300" channel="kanald.tr">
                <title>Eski Program</title>
              </programme>
              <programme start="20261023190000 +0300" stop="20261023200000 +0300" channel="kanald.tr">
                <title>Ana Haber</title>
              </programme>
              <programme start="20261023200000 +0300" stop="20261023220000 +0300" channel="kanald.tr">
                <title>Uzak Şehir</title>
              </programme>
            </tv>
        """.trimIndent()

        val cache = EpgCacheManager()
        val at1930 = EpgDateTime.parseEpochMs("20261023193000 +0300")!!
        cache.loadXml(xml, nowEpochMs = at1930, rebaseSampleIfOutdated = false)

        val ch = PreviewChannel(
            id = OpaqueId(9L, 9L),
            name = "Kanal D HD",
            category = "Ulusal",
            logo = "kanal_kanald",
            providerId = "KanalD.tr"
        )

        val nowNext = cache.nowNext(ch, at1930)
        assertNotNull(nowNext.now)
        assertEquals("Ana Haber", nowNext.now?.title)
        assertEquals(50, nowNext.now?.progressPercentAt(at1930))
        assertEquals("Uzak Şehir", nowNext.next?.title)

        val at2030 = EpgDateTime.parseEpochMs("20261023203000 +0300")!!
        cache.evictExpired(at2030)
        val afterEvict = cache.nowNext(ch, at2030)
        assertEquals("Uzak Şehir", afterEvict.now?.title)
        assertEquals(25, afterEvict.now?.progressPercentAt(at2030))
        assertTrue(cache.programmeCount == 1)
    }

    @Test
    fun extractsXTvgUrlAndUrlTvgFromM3uHeader() {
        val m3u1 = "#EXTM3U x-tvg-url=\"https://ornek.com/epg.xml\"\n#EXTINF:-1,TRT 1\nhttps://yayin/1.m3u8"
        val m3u2 = "#EXTM3U url-tvg=\"https://ornek.com/rehber.xml.gz\"\n#EXTINF:-1,Show TV\nhttps://yayin/2.m3u8"
        assertEquals("https://ornek.com/epg.xml", PublicM3u.extractEpgUrl(m3u1))
        assertEquals("https://ornek.com/rehber.xml.gz", PublicM3u.extractEpgUrl(m3u2))
    }
}
