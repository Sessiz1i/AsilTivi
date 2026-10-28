package com.omer.belestiviclone.sharedui

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalLanguage = staticCompositionLocalOf { "Türkçe" }
internal val LocalEnglish = staticCompositionLocalOf { false }

// Explicit presentation headings keep Turkish dotted I correct without touching data values.
private val sectionHeadings = listOf("KANALLAR VE LİSTELER", "GÖRÜNÜM", "OYNATMA", "UYGULAMA", "YEDEKLEME VE AKTARIM", "BELEŞ TİVİ +", "YARDIM VE HAKKINDA")
private val optionHeadings = mapOf(
    "list" to "LİSTE KONUMU", "language" to "UYGULAMA DİLİ", "position" to "KONUM", "size" to "BOYUT",
    "quality" to "Görüntü Kalitesi", "format" to "VARSAYILAN GÖRÜNTÜ FORMATI",
    "audio" to "TERCİH EDİLEN SES DİLİ", "subtitle" to "ALTYAZI VARSAYILANI",
    "theme" to "TEMA SEÇİMİ", "sleepTimer" to "UYKU ZAMANLAYICISI",
    "subtitleSize" to "ALTYAZI BOYUTU", "subtitleColor" to "ALTYAZI RENGİ",
    "subtitleBgOpacity" to "ALTYAZI ARKA PLAN OPAKLIĞI",
)
internal fun settingsSectionHeading(section: Int): String = sectionHeadings[section]
internal fun optionHeading(kind: String): String = optionHeadings.getValue(kind)

private val translations = mapOf(
    "KANALLAR VE LİSTELER" to "CHANNELS AND LISTS", "GÖRÜNÜM" to "APPEARANCE", "OYNATMA" to "PLAYBACK",
    "UYGULAMA" to "APPLICATION", "YEDEKLEME VE AKTARIM" to "BACKUP AND TRANSFER", "BELEŞ TİVİ +" to "BELEŞ TİVİ PLUS", "YARDIM VE HAKKINDA" to "HELP AND ABOUT",
    "LİSTEM" to "MY LIST", "KENDİ LİSTENİ EKLE" to "ADD YOUR OWN LIST", "KANAL DÜZENİ" to "CHANNEL ARRANGEMENT",
    "VİDEO" to "VIDEO", "SES & ALTYAZI" to "AUDIO & SUBTITLES", "DİL" to "LANGUAGE", "BAŞLATMA" to "STARTUP",
    "ALTYAZI GÖRÜNÜMÜ" to "SUBTITLE APPEARANCE", "TEMA" to "THEME", "UYGULAMA TEMASI" to "APPLICATION THEME", "ZAMANLAYICI" to "TIMER",
    "BAŞLATMA & DAVRANIŞ" to "STARTUP & BEHAVIOR",
    "TEMA SEÇİMİ" to "THEME SELECTION", "UYKU ZAMANLAYICISI" to "SLEEP TIMER",
    "ALTYAZI BOYUTU" to "SUBTITLE SIZE", "ALTYAZI RENGİ" to "SUBTITLE COLOR",
    "ALTYAZI ARKA PLAN OPAKLIĞI" to "SUBTITLE BACKGROUND OPACITY",
    "Altyazı Boyutu" to "Subtitle size", "Altyazı Rengi" to "Subtitle color",
    "Altyazı Arka Plan Opaklığı" to "Subtitle background opacity",
    "Beleş TiVi Klasik" to "Beleş TiVi Classic", "Klasik" to "Classic", "Modern" to "Modern", "Altın" to "Gold", "Sinema" to "Cinema",
    "15 dk" to "15 min", "30 dk" to "30 min", "45 dk" to "45 min", "60 dk" to "60 min", "90 dk" to "90 min", "120 dk" to "120 min",
    "Çok Büyük" to "Very Large", "Beyaz" to "White", "Sarı" to "Yellow", "Yeşil" to "Green", "Camgöbeği" to "Cyan",
    "%0 (Saydam)" to "%0 (Transparent)", "%100 (Siyah)" to "%100 (Black)",
    "Favorilerden Başlat" to "Start with favorites", "Arka Planda Sesi Sürdür" to "Background audio",
    "Resim İçinde Resim (PiP)" to "Picture-in-Picture (PiP)",
    "GEÇMİŞ" to "HISTORY", "FAVORİLER" to "FAVORITES", "DESTEK" to "SUPPORT", "DİĞER İŞLEMLER" to "OTHER ACTIONS",
    "DOSYA SEÇ" to "CHOOSE FILE",
    "KANAL LİSTESİ" to "CHANNEL LIST", "KANAL BİLGİSİ" to "CHANNEL INFORMATION", "Konum" to "Position", "Boyut" to "Size",
    "KONUM" to "POSITION", "BOYUT" to "SIZE", "LİSTE KONUMU" to "LIST POSITION", "UYGULAMA DİLİ" to "APPLICATION LANGUAGE",
    "VARSAYILAN GÖRÜNTÜ FORMATI" to "DEFAULT VIDEO FORMAT", "TERCİH EDİLEN SES DİLİ" to "PREFERRED AUDIO LANGUAGE", "ALTYAZI VARSAYILANI" to "DEFAULT SUBTITLES",
    "ÇOKLU EKRAN" to "MULTI SCREEN", "AYARLAR" to "SETTINGS", "HIZLI AKSİYONLAR" to "QUICK ACTIONS", "KATEGORİ YÖNETİMİ" to "CATEGORY MANAGEMENT",
    "TÜMÜNÜ GÖSTER" to "SHOW ALL", "TÜMÜNÜ GİZLE" to "HIDE ALL", "Kanallar Ve Listeler ›" to "Channels And Lists ›",
    "Örn: Evdeki TV, Yedek Liste" to "Example: Home TV, Backup List", "Kullanıcı adınız" to "Your username", "Şifreniz" to "Your password",
    "Kaç ekranı aynı anda izlemek istersin?" to "How many screens would you like to watch?",
    "Yan yana izleme · Orta performans" to "Side by side · Medium performance", "Izgara düzeni · Yüksek performans" to "Grid layout · High performance",
    "Çoklu ekran modu cihazınızın işlemcisini ve internetinizi yoğun kullanır." to "Multi screen mode uses substantial processing and network resources.",
    "Eski veya düşük donanımlı cihazlarda donma yaşanabilir. Devam etmek istiyor\n musunuz?" to "Older or less powerful devices may stutter.\nWould you like to continue?",
    "Önizleme: tek seçili player; dört bağımsız yayın uygulanmadı." to "Preview: one selected player; four independent streams are unavailable.",
    "Önizleme: yalnız seçili tek yayın oynatılır." to "Preview: only the selected stream plays.",
    "Favoriler" to "Favorites", "Son İzlenenler" to "Recently watched", "Tümü" to "All",
    "Örnek" to "Sample", "Ulusal" to "National", "Haber" to "News", "Spor" to "Sports",
    "Çocuk" to "Kids", "Müzik" to "Music", "Belgesel" to "Documentary",
    "Liste Konumu" to "List position", "Liste Şeffaflığı" to "List opacity",
    "Kanal Bilgisi Konum" to "Channel info position", "Kanal Bilgisi Boyut" to "Channel info size",
    "Uygulama Dili" to "Application language", "Görüntü Kalitesi" to "Video quality",
    "Varsayılan Görüntü Formatı" to "Default video format", "Tercih Edilen Ses Dili" to "Preferred audio language",
    "Altyazı Varsayılanı" to "Default subtitles", "Yatay (Alt)" to "Horizontal (Bottom)",
    "Dikey (Sidebar)" to "Vertical (Sidebar)", "Sol" to "Left", "Orta" to "Center", "Sağ" to "Right",
    "Küçük" to "Small", "Normal" to "Normal", "Büyük" to "Large", "Türkçe" to "Turkish",
    "Orijinal" to "Original", "Sığdır (Fit)" to "Fit", "Tam Ekran (Kes)" to "Crop",
    "GÖRÜNTÜ ORANI" to "ASPECT RATIO",
    "Otomatik (Orijinal yayın oranı)" to "Automatic (Original aspect ratio)",
    "Ekrana Yay (Stretch / Fill)" to "Stretch to Screen (Fill)",
    "Yakınlaştır (Zoom / Crop)" to "Zoom (Crop)",
    "16:9 (Geniş ekran)" to "16:9 (Widescreen)",
    "4:3 (Klasik ekran)" to "4:3 (Classic)",
    "1.85:1 (Geniş sinema)" to "1.85:1 (Wide Cinema)",
    "2.39:1 (Anamorfik sinema)" to "2.39:1 (Anamorphic Cinema)",
    "Program Bilgisi Bulunamadı" to "No Program Info Found",
    "Program bilgisi bulunamadı" to "No program info found",
    "EPG (PROGRAM REHBERİ)" to "EPG (PROGRAM GUIDE)",
    "XMLTV EPG URL" to "XMLTV EPG URL",
    "Varsayılan XMLTV EPG kaynağı aktif" to "Default XMLTV EPG source active",
    "ÜLKE LİSTELERİ" to "COUNTRY LISTS",
    "Ülke Listeleri" to "Country Lists",
    "Aktif Listem" to "My Active List",
    "Stalker / MAC" to "Stalker / MAC",
    "Portal adresi ve kayıtlı MAC ile bağlan" to "Connect with portal URL and MAC address",
    "Açık" to "On", "Kapalı" to "Off", "Otomatik (İlk Bulunan)" to "Automatic (First available)",
    "Favorilerden kaldır" to "Remove from favorites", "Favorilere ekle" to "Add to favorites",
    "Sırasını değiştir…" to "Change order…", "En başa taşı" to "Move to start", "En sona taşı" to "Move to end",
    "Vazgeç" to "Cancel", "Kanalı gizle" to "Hide channel", "Gizlenen kanalları geri getir" to "Restore hidden channels",
    "Kategorileri Yönet" to "Manage categories", "Tümünü göster" to "Show all", "Tümünü gizle" to "Hide all",
    "Geçmişi Temizle?" to "Clear history?", "İPTAL" to "CANCEL", "EVET, SİL" to "YES, DELETE",
    "DEVAM ET" to "CONTINUE", "DEVAM" to "CONTINUE", "Performans Uyarısı" to "Performance warning",
    "Çoklu Ekran Düzeni" to "Multi screen layout", "2 Ekran" to "2 Screens", "4 Ekran" to "4 Screens",
    "2 Ekran · Yan Yana" to "2 Screens · Side by Side",
    "1 Büyük + 2 Küçük" to "1 Big + 2 Small",
    "1 Büyük + 3 Küçük" to "1 Big + 3 Small",
    "4 Ekran · Eşit" to "4 Screens · Equal",
    "Kayıtlı Düzenler" to "Saved Layouts",
    "Son Çoklu Ekran" to "Last Multi-Screen",
    "Düzen" to "Layout", "Kaydet" to "Save", "Paketler" to "Presets",
    "+ Kanal Ekle" to "+ Add channel", "Ekrana Sığdır" to "Fit to screen", "× ÇIKIŞ" to "× EXIT",
    "Çıkmak İstiyor Musunuz?" to "Do you want to exit?", "EVET, ÇIKIŞ" to "YES, EXIT", "HAYIR, DEVAM" to "NO, CONTINUE",
    "Radyo" to "Radio", "Görüntü" to "Display", "Bilgi" to "Info", "Çıkış" to "Exit",
    "Örnek panel · yayın yok" to "Sample panel · no stream", "Orijinal · görünüm tercihi" to "Original · display preference",
    "Liste Adı" to "List name", "M3U URL (örnek)" to "M3U URL (sample)",
    "Sunucu Adresi (örnek)" to "Server address (sample)", "Örnek kullanıcı adı" to "Sample username",
    "Örnek parola · saklanmaz" to "Sample password · not stored", "Örnek kaynak kaydı" to "Sample source record",
    "Örnek kaydı düzenle" to "Edit sample record", "Örnek kaydı sakla" to "Save sample record",
    "Listeyi Yükle" to "Load list", "LİSTEYİ YÜKLE" to "LOAD LIST", "DÜZENLE" to "EDIT", "SİL" to "DELETE",
    "Kayıtlı Listelerim" to "Saved lists", "Dosya Seç" to "Choose file",
    "Örnek kayıt yazılıyor…" to "Saving sample record…", "Örnek profil açılıyor…" to "Opening sample profile…",
    "Örnek profil · yerel kayıt" to "Sample profile · local storage", "Örnek profil · yalnız bu oturumda" to "Sample profile · this session only",
    "Örnek video" to "Sample video", "← GERİ DÖN" to "← GO BACK",
    "Örnek video; kalite ve dil tercihlerinin yayın etkisi doğrulanmadı." to "Sample video; stream quality and language effects are unverified.",
    "Gerçek hesap bilgisi girmeyin. Ağ bağlantısı yapılmaz; yalnız örnek kayıt adı saklanır." to "Do not enter real account details. No connection is made; only the sample record name is saved.",
    "Önizleme: panel atamaları gösterilir. Dört gerçek yayın doğrulanmadı." to "Preview: panel assignments are shown. Four real streams are unverified.",
    "Çoklu ekran modu cihazınızın işlemcisini ve internetinizi yoğun kullanır. Eski veya düşük donanımlı cihazlarda donma yaşanabilir. Devam etmek istiyor musunuz?" to "Multi screen mode uses substantial processing and network resources. Older devices may stutter. Continue?",
    "Kaydedilemedi. Önceki örnek durum korundu." to "Save failed. The previous sample state was preserved.",
    "Örnek kayıt saklandı. Gerçek liste yüklenmedi." to "Sample record saved. No real list was loaded.",
    "Bu örnek kayıt gerçek liste yüklemez." to "This sample record does not load a real list.",
    "Örnek kanal kartı; yayın bağlantısı yok." to "Sample channel card; no stream connection.",
    "Örnek izleme geçmişi tamamen temizlendi!" to "Sample watch history cleared!",
    "Önizleme: dış destek mesajı gönderilmez." to "Preview: no external support message is sent.",
    "Önizleme: dış bağlantı açılmaz." to "Preview: no external link is opened.",
    "Örnek profil; gerçek liste güncellemesi bağlı değil." to "Sample profile; real list refresh is unavailable.",
    "Gerçek liste yükleme bu önizlemeye bağlı değil." to "Real list loading is unavailable in this preview.",
    "Tercih kaydedildi. Bu örnek videoda tüm yayın seçenekleri doğrulanmadı." to "Preference saved. Not all stream options are verified for this sample video.",
    "Dosyadan kaynak yükleme görünümü. Bu önizlemede dosya okunmaz." to "File source form. No file is read in this preview.",
    "Önizleme: gerçek dosya yükleme bağlı değil." to "Preview: real file loading is unavailable.",
    "Önbelleği Temizle ayrı işlemdir. Önizlemede gerçek veri veya önbellek silinmez." to "Clear cache is a separate operation. This preview does not delete real data or cache.",
    "Önizleme: önbellek temizleme bağlı değil." to "Preview: cache clearing is unavailable.",
)

internal fun translated(value: String, english: Boolean): String {
    if (!english) return value
    translations[value]?.let { return it }
    return value.split("  ›  ").joinToString("  ›  ") { part ->
        translations[part] ?: if (part.endsWith("  ✓")) (translations[part.removeSuffix("  ✓")] ?: part.removeSuffix("  ✓")) + "  ✓" else part
    }
}

// Connected source/backup surfaces share the same presentation lookup in all three languages.
private val german = mapOf(
    "Kanallar Ve Listeler" to "Kanäle und Listen", "Görünüm" to "Darstellung", "Oynatma" to "Wiedergabe", "Uygulama" to "Anwendung", "Yedekleme Ve Aktarım" to "Sicherung und Transfer", "Beleş TiVi +" to "Beleş TiVi +", "Yardım Ve Hakkında" to "Hilfe und Info",
    "KANALLAR VE LİSTELER" to "KANÄLE UND LISTEN", "YEDEKLEME VE AKTARIM" to "SICHERUNG UND TRANSFER", "LİSTEM" to "MEINE LISTE", "KENDİ LİSTENİ EKLE" to "EIGENE LISTE HINZUFÜGEN", "KANAL DÜZENİ" to "KANALANORDNUNG", "FAVORİLER" to "FAVORITEN",
    "Ek Liste" to "Zusatzliste", "Ek Liste Ekle" to "Zusatzliste hinzufügen", "Listeyi Güncelle" to "Liste aktualisieren", "Son başarılı snapshot korunur" to "Letzter erfolgreicher Stand bleibt erhalten", "En güncel kanal listelerini indir" to "Aktuellste Kanallisten herunterladen", "Ana kaynağa bağlı tek ek liste" to "Eine Zusatzliste pro Hauptquelle", "Varsayılana Dön" to "Standard wiederherstellen", "Varsayılan Listeye Geri Dön" to "Zur Standardliste zurückkehren", "Varsayılan listeye geri dön" to "Zur Standardliste zurückkehren", "Klonun trlist kaynağı; favoriler korunur" to "trlist-Quelle; Favoriten bleiben erhalten",
    "M3U URL" to "M3U URL", "Özel liste adresi ile bağlan" to "Eigene Listenadresse verwenden", "Xtream Codes" to "Xtream Codes", "Sunucu bilgileri ile bağlantı" to "Mit Serverdaten verbinden", "Dosyadan Yükle" to "Aus Datei laden", "M3U dosyası seç" to "M3U-Datei wählen", "Kategorileri Yönet" to "Kategorien verwalten", "Sıralama ve görünürlük" to "Reihenfolge und Sichtbarkeit",
    "Favorileri Dışa Aktar" to "Favoriten exportieren", "Favori listesini yedekle" to "Favoritenliste sichern", "Favorileri İçe Aktar" to "Favoriten importieren", "Yedekten geri yükle" to "Aus Sicherung laden",
    "DOSYA SEÇ" to "DATEI WÄHLEN", "Ayarlar" to "Einstellungen", "ÇIKIŞ" to "BEENDEN", "Paneli Kapat" to "Fenster schließen", "Yerel kayıt" to "Lokal gespeichert", "İşlem sürüyor…" to "Vorgang läuft…", "Uygulama Dili" to "Sprache", "UYGULAMA DİLİ" to "SPRACHE", "UYGULAMA" to "ANWENDUNG", "DİL" to "SPRACHE",
    "Çıkmak İstiyor Musunuz?" to "Möchten Sie beenden?", "EVET, ÇIKIŞ" to "JA, BEENDEN", "HAYIR, DEVAM" to "NEIN, WEITER", "Radyo" to "Radio", "Görüntü" to "Bildformat", "Bilgi" to "Info", "Çıkış" to "Beenden"
)
private val connectedEnglish = mapOf(
    "Kanallar Ve Listeler" to "Channels and Lists", "Yedekleme Ve Aktarım" to "Backup and Transfer", "Beleş TiVi +" to "Beleş TiVi +", "Yardım Ve Hakkında" to "Help and About",
    "Ek Liste" to "Extra List", "Ek Liste Ekle" to "Add Extra List", "Listeyi Güncelle" to "Refresh List", "Son başarılı snapshot korunur" to "Last successful snapshot preserved", "En güncel kanal listelerini indir" to "Download latest channel lists", "Ana kaynağa bağlı tek ek liste" to "One extra list per main source", "Varsayılana Dön" to "Restore Default", "Varsayılan Listeye Geri Dön" to "Return to default list", "Varsayılan listeye geri dön" to "Return to default list", "Klonun trlist kaynağı; favoriler korunur" to "Clone trlist source; favorites preserved",
    "Özel liste adresi ile bağlan" to "Use a custom list address", "Sunucu bilgileri ile bağlantı" to "Connect with server details", "Dosyadan Yükle" to "Load from File", "M3U dosyası seç" to "Choose M3U file", "Sıralama ve görünürlük" to "Order and visibility",
    "Favorileri Dışa Aktar" to "Export Favorites", "Favori listesini yedekle" to "Back up favorite list", "Favorileri İçe Aktar" to "Import Favorites", "Yedekten geri yükle" to "Restore from backup", "Paneli Kapat" to "Close Panel", "Ayarlar" to "Settings", "ÇIKIŞ" to "EXIT", "Yerel kayıt" to "Local storage", "İşlem sürüyor…" to "Working…",
    "İki bağımsız yayın; dört yayın kapasitesi doğrulanmadı." to "Two independent streams; four-stream capacity unverified."
)
internal fun presentationText(value: String, language: String): String = when (language) {
    "Deutsch" -> german[value] ?: if (Regex("\\d+ içerik").matches(value)) value.substringBefore(' ') + " Einträge" else value
    "English" -> connectedEnglish[value] ?: if (Regex("\\d+ içerik").matches(value)) value.substringBefore(' ') + " items" else translated(value, true)
    else -> value
}
