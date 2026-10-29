package com.omer.belestiviclone

import android.view.KeyEvent

internal data class TvNavigationCommand(val delta: Int, val hiddenPanelOnly: Boolean) {
    fun eligible(normalLive: Boolean, panelVisible: Boolean, imeVisible: Boolean) =
        normalLive && !imeVisible && (!hiddenPanelOnly || !panelVisible)
}

/** Decode independently of visibility so an owned press can consume its release after a UI change. */
internal fun tvNavigationCommand(keyCode: Int): TvNavigationCommand? = when (keyCode) {
    KeyEvent.KEYCODE_CHANNEL_UP -> TvNavigationCommand(1, false)
    KeyEvent.KEYCODE_CHANNEL_DOWN -> TvNavigationCommand(-1, false)
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> TvNavigationCommand(1, true)
    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT -> TvNavigationCommand(-1, true)
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TvNavigationCommand(0, true)
    else -> null
}

/** Kumanda 0..9 ve Numpad 0..9 tuşlarını rakama (0..9) çevirir. */
internal fun tvDigitFromKeyCode(keyCode: Int): Int? = when (keyCode) {
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
    else -> null
}
