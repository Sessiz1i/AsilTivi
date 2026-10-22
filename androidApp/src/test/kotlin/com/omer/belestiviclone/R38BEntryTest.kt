package com.omer.belestiviclone

import kotlin.test.*

class R38BEntryTest {
    @Test fun wrongMainRouteNeverInvokesStoreOrCatalogConstructors() {
        val cases = listOf(
            Triple(null, R38AEntry.action, setOf("r38aAction", "candidate")),
            Triple(null, R38AEntry.action, emptySet()),
            Triple(null, "android.intent.action.MAIN", setOf("r38aAction")),
            Triple(null, "com.omer.belestiviclone.R38A_IDENTITY", emptySet()),
            Triple(null, "android.intent.action.MAIN", setOf("r38aIdentityAction")),
            Triple(null, R38BEntry.action, emptySet()),
            Triple(null, null, setOf("r38bAction")),
            Triple("toplu-r38b-broken", null, emptySet()),
            Triple("toplu-r38a-broken", null, emptySet()),
            Triple(null, null, setOf("r38bManifest")),
        )
        cases.forEach { (session, action, keys) ->
            var stores = 0; var catalogs = 0
            assertNull(MainEntryGuard.product(session, action, keys) { stores++; catalogs++; true })
            assertEquals(0, stores); assertEquals(0, catalogs)
        }
    }
    @Test fun unmarkedMainAndLauncherPreserveProductRoute() {
        listOf(null, "android.intent.action.MAIN").forEach { action ->
            var stores = 0; var catalogs = 0
            assertEquals(true, MainEntryGuard.product(null, action, emptySet()) { stores++; catalogs++; true })
            assertEquals(1, stores); assertEquals(1, catalogs)
        }
    }
    private fun admit(debug: Boolean = true, candidate: String? = "R20-C013", session: String? = "toplu-r38b-20261016-c013-main",
        action: String? = R38BEntry.action, command: String? = "suite", database: String? = "toplu-r38b-20261016-c013-main.db",
        key: Int = 2, className: String = R38BEntry.activity, restored: Boolean = false) =
        R38BEntry.allowed(debug, candidate, session, action, command, database, key, className, restored)
    @Test fun exactDedicatedRouteAndAllMalformedDimensions() {
        assertTrue(admit())
        assertFalse(admit(debug=false)); assertFalse(admit(candidate=null)); assertFalse(admit(candidate="R20-C014"))
        assertFalse(admit(session=null)); assertFalse(admit(session="toplu-r38b-20261016-c013-main-x"))
        assertFalse(admit(database=null)); assertFalse(admit(database="belestivi_preview_v1.db"))
        assertFalse(admit(key=1)); assertFalse(admit(key=-1)); assertFalse(admit(action=null)); assertFalse(admit(command=null))
        assertFalse(admit(command="apply")); assertFalse(admit(className="com.omer.belestiviclone.MainActivity")); assertFalse(admit(restored=true))
    }
}
