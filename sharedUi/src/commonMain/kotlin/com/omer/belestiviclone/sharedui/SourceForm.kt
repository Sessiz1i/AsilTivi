package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val sourceFormKinds = setOf("m3u", "supplement", "xtream", "file")

/** Same settings shell and footer; back drops the secret draft through PreviewState.dismiss. */
@Composable
internal fun SourceForm(s: PreviewState, image: @Composable (String, Modifier) -> Unit, modifier: Modifier) {
    val kind = s.dialog ?: return
    val back = remember(kind) { FocusRequester() }
    val title = when (kind) {
        "xtream" -> s.message("XTREAM BİLGİLERİ", "XTREAM DETAILS", "XTREAM-ZUGANGSDATEN")
        "supplement" -> s.message("Ek Liste", "Extra List", "Zusatzliste")
        "file" -> s.message("Cihazdan M3U Dosyası Yükle", "Load M3U File", "M3U-Datei laden")
        else -> s.message(if (s.editing == null) "YENİ LİSTE EKLE" else "LİSTEYİ DÜZENLE", if (s.editing == null) "ADD NEW LIST" else "EDIT LIST", if (s.editing == null) "NEUE LISTE" else "LISTE BEARBEITEN")
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 19.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Action(s.message("Kaynak", "Source", "Quelle") + " › $title · " + s.message("Geri", "Back", "Zurück"), Modifier.fillMaxWidth().height(45.dp).focusRequester(back), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 20.dp), content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Label("←", 12, Color(0xFFE67E22)); Label(s.message("Kaynak ›", "Source ›", "Quelle ›"), 10, Color(0xFF69798F), bold = true); Label(title, 10, bold = true, maxLines = 1)
            }
        }, click = s::dismiss)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.height(26.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(16.dp).background(Color(0xFFE67E22)))
            Label(if (kind == "file") s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN") else title, 10, Color(0xFF94A2B7), Modifier.padding(start = 10.dp), true, letterSpacing = 1f)
        }
        if (kind == "file") {
            Column(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF13233D)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                image("ikon_dosya", Modifier.size(40.dp, 29.dp)); Label(title, 12, bold = true)
                Label(s.message("Seçilen M3U uygulamaya kopyalanır; dış dosyadan bağımsızdır.", "The selected M3U is copied into the app.", "Die gewählte M3U wird in die App kopiert."), 10, Color(0xFF94A2B7))
            }
        } else {
            if (kind != "xtream") Field(s.formName, s.message("LİSTE ADI", "LIST NAME", "LISTENNAME"), "ikon_liste_adi", image, placeholder = s.message("Örn: Evdeki TV, Yedek Liste", "Example: Home TV, Backup List", "Beispiel: TV zu Hause, Ersatzliste")) { s.formName = it }
            if (!s.editingFile) Field(s.formAddress, if (kind == "xtream") s.message("SUNUCU ADRESİ", "SERVER ADDRESS", "SERVERADRESSE") else "M3U URL", if (kind == "xtream") "ikon_sunucu" else "ikon_url", image, placeholder = if (kind == "xtream") "http://ornek-sunucu.com:8080" else s.message("http://... veya https://...", "http://... or https://...", "http://... oder https://...")) { s.formAddress = it }
            if (kind == "xtream") {
                Field(s.formUser, s.message("KULLANICI ADI", "USERNAME", "BENUTZERNAME"), "ikon_kullanici", image, placeholder = s.message("Kullanıcı adınız", "Your username", "Ihr Benutzername")) { s.formUser = it }
                Field(s.formPassword, s.message("ŞİFRE", "PASSWORD", "PASSWORT"), "ikon_sifre", image, password = true, placeholder = s.message("Şifreniz", "Your password", "Ihr Passwort")) { s.formPassword = it }
            }
        }
        Spacer(Modifier.height(9.dp))
        Action(if (kind == "file") s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN") else if (kind == "xtream") s.message("BAĞLAN", "CONNECT", "VERBINDEN") else s.message("KAYDET VE YÜKLE", "SAVE AND LOAD", "SPEICHERN UND LADEN"), Modifier.fillMaxWidth().height(42.dp), tint = Color(0xFFE67E22), strokeWidth = 0.dp, enabled = !s.busy, content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (kind == "xtream") Label("🚀", 12) else image(if (kind == "file") "ikon_dosya" else "ikon_kaydet", Modifier.size(12.dp, 13.dp))
                Label(if (kind == "file") s.message("DOSYA SEÇ", "CHOOSE FILE", "DATEI WÄHLEN") else if (kind == "xtream") s.message("BAĞLAN", "CONNECT", "VERBINDEN") else s.message("KAYDET VE YÜKLE", "SAVE AND LOAD", "SPEICHERN UND LADEN"), 11, bold = true)
            }
        }) {
            when (kind) {
                "file" -> s.requestDocument("file")
                "xtream" -> s.notice = s.message("Hesap bağlantısı bağlı değil; bilgiler saklanmaz.", "Account connection unavailable; credentials are not stored.", "Kontoverbindung nicht verfügbar; Daten werden nicht gespeichert.")
                else -> if (kind == "supplement" && s.data.library?.extra != null) s.dialog = "replaceExtra" else s.saveSource()
            }
        }
        Label(if (kind == "xtream") s.message("Hesap bağlantısı bağlı değil; bilgiler saklanmaz.", "Account connection unavailable; credentials are not stored.", "Kontoverbindung nicht verfügbar; Daten werden nicht gespeichert.") else s.message("Başarıyla yüklenip kaydedilene kadar önceki kaynak korunur.", "Previous source is preserved until loading and saving succeed.", "Vorherige Quelle bleibt bis zum erfolgreichen Speichern erhalten."), 10, Color(0xFF94A2B7), Modifier.padding(top = 12.dp))
        if (s.busy) Action(s.message("İPTAL", "CANCEL", "ABBRECHEN"), Modifier.fillMaxWidth(), enabled = !s.committingSource, click = s::cancelSource)
        if (kind == "supplement" && s.data.library?.extra != null) Action(s.message("EK LİSTEYİ KALDIR", "REMOVE EXTRA LIST", "ZUSATZLISTE ENTFERNEN"), Modifier.fillMaxWidth(), enabled = !s.busy) { s.dialog = "removeExtra" }
        if (kind == "m3u") {
            Spacer(Modifier.height(23.dp)); Label(s.message("KAYITLI LİSTELERİM", "MY SAVED LISTS", "GESPEICHERTE LISTEN") + " (${s.savedSources.size})", 10, Color(0xFF94A2B7), bold = true, letterSpacing = 1f)
            s.savedSources.forEachIndexed { i, source ->
                val name = source.name
                Action(name, Modifier.fillMaxWidth().height(58.dp), strokeWidth = 0.dp, padding = PaddingValues(horizontal = 20.dp), content = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        image("ikon_tv", Modifier.size(21.dp, 23.dp)); Column(Modifier.weight(1f)) { Label(name, 12, bold = true, maxLines = 1); Label(if (source.kind == com.omer.belestiviclone.shared.preview.SourceKind.FILE) s.message("Uygulamaya alınmış dosya kopyası", "Imported file copy", "Importierte Dateikopie") else source.address.replace(Regex("//[^/@]*@"), "//***@"), 9, Color(0xFF94A2B7), maxLines = 1) }; Label("›", 12, Color(0xFF60728E))
                    }
                }) { s.editing = i; s.dialog = "sourceMenu" }
            }
        }
    }
    LaunchedEffect(kind) { back.requestFocus() }
}
