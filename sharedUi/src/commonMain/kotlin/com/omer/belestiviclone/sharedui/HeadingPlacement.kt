package com.omer.belestiviclone.sharedui

/** Design units, after the existing viewport density scale. No channel/video inspection. */
const val LiveSidebarTop = 87f

data class HeadingPlacement(val top: Float, val shifted: Boolean)

fun headingPlacement(format: String, width: Float, height: Float, headingHeight: Float): HeadingPlacement {
    val original = HeadingPlacement(22f, false)
    // Bound this change to the evidenced landscape Fit layout. A tall wrapped subtitle or
    // large font must not be pushed into the sidebar. Unsupported constraints keep R07 placement.
    if (format != "Sığdır (Fit)" || width < height) return original
    val headingRight = 19f + width * .43f
    val controlsLeft = width - (104f + 120f + 79f + 68f + 3 * 10f + 18f)
    if (headingRight + 4f > controlsLeft || 45f + headingHeight + 4f > LiveSidebarTop) return original
    return HeadingPlacement(45f, true)
}
