package com.omer.belestiviclone

import kotlin.test.*

class R30EntryTest {
    @Test fun onlyTwoExactSessionsAndDumpProtectedRouteAdmit() {
        for (session in R30Entry.sessions) {
            assertEquals(session, R30Entry.admit(true, session, R30Entry.action, "open", R30Entry.activity))
            assertNull(R30Entry.admit(false, session, R30Entry.action, "open", R30Entry.activity))
            assertNull(R30Entry.admit(true, session, R30Entry.action, "open", "com.omer.belestiviclone.MainActivity"))
            for (action in listOf(null, "", "inspect", "apply")) assertNull(R30Entry.admit(true, session, action, "open", R30Entry.activity))
            for (command in listOf(null, "", "inspect", "apply", "rollback")) assertNull(R30Entry.admit(true, session, R30Entry.action, command, R30Entry.activity))
        }
    }
    @Test fun missingCorruptAndOtherProfilesCannotFallBack() {
        for (session in listOf(null, "", "toplu-r30-render-a-20261015 ", "TOPLU-R30-RENDER-A-20261015", "belestivi_preview_v1", "toplu-r28-ekoturk-20261015-r1", "../normal")) {
            assertTrue(R30Entry.marked(session, R30Entry.action, true, R30Entry.activity))
            assertNull(R30Entry.admit(true, session, R30Entry.action, "open", R30Entry.activity))
        }
    }
    @Test fun unmarkedProductEntryIsUnaffected() {
        assertFalse(R30Entry.marked(null, "android.intent.action.MAIN", false, "com.omer.belestiviclone.MainActivity"))
        assertTrue(R30Entry.marked("toplu-r30-invalid", null, false, "com.omer.belestiviclone.MainActivity"))
    }
}
