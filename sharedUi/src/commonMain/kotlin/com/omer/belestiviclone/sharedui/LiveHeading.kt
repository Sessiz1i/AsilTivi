package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The production and fixture share this measured placement, including the subtitle. */
@Composable
fun Modifier.liveHeadingPlacement(format: String, width: Float, height: Float): Modifier {
    val density = LocalDensity.current.density
    return layout { measurable, constraints ->
        val heading = measurable.measure(constraints)
        val placement = headingPlacement(format, width, height, heading.height / density)
        layout(heading.width, heading.height) {
            heading.place((19f * density).roundToInt(), (placement.top * density).roundToInt())
        }
    }
}

/** Production heading, also measured by the debug-only layer inspection activity.
 * Hidden layers keep their real constraints and placement; no fallback broadcast image.
 */
@Composable
fun LiveHeading(
    name: String,
    logo: String,
    subtitle: String,
    image: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    showUi: Boolean = true,
    showLogo: Boolean = true,
    includeLogo: Boolean = true,
    backdrop: Boolean = false,
    leadingChannelLogo: Boolean = false,
    onBounds: ((String, Rect) -> Unit)? = null,
) {
    fun Modifier.observe(part: String): Modifier = if (onBounds == null) this else
        onGloballyPositioned { onBounds(part, it.boundsInWindow()) }
    val surface = if (backdrop) Modifier.background(Color(0xB8000000), RoundedCornerShape(11.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp) else Modifier
    Column(modifier.observe("heading").then(surface)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(24.dp).clipToBounds().alpha(if (showUi) 1f else 0f).observe(if (leadingChannelLogo) "leading-logo" else "tv")) {
                image(if (leadingChannelLogo) logo else "ikon_tv", Modifier.fillMaxSize())
            }
            Label(name, 22, Color(0xFFFFD600), Modifier.weight(1f, fill = false)
                .alpha(if (showUi) 1f else 0f).observe("text"), bold = true, maxLines = 1)
            if (includeLogo) Box(Modifier.size(49.dp, 24.dp).clipToBounds().alpha(if (showLogo) 1f else 0f).observe("logo")) {
                image(logo, Modifier.fillMaxSize())
            }
        }
        Label(subtitle, 10, Color.LightGray, Modifier.alpha(if (showUi) 1f else 0f).observe("subtitle"))
    }
}
