package com.omer.belestiviclone

/** Caption-only viewport; the video rectangle and the measured information card stay unchanged. */
internal fun captionAvailableHeight(height: Int, bottomInset: Int) = (height-bottomInset.coerceAtLeast(0)).coerceAtLeast(0)
internal fun captionBottomTop(height: Int, cueHeight: Float, bottomInset: Int, negativeLine: Float? = null, lineStep: Float = 0f): Float {
    val available=captionAvailableHeight(height,bottomInset).toFloat()
    val top=if(negativeLine == null) available*.9f-cueHeight else available+(negativeLine+1)*lineStep-cueHeight
    return top.coerceIn(0f,(available-cueHeight).coerceAtLeast(0f))
}
