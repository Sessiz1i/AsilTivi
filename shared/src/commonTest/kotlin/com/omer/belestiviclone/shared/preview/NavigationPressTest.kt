package com.omer.belestiviclone.shared.preview

import kotlin.test.*

class NavigationPressTest {
    @Test fun revealReleaseIsConsumedAfterPanelBecomesVisibleAndCannotClickNewFocus() {
        val gate = NavigationPress()
        assertEquals(NavigationPressResult(true, true), gate.event(23, true, false, true))
        repeat(30) { assertEquals(NavigationPressResult(true), gate.event(23, true, true, false)) }
        assertEquals(NavigationPressResult(true), gate.event(23, false, false, false))
        assertEquals(NavigationPressResult(false), gate.event(23, false, false, true))
    }
    @Test fun channelHoldProducesExactlyOneStepAndNextPhysicalPressWorks() {
        val gate = NavigationPress()
        var steps = 0
        fun event(down: Boolean, repeat: Boolean = false) { if (gate.event(166, down, repeat, true).perform) steps++ }
        event(true); repeat(40) { event(true, true) }; event(false)
        assertEquals(1, steps)
        event(true); event(false); assertEquals(2, steps)
    }
    @Test fun ineligibleAndOrphanRepeatsNeverActAndFocusLossDropsHeldPress() {
        val gate = NavigationPress()
        assertFalse(gate.event(167, true, false, false).consumed)
        assertFalse(gate.event(167, false, false, false).consumed)
        assertFalse(gate.event(167, true, true, true).perform)
        assertTrue(gate.event(167, true, false, true).perform)
        gate.reset()
        assertFalse(gate.event(167, false, false, true).perform)
        assertTrue(gate.event(167, true, false, true).perform)
    }
}
