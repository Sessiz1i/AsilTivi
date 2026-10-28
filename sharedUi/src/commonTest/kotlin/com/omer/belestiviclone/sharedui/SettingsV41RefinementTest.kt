package com.omer.belestiviclone.sharedui

import kotlin.test.*

class SettingsV41RefinementTest {

    @Test
    fun testSettingsSectionsCountAndOrder() {
        assertEquals("KANALLAR VE LİSTELER", settingsSectionHeading(0))
        assertEquals("GÖRÜNÜM", settingsSectionHeading(1))
        assertEquals("OYNATMA", settingsSectionHeading(2))
        assertEquals("UYGULAMA", settingsSectionHeading(3))
        assertEquals("YEDEKLEME VE AKTARIM", settingsSectionHeading(4))
        assertEquals("BELEŞ TİVİ +", settingsSectionHeading(5))
        assertEquals("YARDIM VE HAKKINDA", settingsSectionHeading(6))
    }

    @Test
    fun testV41RefinedTextsInTranslations() {
        // Türkçe -> Almanca ve İngilizce metin eşleşmeleri
        assertEquals("Download latest channel lists", presentationText("En güncel kanal listelerini indir", "English"))
        assertEquals("Aktuellste Kanallisten herunterladen", presentationText("En güncel kanal listelerini indir", "Deutsch"))

        assertEquals("Return to default list", presentationText("Varsayılan listeye geri dön", "English"))
        assertEquals("Zur Standardliste zurückkehren", presentationText("Varsayılan listeye geri dön", "Deutsch"))

        assertEquals("Add Extra List", presentationText("Ek Liste Ekle", "English"))
        assertEquals("Zusatzliste hinzufügen", presentationText("Ek Liste Ekle", "Deutsch"))

        assertEquals("Beleş TiVi +", presentationText("Beleş TiVi +", "English"))
        assertEquals("Beleş TiVi +", presentationText("Beleş TiVi +", "Deutsch"))
    }

    @Test
    fun testSettingsSectionHeadingTranslations() {
        assertEquals("CHANNELS AND LISTS", translated(settingsSectionHeading(0), true))
        assertEquals("APPEARANCE", translated(settingsSectionHeading(1), true))
        assertEquals("PLAYBACK", translated(settingsSectionHeading(2), true))
        assertEquals("APPLICATION", translated(settingsSectionHeading(3), true))
        assertEquals("BACKUP AND TRANSFER", translated(settingsSectionHeading(4), true))
        assertEquals("BELEŞ TİVİ PLUS", translated(settingsSectionHeading(5), true))
        assertEquals("HELP AND ABOUT", translated(settingsSectionHeading(6), true))
    }
}
