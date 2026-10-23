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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omer.belestiviclone.shared.diagnostics.DiagnosticsReport

private val dialogBg = Color(0xFF13233D)
private val cardBorder = Color(0xFF223858)
private val amber = Color(0xFFE67E22)
private val successGreen = Color(0xFF0ABB91)
private val errorRed = Color(0xFFF44747)

@Composable
fun NetworkDiagnosticsDialog(
    s: PreviewState,
    image: @Composable (String, Modifier) -> Unit
) {
    val retryRequester = remember { FocusRequester() }
    val contentDensity = LocalDensity.current

    LaunchedEffect(Unit) {
        s.runNetworkDiagnostics()
    }

    val report = s.diagnosticsReport

    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Box(
                Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .background(dialogBg, RoundedCornerShape(14.dp))
                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                    .padding(22.dp)
                    .semantics { paneTitle = "Bağlantıyı Kontrol Et" }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            image("ikon_canli", Modifier.size(24.dp))
                            Column {
                                Label("Bağlantıyı Kontrol Et", 16, bold = true)
                                Label("Ağ, DNS ve canlı yayın erişim durumu", 10, Color(0xFF94A2B7))
                            }
                        }
                        Action("×", Modifier.size(32.dp), tint = Color.Transparent, strokeWidth = 0.dp, click = s::dismiss)
                    }

                    // Status Cards
                    DiagnosticItemCard(
                        title = "1. İnternet Bağlantısı",
                        statusText = if (report.network.isConnected) "Bağlı (${report.network.typeName})" else if (report.inProgress) "Kontrol ediliyor…" else "Bağlantı Yok",
                        detailText = if (report.network.localIp.isNotBlank()) "Yerel IP: ${report.network.localIp}" else report.network.details,
                        isSuccess = report.network.isConnected,
                        inProgress = report.inProgress && !report.network.isConnected,
                        latencyMs = -1
                    )

                    DiagnosticItemCard(
                        title = "2. DNS Çözümleme Testi",
                        statusText = if (report.dns.isSuccess) "Başarılı" else if (report.inProgress) "Sorgulanıyor…" else "Çözümlenemedi",
                        detailText = if (report.dns.isSuccess) "${report.dns.host} → ${report.dns.resolvedIp.orEmpty()}" else report.dns.error ?: "Sorgu bekleniyor",
                        isSuccess = report.dns.isSuccess,
                        inProgress = report.inProgress && report.network.isConnected && !report.dns.isSuccess,
                        latencyMs = if (report.dns.isSuccess) report.dns.latencyMs else -1
                    )

                    DiagnosticItemCard(
                        title = "3. Canlı Yayın Sunucusu Testi",
                        statusText = if (report.stream.isSuccess) "Erişilebilir (HTTP ${report.stream.httpCode})" else if (report.inProgress) "Bağlantı deneniyor…" else "Erişilemedi",
                        detailText = if (report.stream.isSuccess) "Canlı akış yanıt veriyor" else report.stream.error ?: "Akış testi bekleniyor",
                        isSuccess = report.stream.isSuccess,
                        inProgress = report.inProgress && report.dns.isSuccess && !report.stream.isSuccess,
                        latencyMs = if (report.stream.isSuccess) report.stream.latencyMs else -1
                    )

                    // Overall Summary Badge
                    if (!report.inProgress) {
                        val overallSuccess = report.isOverallSuccess
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (overallSuccess) Color(0xFF0D2821) else Color(0xFF2C1318), RoundedCornerShape(8.dp))
                                .border(1.dp, if (overallSuccess) successGreen.copy(alpha = 0.5f) else errorRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Label(if (overallSuccess) "✅" else "⚠️", 14)
                            Label(
                                if (overallSuccess) "Tüm bağlantı testleri başarılı. Yayın akışı sorunsuz çalışacaktır."
                                else "Bazı testler başarısız oldu. Ağ bağlantınızı veya kaynak ayarlarınızı kontrol edin.",
                                11,
                                if (overallSuccess) successGreen else errorRed,
                                bold = true
                            )
                        }
                    }

                    // Bottom Buttons
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Action(
                            label = if (report.inProgress) "TEST EDİLİYOR…" else "YENİDEN TEST ET",
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .focusRequester(retryRequester),
                            enabled = !report.inProgress,
                            tint = amber,
                            radius = 8.dp,
                            strokeWidth = 0.dp,
                            content = {
                                Label(
                                    if (report.inProgress) "TEST EDİLİYOR…" else "YENİDEN TEST ET",
                                    11,
                                    Color.White,
                                    bold = true
                                )
                            },
                            click = s::runNetworkDiagnostics
                        )
                        Action(
                            label = "KAPAT",
                            modifier = Modifier
                                .width(90.dp)
                                .height(40.dp),
                            tint = Color(0xFF1E3557),
                            radius = 8.dp,
                            strokeWidth = 0.dp,
                            content = { Label("KAPAT", 11, Color.White, bold = true) },
                            click = s::dismiss
                        )
                    }
                }
            }
            LaunchedEffect(Unit) {
                retryRequester.requestFocus()
            }
        }
    }
}

@Composable
private fun DiagnosticItemCard(
    title: String,
    statusText: String,
    detailText: String,
    isSuccess: Boolean,
    inProgress: Boolean,
    latencyMs: Long
) {
    val indicatorColor = when {
        inProgress -> amber
        isSuccess -> successGreen
        else -> errorRed
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A1628), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E3557), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(indicatorColor.copy(alpha = 0.15f), RoundedCornerShape(15.dp))
                .border(1.5.dp, indicatorColor, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Label(
                when {
                    inProgress -> "⏳"
                    isSuccess -> "✓"
                    else -> "✗"
                },
                13,
                indicatorColor,
                bold = true
            )
        }

        Column(Modifier.weight(1f)) {
            Label(title, 11, Color(0xFF94A2B7), bold = true)
            Spacer(Modifier.height(2.dp))
            Label(statusText, 12, Color.White, bold = true)
            if (detailText.isNotBlank()) {
                Label(detailText, 10, Color(0xFF6A7B95))
            }
        }

        if (latencyMs >= 0) {
            Box(
                Modifier
                    .background(Color(0xFF13233D), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Label("$latencyMs ms", 10, amber, bold = true)
            }
        }
    }
}
