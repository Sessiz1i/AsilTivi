package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class R33ProfileTransitionTest {
    private var seq = 30000L
    private fun allocate() = OpaqueId(99, seq++)
    private val ops = SourceOperations(::allocate)
    private val text = listOf(File("../androidApp/src/main/assets/trlist.m3u"), File("androidApp/src/main/assets/trlist.m3u")).first { it.isFile }.readText()
    private val seed = PublicM3u.catalog(PublicM3u.parse(text), IdentitySupply(List(411) { ChannelId(OpaqueId(1,it.toLong())) },
        List(411) { MembershipId(OpaqueId(2,it.toLong())) }, List(411) { CategoryId(OpaqueId(3,it.toLong())) }))
    private val context = PilotContext(R33ProfileTransition.testSession, VerifiedDefinitionTransition.seedSha256, seed,
        ops.parsed(PublicM3u.source,"trlist",SourceKind.DEFAULT,PublicM3u.url,text))
    private val admission = R33Admission(true,2,"${R33ProfileTransition.testSession}.db",context.session,"apply")
    private val entry = R33ProfileTransition.entry
    private val target = seed.single { it.definition == entry.old }.id
    private fun base(): PreviewData {
        val data = PreviewData(channels=seed,profileId=PublicM3u.profile,sources=listOf(R23ProfileTransition.defaultLabel),
            favorites=listOf(target,seed.last().id),history=listOf(target),hidden=setOf(seed.last().id),hiddenCategories=setOf("Spor"),
            categories=seed.flatMap { it.groups }.distinct().reversed()).withPref("lastChannel",PreviewCodec.id(target))
        val initial = data.copy(library=ops.bootstrap(data).copy(records=listOf(context.seedSource)))
        val r23=VerifiedDefinitionTransition.apply(initial,context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest,::allocate)
        val c=context.copy(session=R26ProfileTransition.testSession)
        val r26=R26ProfileTransition.prepare(r23,c,R26Admission(true,2,"${c.session}.db",c.session,"apply"))
        val c28=context.copy(session=R28ProfileTransition.testSession)
        return R28ProfileTransition.prepare(r26,c28,R28Admission(true,2,"${c28.session}.db",c28.session,"apply"))
    }
    private fun apply(data: PreviewData = base(), action: String = "apply") = R33ProfileTransition.prepare(data,context,admission.copy(action=action))
    private fun rejected(data: PreviewData) {
        val old = runCatching { LibraryCodec.encode(data) }.getOrNull()
        val model = data.copy()
        assertFailsWith<TransitionProblem> { apply(data) };assertEquals(model,data)
        if(old!=null) assertEquals(old,LibraryCodec.encode(data))
    }
    @Test fun compiledDefinitionExactlyMatchesFrozenApprovalAndPrivateManifest() {
        fun sha(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        assertEquals("466ecaa3d44d6c5e2eba1ea7e7ee6561a01a7141af8c64fa641a180669299bbb",sha(entry.old.address.raw))
        assertEquals("996999db7e533ad27fc02ec08846cbde730d965f88cfd6a3694c2ebcdb72b7aa",sha(entry.new.address.raw))
        val manifest=listOf(File("../androidApp/src/debug/assets/r33-transition.private.json"),File("androidApp/src/debug/assets/r33-transition.private.json")).first { it.isFile }
        assertEquals(R33ManifestBinding.sha256,sha(manifest.readText()))
        assertTrue(manifest.readText().contains(entry.old.address.raw));assertTrue(manifest.readText().contains(entry.new.address.raw))
        assertTrue(entry.old.headers.isEmpty() && entry.new.headers.isEmpty())
        assertNull(entry.old.mimeType);assertNull(entry.new.mimeType)
        assertTrue(entry.old.unknownRequiredDirectives.isEmpty() && entry.new.unknownRequiredDirectives.isEmpty())
    }
    @Test fun onlyOneIdDefinitionAndAllRelationsRemain() {
        val old=base();val next=apply(old)
        assertEquals(old.copy(channels=next.channels,library=next.library),next)
        assertEquals(old.channels.map { it.id },next.channels.map { it.id })
        assertEquals(1,old.channels.zip(next.channels).count { it.first != it.second })
        assertEquals(entry.new,next.channels.single { it.id==target }.definition)
        assertEquals(old.channels.filter { it.id!=target },next.channels.filter { it.id!=target })
        assertEquals(old.library!!.transition,next.library!!.transition)
        assertEquals(old.library.r26,next.library.r26)
        assertEquals(old.library.r28,next.library.r28)
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
    @Test fun rollbackPopsOnlyAvazAndKeepsInterveningUserState() {
        val old=base();val changed=apply(old).copy(favorites=old.favorites.reversed(),history=old.history+seed.last().id)
            .withPref("format","Sığdır (Fit)").withPref("lastChannel",PreviewCodec.id(seed.last().id))
        val popped=apply(changed,"rollback")
        assertEquals(changed.copy(channels=popped.channels,library=popped.library),popped)
        assertEquals(old.channels,popped.channels);assertEquals(old.library,popped.library)
        assertNull(popped.library!!.r33);assertTrue(popped.library.transition!!.applied)
        assertEquals("BT-SOURCES-4",PreviewCodec.unpack(LibraryCodec.encode(popped)).first())
        assertSame(popped,apply(popped,"rollback"));assertNotNull(apply(popped).library!!.r33)
    }
    @Test fun r23AndOldPilotActionsFailClosedUntilR33IsPopped() {
        val data=apply();val c=context.copy(session=R23ProfileTransition.testSession)
        listOf("preflight","apply","rollback","legacy-exit").forEach { action ->
            val a=R23Admission(true,2,"${c.session}.db",c.session,action)
            assertFailsWith<TransitionProblem> { R23ProfileTransition.prepare(data,c,a,ApprovedR21.manifest,::allocate) }
        }
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.rollback(data,c,ApprovedR21.manifest) }
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.legacyExit(data,c,ApprovedR21.manifest) }
        val popped=apply(data,"rollback")
        assertFailsWith<TransitionProblem> { VerifiedDefinitionTransition.rollback(popped,c,ApprovedR21.manifest) }
        val c26=context.copy(session=R26ProfileTransition.testSession)
        val a26=R26Admission(true,2,"${c26.session}.db",c26.session,"rollback")
        listOf("preflight","apply","rollback").forEach { action ->
            assertFailsWith<TransitionProblem> { R26ProfileTransition.prepare(data,c26,a26.copy(action=action)) }
        }
        val c28=context.copy(session=R28ProfileTransition.testSession)
        val a28=R28Admission(true,2,"${c28.session}.db",c28.session,"rollback")
        listOf("preflight","apply","rollback").forEach { action ->
            assertFailsWith<TransitionProblem> { R28ProfileTransition.prepare(data,c28,a28.copy(action=action)) }
        }
        val popped28=R28ProfileTransition.prepare(popped,c28,a28)
        val popped26=R26ProfileTransition.prepare(popped28,c26,a26)
        assertFalse(VerifiedDefinitionTransition.rollback(popped26,c,ApprovedR21.manifest).library!!.transition!!.applied)
    }
    @Test fun onlyExactDebugKeyDatabaseSessionAndActionAreAdmitted() {
        listOf(admission.copy(debug=false),admission.copy(key=1),admission.copy(databaseName="other.db"),admission.copy(session=null),
            admission.copy(session="toplu-other"),admission.copy(action=null),admission.copy(action="legacy-exit"),
            admission.copy(session=R33ProfileTransition.normalSession)).forEach { a ->
            assertFalse(R33ProfileTransition.allowed(a));assertFailsWith<TransitionProblem> { R33ProfileTransition.prepare(base(),context,a) }
        }
        val normal=admission.copy(databaseName=R23ProfileTransition.normalDatabase,session=R33ProfileTransition.normalSession)
        assertFalse(R33ProfileTransition.allowed(normal));assertFalse(R33ProfileTransition.allowed(normal.copy(approval="wrong")))
        assertTrue(R33ProfileTransition.allowed(normal.copy(approval=R33ProfileTransition.approval)))
    }
    @Test fun noLibraryMissingOrRolledBackR23IsRejected() {
        val b=base();rejected(b.copy(library=null));rejected(b.copy(library=b.library!!.copy(transition=null)))
        rejected(b.copy(library=b.library.copy(transition=b.library.transition!!.copy(applied=false))))
    }
    @Test fun missingCacheOrChangedIdAndMissingUiAreRejected() {
        val b=base()
        assertFailsWith<TransitionProblem> { R33ProfileTransition.prepare(b,context.copy(seed=seed.dropLast(1)),admission) }
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
        val b=apply();val lib=b.library!!;val j=lib.r33!!
        listOf(j.copy(target=j.target.copy(id=seed.last().id)),j.copy(sourceBefore=j.sourceBefore.copy(name="edited")),
            j.copy(previous=j.previous.copy(previous=j.previous.previous.copy(previous=j.previous.previous.previous.copy(applied=false)))),j.copy(memberships=emptyList())).forEach {
            val bad=b.copy(library=lib.copy(r33=it));rejected(bad)
            assertFailsWith<TransitionProblem> { apply(bad,"rollback") }
        }
    }
    @Test fun pureAuditDoesNotPrepareApplyAndAdmitsOnlyExactOldOrNewState() {
        val b=base();val before=LibraryCodec.encode(b)
        val j=R33ProfileTransition.audit(b,context,admission.copy(action="preflight"))
        assertNull(b.library!!.r33);assertEquals(before,LibraryCodec.encode(b));assertEquals(target,j.target.id)
        val next=apply(b);assertEquals(next.library!!.r33,R33ProfileTransition.audit(next,context,admission.copy(action="preflight")))
        val row=PreviewRow(2,PreviewCodec.pack(b.favorites.map(PreviewCodec::id)),PreviewCodec.pack(b.hidden.map(PreviewCodec::id)),
            PreviewCodec.pack(b.history.map(PreviewCodec::id)),PreviewCodec.pack(b.categories),PreviewCodec.pack(b.hiddenCategories.toList()),
            PreviewCodec.pack(b.preferences.flatMap { listOf(it.key,it.value) }),before)
        assertEquals(b,R33ReadOnlySnapshot.inspect(admission.copy(action="preflight"),row,context))
        listOf(admission,admission.copy(debug=false,action="preflight"),admission.copy(key=1,action="preflight"),admission.copy(session=null,action="preflight")).forEach {
            assertFails { R33ReadOnlySnapshot.inspect(it,row,context) }
        }
        assertFails { R33ReadOnlySnapshot.inspect(admission.copy(action="preflight"),row.copy(key=1),context) }
        assertFails { R33ReadOnlySnapshot.inspect(admission.copy(action="preflight"),row.copy(sources=PreviewCodec.pack(listOf("BT-SOURCES-6"))),context) }
    }
    @Test fun malformedTestEntranceCannotFallThroughToNormalDatabase() {
        listOf(null,"","bad","toplu-?").forEach { assertFalse(R33Entry.isolatedExtraAllowed(true,true,it)) }
        assertFalse(R33Entry.isolatedExtraAllowed(false,true,context.session))
        assertTrue(R33Entry.isolatedExtraAllowed(true,false,null))
        assertTrue(R33Entry.isolatedExtraAllowed(true,true,context.session))
        assertTrue(R33Entry.allowed(true,context.session,R33Entry.action,false))
        listOf(null,"",context.session+"x").forEach { assertTrue(R33Entry.marked(it,R33Entry.action,false));assertFalse(R33Entry.allowed(true,it,R33Entry.action,false)) }
        assertFalse(R33Entry.allowed(false,context.session,R33Entry.action,false));assertFalse(R33Entry.allowed(true,context.session,null,false))
        assertFalse(R33Entry.allowed(true,context.session,R33Entry.action,true))
    }
    @Test fun changedDefaultTextIsRejectedWithBothLayers() {
        val b=apply();assertFailsWith<TransitionProblem> { ops.default(b,text.replace("#EXTM3U","#EXTM3U\n#EXTINF:-1,Extra\nhttps://extra.test/a.m3u8")) }
    }
    @Test fun unchangedCode44ReaderReadsOnlyPoppedEnvelopeExactly() {
        val b=apply();val popped=apply(b,"rollback")
        val jar=listOf(File("../androidApp/src/debug/assets/r33-code44-reader.jar"),File("androidApp/src/debug/assets/r33-code44-reader.jar")).first { it.isFile }
        val filter=object:ClassLoader(javaClass.classLoader) { override fun loadClass(name:String,resolve:Boolean):Class<*> {
            if(name.startsWith("com.omer.belestiviclone.shared."))throw ClassNotFoundException(name)
            return super.loadClass(name,resolve)
        } }
        URLClassLoader(arrayOf(jar.toURI().toURL()),filter).use { loader ->
            val type=loader.loadClass("com.omer.belestiviclone.shared.evidence.R33Code44Bridge")
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
