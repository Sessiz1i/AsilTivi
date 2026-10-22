package com.omer.belestiviclone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue

/** One non-interactive overlay shared by Live and every Multi slot. Texture geometry is untouched. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun CaptionOverlay(cues: List<Cue>, modifier: Modifier, observed: (Int,Int) -> Unit, bottomInsetPx: Int = 0) {
    val supported=cues.filter { it.verticalType == Cue.TYPE_UNSET && it.shearDegrees == 0f }
    AndroidView(factory={ CaptionView(it) },modifier=modifier,update={ it.cues=supported;it.bottomInsetPx=bottomInsetPx })
    SideEffect { observed(supported.size,cues.size-supported.size) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class CaptionView(context: Context): View(context) {
    var bottomInsetPx=0
        set(value) { val next=value.coerceAtLeast(0);if(field!=next) { field=next;invalidate() } }
    var cues: List<Cue> = emptyList()
        set(value) { if (field == value) return;field=value
            contentDescription=value.mapNotNull { it.text }.joinToString("\n");invalidate() }
    private val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;setShadowLayer(2f,1f,1f,Color.BLACK) }
    private val background=Paint().apply { color=0xB0000000.toInt() }
    init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES }
    private fun offset(anchor: Int,size: Float) = when(anchor) { Cue.ANCHOR_TYPE_START -> 0f;Cue.ANCHOR_TYPE_END -> size;else -> size/2f }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val available=captionAvailableHeight(height,bottomInsetPx)
        if(width<=0 || available<=0) return
        val saveCount=canvas.save()
        canvas.clipRect(0,0,width,available)
        for (cue in cues.sortedBy { it.zIndex }) {
            val w=(width*(if(cue.size == Cue.DIMEN_UNSET) .86f else cue.size)).coerceIn(1f,width.toFloat())
            paint.textSize=when(cue.textSizeType) {
                Cue.TEXT_SIZE_TYPE_ABSOLUTE -> cue.textSize
                Cue.TEXT_SIZE_TYPE_FRACTIONAL,Cue.TEXT_SIZE_TYPE_FRACTIONAL_IGNORE_PADDING -> height*cue.textSize
                else -> height*.048f
            }.coerceAtLeast(1f)
            val layout=cue.text?.let { StaticLayout.Builder.obtain(it,0,it.length,paint,w.toInt())
                .setAlignment(cue.textAlignment ?: Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build() }
            val bitmap=cue.bitmap
            val h=bitmap?.let { if(cue.bitmapHeight == Cue.DIMEN_UNSET) w*it.height/it.width else height*cue.bitmapHeight }
                ?: layout?.height?.toFloat() ?: continue
            val x=width*(if(cue.position == Cue.DIMEN_UNSET) .5f else cue.position)-offset(cue.positionAnchor,w)
            val y=when {
                cue.line == Cue.DIMEN_UNSET -> captionBottomTop(height,h,bottomInsetPx)
                cue.lineType == Cue.LINE_TYPE_FRACTION -> height*cue.line-offset(cue.lineAnchor,h)
                cue.line < 0 -> captionBottomTop(height,h,bottomInsetPx,cue.line,paint.textSize*1.2f)
                else -> cue.line*paint.textSize*1.2f
            }
            val left=x.coerceIn(0f,(width-w).coerceAtLeast(0f));val top=y.coerceIn(0f,(available-h).coerceAtLeast(0f))
            if(bitmap != null) canvas.drawBitmap(bitmap,null,RectF(left,top,left+w,top+h),null)
            else if(layout != null) {
                background.color=if(cue.windowColorSet) cue.windowColor else 0xB0000000.toInt()
                canvas.drawRect(left,top,left+w,top+h,background)
                canvas.save();canvas.translate(left,top);layout.draw(canvas);canvas.restore()
            }
        }
        canvas.restoreToCount(saveCount)
    }
}
