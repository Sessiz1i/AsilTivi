package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omer.belestiviclone.shared.preview.CountryListsCatalog
import com.omer.belestiviclone.shared.preview.SourceKind

internal val sourceFormKinds = setOf("m3u", "supplement", "xtream", "stalker", "countries", "activeSources", "file", "epg")

/** Same settings shell and footer; back drops the secret draft through PreviewState.dismiss. */
@Composable
internal fun SourceForm(s: PreviewState, image: @Composable (String, Modifier) -> Unit, modifier: Modifier) {
    val kind = s.dialog ?: return
    val back = remember(kind) { FocusRequester() }
    val title = when (kind) {
        "xtream" -> s.message("XTREAM CODES BAĞLANTISI", "XTREAM CODES CONNECTION", "XTREAM CODES-VERBINDUNG")
        "stalker" -> s.message("STALKER / MAC PORTAL", "STALKER / MAC PORTAL", "STALKER / MAC-PORTAL")
        "countries" -> s.message("ÜLKE LİSTELERİ", "COUNTRY LISTS", "LÄNDERLISTEN")
        "activeSources" -> s.message("AKTİF VE KAYITLI KAYNAKLAR", "ACTIVE & SAVED SOURCES", "AKTIVE & GESPEICHERTE QUELLEN")
        "supplement" -> s.message("Ek Liste", "Extra List", "Zusatzliste")
        "file" -> s.message("Cihazdan M3U Dosyası Yükle", "Load M3U File", "M3U-Datei laden")
        "epg" -> s.message("XMLTV EPG KAYNAĞI", "XMLTV EPG SOURCE", "XMLTV EPG-QUELLE")
        else -> s.message(if (s.editing == null) "YENİ LİSTE EKLE" else "LİSTEYİ DÜZENLE", if (s.editing == null) "ADD NEW LIST" else "EDIT LIST", if (s.editing == null) "NEUE LISTE" else "LISTE BEARBEITEN")
    }
    val scrollState = rememberScrollState()
    LaunchedEffect(kind) { scrollState.scrollTo(0) }
    Column(modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 16.dp, vertical = 19.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Action("Kanallar Ve Listeler › $title · " + s.message("Geri", "Back", "Zurück"), Modifier.fillMaxWidth().height(45.dp).focusRequester(back), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 20.dp), content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Label("←", 12, Color(0xFFE67E22)); Label("Kanallar Ve Listeler ›", 10, Color(0xFF69798F), bold = true); Label(title, 10, bold = true, maxLines = 1)
            }
        }, click = s::dismiss)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.height(26.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(16.dp).background(Color(0xFFE67E22)))
            Label(if (kind == "file") s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN") else title, 10, Color(0xFF94A2B7), Modifier.padding(start = 10.dp), true, letterSpacing = 1f)
        }
        if (kind in setOf("m3u", "xtream", "stalker")) {
            Action(
                label = "Telefondan Aktar",
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                tint = Color(0xFF13233D),
                stroke = Color(0xFF263F63),
                strokeWidth = 1.dp,
                radius = 10.dp,
                padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                content = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRowIcon("webtransfer", color = Color(0xFFE67E22), modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Label("Telefondan Aktar", 12, Color.White, bold = true)
                            Label("Uzun adresleri kumandayla yazmak yerine telefon kamerasıyla QR okutarak gönder", 9, Color(0xFF94A2B7))
                        }
                        MenuChevronIcon(color = Color(0xFFE67E22), modifier = Modifier.size(16.dp))
                    }
                }
            ) {
                s.openWebTransfer()
            }
        }
        when (kind) {
            "countries" -> CountryListsPanel(s)
            "activeSources" -> ActiveAndSavedSourcesSection(s, image, showHeader = false)
            "file" -> {
                Column(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF13233D), RoundedCornerShape(10.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    image("ikon_dosya", Modifier.size(40.dp, 29.dp)); Label(title, 12, bold = true)
                    Label(s.message("Seçilen M3U uygulamaya kopyalanır; dış dosyadan bağımsızdır.", "The selected M3U is copied into the app.", "Die gewählte M3U wird in die App kopiert."), 10, Color(0xFF94A2B7))
                }
                Spacer(Modifier.height(9.dp))
                PrimarySubmitButton(s, kind, image)
            }
            "epg" -> {
                Field(
                    s.formAddress,
                    s.message("XMLTV EPG URL", "XMLTV EPG URL", "XMLTV EPG-URL"),
                    "ikon_url",
                    image,
                    placeholder = s.message("https://.../epg.xml veya epg.xml.gz", "https://.../epg.xml or epg.xml.gz", "https://.../epg.xml oder epg.xml.gz")
                ) { s.formAddress = it }
                Spacer(Modifier.height(9.dp))
                PrimarySubmitButton(s, kind, image)
                Label(s.message("Boş bırakıp kaydederseniz yerleşik örnek EPG (epg_sample.xml) kullanılır.", "Leave blank and save to use the built-in sample EPG (epg_sample.xml).", "Leer lassen, um das integrierte Beispiel-EPG zu verwenden."), 10, Color(0xFF94A2B7), Modifier.padding(top = 8.dp))
                if (s.data.pref("epgUrl").isNotBlank()) Action(s.message("VARSAYILAN ÖRNEK EPG'YE DÖN", "RESET TO SAMPLE EPG", "STANDARD-EPG WIEDERHERSTELLEN"), Modifier.fillMaxWidth(), enabled = !s.busy, click = s::clearEpgUrl)
            }
            "stalker" -> {
                Field(s.formName, s.message("PORTAL ADI (İSTEĞE BAĞLI)", "PORTAL NAME (OPTIONAL)", "PORTALNAME (OPTIONAL)"), "ikon_liste_adi", image, placeholder = s.message("Örn: MAG Portalım", "Example: My MAG Portal", "Beispiel: Mein MAG-Portal")) { s.formName = it }
                Field(s.formAddress, s.message("PORTAL ADRESİ", "PORTAL URL", "PORTALADRESSE"), "ikon_sunucu", image, placeholder = "http://portal-sunucu.com:8080/c/") { s.formAddress = it }
                Field(s.formMac, s.message("MAC ADRESİ", "MAC ADDRESS", "MAC-ADRESSE"), "ikon_kullanici", image, placeholder = "00:1A:79:12:34:56") { s.formMac = it }
                Spacer(Modifier.height(6.dp))
                Label("CİHAZ BİLGİLERİ (İSTEĞE BAĞLI)", 10, Color(0xFF94A2B7), bold = true, letterSpacing = 1f)
                Field(s.formSerial, "Seri numarası (Serial / SN)", "ikon_ayar", image, placeholder = "İsteğe bağlı") { s.formSerial = it }
                Field(s.formDeviceId, "Cihaz kimliği (Device ID)", "ikon_ayar", image, placeholder = "İsteğe bağlı") { s.formDeviceId = it }
                Field(s.formDeviceId2, "İkinci cihaz kimliği (Device ID 2)", "ikon_ayar", image, placeholder = "İsteğe bağlı") { s.formDeviceId2 = it }
                Spacer(Modifier.height(9.dp))
                PrimarySubmitButton(s, kind, image)
                Label(
                    s.message(
                        "Stalker / Ministra portal protokolü (/stalker_portal/server/load.php?type=stb&action=handshake) ile token alır ve kanal listesini çeker.",
                        "Connects via Stalker portal handshake (load.php?type=stb&action=handshake) and fetches channel list.",
                        "Verbindet über Stalker-Portal-Handshake und lädt die Kanalliste."
                    ),
                    10,
                    Color(0xFF94A2B7),
                    Modifier.padding(top = 8.dp)
                )
                if (s.busy) Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), enabled = !s.committingSource, click = s::cancelSource)
                ActiveAndSavedSourcesSection(s, image, showHeader = true)
            }
            else -> {
                Field(
                    s.formName,
                    if (kind == "xtream") s.message("LİSTE ADI (İSTEĞE BAĞLI)", "LIST NAME (OPTIONAL)", "LISTENNAME (OPTIONAL)") else s.message("LİSTE ADI", "LIST NAME", "LISTENNAME"),
                    "ikon_liste_adi",
                    image,
                    placeholder = if (kind == "xtream") s.message("Örn: IPTV Hesabım", "Example: My IPTV", "Beispiel: Mein IPTV") else s.message("Örn: Evdeki TV, Yedek Liste", "Example: Home TV, Backup List", "Beispiel: TV zu Hause, Ersatzliste")
                ) { s.formName = it }
                if (!s.editingFile) {
                    Field(
                        s.formAddress,
                        if (kind == "xtream") s.message("SUNUCU ADRESİ", "SERVER ADDRESS", "SERVERADRESSE") else "M3U URL",
                        if (kind == "xtream") "ikon_sunucu" else "ikon_url",
                        image,
                        placeholder = if (kind == "xtream") "http://ornek-sunucu.com:8080" else s.message("http://... veya https://...", "http://... or https://...", "http://... oder https://...")
                    ) { s.formAddress = it }
                }
                if (kind == "xtream") {
                    Field(s.formUser, s.message("KULLANICI ADI", "USERNAME", "BENUTZERNAME"), "ikon_kullanici", image, placeholder = s.message("Kullanıcı adınız", "Your username", "Ihr Benutzername")) { s.formUser = it }
                    Field(s.formPassword, s.message("ŞİFRE", "PASSWORD", "PASSWORT"), "ikon_sifre", image, password = true, placeholder = s.message("Şifreniz", "Your password", "Ihr Passwort")) { s.formPassword = it }
                }
                Spacer(Modifier.height(9.dp))
                PrimarySubmitButton(s, kind, image)
                Label(
                    if (kind == "xtream") {
                        s.message(
                            "player_api.php ve get.php üzerinden kimlik doğrulaması yapılır; canlı yayın kategorileri ve EPG kimlikleri aktarılır.",
                            "Authenticates via player_api.php and get.php; imports live categories and EPG IDs.",
                            "Authentifiziert über player_api.php und get.php; importiert Live-Kategorien und EPG-IDs."
                        )
                    } else {
                        s.message("Başarıyla yüklenip kaydedilene kadar önceki kaynak korunur.", "Previous source is preserved until loading and saving succeed.", "Vorherige Quelle bleibt bis zum erfolgreichen Speichern erhalten.")
                    },
                    10,
                    Color(0xFF94A2B7),
                    Modifier.padding(top = 8.dp)
                )
                if (s.busy) Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), enabled = !s.committingSource, click = s::cancelSource)
                if (kind == "supplement" && s.data.library?.extra != null) Action(s.message("EK LİSTEYİ KALDIR", "REMOVE EXTRA LIST", "ZUSATZLISTE ENTFERNEN"), Modifier.fillMaxWidth(), enabled = !s.busy) { s.dialog = "removeExtra" }
                if (kind == "m3u" || kind == "xtream") {
                    ActiveAndSavedSourcesSection(s, image, showHeader = true)
                }
            }
        }
    }
    LaunchedEffect(kind) { back.requestFocus() }
}

@Composable
private fun PrimarySubmitButton(s: PreviewState, kind: String, image: @Composable (String, Modifier) -> Unit) {
    val labelText = when (kind) {
        "file" -> s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN")
        "xtream" -> s.message("BAĞLAN VE YÜKLE", "CONNECT & LOAD", "VERBINDEN & LADEN")
        "stalker" -> s.message("PORTALA BAĞLAN", "CONNECT PORTAL", "PORTAL VERBINDEN")
        else -> s.message("KAYDET VE YÜKLE", "SAVE AND LOAD", "SPEICHERN UND LADEN")
    }
    Action(labelText, Modifier.fillMaxWidth().height(42.dp), tint = Color(0xFFE67E22), strokeWidth = 0.dp, enabled = !s.busy, content = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (kind == "xtream" || kind == "stalker") Label("🚀", 12) else image(if (kind == "file") "ikon_dosya" else "ikon_kaydet", Modifier.size(12.dp, 13.dp))
            Label(labelText, 11, bold = true)
        }
    }) {
        when (kind) {
            "file" -> s.requestDocument("file")
            "xtream" -> s.saveXtreamSource()
            "stalker" -> s.saveStalkerSource()
            "epg" -> s.saveEpgUrl()
            else -> if (kind == "supplement" && s.data.library?.extra != null) s.dialog = "replaceExtra" else s.saveSource()
        }
    }
}

@Composable
private fun CountryListsPanel(s: PreviewState) {
    Label(
        s.message(
            "Beleş TiVi 4.1 ülke paketlerini tek dokunuşla mevcut listenize dahil edebilir, kategorisine göre filtreleyebilir veya bağımsız kaynak olarak yükleyebilirsiniz.",
            "Include country channel packs with a single tap, filter by country category, or load as a standalone source.",
            "Länderpakete mit einem Tippen einschließen, nach Kategorie filtern oder als eigene Quelle laden."
        ),
        10,
        Color(0xFF94A2B7),
        Modifier.padding(bottom = 10.dp)
    )
    val enabledCodes = s.enabledCountryCodes
    CountryListsCatalog.presets.forEach { preset ->
        val isTr = preset.code == "TR"
        val isEnabled = isTr || preset.code in enabledCodes
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF13233D), RoundedCornerShape(10.dp))
                .border(1.dp, if (isEnabled) Color(0xFF0ABB91) else Color(0xFF223858), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(preset.flag, 18)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(preset.name, 12, Color.White, bold = true)
                        Box(
                            Modifier
                                .background(if (isEnabled) Color(0xFF0ABB91) else Color(0xFF2A3B54), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Label(if (isTr) "VARSAYILAN" else if (isEnabled) "DAHİL ✓" else "KAPALI", 8, Color.White, bold = true)
                        }
                    }
                    Label(preset.description, 9, Color(0xFF94A2B7), maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isTr) {
                    Action(
                        label = if (isEnabled) "${preset.name} Çıkar" else "${preset.name} Ekle",
                        modifier = Modifier.weight(1f).height(34.dp),
                        tint = if (isEnabled) Color(0xFF2A3B54) else Color(0xFFE67E22),
                        radius = 8.dp,
                        strokeWidth = 0.dp,
                        enabled = !s.busy,
                        content = { Label(if (isEnabled) "LİSTEDEN ÇIKAR" else "LİSTEYE DAHİL ET (+)", 10, bold = true) }
                    ) { s.toggleCountryList(preset.code) }
                }
                Action(
                    label = "${preset.name} Kategorisini Aç",
                    modifier = Modifier.weight(1f).height(34.dp),
                    tint = Color(0xFF1E3557),
                    radius = 8.dp,
                    strokeWidth = 0.dp,
                    enabled = !s.busy,
                    content = { Label("KATEGORİYİ AÇ (${preset.groupTitle})", 9, Color(0xFFFFD735), bold = true) }
                ) { s.filterByCountry(preset.code) }
                Action(
                    label = "${preset.name} Kaynak Olarak Yükle",
                    modifier = Modifier.weight(1f).height(34.dp),
                    tint = Color(0xFF1A2D4F),
                    radius = 8.dp,
                    strokeWidth = 0.dp,
                    enabled = !s.busy,
                    content = { Label(if (isTr) "VARSAYILANA DÖN" else "TEK BAŞINA YÜKLE", 9, Color(0xFF86C9EF), bold = true) }
                ) {
                    if (isTr) s.dialog = "defaultSource" else s.activateCountrySource(preset.code)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ActiveAndSavedSourcesSection(s: PreviewState, image: @Composable (String, Modifier) -> Unit, showHeader: Boolean) {
    if (showHeader) {
        Spacer(Modifier.height(18.dp))
        Label(s.message("AKTİF ANA KAYNAK", "ACTIVE MAIN SOURCE", "AKTIVE HAUPTQUELLE"), 10, Color(0xFF94A2B7), bold = true, letterSpacing = 1f)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF13233D), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE67E22), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRowIcon("activesources", color = Color(0xFFE67E22), modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(s.profileName, 12, Color.White, bold = true, maxLines = 1)
                    Box(Modifier.background(Color(0xFF0ABB91), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Label(s.activeSourceKindBadge, 8, Color.White, bold = true)
                    }
                }
                val mainAddr = s.data.library?.main?.maskedAddress() ?: "Yerleşik Türkiye Kanal Listesi (TiviListe.m3u)"
                Label("$mainAddr · ${s.activeCount} aktif içerik", 9, Color(0xFF94A2B7), maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(
                label = "Kaynağı Yeniden Yükle",
                modifier = Modifier.weight(1f).height(34.dp),
                tint = Color(0xFF1E3557),
                radius = 8.dp,
                strokeWidth = 0.dp,
                enabled = !s.busy,
                content = { Label("GÜNCELLE / YENİDEN YÜKLE", 9, Color(0xFFFFD735), bold = true) },
                click = s::refreshSource
            )
            Action(
                label = "Varsayılan Kaynağa Dön",
                modifier = Modifier.weight(1f).height(34.dp),
                tint = Color(0xFF1A2D4F),
                radius = 8.dp,
                strokeWidth = 0.dp,
                enabled = !s.busy,
                content = { Label("VARSAYILANA DÖN", 9, Color(0xFF86C9EF), bold = true) }
            ) { s.dialog = "defaultSource" }
        }
    }

    Spacer(Modifier.height(16.dp))
    Label(s.message("KAYITLI LİSTELERİM", "MY SAVED LISTS", "GESPEICHERTE LISTEN") + " (${s.savedSources.size})", 10, Color(0xFF94A2B7), bold = true, letterSpacing = 1f)
    if (s.savedSources.isEmpty()) {
        Box(Modifier.fillMaxWidth().background(Color(0xFF13233D), RoundedCornerShape(10.dp)).padding(16.dp)) {
            Label(s.message("Henüz kayıtlı harici kaynak yok. M3U URL, Xtream, Stalker veya Ülke Listesi ekleyebilirsiniz.", "No saved external sources yet.", "Noch keine gespeicherten externen Quellen."), 10, Color(0xFF94A2B7))
        }
    }
    s.savedSources.forEachIndexed { i, source ->
        val name = source.name
        val isActive = s.data.library?.active == source.id
        val iconName = when (source.kind) {
            SourceKind.XTREAM -> "ikon_xtream"
            SourceKind.STALKER -> "ikon_sunucu"
            SourceKind.FILE -> "ikon_dosya"
            SourceKind.COUNTRY -> "kategori_dunya"
            else -> "ikon_tv"
        }
        Action(name, Modifier.fillMaxWidth().height(60.dp), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 16.dp), content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsRowIcon(iconName, color = if (isActive) Color(0xFF0ABB91) else Color(0xFF94A2B7), modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(name, 12, bold = true, maxLines = 1)
                        Box(Modifier.background(if (isActive) Color(0xFF0ABB91) else Color(0xFF223858), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Label(if (isActive) "${source.kindBadge()} · AKTİF" else source.kindBadge(), 8, Color.White, bold = true)
                        }
                    }
                    Label("${source.maskedAddress()} · ${source.rows.size} kanal", 9, Color(0xFF94A2B7), maxLines = 1)
                }
                MenuChevronIcon(color = Color(0xFF8CA0BA), modifier = Modifier.size(16.dp))
            }
        }) { s.editing = i; s.dialog = "sourceMenu" }
    }
}

