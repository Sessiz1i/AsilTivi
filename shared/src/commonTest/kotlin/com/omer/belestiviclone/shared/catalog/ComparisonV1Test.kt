package com.omer.belestiviclone.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All addresses, credentials, scopes and key references in this file are synthetic. */
class ComparisonV1Test {
    private fun address(text: String) = Address.fromForm(text)
    private fun key(text: String) = ComparisonV1.addressKey(address(text))
    private fun definition(text: String = "https://media.invalid/A") = PlaybackDefinition(address(text))
    private fun same(left: PlaybackDefinition, right: PlaybackDefinition) =
        ComparisonV1.playbackKey(left) != null && ComparisonV1.playbackKey(left) == ComparisonV1.playbackKey(right)

    @Test fun formBoundaryAndAsciiDnsCaseOnly() {
        val input = address(" \thTTpS://EXAMPLE.INVALID/A?x=1#F\n ")
        assertEquals("hTTpS://EXAMPLE.INVALID/A?x=1#F", input.raw)
        assertEquals(key("https://example.invalid/A?x=1#F"), ComparisonV1.addressKey(input))
        assertFalse(key("https://example.invalid/a?x=1#F") == ComparisonV1.addressKey(input))
        assertFalse(key("http://example.invalid/A?x=1#F") == ComparisonV1.addressKey(input))
    }

    @Test fun queryOrderRepetitionTokensAndEmptyValuesRemainDistinct() {
        val pairs = listOf(
            "?x=1&y=2" to "?y=2&x=1", "?x=1&x=2" to "?x=2&x=1",
            "?x=1&x=1" to "?x=1", "?token=fixture-a" to "?token=fixture-b",
            "?x" to "?x=", "" to "?", "?X=1" to "?x=1",
        )
        pairs.forEachIndexed { index, (left, right) ->
            assertFalse(key("https://media.invalid/a$left") == key("https://media.invalid/a$right"), "query case $index")
        }
    }

    @Test fun pathFragmentPortPercentAndDotSegmentsRemainDistinct() {
        val pairs = listOf(
            "https://media.invalid" to "https://media.invalid/",
            "https://media.invalid/a#A" to "https://media.invalid/a#B",
            "https://media.invalid/a" to "https://media.invalid:443/a",
            "https://media.invalid:0443/a" to "https://media.invalid:443/a",
            "https://media.invalid/a%2Fb" to "https://media.invalid/a/b",
            "https://media.invalid/a%2fb" to "https://media.invalid/a%2Fb",
            "https://media.invalid/a/../b" to "https://media.invalid/b",
            "https://media.invalid./a" to "https://media.invalid/a",
        )
        pairs.forEachIndexed { index, (left, right) ->
            assertNotNull(key(left), "address case $index")
            assertFalse(key(left) == key(right), "address case $index")
        }
    }

    @Test fun exoticAuthoritiesRemainExactWithoutCanonicalization() {
        listOf("https://örnek.invalid/a", "https://[2001:db8::1]/a", "https://host_name.invalid/a").forEach {
            assertNotNull(key(it))
            assertEquals(key(it), key(it))
            assertFalse(key(it) == key(it.replace("https", "HTTPS")))
        }
        assertFalse(key("https://[2001:0db8::1]/a") == key("https://[2001:db8::1]/a"))
        assertFalse(key("https://örnek.invalid/a") == key("https://xn--rnek-4qa.invalid/a"))
    }

    @Test fun invalidAndUnsupportedAddressesCannotMatch() {
        listOf("", "media.invalid/a", "ftp://media.invalid/a", "https:///a", "https://media.invalid/a b",
            "https://media.invalid/%xx", "https://media.invalid/%2", "https://media.invalid/a\\b").forEachIndexed { index, input ->
            assertNull(key(input), "invalid case $index")
        }
    }

    @Test fun userInfoIsPreservedAlongsideHostCase() {
        assertEquals(key("HTTPS://Fixture:Pass@MEDIA.INVALID/a"), key("https://Fixture:Pass@media.invalid/a"))
        assertFalse(key("https://Fixture:Pass@media.invalid/a") == key("https://fixture:Pass@media.invalid/a"))
    }

    @Test fun sourceAddressKeyIsSeparateFromPlaybackKey() {
        val a = address("https://media.invalid/a")
        val source = ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, a)
        assertNotNull(source)
        assertEquals(source, ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, address("HTTPS://MEDIA.INVALID/a")))
        assertFalse(same(PlaybackDefinition(a), PlaybackDefinition(a, mimeType = "fixture/type")))
        assertFalse(source == ComparisonV1.sourceKey(SourceAddressKind.M3U_URL, address("https://media.invalid/a?x=1")))
    }

    @Test fun headerNamesAndIndependentNameOrderAreInsensitive() {
        val base = definition().copy(headers = listOf(Header("Authorization", "fixture"), Header("X-Test", "v")))
        assertTrue(same(base, base.copy(headers = listOf(Header("x-test", "v"), Header("AUTHORIZATION", "fixture")))))
    }

    @Test fun headerValuesRepetitionsAndMissingVersusEmptyAreExact() {
        val base = definition()
        val headers = base.copy(headers = listOf(Header("X-Test", "a"), Header("x-test", "b")))
        assertFalse(same(headers, headers.copy(headers = headers.headers.reversed())))
        assertFalse(same(base, base.copy(headers = listOf(Header("X-Test", "")))))
        assertFalse(same(base.copy(headers = listOf(Header("Authorization", "fixture-a"))),
            base.copy(headers = listOf(Header("authorization", "fixture-b")))))
        assertFalse(same(base.copy(headers = listOf(Header("X-Test", "v"))),
            base.copy(headers = listOf(Header("X-Test", " v")))))
        assertFalse(same(headers, headers.copy(headers = headers.headers + headers.headers.last())))
    }

    @Test fun requiredPlaybackFieldsParticipateInEquality() {
        val base = definition()
        val variants = listOf(
            base.copy(mimeType = ""), base.copy(mimeType = "fixture/type"), base.copy(protocol = ""),
            base.copy(protocol = "fixture-hls"), base.copy(authorityScope = ""), base.copy(authorityScope = "fixture-scope"),
            base.copy(drm = DrmDefinition("fixture-drm")),
            base.copy(options = listOf(NamedPlaybackOption("mode", "fixture"))),
        )
        variants.forEachIndexed { index, variant -> assertFalse(same(base, variant), "field case $index") }
        assertFalse(same(base.copy(mimeType = "fixture/A"), base.copy(mimeType = "fixture/B")))
        assertFalse(same(base.copy(protocol = "fixture-a"), base.copy(protocol = "fixture-b")))
        assertFalse(same(base.copy(authorityScope = "fixture-a"), base.copy(authorityScope = "fixture-b")))
        assertFalse(same(base.copy(options = listOf(NamedPlaybackOption("mode", "a"))),
            base.copy(options = listOf(NamedPlaybackOption("mode", "b")))))
    }

    @Test fun drmLicenseHeadersKeyScopeAndOptionsAreLossless() {
        val drm = DrmDefinition("fixture-drm", address("https://license.invalid/a"),
            listOf(Header("Authorization", "fixture-a")), "fixture-key-a")
        val base = definition().copy(drm = drm)
        assertTrue(same(base, base.copy(drm = drm.copy(licenseAddress = address("HTTPS://LICENSE.INVALID/a"),
            licenseHeaders = listOf(Header("authorization", "fixture-a"))))))
        listOf(drm.copy(scheme = "fixture-other"), drm.copy(licenseAddress = null),
            drm.copy(licenseAddress = address("https://license.invalid/b")), drm.copy(keyReference = "fixture-key-b"),
            drm.copy(licenseHeaders = listOf(Header("Authorization", "fixture-b"))),
            drm.copy(options = listOf(NamedPlaybackOption("mode", "fixture")))).forEachIndexed { index, variant ->
            assertFalse(same(base, base.copy(drm = variant)), "drm case $index")
        }
    }

    @Test fun unknownRequiredOrLossyInformationBlocksEvenIdenticalInputs() {
        val base = definition()
        listOf(base.copy(unknownRequiredDirectives = listOf("fixture-unknown")), base.copy(isLossless = false),
            base.copy(headers = listOf(Header("invalid name", "fixture"))),
            base.copy(drm = DrmDefinition("fixture", address("unsupported:fixture")))).forEach {
            assertNull(ComparisonV1.playbackKey(it))
            assertFalse(same(it, it))
        }
    }

    @Test fun diagnosticStringsDoNotExposeComparisonMaterial() {
        val marker = "fixture-sensitive-marker"
        val input = definition("https://media.invalid/a?token=$marker").copy(headers = listOf(Header("Authorization", marker)),
            drm = DrmDefinition(marker, keyReference = marker), options = listOf(NamedPlaybackOption("fixture", marker)))
        listOf(input.toString(), input.address.toString(), input.headers.toString(), input.drm.toString(),
            input.options.toString(), ComparisonV1.playbackKey(input).toString(), key(input.address.raw).toString()).forEach {
            assertFalse(marker in it, "redacted diagnostics")
        }
    }
}
