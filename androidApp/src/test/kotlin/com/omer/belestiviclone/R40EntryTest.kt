package com.omer.belestiviclone

import kotlin.test.*
import org.junit.Test

class R40EntryTest {
    private fun admit(debug:Boolean=true,candidate:String?="R20-C013",session:String?="toplu-r40-20261016-a",
        action:String?=R40Entry.action,command:String?="open",db:String?="toplu-r40-20261016-a.db",key:Int=2,
        clazz:String=R40Entry.activity,restored:Boolean=false) = R40Entry.admit(debug,candidate,session,action,command,db,key,clazz,restored)
    @Test fun exactAdmissionAndEveryDimensionRejects() {
        assertNotNull(admit());assertNotNull(admit(session="toplu-r40-20261016-b",db="toplu-r40-20261016-b.db"))
        listOf(admit(debug=false),admit(candidate=null),admit(candidate="R20-C014"),admit(session=null),
            admit(session="toplu-r38c-20261016-c013-main"),admit(action=null),admit(action="android.intent.action.MAIN"),
            admit(command=null),admit(command="apply"),admit(db=null),admit(db="belestivi_preview_v1.db"),
            admit(db="toplu-r40-20261016-b.db"),admit(key=1),admit(clazz="com.omer.belestiviclone.MainActivity"),admit(restored=true))
            .forEach { assertNull(it) }
    }
    @Test fun ProductRouteMarkersNeverConstructStores() {
        val variants=listOf(Triple(null,null,setOf("r40Action")),Triple(null,R40Entry.action,emptySet()),
            Triple("toplu-r40-20261016-a",null,emptySet()),Triple(null,null,setOf("R40Candidate")))
        for((session,action,keys) in variants) {
            var constructed=0
            assertNull(MainEntryGuard.product(session,action,keys) { constructed++;"store" });assertEquals(0,constructed)
            assertTrue(R40Entry.marked(session,action,keys,"com.omer.belestiviclone.MainActivity"))
        }
        assertTrue(R40Entry.marked(null,null,emptySet(),R40Entry.activity))
        assertFalse(MainEntryGuard.marked(null,"android.intent.action.MAIN",emptySet()))
    }
}

