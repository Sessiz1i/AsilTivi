package com.omer.belestiviclone.shared.preview

import kotlin.test.*

class R38CNormalAdmissionTest {
    private val a=R38CAdmission(true,2,R23ProfileTransition.normalDatabase,R38CProfileTransition.normalSession,"apply",
        R38CProfileTransition.normalManifest,R38CManifestBinding.approvalSha256,R38CManifestBinding.recordSha256,
        R38CManifestBinding.privateSha256,R38CManifestBinding.manifestSha256,"R20-C013")
    @Test fun exactApprovalOnlyAndNoNormalRollbackOrSyntheticAlias() {
        assertTrue(R38CProfileTransition.allowed(a));assertFalse(R38CProfileTransition.allowedSynthetic(a))
        listOf(a.copy(debug=false),a.copy(key=1),a.copy(databaseName="other.db"),a.copy(session=null),a.copy(action=null),
            a.copy(action="rollback"),a.copy(manifest=R38CProfileTransition.fixtureManifest),a.copy(candidate="R20-C014"),
            a.copy(approvalSha256=null),a.copy(approvalSha256="wrong"),a.copy(recordSha256="wrong"),
            a.copy(privateSha256="wrong"),a.copy(manifestSha256="wrong")).forEach { bad ->
            var opened=0
            assertFalse(R38CProfileTransition.allowed(bad))
            assertFails { R38CProfileTransition.construct(bad) { _,_->opened++ } };assertEquals(0,opened)
        }
    }
    @Test fun realIdCannotBeAdmittedThroughSyntheticContext() {
        val text=listOf(java.io.File("../androidApp/src/main/assets/trlist.m3u"),java.io.File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
        val f=R38CSyntheticFixture(text,"toplu-r38c-20261016-c013-main")
        assertFails { R38CProfileTransition.audit(f.base(),f.context.copy(session=a.session!!),a) }
        assertFails { R38CProfileTransition.audit(f.base(),f.context,f.admission.copy(candidate="wrong")) }
        assertFalse(R38BProfileTransition.allowed(R38BAdmission(true,2,a.databaseName,a.session,"apply")))
    }
    @Test fun encodedJournalRejectsChangedApprovalAndManifestHashes() {
        val text=listOf(java.io.File("../androidApp/src/main/assets/trlist.m3u"),java.io.File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
        val f=R38CSyntheticFixture(text,"toplu-r38c-20261016-c013-main")
        val applied=R38CProfileTransition.prepare(f.base(),f.context,f.admission)
        val outer=PreviewCodec.unpack(LibraryCodec.encode(applied))
        (6..9).forEach { index ->
            val journal=PreviewCodec.unpack(outer[2]).toMutableList();journal[index]="wrong"
            assertFails { LibraryCodec.decode(PreviewCodec.pack(listOf(outer[0],outer[1],PreviewCodec.pack(journal))),PreviewData()) }
        }
    }
}
