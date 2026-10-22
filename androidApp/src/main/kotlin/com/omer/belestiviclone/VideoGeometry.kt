package com.omer.belestiviclone

import kotlin.math.max
import kotlin.math.min

/** Pixel geometry only; does not select/restart media or alter playback diagnostics. */
data class VideoRect(val width: Float, val height: Float, val left: Float, val top: Float)

private fun fitTargetAspect(viewWidth: Int, viewHeight: Int, targetAspect: Float): Pair<Float, Float> {
    val vw = viewWidth.toFloat()
    val vh = viewHeight.toFloat()
    return if (vw / vh > targetAspect) {
        (vh * targetAspect) to vh
    } else {
        vw to (vw / targetAspect)
    }
}

fun videoRect(viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int,
              pixelRatio: Float, rotation: Int, format: String): VideoRect? {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0 ||
        !pixelRatio.isFinite() || pixelRatio <= 0f) return null
    val rawWidth = videoWidth * pixelRatio
    val rotated = rotation % 180 != 0
    val width = if (rotated) videoHeight.toFloat() else rawWidth
    val height = if (rotated) rawWidth else videoHeight.toFloat()
    val (w, h) = when (format) {
        "Orijinal (Panel)" -> width to height // Preserve the separate multi-panel natural-pixel mode.
        "Ekrana Yay (Stretch / Fill)", "Ekrana Yay", "Ekrana yay" -> viewWidth.toFloat() to viewHeight.toFloat()
        "Yakınlaştır (Zoom / Crop)", "Yakınlaştır", "Tam Ekran (Kes)" -> {
            val scale = max(viewWidth / width, viewHeight / height)
            (width * scale) to (height * scale)
        }
        "16:9 (Geniş ekran)", "16:9" -> fitTargetAspect(viewWidth, viewHeight, 16f / 9f)
        "4:3 (Klasik ekran)", "4:3" -> fitTargetAspect(viewWidth, viewHeight, 4f / 3f)
        "1.85:1 (Geniş sinema)", "1.85:1", "Sinema 1.85:1" -> fitTargetAspect(viewWidth, viewHeight, 1.85f)
        "2.39:1 (Anamorfik sinema)", "2.39:1", "Sinema 2.39:1" -> fitTargetAspect(viewWidth, viewHeight, 2.39f)
        "Otomatik (Orijinal yayın oranı)", "Otomatik", "Otomatik (Orijinal)", "Orijinal", "Sığdır (Fit)" -> {
            val scale = min(viewWidth / width, viewHeight / height)
            (width * scale) to (height * scale)
        }
        else -> {
            val scale = min(viewWidth / width, viewHeight / height)
            (width * scale) to (height * scale)
        }
    }
    return VideoRect(w, h, (viewWidth - w) / 2f, (viewHeight - h) / 2f)
}
