package com.omer.belestiviclone

import com.omer.belestiviclone.shared.preview.*
import kotlin.test.*

class R38CNormalEntryTest {
    @Test fun allNormalEntryDimensionsFailBeforeStoreConstruction() {
        val a=R38CAdmission(true,2,R23ProfileTransition.normalDatabase,R38CProfileTransition.normalSession,"apply",
            R38CProfileTransition.normalManifest,R38CManifestBinding.approvalSha256,R38CManifestBinding.recordSha256,
            R38CManifestBinding.privateSha256,R38CManifestBinding.manifestSha256,"R20-C013")
        assertTrue(R38CNormalEntry.allowed(a,R38CNormalEntry.action,R38CNormalEntry.activity,false,true))
        assertFalse(R38CNormalEntry.allowed(a,null,R38CNormalEntry.activity,false,true))
        assertFalse(R38CNormalEntry.allowed(a,"android.intent.action.MAIN",R38CNormalEntry.activity,false,true))
        assertFalse(R38CNormalEntry.allowed(a,R38CNormalEntry.action,"com.omer.belestiviclone.MainActivity",false,true))
        assertFalse(R38CNormalEntry.allowed(a,R38CNormalEntry.action,R38CNormalEntry.activity,true,true))
        assertFalse(R38CNormalEntry.allowed(a,R38CNormalEntry.action,R38CNormalEntry.activity,false,false))
        assertFalse(R38CNormalEntry.allowed(a.copy(action="rollback"),R38CNormalEntry.action,R38CNormalEntry.activity,false,true))
        var stores=0
        assertNull(MainEntryGuard.product(null,"android.intent.action.MAIN",setOf("r38cAction")) { stores++ })
        assertEquals(0,stores)
    }
}
