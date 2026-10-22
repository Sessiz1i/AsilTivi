package com.omer.belestiviclone

import kotlin.test.*

class LogoRequestPolicyTest {
    @Test fun transientFailureHasBoundedRetryDeadlineInsteadOfSessionLifetimeBlock() {
        val policy = LogoRequestPolicy()
        assertTrue(policy.mayRequest("logo", 100))
        assertTrue(policy.record("logo", LogoFailureKind.NETWORK, 100, 0))
        assertFalse(policy.mayRequest("logo", 30_099)); assertTrue(policy.mayRequest("logo", 30_100))
        assertTrue(policy.failure("logo")!!.kind.automaticRetry)
    }
    @Test fun unsupportedAndHttpDoNotCreateAutomaticRequestLoops() {
        val policy = LogoRequestPolicy()
        for (kind in listOf(LogoFailureKind.HTTP, LogoFailureKind.DECODE, LogoFailureKind.UNSUPPORTED)) {
            policy.record("logo", kind, 0, 0)
            assertFalse(policy.failure("logo")!!.kind.automaticRetry)
            assertFalse(policy.mayRequest("logo", 299_999)); assertTrue(policy.mayRequest("logo", 300_000))
        }
    }
    @Test fun clearAllowsRefreshAndRejectsLateFailureFromPreviousGeneration() {
        val policy = LogoRequestPolicy(); policy.record("logo", LogoFailureKind.NETWORK, 10, 0)
        assertEquals(1, policy.clear()); assertTrue(policy.mayRequest("logo", 11))
        assertFalse(policy.record("logo", LogoFailureKind.NETWORK, 12, 0))
        assertTrue(policy.mayRequest("logo", 12)); assertNull(policy.failure("logo"))
    }
    @Test fun failureCapacityDoesNotGrowWithCatalogScrolling() {
        val policy = LogoRequestPolicy(2)
        policy.record("one", LogoFailureKind.HTTP, 0, 0); policy.record("two", LogoFailureKind.HTTP, 0, 0)
        policy.record("three", LogoFailureKind.HTTP, 0, 0)
        assertTrue(policy.mayRequest("one", 1)); assertFalse(policy.mayRequest("two", 1)); assertFalse(policy.mayRequest("three", 1))
    }
    @Test fun exactArtworkAndNamedAliasAgreeWithoutGuessingByHostOrChannelSubstring() {
        val original = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/45/Atv_logo_2010.svg/3840px-Atv_logo_2010.svg.png"
        assertNotNull(approvedLogo(original)); assertEquals(approvedLogo(original), approvedLogo("logo_atv"))
        assertNull(approvedLogo(original + "?different=1")); assertNull(approvedLogo("https://unrelated.test/atv.png"))
    }
    @Test fun publisherVerifiedReplacementsOverrideQuarantineOnlyForExactCatalogKeys() {
        val rap = "https://www.numberone.com.tr/wp-content/uploads/2017/07/N1_TR_Damar.png"
        val turkFm = "https://www.canliradyodinle.fm/wp-content/uploads/number-one-fm.jpg"
        assertNotNull(approvedLogo(rap)); assertNotNull(approvedLogo(turkFm))
        assertNotEquals(approvedLogo(rap), approvedLogo(turkFm))
        assertFalse(forceLogoFallback(rap)); assertFalse(forceLogoFallback(turkFm))
        assertNull(approvedLogo(rap + "?unverified=1"))
    }
    @Test fun ambiguousAndUnmappedLegacyArtworkUsesNeutralFallbackWithoutChangingChannelData() {
        assertTrue(forceLogoFallback("https://cdn.technettv.com/channel/33090/logo_256.jpg"))
        assertTrue(forceLogoFallback("logo_ahaber_kamera")); assertTrue(forceLogoFallback("logo_fashion"))
        assertFalse(forceLogoFallback("logo_atv")); assertFalse(forceLogoFallback("ikon_ayar"))
    }
}
