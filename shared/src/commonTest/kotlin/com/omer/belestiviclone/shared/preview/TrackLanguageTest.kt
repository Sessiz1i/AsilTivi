package com.omer.belestiviclone.shared.preview

import kotlin.test.*

class TrackLanguageTest {
    private fun track(lang: String?, supported: Boolean=true, selected: Boolean=false, index: Int=0) =
        LanguageTrack("g",index,"id-$index",lang,supported,selected)
    @Test fun storedDisplayLabelsAreRuntimeTagsOnly() {
        assertEquals(listOf("tr","en","de","fr","es","ar","ru"),TrackLanguage.labels.values.toList())
        assertEquals("tr",TrackLanguage.audio("Türkçe").tag)
        assertEquals(LanguageMode.DEFAULT,TrackLanguage.audio("Auto").mode)
        assertEquals(LanguageMode.OFF,TrackLanguage.subtitle("Kapalı").mode)
        assertEquals(LanguageMode.UNKNOWN,TrackLanguage.audio("TR").mode)
        assertEquals(LanguageMode.UNKNOWN,TrackLanguage.subtitle("legacy-unknown").mode)
        assertEquals("en-us",TrackLanguage.normalize("EN_us"));assertEquals("en",TrackLanguage.normalize("eng"))
        assertNull(TrackLanguage.normalize(null));assertNull(TrackLanguage.normalize("und"))
        assertNull(TrackLanguage.normalize("und-US"));assertEquals("en-us",TrackLanguage.normalize("eng-US"))
        assertTrue(TrackLanguage.matches("en","en-GB"));assertFalse(TrackLanguage.matches("tr","en"))
    }
    @Test fun firstSupportedIncludesUndeterminedAndSkipsUnsupportedCodec() {
        val tracks=listOf(track("en",false),track(null,index=1),track("fr",index=2))
        assertEquals(1,subtitleTrack(TrackLanguage.subtitle("Otomatik (İlk Bulunan)"),tracks)?.index)
        assertNull(subtitleTrack(TrackLanguage.subtitle("English"),tracks))
        assertEquals(2,subtitleTrack(TrackLanguage.subtitle("Français"),tracks)?.index)
    }
    @Test fun exactThenRegionalMatchingNeverClaimsAnotherLanguage() {
        val tracks=listOf(track("en-GB",index=0),track("en",index=1),track("tr",false,index=2))
        assertEquals(1,subtitleTrack(TrackLanguage.subtitle("English"),tracks)?.index)
        assertNull(subtitleTrack(TrackLanguage.subtitle("Türkçe"),tracks))
        val status=languageStatus("Türkçe",TrackLanguage.audio("Türkçe"),listOf(track("en",selected=true)))
        assertTrue(status.contains("istenen dil yok"));assertTrue(status.contains("en (id-0)"))
        assertFalse(status.contains("gerçek seçilen track"))
    }
    @Test fun offUnknownAndEmptyNeverSelectSubtitle() {
        for(label in listOf("Kapalı","unknown")) assertNull(subtitleTrack(TrackLanguage.subtitle(label),listOf(track("en"))))
        assertNull(subtitleTrack(TrackLanguage.subtitle("English"),emptyList()))
        assertTrue(languageStatus("English",TrackLanguage.audio("English"),listOf(track("en",false))).contains("desteklenen track yok"))
    }
    @Test fun oldPreferenceChannelReleaseAndRetryOwnersCannotPublishCues() {
        val gate=CueOwnerGate();val a=gate.advance("a")!!
        assertTrue(gate.accepts(a,"a",true));assertFalse(gate.accepts(a,"a",false))
        val pref=gate.advance("a")!!;assertFalse(gate.accepts(a,"a",true));assertTrue(gate.accepts(pref,"a",true))
        val next=gate.advance("b")!!;assertFalse(gate.accepts(pref,"a",true));assertFalse(gate.accepts(next,"a",true))
        gate.advance(null);assertFalse(gate.accepts(next,"b",true))
        val retry=gate.advance("c")!!;assertFalse(gate.accepts(next,"b",true));assertTrue(gate.accepts(retry,"c",true))
    }
}
