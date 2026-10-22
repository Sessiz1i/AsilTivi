package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import kotlin.test.*

class PublicM3uTest {
    private fun catalog(text: String): List<PreviewChannel> {
        val result = PublicM3u.parse(text)
        return PublicM3u.catalog(result, IdentitySupply(List(20) { ChannelId(OpaqueId(1, it.toLong())) }, List(20) { MembershipId(OpaqueId(2, it.toLong())) }, List(20) { CategoryId(OpaqueId(3, it.toLong())) }))
    }
    @Test fun bomCrLfTurkishAndQuotedCommaSurvive() {
        val c = catalog("\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"ç.id\" tvg-logo=\"https://x.test/a,b.png\" group-title=\"Çocuk\",TRT Çocuk\r\nhttps://x.test/a?sig=A%2Fb&n=1\r\n").single()
        assertEquals("TRT Çocuk", c.name); assertEquals("Çocuk", c.category); assertEquals("ç.id", c.providerId)
        assertEquals("https://x.test/a,b.png", c.logo); assertEquals("https://x.test/a?sig=A%2Fb&n=1", playbackUri(c))
    }
    @Test fun signedQueriesAreDistinctAndNamesNeverIdentify() {
        val c = catalog("#EXTINF:-1,Same\nhttps://x.test/a?sig=1\n#EXTINF:-1,Same\nhttps://x.test/a?sig=2")
        assertEquals(2, c.size); assertNotEquals(c[0].id, c[1].id)
    }
    @Test fun exactRepeatMergesCatalogButRetainsCategoryContributions() {
        val c = catalog("#EXTINF:-1 group-title=\"A\",One\nhttps://x.test/a\n#EXTINF:-1 group-title=\"B\",Two\nhttps://x.test/a\n#EXTINF:-1 group-title=\"A\",One\nhttps://x.test/a").single()
        assertEquals(listOf("A", "B"), c.groups)
        assertEquals(1, PreviewData(channels = listOf(c), favorites = emptyList()).filtered("B", "").size)
    }
    @Test fun missingFieldsUseUncategorizedAndTvgNameWithoutGuessingIdentity() {
        val c = catalog("#EXTINF:-1 tvg-name=\"İsim\",\nhttps://x.test/a").single()
        assertEquals("İsim", c.name); assertEquals("Kategorisiz", c.category); assertEquals("", c.logo)
    }
    @Test fun badAndMissingUrisHaveSeparateCounts() {
        val p = PublicM3u.parse("#EXTINF:-1,A\n#EXTINF:-1,B\nftp://x/a\n#EXTINF:-1,C\nhttps://x.test/a%xx\n#EXTINF:-1,D\nhttps://x.test/good")
        assertEquals(4, p.rawRecords); assertEquals(1, p.rows.size)
        assertEquals(mapOf("missing-uri" to 1, "unsupported-or-relative-uri" to 1, "invalid-http-uri" to 1), p.skipped)
    }
    @Test fun relativeUriNeedsExplicitPlatformResolver() {
        assertTrue(PublicM3u.parse("#EXTINF:-1,A\n../a.m3u8").rows.isEmpty())
        assertEquals("https://x.test/a.m3u8", PublicM3u.parse("#EXTINF:-1,A\n../a.m3u8") { "https://x.test/a.m3u8" }.rows.single().definition.address.raw)
    }
    @Test fun supportedHeadersParticipateInIdentityAndPlayback() {
        val c = catalog("#EXTINF:-1,A\n#EXTVLCOPT:http-user-agent=Agent A\nhttps://x.test/a\n#EXTINF:-1,A\n#EXTVLCOPT:http-user-agent=Agent B\nhttps://x.test/a")
        assertEquals(2, c.size); assertEquals("Agent A", c.first().definition!!.headers.single().value)
    }
    @Test fun unsupportedDrmIsRetainedButCannotPlayOrSubstituteSample() {
        val c = catalog("#EXTINF:-1,A\n#KODIPROP:inputstream.adaptive.license_key=secret\nhttps://x.test/a").single()
        assertNull(playbackUri(c)); assertEquals(1, c.definition!!.unknownRequiredDirectives.size)
    }
    @Test fun publicChannelsNeverResolveThroughDemoNamesOrVideoFlag() {
        val c = catalog("#EXTINF:-1,Örnek video\nhttps://x.test/actual.mp4").single().copy(video = true)
        assertEquals("https://x.test/actual.mp4", playbackUri(c)); assertNull(sampleVideoUri(c))
        assertFalse(c.id in PreviewData().favorites)
    }
    @Test fun codecKeepsOpaqueIdsAndExactUnicodeSeparators() {
        val values = listOf("https://x.test/?a=1&sig=%2F", "Çocuk:1,2", PreviewCodec.id(OpaqueId(Long.MIN_VALUE, Long.MAX_VALUE)))
        assertEquals(values, PreviewCodec.unpack(PreviewCodec.pack(values)))
        assertEquals(OpaqueId(Long.MIN_VALUE, Long.MAX_VALUE), PreviewCodec.id(values.last()))
    }
}
