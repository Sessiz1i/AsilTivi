package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import java.io.File
import java.util.Base64
import kotlin.test.*

class R38AEntryTest {
    @Test fun fixtureBytesMatchCompiledBinding() {
        val bytes = File("src/debug/assets/r38a-c013.private.json").readBytes()
        val actual = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(R38AFixtureBinding.sha256, actual)
    }
    private fun allowed(c: String?=R38AEntry.candidate,s: String?=R38AEntry.session,a: String?=R38AEntry.action,
        cmd: String?="play",db: String?=R38AEntry.database,key: Int=2,debug: Boolean=true,route: String=R38AEntry.activity) =
        R38AEntry.allowed(debug,c,s,a,cmd,db,key,route)
    @Test fun exactSingleCandidateAndAllMalformedDimensionsReject() {
        assertTrue(allowed())
        for(c in listOf(null,"","R20-C018","R20-C014","R20-C013 ")) assertFalse(allowed(c=c))
        for(s in listOf(null,"","toplu-r38-20261016-c-r20-c013",R38AEntry.session+" ","belestivi_preview_v1","../normal")) assertFalse(allowed(s=s))
        for(a in listOf(null,"", "android.intent.action.MAIN",R38Entry.action)) assertFalse(allowed(a=a))
        for(cmd in listOf(null,"","apply","snapshot","play ")) assertFalse(allowed(cmd=cmd))
        for(db in listOf(null,"","belestivi_preview_v1.db",R38AEntry.database+" ")) assertFalse(allowed(db=db))
        for(key in listOf(-1,0,1,3)) assertFalse(allowed(key=key))
        assertFalse(allowed(debug=false)); assertFalse(allowed(route="com.omer.belestiviclone.MainActivity"))
    }
    private fun fixture(): Triple<PreviewData,List<ChannelContribution>,Pair<PlaybackDefinition,PlaybackDefinition>> {
        val text=File("src/main/assets/trlist.m3u").readText(); val parsed=PublicM3u.parse(text)
        val f=File("src/test/resources/r38-fixture-cases.private.tsv").readLines().single { it.startsWith("R20-C013\t") }.split('\t')
        fun d(s: String)=PlaybackDefinition(Address.fromForm(String(Base64.getDecoder().decode(s),Charsets.UTF_8)))
        var seq=1L; fun id()=OpaqueId(0x52333841,seq++)
        val count=parsed.rows.size
        val channels=PublicM3u.catalog(parsed,IdentitySupply(List(count){ChannelId(id())},List(count){MembershipId(id())},List(count){CategoryId(id())}))
        val data=PreviewData(channels=channels); val ops=SourceOperations(::id)
        val lib=ops.bootstrap(data).copy(records=listOf(ops.parsed(PublicM3u.source,"trlist",SourceKind.DEFAULT,PublicM3u.url,text)))
        return Triple(data.copy(library=lib),parsed.rows,d(f[4]) to d(f[5]))
    }
    @Test fun realSeedUniqueOpaqueIdentityAndEmptyProviderHint() {
        val (data,rows,definitions)=fixture(); val (old,new)=definitions
        val target=R38AIdentityAudit.target(data,data.channels,rows,old,new)
        assertEquals(data.channels.single { it.definition==old }.id,target)
        assertEquals("",data.channels.single { it.id==target }.providerId)
    }
    @Test fun duplicateDeletedSourceAndNewComparisonCollisionFailClosed() {
        val (data,rows,definitions)=fixture(); val (old,new)=definitions; val lib=data.library!!
        val duplicate=lib.main.copy(id=SourceId(OpaqueId(987,1)),deleted=true,rows=listOf(rows.single { it.definition==old }.copy(sourceId=SourceId(OpaqueId(987,1)))))
        assertFailsWith<IllegalArgumentException> { R38AIdentityAudit.target(data.copy(library=lib.copy(records=lib.records+duplicate)),data.channels,rows,old,new) }
        val collision=duplicate.copy(rows=duplicate.rows.map { it.copy(definition=new) })
        assertFailsWith<IllegalArgumentException> { R38AIdentityAudit.target(data.copy(library=lib.copy(records=lib.records+collision)),data.channels,rows,old,new) }
        val changed=data.channels.map { if(it.definition==old) it.copy(providerId="TRTTurk.tr") else it }
        assertFailsWith<IllegalArgumentException> { R38AIdentityAudit.target(data.copy(channels=changed),data.channels,rows,old,new) }
    }
}
