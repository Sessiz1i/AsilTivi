package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.omer.belestiviclone.shared.preview.EpgDateTime
import com.omer.belestiviclone.shared.preview.EpgNowNext
import com.omer.belestiviclone.shared.preview.EpgProgramme
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.preview.StreamTechnicalInfo

/** Kanal değişim kartının (zapping/OSD) saf veri modeli; Compose'dan bağımsız test edilebilir. */
data class ZapCardModel(
    val channelName: String,
    val logo: String,
    val channelNumber: Int,
    /** Teknik bilgi yoksa null (rozet gizlenir). */
    val qualityBadge: String?,
    val hasEpg: Boolean,
    val nowTitle: String,
    val nowRange: String,
    val progress: Float,
    val nextTitle: String,
    val clock: String,
)

const val ZapCardNoEpgText = "Yayın akışı bilgisi yok"
const val ZapCardDurationMs = 3000L

private fun EpgProgramme.compactRange(): String =
    "${EpgDateTime.formatHhMm(startEpochMs)}-${EpgDateTime.formatHhMm(endEpochMs)}"

fun zapCardModel(
    channel: PreviewChannel?,
    channelNumber: Int,
    epg: EpgNowNext,
    technical: StreamTechnicalInfo,
    nowEpochMs: Long,
): ZapCardModel {
    val now = epg.now
    val next = epg.next
    val hasEpg = now != null
    return ZapCardModel(
        channelName = channel?.name.orEmpty(),
        logo = channel?.logo ?: "ikon_tv",
        channelNumber = channelNumber,
        qualityBadge = if (technical.width > 0 && technical.height > 0) technical.resolutionBadge else null,
        hasEpg = hasEpg,
        nowTitle = now?.title.orEmpty(),
        nowRange = now?.compactRange().orEmpty(),
        progress = epg.liveProgress(nowEpochMs).progress.takeIf { hasEpg } ?: 0f,
        nextTitle = next?.title ?: if (hasEpg) "—" else "",
        clock = EpgDateTime.formatHhMm(nowEpochMs),
    )
}

private val ZapGold = Color(0xFFFFC52E)
private val ZapBadge = Color(0xFFE67E22)
private val ZapNextBlue = Color(0xFF7FC4F0)

/** Klasik temada görseldeki sarı; diğer temalarda temanın vurgu rengi. */
internal fun zapAccent(theme: ThemeConfig): Color = if (theme.name == "Klasik") ZapGold else theme.accentColor

@Composable
internal fun ZapCard(model: ZapCardModel, theme: ThemeConfig, image: @Composable (String, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    val accent = zapAccent(theme)
    Row(
        modifier
            .background(Color(0xE6121418), shape)
            .border(1.dp, Color(0x40FFFFFF), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(84.dp, 60.dp), contentAlignment = Alignment.Center) {
                image(model.logo, Modifier.fillMaxSize())
            }
            Label("${model.channelNumber}", 10, Color(0xFF9AA4AE), bold = true)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(model.channelName, 20, Color.White, Modifier.weight(1f), bold = true, maxLines = 1)
                if (model.qualityBadge != null) {
                    Box(Modifier.background(ZapBadge, RoundedCornerShape(3.dp)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Label(model.qualityBadge, 11, Color.White, bold = true, maxLines = 1)
                    }
                }
            }
            if (model.hasEpg) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Şimdi:", 13, accent, bold = true)
                    Label(model.nowTitle, 13, Color.White, Modifier.weight(1f), bold = true, maxLines = 1)
                    Label(model.nowRange, 12, Color(0xFFCFD8DC), bold = true, maxLines = 1)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF3A3F45), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(model.progress.coerceIn(0f, 1f)).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Sonra:", 13, ZapNextBlue, bold = true)
                    Label(model.nextTitle, 13, Color(0xFFB0B8C0), Modifier.weight(1f), maxLines = 1)
                    ZapClockIcon(Modifier.size(20.dp))
                    Label(model.clock, 20, accent, bold = true)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(ZapCardNoEpgText, 13, Color(0xFF9AA4AE), Modifier.weight(1f), maxLines = 1)
                    ZapClockIcon(Modifier.size(20.dp))
                    Label(model.clock, 20, accent, bold = true)
                }
            }
        }
    }
}

/** Harici ikon gerektirmeyen Canvas saat simgesi (beyaz kadran, koyu akrepler). */
@Composable
private fun ZapClockIcon(modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFFF5F5F5), r, c)
        drawCircle(Color(0xFF5B6B7A), r, c, style = Stroke(width = r * 0.14f))
        drawLine(Color(0xFF263238), c, Offset(c.x, c.y - r * 0.6f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
        drawLine(Color(0xFF263238), c, Offset(c.x + r * 0.45f, c.y + r * 0.1f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFE53935), r * 0.1f, c)
    }
}
