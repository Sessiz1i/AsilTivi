package com.omer.belestiviclone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.provider.OpenableColumns
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.sharedui.PreviewState
import com.omer.belestiviclone.sharedui.ToastTone
import kotlinx.coroutines.*

/** SAF grants are used only to read the selected document once. The parsed copy lives in Room. */
@Composable
internal fun DocumentBridge(state: PreviewState) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf(state.data.profileId) }
    var exportText by remember { mutableStateOf("") }
    fun valid(kind: String) = pending == kind && state.data.profileId == profile &&
        (if (kind == "file") state.dialog == kind else state.dialog == null)
    fun failed() { state.notice = state.message("Dosya okunamadı/yazılamadı; uygulama verileri korundu.", "File read/write failed; app data preserved.", "Datei konnte nicht gelesen/geschrieben werden; Daten beibehalten.") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        state.exitRequested = false
        val kind = pending
        if (kind != null && valid(kind)) {
            if (uri == null) {
                pending = null
                state.documentCanceled()
                if (state.page != "settings") state.page = "settings"
            } else scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val text = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.boundedText(if (kind == "import") FavoriteBackup.maxBytes else 4 * 1024 * 1024) }
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "M3U"
                        name to text
                    }
                    if (valid(kind)) { if (kind == "file") state.importM3u(result.first, result.second) else state.importFavorites(result.second) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (valid(kind)) failed() }
                finally { pending = null }
            }
        } else pending = null
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        state.exitRequested = false
        if (valid("export")) {
            if (uri == null) {
                pending = null
                state.documentCanceled()
                if (state.page != "settings") state.page = "settings"
            } else scope.launch {
                try {
                    val bytes = exportText.encodeToByteArray()
                    withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(bytes); it.flush() } }
                    if (valid("export")) { state.dismiss(); state.notice = state.message("Favori yedeği yazıldı.", "Favorite backup written.", "Favoritensicherung geschrieben.") }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (valid("export")) failed() }
                finally { pending = null; exportText = "" }
            }
        } else pending = null
    }
    LaunchedEffect(state.documentRequest) {
        val request = state.documentRequest ?: return@LaunchedEffect
        state.documentLaunched()
        if (request == "cache") {
            clearPreviewLogoCache()
            state.dismiss()
            state.notice = state.message("Logo önbelleği temizlendi; kaynak ve tercihler korundu.", "Logo cache cleared; source and preferences preserved.", "Logo-Cache geleert; Quelle und Einstellungen beibehalten.")
            return@LaunchedEffect
        }
        if (pending != null) return@LaunchedEffect
        pending = request; profile = state.data.profileId
        // A native Compose Dialog must be disposed before SAF takes the window. Otherwise a
        // rejected/canceled result can leave an invisible modal owning focus after rotation.
        if (request == "import" || request == "export") state.dialog = null
        try {
            if (request == "export") {
                exportText = FavoriteBackup.encode(state.data)
                create.launch("beles-clone-favoriler-${System.currentTimeMillis()}.json")
            } else open.launch(if (request == "file") arrayOf("*/*") else arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: Exception) {
            pending = null
            state.documentLaunched()
            state.showBottomToast("Dosya seçici açılamadı", ToastTone.Warning)
            failed()
        }
    }
}
