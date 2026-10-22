package com.omer.belestiviclone

import kotlin.test.*

class R38EntryTest {
    private fun admit(c: String? = "R20-C013", s: String? = c?.let(R38Entry::session),
        a: String? = R38Entry.action, cmd: String? = "play", db: String? = "$s.db",
        key: Int = 2, debug: Boolean = true, route: String = R38Entry.activity) =
        R38Entry.allowed(debug, c, s, a, cmd, db, key, route)
    @Test fun exactThirtyAndNoCrossCandidateSession() {
        assertEquals(30, R38Entry.candidates.size)
        for (c in R38Entry.candidates) assertTrue(admit(c))
        for (c in listOf("R20-C014", "R20-C020", "R20-C029", "R20-C002")) assertFalse(admit(c))
        assertFalse(admit(s = R38Entry.session("R20-C018")))
    }
    @Test fun invalidInputsRejectBeforeAnyStore() {
        for (s in listOf(null, "", "belestivi_preview_v1", "../normal", R38Entry.session("R20-C013") + " ")) assertFalse(admit(s = s))
        assertFalse(admit(c = null)); assertFalse(admit(db = "belestivi_preview_v1.db"))
        assertFalse(admit(db = null)); assertFalse(admit(key = 1)); assertFalse(admit(debug = false))
        assertFalse(admit(a = null)); assertFalse(admit(cmd = null)); assertFalse(admit(cmd = "apply"))
        assertFalse(admit(route = "com.omer.belestiviclone.MainActivity"))
    }
    @Test fun malformedR38CannotReachNormalStartup() {
        assertFalse(R38Entry.marked(null, "android.intent.action.MAIN", false))
        assertTrue(R38Entry.marked("toplu-r38-bad", null, false))
        assertTrue(R38Entry.marked(null, R38Entry.action, false))
        assertTrue(R38Entry.marked(null, null, true))
    }
}
