package com.omer.belestiviclone

import kotlin.test.*
import org.junit.Test

class R42EntryTest {
    private fun admit(debug: Boolean = true, candidate: String? = R42Entry.candidate,
        session: String? = "toplu-r42-20261017-a", action: String? = R42Entry.action, command: String? = "open",
        db: String? = "toplu-r42-20261017-a.db", key: Int = 2, clazz: String = R42Entry.activity, restored: Boolean = false) =
        R42Entry.admit(debug, candidate, session, action, command, db, key, clazz, restored)
    @Test fun exactAdmissionRejectsEveryWrongDimension() {
        assertNotNull(admit()); assertNotNull(admit(session="toplu-r42-20261017-b",db="toplu-r42-20261017-b.db"))
        listOf(admit(debug=false),admit(candidate=null),admit(candidate="SHAKA-ANGEL-ONE"),admit(session=null),
            admit(session="toplu-r42-20261017-c"),admit(session="TOPLU-R42-20261017-A"),admit(action=null),
            admit(action="android.intent.action.MAIN"),admit(command=null),admit(command="apply"),admit(db=null),
            admit(db="belestivi_preview_v1.db"),admit(db="toplu-r42-20261017-b.db"),admit(key=1),admit(key=-1),
            admit(clazz="com.omer.belestiviclone.MainActivity"),admit(restored=true)).forEach { assertNull(it) }
    }
    @Test fun malformedMarkersCannotConstructNormalStoresOrCatalogue() {
        val variants=listOf(Triple(null,null,setOf("r42Action")),Triple(null,null,setOf("R42Unknown")),
            Triple(null,R42Entry.action,emptySet()),Triple("toplu-r42-missing",null,emptySet()),
            Triple("TOPLU-R42-invalid",null,emptySet()),Triple(null,"COM.OMER.BELESTIVICLONE.R42_BAD",emptySet()))
        for ((session,action,keys) in variants) {
            var stores=0; var catalogues=0
            assertNull(MainEntryGuard.product(session,action,keys) { stores++;catalogues++;"constructed" })
            assertEquals(0,stores);assertEquals(0,catalogues)
            assertTrue(R42Entry.marked(session,action,keys,"com.omer.belestiviclone.MainActivity"))
        }
        assertTrue(R42Entry.marked(null,null,emptySet(),R42Entry.activity))
        assertEquals("normal",MainEntryGuard.product(null,"android.intent.action.MAIN",emptySet()) { "normal" })
    }
}
