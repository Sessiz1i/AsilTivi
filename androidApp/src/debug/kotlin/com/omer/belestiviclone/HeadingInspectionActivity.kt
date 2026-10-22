package com.omer.belestiviclone

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.omer.belestiviclone.shared.preview.PublicM3u
import com.omer.belestiviclone.shared.preview.PreviewChannel
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.sharedui.LiveHeading
import com.omer.belestiviclone.sharedui.liveHeadingPlacement

/** No PreviewState, store, preference write, profile switch or user-data access.
 * Uses the existing catalog asset, exact TRT1 channel and production image/player/heading.
 */
class HeadingInspectionActivity : ComponentActivity() {
    private var layer by mutableStateOf("ui")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = intent.getStringExtra("layer") ?: return
        if (next in setOf("ui", "logo", "video", "combined")) layer = next
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        layer = intent.getStringExtra("layer") ?: "ui"
        if (layer !in setOf("ui", "logo", "video", "combined")) { finish(); return }
        val text = assets.open("trlist.m3u").bufferedReader().use { it.readText() }
        val row = PublicM3u.parse(text) { raw -> runCatching { java.net.URI(PublicM3u.url).resolve(raw).toASCIIString() }.getOrNull() }
            .rows.first { it.name == "TRT 1" }
        // In-memory diagnostic identity only. Never read/write the user's AtomicFile/catalog/DB.
        val channel = PreviewChannel(OpaqueId(0x523037, 1), "TRT 1", "", row.logo.orEmpty(), definition = row.definition)
        android.util.Log.i("BelesHeading", "diagnosticFixture=true persistedIdentity=false")
        // Explicit capture parameter, never a persisted preference/default assignment.
        val format = intent.getStringExtra("format") ?: "Sığdır (Fit)"
        val bounds = mutableMapOf<String, Rect>()
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val scale = minOf(maxWidth.value / 855f, maxHeight.value / 481f)
                    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val mode = layer
                            // Alpha-only switches need not trigger another global-position callback.
                            // After layout frames, bind the measured rectangles to every capture layer.
                            LaunchedEffect(mode, maxWidth, maxHeight, density.density, density.fontScale) {
                                withFrameNanos { }; withFrameNanos { }
                                bounds.forEach { (part, rect) ->
                                    android.util.Log.i("BelesHeading", "phase=settled layer=$mode part=$part rect=$rect density=${density.density} scale=$scale fontScale=${density.fontScale}")
                                }
                                val complete = bounds.keys.containsAll(listOf("tv", "text", "logo"))
                                val ordered = complete && bounds.getValue("tv").right <= bounds.getValue("text").left &&
                                    bounds.getValue("text").right <= bounds.getValue("logo").left
                                android.util.Log.i("BelesHeading", "phase=settled layer=$mode complete=$complete measuredSlotOrder=$ordered")
                            }
                            // Same production player; alpha hides a live layer, not a static substitute.
                            SampleVideo(channel, false, false, 0, format, Modifier.fillMaxSize()
                                .alpha(if (mode in setOf("video", "combined")) 1f else 0f))
                            LiveHeading(channel.name, channel.logo, "Özel M3U · trlist · EPG bilgisi yok",
                                image = { name, modifier -> PreviewImage(name, modifier) },
                                modifier = Modifier.liveHeadingPlacement(format, maxWidth.value, maxHeight.value).widthIn(max = maxWidth * .43f),
                                showUi = mode in setOf("ui", "combined"),
                                showLogo = mode in setOf("logo", "combined"),
                                onBounds = { part, rect ->
                                    bounds[part] = rect
                                    android.util.Log.i("BelesHeading", "layer=$mode part=$part rect=$rect density=${density.density} scale=$scale fontScale=${density.fontScale}")
                                    if (bounds.keys.containsAll(listOf("tv", "text", "logo"))) {
                                        val tv = bounds.getValue("tv"); val text = bounds.getValue("text"); val logo = bounds.getValue("logo")
                                        val ordered = tv.right <= text.left && text.right <= logo.left
                                        android.util.Log.i("BelesHeading", "layer=$mode measuredSlotOrder=$ordered")
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
