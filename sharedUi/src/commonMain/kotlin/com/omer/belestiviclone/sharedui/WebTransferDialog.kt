package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omer.belestiviclone.shared.transfer.QrMatrix

private val dialogBg = Color(0xFF13233D)
private val cardBorder = Color(0xFF223858)
private val amber = Color(0xFFE67E22)
private val successGreen = Color(0xFF0ABB91)

@Composable
fun WebTransferDialog(
    s: PreviewState,
    image: @Composable (String, Modifier) -> Unit
) {
    val closeRequester = remember { FocusRequester() }
    val contentDensity = LocalDensity.current

    // Start server when dialog opens, stop when disposed
    DisposableEffect(Unit) {
        s.startWebTransferServer()
        onDispose {
            s.stopWebTransferServer()
        }
    }

    val info = s.webTransferInfo
    val qrMatrix = remember(info?.url) {
        if (info != null && info.isRunning && info.url.isNotBlank()) {
            try {
                QrMatrix.generate(info.url)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Dialog(onDismissRequest = s::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalDensity provides contentDensity) {
            Box(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .background(dialogBg, RoundedCornerShape(14.dp))
                    .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                    .padding(24.dp)
                    .semantics { paneTitle = "Telefondan Aktar" }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            image("ikon_export", Modifier.size(24.dp))
                            Column {
                                Label("Telefondan Aktar", 16, bold = true)
                                Label("Yerel Wi-Fi üzerinden kolay aktarım", 10, Color(0xFF94A2B7))
                            }
                        }
                        Action("×", Modifier.size(32.dp), tint = Color.Transparent, strokeWidth = 0.dp, click = s::dismiss)
                    }

                    if (info == null || !info.isRunning) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A1628), RoundedCornerShape(10.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Label("⚠️ Yerel Ağ Sunucusu Başlatılamadı", 13, Color(0xFFF44747), bold = true)
                            Spacer(Modifier.height(6.dp))
                            Label(
                                info?.error ?: "Lütfen cihazınızın bir Wi-Fi veya Ethernet ağına bağlı olduğundan emin olun.",
                                11,
                                Color(0xFF94A2B7),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        // QR Code display
                        Box(
                            Modifier
                                .size(176.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrMatrix != null) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val matrixSize = qrMatrix.size
                                    val cellSize = size.minDimension / matrixSize
                                    val offsetX = (size.width - cellSize * matrixSize) / 2f
                                    val offsetY = (size.height - cellSize * matrixSize) / 2f

                                    for (r in 0 until matrixSize) {
                                        for (c in 0 until matrixSize) {
                                            if (qrMatrix[r][c]) {
                                                drawRect(
                                                    color = Color.Black,
                                                    topLeft = Offset(offsetX + c * cellSize, offsetY + r * cellSize),
                                                    size = Size(cellSize + 0.5f, cellSize + 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Label("QR Kod", 12, Color.Black)
                            }
                        }

                        // URL display
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A1628), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF1E3557), RoundedCornerShape(10.dp))
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Label("Telefondan bu adrese gidin veya QR kodu tarayın:", 10, Color(0xFF94A2B7))
                            Spacer(Modifier.height(4.dp))
                            Label(info.url, 15, amber, bold = true)
                        }

                        // Real-time transfer status
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0E1A2C), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(if (s.lastTransferSuccess != null) successGreen else amber, RoundedCornerShape(4.dp))
                            )
                            Label(
                                s.lastTransferSuccess?.let { "✅ $it" } ?: "Telefondan veri gönderimi bekleniyor…",
                                11,
                                if (s.lastTransferSuccess != null) successGreen else Color(0xFFC5D2E5),
                                maxLines = 2
                            )
                        }

                        // Instructions
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Label("• Telefonunuz TV ile aynı Wi-Fi veya yerel ağda olmalıdır.", 10, Color(0xFF7E8EA6))
                            Label("• Açılan web sayfasından M3U, Xtream veya Stalker bilgisi girebilirsiniz.", 10, Color(0xFF7E8EA6))
                            Label("• TV'ye Gönder butonuna bastığınızda liste anında televizyona eklenecektir.", 10, Color(0xFF7E8EA6))
                        }
                    }

                    // Action buttons
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Action(
                            label = "KAPAT",
                            modifier = Modifier
                                .width(100.dp)
                                .height(38.dp)
                                .focusRequester(closeRequester),
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
                closeRequester.requestFocus()
            }
        }
    }
}
