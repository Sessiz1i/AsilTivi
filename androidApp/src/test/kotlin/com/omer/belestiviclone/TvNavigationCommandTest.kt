package com.omer.belestiviclone

import android.view.KeyEvent
import com.omer.belestiviclone.shared.preview.NavigationPress
import kotlin.test.*

class TvNavigationCommandTest {
    @Test fun eachArrowStepsOnlyWhileControlsAreHidden() {
        val arrows = mapOf(KeyEvent.KEYCODE_DPAD_UP to 1, KeyEvent.KEYCODE_DPAD_RIGHT to 1,
            KeyEvent.KEYCODE_DPAD_DOWN to -1, KeyEvent.KEYCODE_DPAD_LEFT to -1)
        for ((key, delta) in arrows) {
            val command = tvNavigationCommand(key)!!
            assertEquals(delta, command.delta)
            assertTrue(command.eligible(normalLive = true, panelVisible = false, imeVisible = false))
            assertFalse(command.eligible(normalLive = true, panelVisible = true, imeVisible = false))
            assertFalse(command.eligible(normalLive = false, panelVisible = false, imeVisible = false))
            assertFalse(command.eligible(normalLive = true, panelVisible = false, imeVisible = true))
        }
    }
    @Test fun heldArrowCannotQueueStepsOrLeakItsReleaseWhenPanelOpens() {
        val key = KeyEvent.KEYCODE_DPAD_RIGHT
        val command = tvNavigationCommand(key)!!; val gate = NavigationPress()
        assertTrue(gate.event(key, true, false, command.eligible(true, false, false)).perform)
        repeat(20) { assertFalse(gate.event(key, true, true, command.eligible(true, false, false)).perform) }
        assertTrue(gate.event(key, false, false, command.eligible(true, true, false)).consumed)
        assertFalse(gate.event(key, true, false, command.eligible(true, true, false)).consumed)
        assertFalse(gate.event(key, false, false, command.eligible(true, true, false)).consumed)
    }
    @Test fun dedicatedChannelKeysStillWorkWithVisibleControlsAndOkStillRevealsOnly() {
        for (key in listOf(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            val command = tvNavigationCommand(key)!!
            assertTrue(command.eligible(true, true, false)); assertTrue(command.eligible(true, false, false))
        }
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            val command = tvNavigationCommand(key)!!
            assertEquals(0, command.delta); assertTrue(command.eligible(true, false, false))
            assertFalse(command.eligible(true, true, false))
        }
    }
    @Test fun backVolumeMediaAndTextKeysRemainOutsideThisAdapter() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_A)) {
            assertNull(tvNavigationCommand(key))
        }
    }
}
