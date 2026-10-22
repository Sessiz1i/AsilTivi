package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class R38CProfileTransitionTest {
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val f = R38CSyntheticFixture(text, "toplu-r38c-20261016-c013-main")
    private fun apply(data: PreviewData = f.base(), action: String = "apply") =
        R38CProfileTransition.prepare(data, f.context, f.admission.copy(action=action))
    private fun row(d: PreviewData) = PreviewRow(2, PreviewCodec.pack(d.favorites.map(PreviewCodec::id)), PreviewCodec.pack(d.hidden.map(PreviewCodec::id)),
        PreviewCodec.pack(d.history.map(PreviewCodec::id)), PreviewCodec.pack(d.categories), PreviewCodec.pack(d.hiddenCategories.toList()),
        PreviewCodec.pack(d.preferences.flatMap { listOf(it.key,it.value) }), LibraryCodec.encode(d))
    private fun rejected(d: PreviewData) { assertFails { apply(d) }; assertFails { apply(d,"rollback") } }
    @Test fun onlyExactSyntheticAdmissionCanConstructStore() {
        assertTrue(R38CProfileTransition.allowedSynthetic(f.admission))
        listOf(f.admission.copy(debug=false), f.admission.copy(key=1), f.admission.copy(session=null),
            f.admission.copy(session="toplu-r38c-20261016-c013-main-x"), f.admission.copy(databaseName="belestivi_preview_v1.db"),
            f.admission.copy(databaseName="other.db"), f.admission.copy(action=null), f.admission.copy(manifest="NORMAL_APPROVAL_REQUIRED")).forEach { a ->
            var constructors=0
            assertFalse(R38CProfileTransition.allowedSynthetic(a))
            assertFails { R38CProfileTransition.constructSynthetic(a) { _,_ -> constructors++ } }; assertEquals(0,constructors)
            assertFails { R38CProfileTransition.prepare(f.base(), f.context, a) }
        }
    }
    @Test fun exactTargetThreeRepresentationsAndPreviousJournalsRemain() {
        val b=f.base(); val n=apply(b); val lib=b.library!!;val next=n.library!!
        assertEquals(1,b.channels.zip(n.channels).count { it.first!=it.second })
        assertEquals(b.channels.map { it.id },n.channels.map { it.id })
        assertEquals(b.channels.filter { it.id!=f.target },n.channels.filter { it.id!=f.target })
        assertEquals(b.copy(channels=n.channels,library=n.library),n)
        assertEquals(lib.snapshot.memberships,next.snapshot.memberships);assertEquals(lib.snapshot.categories,next.snapshot.categories)
        assertEquals(lib.snapshot.channels.map { it.id to it.firstSeen },next.snapshot.channels.map { it.id to it.firstSeen })
        assertEquals(lib.transition,next.transition);assertEquals(lib.r26,next.r26);assertEquals(lib.r28,next.r28)
        assertEquals(R38CProfileTransition.entry.new,n.channels.single { it.id==f.target }.definition)
        assertEquals(R38CProfileTransition.entry.new,next.snapshot.channels.single { it.id.value==f.target }.definition)
        assertEquals(R38CProfileTransition.entry.new,next.main.rows.single { it.rowOrder==38L }.definition)
        val planned=planR38C(f.request,row(b));assertEquals(planned.expected.copy(sources=planned.next.sources),planned.next)
    }
    @Test fun noOpAndCodecRoundtripKeepIndependentPreviousJournalBytes() {
        val b=f.base();val n=apply(b);assertSame(n,apply(n))
        assertEquals(n,R31ReadOnlySnapshot.decode(row(n)))
        fun journals(s:String):List<String> { val x=PreviewCodec.unpack(s); return if(x[0]=="BT-SOURCES-1") emptyList() else listOf(x[2])+journals(x[1]) }
        assertEquals(journals(LibraryCodec.encode(b)),journals(PreviewCodec.unpack(LibraryCodec.encode(n))[1]))
        assertEquals(n,f.ops.default(n,text));assertEquals(n,f.ops.rebuild(n,n.library!!))
    }
    @Test fun lifoPopsOnlyC013AndPreservesAllInterveningUserColumns() {
        val b=f.base(); val changed=f.intervening(apply(b));val popped=apply(changed,"rollback")
        assertEquals(changed.copy(channels=popped.channels,library=popped.library),popped)
        assertEquals(b.channels,popped.channels);assertEquals(b.library,popped.library)
        assertEquals("BT-SOURCES-4",PreviewCodec.unpack(row(popped).sources)[0]);assertSame(popped,apply(popped,"rollback"))
        assertEquals(row(changed).copy(sources=row(popped).sources),row(popped))
    }
    @Test fun wrongManifestAndJournalBindingNeverPassAsNoOpOrRollback() {
        val n=apply();val lib=n.library!!;val j=lib.r38c!!
        listOf(j.copy(manifest="wrong"),j.copy(target=j.target.copy(id=f.seed.last().id)),j.copy(memberships=emptyList()),
            j.copy(sourceBefore=j.sourceBefore.copy(name="tampered")),j.copy(previous=j.previous.copy(target=j.previous.target.copy(id=f.target)))).forEach {
            rejected(n.copy(library=lib.copy(r38c=it)))
        }
    }
    @Test fun missingDuplicateAndFullOldDefinitionsAreRejected() {
        val b=f.base();val lib=b.library!!;val e=R38CProfileTransition.entry
        rejected(b.copy(channels=b.channels.filter { it.id!=f.target }))
        rejected(b.copy(channels=b.channels+b.channels.single { it.id==f.target }))
        rejected(b.copy(channels=b.channels.map { if(it.id==f.target) it.copy(definition=e.old.copy(mimeType="video/mp2t")) else it }))
        rejected(b.copy(library=lib.copy(records=lib.records.map { it.copy(rows=it.rows+it.rows.single { r->r.rowOrder==38L }) })))
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(channels=lib.snapshot.channels.filter { it.id.value!=f.target }))))
    }
    @Test fun savedExtraAndDeletedSharingAndNewComparisonCollisionReject() {
        val b=f.base();val lib=b.library!!;val row=lib.main.rows.single { it.rowOrder==38L };val sid=SourceId(OpaqueId(5,5))
        listOf(false,true).forEach { deleted -> listOf(null,PublicM3u.source).forEach { parent ->
            listOf(R38CProfileTransition.entry.old,R38CProfileTransition.entry.new).forEach { d ->
                rejected(b.copy(library=lib.copy(records=lib.records+lib.main.copy(id=sid,kind=SourceKind.FILE,parent=parent,deleted=deleted,rows=listOf(row.copy(sourceId=sid,definition=d))))))
            }
        } }
        rejected(b.copy(channels=b.channels.map { if(it.id==f.seed.last().id) it.copy(definition=R38CProfileTransition.entry.new) else it }))
    }
    @Test fun lossyFieldsWrongSourceAndMembershipRejected() {
        val b=f.base();val lib=b.library!!
        rejected(b.copy(channels=b.channels.map { if(it.id==f.seed.last().id) it.copy(definition=it.definition!!.copy(protocol="unsupported")) else it }))
        rejected(b.copy(library=lib.copy(records=lib.records.map { it.copy(parent=PublicM3u.source) })))
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(memberships=lib.snapshot.memberships.filter { it.channelId.value!=f.target }))))
        val lossy=b.copy(channels=b.channels.map { if(it.id==f.target) it.copy(definition=it.definition!!.copy(options=listOf(NamedPlaybackOption("x","x")))) else it })
        assertFails { LibraryCodec.encode(lossy) }
    }
    @Test fun oldMutatorsMustPopUpperLayerFirst() {
        val n=apply()
        listOf("preflight","apply","rollback").forEach { action ->
            val c26=f.context.copy(session=R26ProfileTransition.testSession);val c28=f.context.copy(session=R28ProfileTransition.testSession);val c33=f.context.copy(session=R33ProfileTransition.testSession)
            assertFails { R26ProfileTransition.prepare(n,c26,R26Admission(true,2,"${c26.session}.db",c26.session,action)) }
            assertFails { R28ProfileTransition.prepare(n,c28,R28Admission(true,2,"${c28.session}.db",c28.session,action)) }
            assertFails { R33ProfileTransition.prepare(n,c33,R33Admission(true,2,"${c33.session}.db",c33.session,action)) }
        }
        assertFails { VerifiedDefinitionTransition.rollback(n,f.context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest) }
        assertFails { f.ops.default(n,text+"\n#EXTINF:-1,Extra\nhttps://example.test/a.m3u8") }
    }
    @Test fun unchangedCode49IndependentReaderReadsPoppedSevenColumnsExactly() {
        val popped=apply(f.intervening(apply()),"rollback");val r=row(popped)
        val jar=listOf(File("../androidApp/src/debug/assets/r38b-code49-reader.jar"),File("androidApp/src/debug/assets/r38b-code49-reader.jar")).first { it.isFile }
        val filter=object:ClassLoader(javaClass.classLoader) { override fun loadClass(name:String,resolve:Boolean):Class<*> {
            if(name.startsWith("com.omer.belestiviclone.shared.")) throw ClassNotFoundException(name)
            return super.loadClass(name,resolve)
        } }
        URLClassLoader(arrayOf(jar.toURI().toURL()),filter).use { loader ->
            val type=loader.loadClass("com.omer.belestiviclone.shared.evidence.R38BCode49Bridge")
            listOf(type.name,"com.omer.belestiviclone.shared.preview.LibraryCodec","com.omer.belestiviclone.shared.preview.PreviewData","com.omer.belestiviclone.shared.catalog.PlaybackDefinition").forEach { assertSame(loader,loader.loadClass(it).classLoader) }
            val input=arrayOf(r.sources,r.favorites,r.hidden,r.history,r.categories,r.hiddenCategories,r.preferences)
            @Suppress("UNCHECKED_CAST") val result=type.getMethod("read",Array<String>::class.java).invoke(null,input as Any) as Array<String>
            assertEquals(input.toList(),result.take(7));assertEquals("408",result[7]);assertEquals(PreviewCodec.pack(popped.channels.map { PreviewCodec.id(it.id) }),result[10])
        }
    }
}
