package com.omer.belestiviclone

import kotlin.math.max
import kotlin.math.min

/** Pixel geometry only; does not select/restart media or alter playback diagnostics. */
data class VideoRect(val width: Float, val height: Float, val left: Float, val top: Float)

fun videoRect(viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int,
              pixelRatio: Float, rotation: Int, format: String): VideoRect? {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0 ||
        !pixelRatio.isFinite() || pixelRatio <= 0f) return null
    val rawWidth = videoWidth * pixelRatio
    val rotated = rotation % 180 != 0
    val width = if (rotated) videoHeight.toFloat() else rawWidth
    val height = if (rotated) rawWidth else videoHeight.toFloat()
    val scale = when (format) {
        "Orijinal (Panel)" -> 1f // Preserve the separate multi-panel natural-pixel mode.
        "Orijinal" -> min(viewWidth / width, viewHeight / height)
        "Tam Ekran (Kes)" -> max(viewWidth / width, viewHeight / height)
        else -> min(viewWidth / width, viewHeight / height)
    }
    val w = width * scale; val h = height * scale
    return VideoRect(w, h, (viewWidth - w) / 2f, (viewHeight - h) / 2f)
}
