package com.omer.belestiviclone.sharedui

import androidx.compose.ui.input.key.Key

/** Only the channel list's own axis zaps; the other axis remains available for UI navigation. */
internal fun channelArrowDelta(vertical: Boolean, key: Key): Int? = when {
    vertical && key == Key.DirectionUp -> -1
    vertical && key == Key.DirectionDown -> 1
    !vertical && key == Key.DirectionLeft -> -1
    !vertical && key == Key.DirectionRight -> 1
    else -> null
}
