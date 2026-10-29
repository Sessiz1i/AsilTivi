package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Faz D2: Modern temalarda üst araç çubuğu, radyo ve oynatıcı kontrolleri için
 * minimalist, monokrom vektörel Canvas ikonları.
 */
@Composable
internal fun TopBarActionIcon(
    icon: String,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(14.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.35f.dp.toPx()

        when (icon.lowercase()) {
            "ikon_audio", "audio", "radyo" -> {
                // Radyo: Gövde, anten ve ayar düğmesi
                val rw = w * 0.85f
                val rh = h * 0.58f
                val left = (w - rw) / 2f
                val top = h * 0.34f
                // Anten
                drawLine(
                    color = color,
                    start = Offset(left + rw * 0.20f, top),
                    end = Offset(w * 0.82f, h * 0.10f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // Gövde
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(stroke)
                )
                // Kadran daire
                drawCircle(
                    color = color,
                    radius = rh * 0.26f,
                    center = Offset(left + rw * 0.35f, top + rh * 0.50f),
                    style = Stroke(stroke * 0.85f)
                )
                // Frekans çizgileri
                drawLine(
                    color = color,
                    start = Offset(left + rw * 0.65f, top + rh * 0.36f),
                    end = Offset(left + rw * 0.84f, top + rh * 0.36f),
                    strokeWidth = stroke * 0.85f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(left + rw * 0.65f, top + rh * 0.64f),
                    end = Offset(left + rw * 0.84f, top + rh * 0.64f),
                    strokeWidth = stroke * 0.85f,
                    cap = StrokeCap.Round
                )
            }
            "ikon_coklu", "coklu", "multi" -> {
                // Çoklu Ekran: 2x2 pencere ızgarası
                val gap = 1.6f.dp.toPx()
                val cellW = (w * 0.88f - gap) / 2f
                val cellH = (h * 0.74f - gap) / 2f
                val startX = (w - (cellW * 2 + gap)) / 2f
                val startY = (h - (cellH * 2 + gap)) / 2f

                for (r in 0..1) {
                    for (c in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(startX + c * (cellW + gap), startY + r * (cellH + gap)),
                            size = Size(cellW, cellH),
                            cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
                            style = Stroke(stroke * 0.9f)
                        )
                    }
                }
            }
            "ikon_format", "format" -> {
                // Görüntü Oranı: 16:9 geniş ekran çerçevesi ve köşe işaretleri
                val rw = w * 0.88f
                val rh = h * 0.60f
                val left = (w - rw) / 2f
                val top = (h - rh) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(rw, rh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(stroke)
                )
                // İç oran genişleme okları / merkez vizör
                val cx = w / 2f
                val cy = h / 2f
                drawLine(color, Offset(cx - rw * 0.22f, cy), Offset(cx + rw * 0.22f, cy), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy - rh * 0.25f), Offset(cx, cy + rh * 0.25f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }
            "ikon_saat", "bilgi", "info" -> {
                // Bilgi: Daire içinde 'i'
                val r = w * 0.44f
                val center = Offset(w / 2f, h / 2f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                // 'i' noktası
                drawCircle(color = color, radius = stroke * 0.65f, center = Offset(w / 2f, h * 0.32f))
                // 'i' gövdesi
                drawLine(
                    color = color,
                    start = Offset(w / 2f, h * 0.46f),
                    end = Offset(w / 2f, h * 0.72f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            "ikon_ayar", "ayar", "ayarlar", "settings" -> {
                // Ayarlar: Minimalist dişli çark
                val cx = w / 2f
                val cy = h / 2f
                val innerR = w * 0.20f
                val outerR = w * 0.40f
                drawCircle(color = color, radius = innerR, center = Offset(cx, cy), style = Stroke(stroke * 0.9f))
                for (i in 0 until 6) {
                    val angle = (i * 60f) * (kotlin.math.PI / 180.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color = color,
                        start = Offset(cx + (innerR + 1.dp.toPx()) * cos, cy + (innerR + 1.dp.toPx()) * sin),
                        end = Offset(cx + outerR * cos, cy + outerR * sin),
                        strokeWidth = stroke * 1.1f,
                        cap = StrokeCap.Round
                    )
                }
            }
            "ikon_cikis", "cikis", "exit" -> {
                // Çıkış: Kapı + dışarı bakan ok
                val left = w * 0.16f
                val top = h * 0.15f
                val dw = w * 0.44f
                val dh = h * 0.70f
                // Kapı çerçevesi (üst, sol, alt)
                val doorPath = Path().apply {
                    moveTo(left + dw, top)
                    lineTo(left, top)
                    lineTo(left, top + dh)
                    lineTo(left + dw, top + dh)
                }
                drawPath(doorPath, color = color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Dışarı çıkan ok
                val arrowY = h / 2f
                val arrowStart = left + dw * 0.45f
                val arrowEnd = w * 0.88f
                drawLine(color = color, start = Offset(arrowStart, arrowY), end = Offset(arrowEnd, arrowY), strokeWidth = stroke, cap = StrokeCap.Round)
                val headSize = w * 0.18f
                drawLine(color = color, start = Offset(arrowEnd - headSize, arrowY - headSize), end = Offset(arrowEnd, arrowY), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(arrowEnd - headSize, arrowY + headSize), end = Offset(arrowEnd, arrowY), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "kategori_ara", "ara", "search" -> {
                // Büyüteç
                val r = w * 0.32f
                val cx = w * 0.40f
                val cy = h * 0.40f
                drawCircle(color, radius = r, center = Offset(cx, cy), style = Stroke(stroke))
                val cos45 = 0.7071f
                drawLine(
                    color,
                    start = Offset(cx + r * cos45, cy + r * cos45),
                    end = Offset(w * 0.90f, h * 0.90f),
                    strokeWidth = stroke * 1.25f,
                    cap = StrokeCap.Round
                )
            }
            "ikon_tv", "tv" -> {
                // TV Monitörü
                val sw = w * 0.86f
                val sh = h * 0.60f
                val left = (w - sw) / 2f
                val top = h * 0.14f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(sw, sh),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(stroke)
                )
                // Ayaklar
                val stemTop = top + sh
                val stemBottom = stemTop + h * 0.14f
                drawLine(color, Offset(w / 2f, stemTop), Offset(w / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
                val baseW = w * 0.45f
                drawLine(color, Offset(w / 2f - baseW / 2f, stemBottom), Offset(w / 2f + baseW / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "dim", "karart" -> {
                // Hilal Ay
                val path = Path().apply {
                    val r = w * 0.42f
                    val cx = w * 0.52f
                    val cy = h * 0.50f
                    moveTo(cx, cy - r)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 180f,
                        forceMoveTo = false
                    )
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(cx - r * 0.6f, cy - r, cx + r * 1.4f, cy + r),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = -180f,
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(path, color = color)
            }
            else -> {
                val rw = w * 0.82f
                val rh = h * 0.62f
                drawRoundRect(color, topLeft = Offset((w - rw) / 2f, (h - rh) / 2f), size = Size(rw, rh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
            }
        }
    }
}

/**
 * Canlı TV ve Radyo oynatıcı ses butonu ikonu: Ses Açık (hoparlör + ses dalgaları) / Sessiz (çapraz çizgili)
 */
@Composable
internal fun PlayerAudioIcon(
    isMuted: Boolean,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(12.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.25f.dp.toPx()

        // Hoparlör gövdesi (kare + koni)
        val bodyPath = Path().apply {
            val startX = w * 0.12f
            val endX = w * 0.46f
            val midY = h * 0.50f
            val bodyH = h * 0.32f
            val coneH = h * 0.70f

            moveTo(startX, midY - bodyH / 2f)
            lineTo(startX + (endX - startX) * 0.45f, midY - bodyH / 2f)
            lineTo(endX, midY - coneH / 2f)
            lineTo(endX, midY + coneH / 2f)
            lineTo(startX + (endX - startX) * 0.45f, midY + bodyH / 2f)
            lineTo(startX, midY + bodyH / 2f)
            close()
        }
        drawPath(bodyPath, color = color)

        if (isMuted) {
            // Sessiz: Küçük çapraz x
            val xCenter = w * 0.74f
            val yCenter = h * 0.50f
            val size = w * 0.16f
            drawLine(color, Offset(xCenter - size, yCenter - size), Offset(xCenter + size, yCenter + size), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
            drawLine(color, Offset(xCenter - size, yCenter + size), Offset(xCenter + size, yCenter - size), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
        } else {
            // Ses Açık: 2 ses dalgası yayı
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.36f, h * 0.28f),
                size = Size(w * 0.34f, h * 0.44f),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(w * 0.44f, h * 0.16f),
                size = Size(w * 0.50f, h * 0.68f),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Canlı TV ve Radyo oynatıcı buton ikonu: Oynat (Play üçgeni) / Duraklat (Pause iki çizgi)
 */
@Composable
internal fun PlayerPlayPauseIcon(
    isPaused: Boolean,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(11.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        if (isPaused) {
            // Oynat: Sağa bakan üçgen (dolu)
            val path = Path().apply {
                val padX = w * 0.18f
                val padY = h * 0.14f
                moveTo(padX, padY)
                lineTo(w - padX * 0.8f, h / 2f)
                lineTo(padX, h - padY)
                close()
            }
            drawPath(path = path, color = color)
        } else {
            // Duraklat: İki dikey sütun
            val barW = w * 0.24f
            val barH = h * 0.72f
            val top = (h - barH) / 2f
            val spacing = w * 0.20f
            val startX = (w - (barW * 2 + spacing)) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + barW + spacing, top),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

/**
 * Canlı TV'de canlı yayına dön (⏩) veya radyo yenileme ikonu
 */
@Composable
internal fun PlayerRetryIcon(
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(11.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.25f.dp.toPx()

        val r = w * 0.38f
        val center = Offset(w / 2f, h / 2f)
        drawArc(
            color = color,
            startAngle = 45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        // Ok ucu
        val arrowHead = Path().apply {
            val arrowX = center.x + r * kotlin.math.cos(45f * kotlin.math.PI / 180f).toFloat()
            val arrowY = center.y + r * kotlin.math.sin(45f * kotlin.math.PI / 180f).toFloat()
            moveTo(arrowX, arrowY - stroke * 2.2f)
            lineTo(arrowX + stroke * 1.5f, arrowY)
            lineTo(arrowX - stroke * 1.5f, arrowY)
            close()
        }
        drawPath(arrowHead, color = color)
    }
}

/**
 * Faz D2 (Madde 10 & 11): Ayarlar ekranının sağ panelindeki 7 sekmenin tamamında yer alan
 * tüm satır ve kartlar için minimalist, monokrom, vektörel Canvas ikonları.
 */
@Composable
internal fun SettingsRowIcon(
    key: String,
    color: Color = Color.White,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.45f.dp.toPx()

        when (key.lowercase()) {
            // --- GÖRÜNÜM SEKMESİ (section = 1) ---
            "theme", "ikon_gorunum" -> {
                // Uygulama Teması: Palet
                val r = w * 0.42f
                val center = Offset(w / 2f, h / 2f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                val dotR = 1.4.dp.toPx()
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.35f, h * 0.38f))
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.65f, h * 0.38f))
                drawCircle(color = color, radius = dotR, center = Offset(w * 0.50f, h * 0.65f))
            }
            "radiobg" -> {
                // Radyo Arka Planı: Ses dalgaları / Işıma
                val cx = w / 2f
                val cy = h * 0.65f
                drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(cx, cy))
                drawArc(color = color, startAngle = -150f, sweepAngle = 120f, useCenter = false, topLeft = Offset(w * 0.28f, h * 0.38f), size = Size(w * 0.44f, h * 0.44f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color = color, startAngle = -155f, sweepAngle = 130f, useCenter = false, topLeft = Offset(w * 0.14f, h * 0.20f), size = Size(w * 0.72f, h * 0.72f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            "list", "ikon_list" -> {
                // Liste Konumu / Liste: Yan panel + liste çizgileri
                val rw = w * 0.84f
                val rh = h * 0.72f
                val left = (w - rw) / 2f
                val top = (h - rh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(rw, rh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Sol dikey bölme
                drawLine(color = color, start = Offset(left + rw * 0.35f, top), end = Offset(left + rw * 0.35f, top + rh), strokeWidth = stroke)
                // Sağ yatay satırlar
                drawLine(color = color, start = Offset(left + rw * 0.48f, top + rh * 0.30f), end = Offset(left + rw * 0.86f, top + rh * 0.30f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + rw * 0.48f, top + rh * 0.65f), end = Offset(left + rw * 0.86f, top + rh * 0.65f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
            "opacity", "ikon_opacity", "subtitlebgopacity" -> {
                // Şeffaflık / Opaklık: Kesişen iki halka
                val r = w * 0.28f
                val cy = h / 2f
                drawCircle(color = color, radius = r, center = Offset(w * 0.40f, cy), style = Stroke(stroke))
                drawCircle(color = color, radius = r, center = Offset(w * 0.60f, cy), style = Stroke(stroke))
                // Kesişimde küçük dolgu çizgisi
                drawLine(color = color, start = Offset(w / 2f, cy - r * 0.5f), end = Offset(w / 2f, cy + r * 0.5f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
            "position", "ikon_position" -> {
                // Konum: Ekran ve içinde yerleşim kutusu
                val sw = w * 0.84f
                val sh = h * 0.64f
                val left = (w - sw) / 2f
                val top = (h - sh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(sw, sh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Alt-orta konum gösterge kutusu (dolu)
                val bx = left + sw * 0.30f
                val by = top + sh * 0.55f
                drawRoundRect(color = color, topLeft = Offset(bx, by), size = Size(sw * 0.40f, sh * 0.32f), cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()))
            }
            "size", "ikon_size" -> {
                // Boyut: Çift yönlü çapraz ok veya iç içe kareler
                val rw = w * 0.80f
                val rh = h * 0.80f
                val left = (w - rw) / 2f
                val top = (h - rh) / 2f
                // Dış köşe okları
                drawLine(color = color, start = Offset(left, top + rh * 0.40f), end = Offset(left, top), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left, top), end = Offset(left + rw * 0.40f, top), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + rw, top + rh * 0.60f), end = Offset(left + rw, top + rh), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + rw, top + rh), end = Offset(left + rw * 0.60f, top + rh), strokeWidth = stroke, cap = StrokeCap.Round)
                // Çapraz çizgi
                drawLine(color = color, start = Offset(left + 2.dp.toPx(), top + 2.dp.toPx()), end = Offset(left + rw - 2.dp.toPx(), top + rh - 2.dp.toPx()), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }

            // --- UYGULAMA SEKMESİ (section = 3) ---
            "language", "ikon_language" -> {
                // Dil: Dünya küresi
                val r = w * 0.42f
                val center = Offset(w / 2f, h / 2f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                drawLine(color = color, start = Offset(center.x - r, center.y), end = Offset(center.x + r, center.y), strokeWidth = stroke * 0.8f)
                drawOval(color = color, topLeft = Offset(w * 0.28f, center.y - r), size = Size(w * 0.44f, r * 2f), style = Stroke(stroke * 0.8f))
            }
            "startupfavorites", "kategori_favoriler" -> {
                // Favorilerden Başlat: Yıldız
                val cx = w / 2f
                val cy = h / 2f
                val outer = w * 0.44f
                val starPath = Path().apply {
                    moveTo(cx, cy - outer)
                    quadraticTo(cx, cy, cx + outer, cy)
                    quadraticTo(cx, cy, cx, cy + outer)
                    quadraticTo(cx, cy, cx - outer, cy)
                    quadraticTo(cx, cy, cx, cy - outer)
                    close()
                }
                drawPath(starPath, color = color)
            }
            "startup", "ikon_startup" -> {
                // Açılış Tercihi: Güç / Açma simgesi
                val r = w * 0.36f
                val center = Offset(w / 2f, h * 0.54f)
                drawArc(color = color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color = color, start = Offset(w / 2f, h * 0.16f), end = Offset(w / 2f, h * 0.52f), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
            }
            "backgroundaudio", "ikon_ses" -> {
                // Arka Planda Ses: Kulaklık
                val r = w * 0.36f
                val cx = w / 2f
                val cy = h * 0.46f
                drawArc(color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                // Kulaklık pedleri
                drawRoundRect(color = color, topLeft = Offset(cx - r - 1.dp.toPx(), cy), size = Size(w * 0.16f, h * 0.38f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                drawRoundRect(color = color, topLeft = Offset(cx + r - w * 0.16f + 1.dp.toPx(), cy), size = Size(w * 0.16f, h * 0.38f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
            }
            "autopip" -> {
                // PiP (Resim İçinde Resim): Büyük ekran içinde küçük ekran
                val bw = w * 0.86f
                val bh = h * 0.64f
                val left = (w - bw) / 2f
                val top = (h - bh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(bw, bh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Küçük iç pencere (dolu)
                val sw = bw * 0.42f
                val sh = bh * 0.44f
                val sLeft = left + bw - sw - 2.dp.toPx()
                val sTop = top + bh - sh - 2.dp.toPx()
                drawRoundRect(color = color, topLeft = Offset(sLeft, sTop), size = Size(sw, sh), cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()))
            }
            "history", "kategori_gecmis" -> {
                // İzleme Geçmişi: Geriye dönen saat oku
                val r = w * 0.38f
                val center = Offset(w / 2f, h / 2f)
                drawArc(color = color, startAngle = 30f, sweepAngle = 300f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                // Saat kolları
                drawLine(color = color, start = center, end = Offset(center.x, center.y - r * 0.60f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                drawLine(color = color, start = center, end = Offset(center.x + r * 0.50f, center.y), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
            }
            "clear", "ikon_cop" -> {
                // Geçmişi Temizle / Çöp Kutusu
                val topY = h * 0.22f
                val bottomY = h * 0.84f
                val boxW = w * 0.60f
                val leftX = (w - boxW) / 2f
                // Kapak
                drawLine(color = color, start = Offset(leftX - 2.dp.toPx(), topY), end = Offset(leftX + boxW + 2.dp.toPx(), topY), strokeWidth = stroke, cap = StrokeCap.Round)
                // Kulp
                drawArc(color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.40f, topY - 3.dp.toPx()), size = Size(w * 0.20f, 6.dp.toPx()), style = Stroke(stroke * 0.85f))
                // Gövde
                val canPath = Path().apply {
                    moveTo(leftX + 1.dp.toPx(), topY)
                    lineTo(leftX + 3.dp.toPx(), bottomY)
                    lineTo(leftX + boxW - 3.dp.toPx(), bottomY)
                    lineTo(leftX + boxW - 1.dp.toPx(), topY)
                }
                drawPath(canPath, color = color, style = Stroke(stroke, join = StrokeJoin.Round))
                // İç dikey çizgiler
                drawLine(color = color, start = Offset(w * 0.42f, topY + 4.dp.toPx()), end = Offset(w * 0.42f, bottomY - 3.dp.toPx()), strokeWidth = stroke * 0.8f)
                drawLine(color = color, start = Offset(w * 0.58f, topY + 4.dp.toPx()), end = Offset(w * 0.58f, bottomY - 3.dp.toPx()), strokeWidth = stroke * 0.8f)
            }

            // --- KANALLAR VE LİSTELER SEKMESİ (section = 0) ---
            "activesources", "ikon_tv" -> {
                // Aktif Listem: TV Monitörü
                val sw = w * 0.86f
                val sh = h * 0.60f
                val left = (w - sw) / 2f
                val top = h * 0.14f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(sw, sh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                val stemTop = top + sh
                val stemBottom = stemTop + h * 0.14f
                drawLine(color, Offset(w / 2f, stemTop), Offset(w / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
                val baseW = w * 0.45f
                drawLine(color, Offset(w / 2f - baseW / 2f, stemBottom), Offset(w / 2f + baseW / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "supplement" -> {
                // Ek Liste Ekle: Liste + Artı (+)
                val lw = w * 0.65f
                val lh = h * 0.74f
                val left = w * 0.14f
                val top = (h - lh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(lw, lh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                drawLine(color = color, start = Offset(left + lw * 0.20f, top + lh * 0.30f), end = Offset(left + lw * 0.80f, top + lh * 0.30f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + lw * 0.20f, top + lh * 0.55f), end = Offset(left + lw * 0.55f, top + lh * 0.55f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                // Artı işareti
                val plusCx = w * 0.76f
                val plusCy = h * 0.72f
                val plusSize = 4.dp.toPx()
                drawLine(color = color, start = Offset(plusCx - plusSize, plusCy), end = Offset(plusCx + plusSize, plusCy), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(plusCx, plusCy - plusSize), end = Offset(plusCx, plusCy + plusSize), strokeWidth = stroke * 1.1f, cap = StrokeCap.Round)
            }
            "refresh", "ikon_canli" -> {
                // Listeyi Güncelle: Çift döngü okları
                val r = w * 0.38f
                val center = Offset(w / 2f, h / 2f)
                drawArc(color = color, startAngle = 10f, sweepAngle = 140f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color = color, startAngle = 190f, sweepAngle = 140f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                // Üst ve alt ok uçları
                val rad1 = (150f * kotlin.math.PI / 180f).toFloat()
                val x1 = center.x + r * kotlin.math.cos(rad1)
                val y1 = center.y + r * kotlin.math.sin(rad1)
                drawLine(color = color, start = Offset(x1, y1), end = Offset(x1 + 3.dp.toPx(), y1 - 2.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)

                val rad2 = (330f * kotlin.math.PI / 180f).toFloat()
                val x2 = center.x + r * kotlin.math.cos(rad2)
                val y2 = center.y + r * kotlin.math.sin(rad2)
                drawLine(color = color, start = Offset(x2, y2), end = Offset(x2 - 3.dp.toPx(), y2 + 2.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "defaultsource" -> {
                // Varsayılan Listeye Geri Dön: Geriye dönen kavisli ok
                val r = w * 0.38f
                val center = Offset(w / 2f, h * 0.54f)
                drawArc(color = color, startAngle = 30f, sweepAngle = 260f, useCenter = false, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round))
                // Ok ucu
                val arrowTipX = center.x - r
                val arrowTipY = center.y
                drawLine(color = color, start = Offset(arrowTipX - 3.5.dp.toPx(), arrowTipY - 2.dp.toPx()), end = Offset(arrowTipX, arrowTipY), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(arrowTipX + 1.dp.toPx(), arrowTipY - 4.dp.toPx()), end = Offset(arrowTipX, arrowTipY), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "m3u", "ikon_url" -> {
                // M3U URL: Bağlantı zinciri (Link)
                val path1 = Path().apply {
                    val cx = w * 0.40f
                    val cy = h * 0.60f
                    moveTo(cx - 3.dp.toPx(), cy + 3.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy + 1.dp.toPx())
                    arcTo(androidx.compose.ui.geometry.Rect(cx - 7.dp.toPx(), cy - 2.dp.toPx(), cx - 1.dp.toPx(), cy + 4.dp.toPx()), 90f, 180f, false)
                    lineTo(cx - 1.dp.toPx(), cy - 2.dp.toPx())
                }
                drawPath(path1, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
                val path2 = Path().apply {
                    val cx = w * 0.60f
                    val cy = h * 0.40f
                    moveTo(cx + 3.dp.toPx(), cy - 3.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy - 1.dp.toPx())
                    arcTo(androidx.compose.ui.geometry.Rect(cx + 1.dp.toPx(), cy - 4.dp.toPx(), cx + 7.dp.toPx(), cy + 2.dp.toPx()), -90f, 180f, false)
                    lineTo(cx + 1.dp.toPx(), cy + 2.dp.toPx())
                }
                drawPath(path2, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
                // Bağlantı orta çizgisi
                drawLine(color = color, start = Offset(w * 0.38f, h * 0.62f), end = Offset(w * 0.62f, h * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "xtream", "ikon_xtream" -> {
                // Xtream Codes: Sunucu yığını (Server stack)
                val sw = w * 0.84f
                val sh = h * 0.30f
                val left = (w - sw) / 2f
                val top1 = h * 0.16f
                val top2 = h * 0.54f
                drawRoundRect(color = color, topLeft = Offset(left, top1), size = Size(sw, sh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                drawRoundRect(color = color, topLeft = Offset(left, top2), size = Size(sw, sh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Sunucu LED noktaları
                drawCircle(color = color, radius = 1.2.dp.toPx(), center = Offset(left + sw * 0.20f, top1 + sh * 0.50f))
                drawCircle(color = color, radius = 1.2.dp.toPx(), center = Offset(left + sw * 0.36f, top1 + sh * 0.50f))
                drawCircle(color = color, radius = 1.2.dp.toPx(), center = Offset(left + sw * 0.20f, top2 + sh * 0.50f))
                drawCircle(color = color, radius = 1.2.dp.toPx(), center = Offset(left + sw * 0.36f, top2 + sh * 0.50f))
            }
            "stalker", "ikon_sunucu" -> {
                // Stalker / MAC: Mikroçip
                val cw = w * 0.58f
                val ch = h * 0.58f
                val left = (w - cw) / 2f
                val top = (h - ch) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(cw, ch), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Pin bacakları (üst, alt, sol, sağ)
                val pinLen = 2.5.dp.toPx()
                for (i in 0..2) {
                    val frac = 0.25f + i * 0.25f
                    // Üst/Alt
                    drawLine(color = color, start = Offset(left + cw * frac, top), end = Offset(left + cw * frac, top - pinLen), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                    drawLine(color = color, start = Offset(left + cw * frac, top + ch), end = Offset(left + cw * frac, top + ch + pinLen), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                    // Sol/Sağ
                    drawLine(color = color, start = Offset(left, top + ch * frac), end = Offset(left - pinLen, top + ch * frac), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                    drawLine(color = color, start = Offset(left + cw, top + ch * frac), end = Offset(left + cw + pinLen, top + ch * frac), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                }
            }
            "file", "ikon_dosya" -> {
                // Dosyadan Yükle: Belge kağıdı (köşesi katlanmış)
                val dw = w * 0.66f
                val dh = h * 0.80f
                val left = (w - dw) / 2f
                val top = (h - dh) / 2f
                val fold = w * 0.20f
                val docPath = Path().apply {
                    moveTo(left, top)
                    lineTo(left + dw - fold, top)
                    lineTo(left + dw, top + fold)
                    lineTo(left + dw, top + dh)
                    lineTo(left, top + dh)
                    close()
                }
                drawPath(docPath, color = color, style = Stroke(stroke, join = StrokeJoin.Round))
                // Katlanmış kulakçık
                drawLine(color = color, start = Offset(left + dw - fold, top), end = Offset(left + dw - fold, top + fold), strokeWidth = stroke)
                drawLine(color = color, start = Offset(left + dw - fold, top + fold), end = Offset(left + dw, top + fold), strokeWidth = stroke)
            }
            "countries", "kategori_dunya" -> {
                // Ülke Listeleri: Dünya Küresi
                val r = w * 0.42f
                val center = Offset(w / 2f, h / 2f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                drawLine(color = color, start = Offset(center.x - r, center.y), end = Offset(center.x + r, center.y), strokeWidth = stroke * 0.85f)
                drawOval(color = color, topLeft = Offset(w * 0.26f, center.y - r), size = Size(w * 0.48f, r * 2f), style = Stroke(stroke * 0.85f))
            }
            "epg", "ikon_saat" -> {
                // XMLTV EPG: Takvim / Program çizelgesi
                val cw = w * 0.78f
                val ch = h * 0.72f
                val left = (w - cw) / 2f
                val top = h * 0.18f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(cw, ch), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // Üst başlık çizgisi
                drawLine(color = color, start = Offset(left, top + ch * 0.28f), end = Offset(left + cw, top + ch * 0.28f), strokeWidth = stroke)
                // Askı kulakçıkları
                drawLine(color = color, start = Offset(left + cw * 0.28f, top - 2.dp.toPx()), end = Offset(left + cw * 0.28f, top + 2.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + cw * 0.72f, top - 2.dp.toPx()), end = Offset(left + cw * 0.72f, top + 2.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
                // İç takvim ızgarası noktaları
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(left + cw * 0.30f, top + ch * 0.55f))
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(left + cw * 0.50f, top + ch * 0.55f))
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(left + cw * 0.70f, top + ch * 0.55f))
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(left + cw * 0.30f, top + ch * 0.78f))
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(left + cw * 0.50f, top + ch * 0.78f))
            }
            "categories", "ikon_category" -> {
                // Kategorileri Yönet: 3 kademeli klasör / etiket
                val w1 = w * 0.76f
                val h1 = h * 0.20f
                val left = (w - w1) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, h * 0.16f), size = Size(w1, h1), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawRoundRect(color = color, topLeft = Offset(left, h * 0.42f), size = Size(w1, h1), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
                drawRoundRect(color = color, topLeft = Offset(left, h * 0.68f), size = Size(w1, h1), cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()), style = Stroke(stroke))
            }

            // --- OYNATMA SEKMESİ (section = 2) ---
            "sleeptimer" -> {
                // Uyku Zamanlayıcısı: Saat + Hilal
                val r = w * 0.34f
                val center = Offset(w * 0.42f, h * 0.54f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                drawLine(color = color, start = center, end = Offset(center.x, center.y - r * 0.55f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                drawLine(color = color, start = center, end = Offset(center.x + r * 0.45f, center.y), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                // Küçük "zZ" simgesi
                val z1 = Path().apply {
                    val zx = w * 0.78f
                    val zy = h * 0.20f
                    moveTo(zx, zy)
                    lineTo(zx + 3.dp.toPx(), zy)
                    lineTo(zx, zy + 3.dp.toPx())
                    lineTo(zx + 3.dp.toPx(), zy + 3.dp.toPx())
                }
                drawPath(z1, color = color, style = Stroke(stroke * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "quality", "ikon_quality" -> {
                // Görüntü Kalitesi: "HD" Rozeti
                val bw = w * 0.86f
                val bh = h * 0.62f
                val left = (w - bw) / 2f
                val top = (h - bh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(bw, bh), cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()), style = Stroke(stroke))
                // 'H' harfi
                val hLeft = left + bw * 0.24f
                val hTop = top + bh * 0.26f
                val hBottom = top + bh * 0.74f
                val hW = bw * 0.20f
                drawLine(color = color, start = Offset(hLeft, hTop), end = Offset(hLeft, hBottom), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(hLeft + hW, hTop), end = Offset(hLeft + hW, hBottom), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(hLeft, (hTop + hBottom) / 2f), end = Offset(hLeft + hW, (hTop + hBottom) / 2f), strokeWidth = stroke * 0.9f)
                // 'D' harfi
                val dLeft = left + bw * 0.58f
                val dW = bw * 0.20f
                drawLine(color = color, start = Offset(dLeft, hTop), end = Offset(dLeft, hBottom), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
                drawArc(color = color, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(dLeft - dW * 0.2f, hTop), size = Size(dW * 1.2f, hBottom - hTop), style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
            }
            "format" -> {
                // Varsayılan Format: 16:9 çerçeve
                val rw = w * 0.88f
                val rh = h * 0.58f
                val left = (w - rw) / 2f
                val top = (h - rh) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(rw, rh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                val cx = w / 2f
                val cy = h / 2f
                drawLine(color, Offset(cx - rw * 0.25f, cy), Offset(cx + rw * 0.25f, cy), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
            "audio" -> {
                // Tercih Edilen Ses Dili: Hoparlör
                val startX = w * 0.16f
                val endX = w * 0.48f
                val midY = h * 0.50f
                val bodyH = h * 0.32f
                val coneH = h * 0.68f
                val bodyPath = Path().apply {
                    moveTo(startX, midY - bodyH / 2f)
                    lineTo(startX + (endX - startX) * 0.45f, midY - bodyH / 2f)
                    lineTo(endX, midY - coneH / 2f)
                    lineTo(endX, midY + coneH / 2f)
                    lineTo(startX + (endX - startX) * 0.45f, midY + bodyH / 2f)
                    lineTo(startX, midY + bodyH / 2f)
                    close()
                }
                drawPath(bodyPath, color = color)
                drawArc(color = color, startAngle = -45f, sweepAngle = 90f, useCenter = false, topLeft = Offset(w * 0.44f, h * 0.26f), size = Size(w * 0.36f, h * 0.48f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            "subtitle", "ikon_subtitle" -> {
                // Altyazı Varsayılanı: "CC" / Altyazı Kutusu
                val cw = w * 0.86f
                val ch = h * 0.62f
                val left = (w - cw) / 2f
                val top = (h - ch) / 2f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(cw, ch), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // İç iki konuşma/altyazı çizgisi
                drawLine(color = color, start = Offset(left + cw * 0.20f, top + ch * 0.40f), end = Offset(left + cw * 0.80f, top + ch * 0.40f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(left + cw * 0.30f, top + ch * 0.68f), end = Offset(left + cw * 0.70f, top + ch * 0.68f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
            "subtitlesize" -> {
                // Altyazı Boyutu: Büyük 'A' ve küçük 'a'
                // Büyük A
                val pathA = Path().apply {
                    val aLeft = w * 0.16f
                    val aRight = w * 0.52f
                    val aTop = h * 0.20f
                    val aBottom = h * 0.80f
                    val midX = (aLeft + aRight) / 2f
                    moveTo(midX, aTop)
                    lineTo(aLeft, aBottom)
                    moveTo(midX, aTop)
                    lineTo(aRight, aBottom)
                    moveTo(aLeft + (aRight - aLeft) * 0.22f, aTop + (aBottom - aTop) * 0.60f)
                    lineTo(aRight - (aRight - aLeft) * 0.22f, aTop + (aBottom - aTop) * 0.60f)
                }
                drawPath(pathA, color = color, style = Stroke(stroke * 0.95f, cap = StrokeCap.Round))
                // Küçük a
                val smallR = w * 0.14f
                val smallCx = w * 0.74f
                val smallCy = h * 0.66f
                drawCircle(color = color, radius = smallR, center = Offset(smallCx, smallCy), style = Stroke(stroke * 0.85f))
                drawLine(color = color, start = Offset(smallCx + smallR, smallCy - smallR), end = Offset(smallCx + smallR, smallCy + smallR * 1.2f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
            }
            "subtitlecolor" -> {
                // Altyazı Rengi: Renk damlası
                val cx = w / 2f
                val path = Path().apply {
                    val topY = h * 0.18f
                    val botY = h * 0.78f
                    val rad = w * 0.26f
                    moveTo(cx, topY)
                    cubicTo(cx - rad * 1.2f, topY + rad * 1.4f, cx - rad, botY, cx, botY)
                    cubicTo(cx + rad, botY, cx + rad * 1.2f, topY + rad * 1.4f, cx, topY)
                    close()
                }
                drawPath(path, color = color, style = Stroke(stroke, cap = StrokeCap.Round))
            }

            // --- YEDEKLEME VE AKTARIM SEKMESİ (section = 4) ---
            "webtransfer" -> {
                // Telefondan Aktar: Telefon ve Wi-Fi sinyali
                val pw = w * 0.44f
                val ph = h * 0.74f
                val pLeft = w * 0.14f
                val pTop = (h - ph) / 2f
                drawRoundRect(color = color, topLeft = Offset(pLeft, pTop), size = Size(pw, ph), cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()), style = Stroke(stroke))
                drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(pLeft + pw / 2f, pTop + ph * 0.84f))
                // Wi-Fi sinyal yayları (sağ tarafa)
                drawArc(color = color, startAngle = -45f, sweepAngle = 90f, useCenter = false, topLeft = Offset(w * 0.58f, h * 0.32f), size = Size(w * 0.24f, h * 0.36f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color = color, startAngle = -50f, sweepAngle = 100f, useCenter = false, topLeft = Offset(w * 0.66f, h * 0.20f), size = Size(w * 0.30f, h * 0.60f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            "export", "ikon_export" -> {
                // Favorileri Dışa Aktar: Kutu + Yukarı Ok
                val bw = w * 0.66f
                val bh = h * 0.44f
                val bLeft = (w - bw) / 2f
                val bTop = h * 0.44f
                val trayPath = Path().apply {
                    moveTo(bLeft, bTop)
                    lineTo(bLeft, bTop + bh)
                    lineTo(bLeft + bw, bTop + bh)
                    lineTo(bLeft + bw, bTop)
                }
                drawPath(trayPath, color = color, style = Stroke(stroke, join = StrokeJoin.Round))
                // Yukarı Ok
                val arrowX = w / 2f
                val arrowTop = h * 0.14f
                val arrowBottom = h * 0.58f
                drawLine(color = color, start = Offset(arrowX, arrowBottom), end = Offset(arrowX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
                val head = w * 0.18f
                drawLine(color = color, start = Offset(arrowX - head, arrowTop + head), end = Offset(arrowX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(arrowX + head, arrowTop + head), end = Offset(arrowX, arrowTop), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "import", "ikon_import" -> {
                // Favorileri İçe Aktar: Kutu + Aşağı Ok
                val bw = w * 0.66f
                val bh = h * 0.44f
                val bLeft = (w - bw) / 2f
                val bTop = h * 0.44f
                val trayPath = Path().apply {
                    moveTo(bLeft, bTop)
                    lineTo(bLeft, bTop + bh)
                    lineTo(bLeft + bw, bTop + bh)
                    lineTo(bLeft + bw, bTop)
                }
                drawPath(trayPath, color = color, style = Stroke(stroke, join = StrokeJoin.Round))
                // Aşağı Ok
                val arrowX = w / 2f
                val arrowTop = h * 0.14f
                val arrowBottom = h * 0.58f
                drawLine(color = color, start = Offset(arrowX, arrowTop), end = Offset(arrowX, arrowBottom), strokeWidth = stroke, cap = StrokeCap.Round)
                val head = w * 0.18f
                drawLine(color = color, start = Offset(arrowX - head, arrowBottom - head), end = Offset(arrowX, arrowBottom), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color = color, start = Offset(arrowX + head, arrowBottom - head), end = Offset(arrowX, arrowBottom), strokeWidth = stroke, cap = StrokeCap.Round)
            }

            // --- YARDIM VE HAKKINDA SEKMESİ (section = 6) ---
            "about", "ikon_hakkinda" -> {
                // Beleş TiVi Clone Hakkında: Monitör + Yıldız/Rozet
                val sw = w * 0.86f
                val sh = h * 0.60f
                val left = (w - sw) / 2f
                val top = h * 0.14f
                drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(sw, sh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
                // 'i' harfi veya yıldız
                drawCircle(color = color, radius = stroke * 0.7f, center = Offset(w / 2f, top + sh * 0.35f))
                drawLine(color = color, start = Offset(w / 2f, top + sh * 0.52f), end = Offset(w / 2f, top + sh * 0.75f), strokeWidth = stroke, cap = StrokeCap.Round)
                val stemTop = top + sh
                val stemBottom = stemTop + h * 0.14f
                drawLine(color, Offset(w / 2f, stemTop), Offset(w / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
                val baseW = w * 0.45f
                drawLine(color, Offset(w / 2f - baseW / 2f, stemBottom), Offset(w / 2f + baseW / 2f, stemBottom), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            "network" -> {
                // Bağlantıyı Kontrol Et: Wi-Fi sinyal yayları
                val cx = w / 2f
                val cy = h * 0.74f
                drawCircle(color = color, radius = 1.8.dp.toPx(), center = Offset(cx, cy))
                drawArc(color = color, startAngle = -140f, sweepAngle = 100f, useCenter = false, topLeft = Offset(w * 0.28f, h * 0.42f), size = Size(w * 0.44f, h * 0.44f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color = color, startAngle = -145f, sweepAngle = 110f, useCenter = false, topLeft = Offset(w * 0.12f, h * 0.20f), size = Size(w * 0.76f, h * 0.76f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            "support", "ikon_contact" -> {
                // Destek: Can simidi (Lifebuoy)
                val r = w * 0.40f
                val center = Offset(w / 2f, h / 2f)
                drawCircle(color = color, radius = r, center = center, style = Stroke(stroke))
                drawCircle(color = color, radius = r * 0.45f, center = center, style = Stroke(stroke * 0.85f))
                // 4 şerit
                drawLine(color = color, start = Offset(center.x, center.y - r), end = Offset(center.x, center.y - r * 0.45f), strokeWidth = stroke * 0.9f)
                drawLine(color = color, start = Offset(center.x, center.y + r * 0.45f), end = Offset(center.x, center.y + r), strokeWidth = stroke * 0.9f)
                drawLine(color = color, start = Offset(center.x - r, center.y), end = Offset(center.x - r * 0.45f, center.y), strokeWidth = stroke * 0.9f)
                drawLine(color = color, start = Offset(center.x + r * 0.45f, center.y), end = Offset(center.x + r, center.y), strokeWidth = stroke * 0.9f)
            }
            "telegram", "ikon_telegram" -> {
                // Telegram: Kağıt Uçak
                val planePath = Path().apply {
                    val p1 = Offset(w * 0.16f, h * 0.52f)
                    val p2 = Offset(w * 0.84f, h * 0.20f)
                    val p3 = Offset(w * 0.48f, h * 0.80f)
                    val p4 = Offset(w * 0.42f, h * 0.58f)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                    moveTo(p4.x, p4.y)
                    lineTo(p2.x, p2.y)
                }
                drawPath(planePath, color = color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            "cache", "ikon_cache" -> {
                // Önbelleği Temizle: Fırça / Süpürge
                val broomPath = Path().apply {
                    // Sap
                    moveTo(w * 0.78f, h * 0.18f)
                    lineTo(w * 0.48f, h * 0.48f)
                }
                drawPath(broomPath, color = color, style = Stroke(stroke * 1.2f, cap = StrokeCap.Round))
                // Fırça başlığı
                val headPath = Path().apply {
                    moveTo(w * 0.40f, h * 0.44f)
                    lineTo(w * 0.56f, h * 0.60f)
                    lineTo(w * 0.38f, h * 0.82f)
                    lineTo(w * 0.22f, h * 0.66f)
                    close()
                }
                drawPath(headPath, color = color, style = Stroke(stroke, join = StrokeJoin.Round))
                // Fırça kılları
                drawLine(color = color, start = Offset(w * 0.26f, h * 0.70f), end = Offset(w * 0.16f, h * 0.84f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }

            else -> {
                // Varsayılan: Minimalist yuvarlatılmış dikdörtgen
                val rw = w * 0.80f
                val rh = h * 0.65f
                drawRoundRect(color, topLeft = Offset((w - rw) / 2f, (h - rh) / 2f), size = Size(rw, rh), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(stroke))
            }
        }
    }
}
