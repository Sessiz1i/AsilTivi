package com.omer.belestiviclone.sharedui

import kotlin.test.*

class SettingsHeadingTest {
    @Test fun presentationHeadingsKeepTurkishUnicodeAndEnglishTranslations() {
        assertEquals("KANALLAR VE LİSTELER", settingsSectionHeading(0))
        assertEquals("LİSTE KONUMU", optionHeading("list"))
        assertEquals("UYGULAMA DİLİ", optionHeading("language"))
        assertEquals("TERCİH EDİLEN SES DİLİ", optionHeading("audio"))
        assertEquals("CHANNELS AND LISTS", translated(settingsSectionHeading(0), true))
        assertEquals("LIST POSITION", translated(optionHeading("list"), true))
        assertEquals("APPLICATION LANGUAGE", translated(optionHeading("language"), true))
        assertEquals("PREFERRED AUDIO LANGUAGE", translated(optionHeading("audio"), true))
        // Quality keeps its separate, mixed-case popup title.
        assertEquals("Video quality", translated(optionHeading("quality"), true))
        for (section in 0..5) assertNotEquals(settingsSectionHeading(section), translated(settingsSectionHeading(section), true))
    }

    @Test fun headingsDoNotChangePreferenceValuesOrUnknownTextFallback() {
        for (value in listOf("Dikey (Sidebar)", "Sığdır (Fit)", "Deutsch", "İzmir / SPOR / Spor", "https://fixture.invalid/Liste?Id=iIıİ")) {
            assertEquals(value, translated(value, false))
        }
        assertEquals("Fit", translated("Sığdır (Fit)", true))
        assertEquals("Deutsch", translated("Deutsch", true))
        assertEquals("İzmir / SPOR / Spor", translated("İzmir / SPOR / Spor", true))
        assertEquals("https://fixture.invalid/Liste?Id=iIıİ", translated("https://fixture.invalid/Liste?Id=iIıİ", true))
    }
}
