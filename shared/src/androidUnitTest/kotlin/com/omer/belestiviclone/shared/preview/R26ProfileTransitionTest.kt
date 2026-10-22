package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class R26ProfileTransitionTest {
    private var seq = 30000L
    private fun allocate() = OpaqueId(99, seq++)
    private val ops = SourceOperations(::allocate)
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val seed = PublicM3u.catalog(PublicM3u.parse(text), IdentitySupply(List(411) { ChannelId(OpaqueId(1,it.toLong())) },
        List(411) { MembershipId(OpaqueId(2,it.toLong())) }, List(411) { CategoryId(OpaqueId(3,it.toLong())) }))
    private val context = PilotContext(R26ProfileTransition.testSession, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source,"trlist",SourceKind.DEFAULT,PublicM3u.url,text))
    private val admission = R26Admission(true,2,"${R26ProfileTransition.testSession}.db",context.session,"apply")
    private val entry = R26ProfileTransition.entry
    private val target = seed.single { it.definition == entry.old }.id
    private fun base(): PreviewData {
        val data = PreviewData(channels=seed,profileId=PublicM3u.profile,sources=listOf(R23ProfileTransition.defaultLabel),
            favorites=listOf(target,seed.last().id),history=listOf(target),hidden=setOf(seed.last().id),hiddenCategories=setOf("Spor"),
            categories=seed.flatMap { it.groups }.distinct().reversed()).withPref("lastChannel",PreviewCodec.id(target))
        val initial = data.copy(library=ops.bootstrap(data).copy(records=listOf(context.seedSource)))
        return VerifiedDefinitionTransition.apply(initial,context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest,::allocate)
    }
    private fun apply(data: PreviewData = base(), action: String = "apply") = R26ProfileTransition.prepare(data,context,admission.copy(action=action))
    private fun rejected(data: PreviewData) {
        val old = runCatching { LibraryCodec.encode(data) }.getOrNull()
        val model = data.copy()
        assertFailsWith<TransitionProblem> { apply(data) };assertEquals(model,data)
        if(old!=null) assertEquals(old,LibraryCodec.encode(data))
    }
    @Test fun onlyOneIdDefinitionAndAllRelationsRemain() {
        val old=base();val next=apply(old)
        assertEquals(old.copy(channels=next.channels,library=next.library),next)
        assertEquals(old.channels.map { it.id },next.channels.map { it.id })
        assertEquals(1,old.channels.zip(next.channels).count { it.first != it.second })
        assertEquals(entry.new,next.channels.single { it.id==target }.definition)
        assertEquals(old.channels.filter { it.id!=target },next.channels.filter { it.id!=target })
        assertEquals(old.library!!.transition,next.library!!.transition)
        assertEquals(old.library.snapshot.memberships,next.library.snapshot.memberships)
        assertEquals(old.library.snapshot.categories,next.library.snapshot.categories)
        assertEquals(old.library.snapshot.channels.map { it.id to it.firstSeen },next.library.snapshot.channels.map { it.id to it.firstSeen })
    }
    @Test fun noOpReopenDefaultAndRebuildAllocateNothing() {
        val next=apply();assertSame(next,apply(next))
        val reopened=LibraryCodec.decode(LibraryCodec.encode(next),next.copy(library=null,channels=emptyList()))
        assertEquals(next,reopened)
        val noAllocation=SourceOperations { error("unexpected-allocation") }
        assertEquals(next,noAllocation.default(reopened,text));assertEquals(next,noAllocation.rebuild(reopened,reopened.library!!))
    }
    @Test fun rollbackPopsOnly24AndKeepsInterveningUserState() {
        val old=base();val changed=apply(old).copy(favorites=old.favorites.reversed(),history=old.history+seed.last().id)
            .withPref("format","Sığdır (Fit)").withPref("lastChannel",PreviewCodec.id(seed.last().id))
        val popped=apply(changed,"rollback")
        assertEquals(changed.copy(channels=popped.channels,library=popped.library),popped)
        assertEquals(old.channels,popped.channels);assertEquals(old.library,popped.library)
        assertNull(popped.library!!.r26);assertTrue(popped.library.transition!!.applied)
        assertEquals("BT-SOURCES-2",PreviewCodec.unpack(LibraryCodec.encode(popped)).first())
        assertSame(popped,apply(popped,"rollback"));assertNotNull(apply(popped).library!!.r26)
    }
    @Test fun r23AndOldPilotActionsFailClosedUntilR26IsPopped() {
        val data=apply();val c=context.copy(session=R23ProfileTransition.testSession)
        listOf("preflight","apply","rollback","legacy-exit").forEach { action ->
            val a=R23Admission(true,2,"${c.session}.db",c.session,action)
            assertFailsWith<TransitionProblem> { R23ProfileTransition.prepare(data,c,a,ApprovedR21.manifest,::allocate) }
        }
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.rollback(data,c,ApprovedR21.manifest) }
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.legacyExit(data,c,ApprovedR21.manifest) }
        val popped=apply(data,"rollback")
        assertFalse(VerifiedDefinitionTransition.rollback(popped,c,ApprovedR21.manifest).library!!.transition!!.applied)
    }
    @Test fun onlyExactDebugKeyDatabaseSessionAndActionAreAdmitted() {
        listOf(admission.copy(debug=false),admission.copy(key=1),admission.copy(databaseName="other.db"),admission.copy(session=null),
            admission.copy(session="toplu-other"),admission.copy(action=null),admission.copy(action="legacy-exit"),
            admission.copy(session=R26ProfileTransition.normalSession)).forEach { a ->
            assertFalse(R26ProfileTransition.allowed(a));assertFailsWith<TransitionProblem> { R26ProfileTransition.prepare(base(),context,a) }
        }
        val normal=admission.copy(databaseName=R23ProfileTransition.normalDatabase,session=R26ProfileTransition.normalSession)
        assertFalse(R26ProfileTransition.allowed(normal));assertFalse(R26ProfileTransition.allowed(normal.copy(approval="wrong")))
        assertTrue(R26ProfileTransition.allowed(normal.copy(approval=R26ProfileTransition.approval)))
    }
    @Test fun noLibraryMissingOrRolledBackR23IsRejected() {
        val b=base();rejected(b.copy(library=null));rejected(b.copy(library=b.library!!.copy(transition=null)))
        rejected(b.copy(library=b.library.copy(transition=b.library.transition!!.copy(applied=false))))
    }
    @Test fun missingCacheOrChangedIdAndMissingUiAreRejected() {
        val b=base()
        assertFailsWith<TransitionProblem> { R26ProfileTransition.prepare(b,context.copy(seed=seed.dropLast(1)),admission) }
        rejected(b.copy(channels=b.channels.filter { it.id!=target }))
        rejected(b.copy(channels=b.channels.map { if(it.id==target) it.copy(id=OpaqueId(7,7)) else it }))
    }
    @Test fun fullDefinitionRequiredAndUnsupportedFieldRejected() {
        val b=base();val lib=b.library!!
        rejected(b.copy(channels=b.channels.map { if(it.id==target) it.copy(definition=entry.old.copy(mimeType="video/mp2t")) else it }))
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(channels=lib.snapshot.channels.map { if(it.id.value==target) it.copy(definition=entry.old.copy(protocol="other")) else it }))))
        rejected(b.copy(channels=b.channels.map { if(it.id==seed.last().id) it.copy(definition=it.definition!!.copy(options=listOf(NamedPlaybackOption("unsupported","x")))) else it }))
    }
    @Test fun newKeyCollisionAcrossSnapshotUiOrOtherSourceRejected() {
        val b=base();val lib=b.library!!;val other=seed.last().id
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(channels=lib.snapshot.channels.map { if(it.id.value==other) it.copy(definition=entry.new) else it }))))
        rejected(b.copy(channels=b.channels.map { if(it.id==other) it.copy(definition=entry.new) else it }))
        val sid=SourceId(OpaqueId(8,8));val row=lib.main.rows.single { it.definition==entry.old }
        listOf(entry.old,entry.new).forEach { d -> rejected(b.copy(library=lib.copy(records=lib.records+lib.main.copy(id=sid,kind=SourceKind.URL,rows=listOf(row.copy(sourceId=sid,definition=d)))))) }
    }
    @Test fun sharedMembershipMissingSnapshotRowAndEditedSourceFail() {
        val b=base();val lib=b.library!!
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(memberships=lib.snapshot.memberships.filter { it.channelId.value!=target }))))
        val m=lib.snapshot.memberships.first { it.channelId.value==target }
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(memberships=lib.snapshot.memberships+m.copy(id=MembershipId(OpaqueId(9,9)),sourceId=SourceId(OpaqueId(8,8)))))))
        rejected(b.copy(library=lib.copy(records=lib.records.map { it.copy(rows=it.rows.filter { r->r.definition!=entry.old }) })))
        rejected(b.copy(library=lib.copy(snapshot=lib.snapshot.copy(channels=lib.snapshot.channels.filter { it.id.value!=target }))))
        rejected(b.copy(library=lib.copy(records=lib.records.map { it.copy(parent=PublicM3u.source) })))
    }
    @Test fun tamperedLayerNeverPassesAsNoOpOrRollback() {
        val b=apply();val lib=b.library!!;val j=lib.r26!!
        listOf(j.copy(target=j.target.copy(id=seed.last().id)),j.copy(sourceBefore=j.sourceBefore.copy(name="edited")),
            j.copy(previous=j.previous.copy(applied=false)),j.copy(memberships=emptyList())).forEach {
            val bad=b.copy(library=lib.copy(r26=it));rejected(bad)
            assertFailsWith<TransitionProblem> { apply(bad,"rollback") }
        }
    }
    @Test fun changedDefaultTextIsRejectedWithBothLayers() {
        val b=apply();assertFailsWith<TransitionProblem> { ops.default(b,text.replace("#EXTM3U","#EXTM3U\n#EXTINF:-1,Extra\nhttps://extra.test/a.m3u8")) }
    }
    @Test fun unchangedCode39ReaderReadsOnlyPoppedEnvelopeExactly() {
        val b=apply();val popped=apply(b,"rollback")
        val jar=listOf(File("../androidApp/src/debug/assets/r23-code39-reader.jar"),File("androidApp/src/debug/assets/r23-code39-reader.jar")).first { it.isFile }
        val filter=object:ClassLoader(javaClass.classLoader) { override fun loadClass(name:String,resolve:Boolean):Class<*> {
            if(name.startsWith("com.omer.belestiviclone.shared."))throw ClassNotFoundException(name)
            return super.loadClass(name,resolve)
        } }
        URLClassLoader(arrayOf(jar.toURI().toURL()),filter).use { loader ->
            val type=loader.loadClass("com.omer.belestiviclone.shared.evidence.R23Bridge")
            assertSame(loader,loader.loadClass("com.omer.belestiviclone.shared.preview.LibraryCodec").classLoader)
            val f=type.getMethod("read",Array<String>::class.java)
            val encoded=LibraryCodec.encode(popped)
            val row=arrayOf(encoded,PreviewCodec.pack(popped.favorites.map(PreviewCodec::id)),PreviewCodec.pack(popped.hidden.map(PreviewCodec::id)),
                PreviewCodec.pack(popped.history.map(PreviewCodec::id)),PreviewCodec.pack(popped.categories),PreviewCodec.pack(popped.hiddenCategories.toList()),
                PreviewCodec.pack(popped.preferences.flatMap { listOf(it.key,it.value) }))
            @Suppress("UNCHECKED_CAST") val result=f.invoke(null,row as Any) as Array<String>
            assertEquals(row.toList(),result.take(7));assertEquals("408",result[7])
            assertFails { f.invoke(null,row.copyOf().also { it[0]=LibraryCodec.encode(b) } as Any) }
        }
    }
}
