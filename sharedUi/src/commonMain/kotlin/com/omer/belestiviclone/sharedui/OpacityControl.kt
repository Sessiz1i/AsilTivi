package com.omer.belestiviclone.sharedui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Drag/tap commits on release. TV arrows draft, OK commits, Back/focus loss cancels. */
@Composable
internal fun OpacityControl(s: PreviewState, image: @Composable (String, Modifier) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val value = s.opacityValue
    Column(Modifier.fillMaxWidth().height(68.dp).background(Color(0xFF13233D), RoundedCornerShape(10.dp)).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            image("ikon_opacity", Modifier.size(21.dp, 23.dp)); Label("Şeffaflık", 12, modifier = Modifier.weight(1f), bold = true); Label("%$value", 10, Color(0xFFE67E22), bold = true)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(29.dp)) {
            val requester = remember { FocusRequester() }
            val width = constraints.maxWidth.toFloat()
            fun update(x: Float) { if (width > 0) s.updateOpacity(((x / width) * 100).roundToInt()) }
            Canvas(Modifier.fillMaxSize().focusRequester(requester).onFocusChanged {
                if (focused && !it.isFocused) s.cancelOpacity()
                focused = it.isFocused
            }.then(if (focused) Modifier.border(1.dp, Color(0xFFE67E22)) else Modifier)
                .semantics {
                    contentDescription = "Şeffaflık"
                    stateDescription = "%$value"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 99)
                    setProgress { requested -> if (s.busy || !requested.isFinite()) false else { s.updateOpacity(requested.roundToInt()); s.commitOpacity(); true } }
                }
                .onPreviewKeyEvent { e ->
                    if (e.key in listOf(Key.DirectionLeft, Key.DirectionRight)) {
                        if (e.type == KeyEventType.KeyDown) s.updateOpacity(s.opacityValue + if (e.key == Key.DirectionRight) 1 else -1)
                        true
                    } else if (e.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) {
                        if (e.type == KeyEventType.KeyUp) s.commitOpacity()
                        true
                    } else false
                }.focusable(enabled = !s.busy && s.dialog == null)
                .pointerInput(width) { detectTapGestures { requester.requestFocus(); update(it.x); s.commitOpacity() } }
                .pointerInput(width) { detectHorizontalDragGestures(onDragStart = { requester.requestFocus(); update(it.x) }, onDragEnd = s::commitOpacity, onDragCancel = s::cancelOpacity) { change, _ -> change.consume(); update(change.position.x) } }
            ) {
                val inset = 4.5.dp.toPx(); val track = (size.width - inset * 2).coerceAtLeast(0f)
                val x = inset + track * value / 100f; val y = size.height / 2
                drawLine(Color(0xFF44546B), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                drawLine(Color(0xFF80CBC4), Offset(inset, y), Offset(x, y), 1.dp.toPx())
                drawCircle(Color(0xFF80CBC4), inset, Offset(x, y))
            }
        }
    }
}
