package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONObject

/** Shell DUMP entry, explicit single-record action; absent from product startup/release. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class R28ProfileInspectionActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: AndroidPreviewPlayer? = null
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun admission(action: String): R28Admission {
        val isolated = intent.getStringExtra("isolatedProfile")
        require(isolated == null || isolated == R28ProfileTransition.testSession)
        return R28Admission(BuildConfig.DEBUG, 2,
            if (isolated == null) R23ProfileTransition.normalDatabase else "$isolated.db",
            isolated ?: R28ProfileTransition.normalSession, action, intent.getStringExtra("approval"))
    }
    private fun store(a: R28Admission) = AndroidPreviewStore(applicationContext, key=a.key,databaseName=a.databaseName) { error("R28-initializer-forbidden") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command=intent.getStringExtra("r28Action")
        scope.launch {
            try {
                when(command) {
                    "test-suite" -> { require(intent.getStringExtra("isolatedProfile")==R28ProfileTransition.testSession);suite() }
                    "preflight","apply","rollback","inspect","default-check","play" -> {
                        val a=admission(if(command in listOf("inspect","default-check","play")) "preflight" else command!!)
                        require(getDatabasePath(a.databaseName).isFile) { "missing-database" }
                        val request=androidR28Request(applicationContext,a)
                        val s=store(a)
                        try {
                            val p=if(command in listOf("apply","rollback")) s.executeR28(request,requireNotNull(intent.getStringExtra("expectedRevision"))) else s.prepareR28(request)
                            summary("$command-before",p.before,p.expected)
                            val row=requireNotNull(s.readRow())
                            if(command in listOf("apply","rollback")) {
                                check(row==p.next);summary("$command-after",p.after,row)
                                Log.i("BelesR28","action=$command result=${if(p.expected==p.next) "no-op" else "committed"} userColumnsEqual=${p.expected.copy(sources=row.sources)==row}")
                            } else {
                                check(row==p.expected);Log.i("BelesR28","action=$command noWrite=true eligible=true expectedRevision=${r23Revision(row)}")
                            }
                            if(command=="default-check") {
                                val ops=SourceOperations { error("unexpected-allocation") }
                                check(ops.default(p.before,assets.open("trlist.m3u").bufferedReader().use { it.readText() })==p.before)
                                check(ops.rebuild(p.before,p.before.library!!)==p.before)
                                Log.i("BelesR28","default-rebuild noWrite=true sameModel=true PASS")
                            }
                            if(command=="play") play(p.before,request)
                        } finally { s.close() }
                    }
                    else -> throw TransitionProblem("missing-or-invalid-action")
                }
            } catch(e:Exception) {
                Log.e("BelesR28","FAILED action=$command type=${e.javaClass.simpleName} code=${(e as? TransitionProblem)?.code ?: "check-failed"} noInitializer=true")
            } finally { withContext(Dispatchers.Main) { player?.release();player=null;finish() } }
        }
    }
    private fun summary(stage:String,data:PreviewData,row:PreviewRow) {
        val lib=data.library!!;val e=R28ProfileTransition.entry
        val ch=data.channels.single { it.definition in listOf(e.old,e.new) }
        fun digest(items:List<String>)=hash(PreviewCodec.pack(items))
        val f=PreviewCodec.unpack(row.sources)
        val r26=if(f[0]=="BT-SOURCES-4") PreviewCodec.unpack(f[1]) else f
        val inner=if(r26[0]=="BT-SOURCES-3") PreviewCodec.unpack(r26[1]) else r26
        val info=JSONObject().put("stage",stage).put("session",admission("preflight").session).put("revision",r23Revision(row))
            .put("sourcesSha256",hash(row.sources)).put("envelope",f[0]).put("r28Applied",lib.r28!=null)
            .put("r26Applied",lib.r26!=null).put("r26JournalSha256",hash(r26[2]))
            .put("r23Applied",lib.transition!!.applied).put("r23JournalSha256",hash(inner[2]))
            .put("channels",data.channels.size).put("memberships",lib.snapshot.memberships.size).put("categories",lib.snapshot.categories.size)
            .put("favorites",data.favorites.size).put("history",data.history.size)
            .put("idsSha256",digest(data.channels.map { PreviewCodec.id(it.id) }))
            .put("other407Sha256",hash(LibraryCodec.channels(data.channels.filter { it.id!=ch.id })))
            .put("metadataSha256",hash(LibraryCodec.channels(data.channels.map { it.copy(definition=null) })))
            .put("membershipsSha256",digest(lib.snapshot.memberships.map { PreviewCodec.pack(listOf(PreviewCodec.id(it.id.value),PreviewCodec.id(it.channelId.value),PreviewCodec.id(it.sourceId.value),PreviewCodec.id(it.categoryId.value),it.isCurrent.toString())) }))
            .put("categoryIdsSha256",digest(lib.snapshot.categories.map { "${PreviewCodec.id(it.id.value)}:${it.label}:${it.firstSeen}" }))
            .put("firstSeenSha256",digest(lib.snapshot.channels.map { "${PreviewCodec.id(it.id.value)}:${it.firstSeen}" }))
        listOf("favorites" to row.favorites,"hidden" to row.hidden,"history" to row.history,"categories" to row.categories,
            "hiddenCategories" to row.hiddenCategories,"preferences" to row.preferences).forEach { (n,v)->info.put(n+"Sha256",hash(v)) }
        Log.i("BelesR28","state=$info")
        (ApprovedR21.manifest.entries+R26ProfileTransition.entry+e).forEach { entry ->
            val c=data.channels.single { it.definition in listOf(entry.old,entry.new) };val d=c.definition!!
            val serialized=PreviewCodec.pack(listOf(d.address.raw,PreviewCodec.pack(d.headers.flatMap { listOf(it.name,it.value) }),d.mimeType.orEmpty(),PreviewCodec.pack(d.unknownRequiredDirectives)))
            Log.i("BelesR28","stage=$stage candidate=${entry.candidate} id=${PreviewCodec.id(c.id)} old=${d==entry.old} uriSha256=${hash(d.address.raw)} definitionSha256=${hash(serialized)}")
        }
    }
    private suspend fun play(data:PreviewData,request:R28Request) {
        val id=request.context.seed.single { it.definition==R28ProfileTransition.entry.old }.id
        val channel=data.channels.single { it.id==id };check(channel.definition==R28ProfileTransition.entry.new)
        val first=CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            val texture=TextureView(this@R28ProfileInspectionActivity);setContentView(texture)
            player=AndroidPreviewPlayer(applicationContext) { Log.i("BelesR28","probe-played historyWrite=false") }
            player!!.exo.addAnalyticsListener(object:androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onRenderedFirstFrame(eventTime:androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,output:Any,renderTimeMs:Long) {
                    first.complete(Unit);Log.i("BelesR28","probe-first-frame id=${PreviewCodec.id(id)} historyWrite=false")
                }
            })
            player!!.exo.setVideoTextureView(texture);player!!.select(channel)
        }
        withTimeout(25000) { first.await() }
        withTimeout(10000) { withContext(Dispatchers.Main) {
            while(player!!.exo.playbackState!=androidx.media3.common.Player.STATE_READY || !player!!.exo.isPlaying) delay(100)
        } }
        val positions=mutableListOf<Long>()
        repeat(7) {
            withContext(Dispatchers.Main) {
                check(player!!.exo.playbackState==androidx.media3.common.Player.STATE_READY && player!!.exo.isPlaying)
                positions+=player!!.exo.currentPosition
            }
            if(it<6) delay(1000)
        }
        check(positions.zipWithNext().any { (a,b)->b>a })
        Log.i("BelesR28","probe-complete id=${PreviewCodec.id(id)} uriSha256=${hash(channel.definition!!.address.raw)} firstFrame=true READY=true positions=${positions.joinToString(",")} historyWrite=false PASS")
        if(request.admission.session==R28ProfileTransition.testSession) {
            File(filesDir,"${R28ProfileTransition.testSession}-media-proof").writeText(r28RuntimeProof(applicationContext))
        }
        delay(10000) // Keep the successful test frame visible for a bounded evidence capture.
    }
    private fun oldReader(row:PreviewRow) {
        val bytes=assets.open("r26-code41-reader.dex").use { it.readBytes() }
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest==R28OldReaderBinding.dexSha256)
        val dex=File(codeCacheDir,"r28-code41-reader.dex")
        if(!dex.exists()) dex.outputStream().use { check(dex.setReadOnly());it.write(bytes) }
        check(dex.readBytes().contentEquals(bytes))
        val filtered=object:ClassLoader(javaClass.classLoader) { override fun loadClass(name:String,resolve:Boolean):Class<*> {
            if(name.startsWith("com.omer.belestiviclone.shared."))throw ClassNotFoundException(name)
            return super.loadClass(name,resolve)
        } }
        val loader=DexClassLoader(dex.path,codeCacheDir.path,null,filtered)
        val type=loader.loadClass("com.omer.belestiviclone.shared.evidence.R26Bridge")
        check(type.classLoader===loader && loader.loadClass("com.omer.belestiviclone.shared.preview.LibraryCodec").classLoader===loader)
        val input=arrayOf(row.sources,row.favorites,row.hidden,row.history,row.categories,row.hiddenCategories,row.preferences)
        @Suppress("UNCHECKED_CAST") val result=type.getMethod("read",Array<String>::class.java).invoke(null,input as Any) as Array<String>
        check(result.take(7)==input.toList() && result[7]=="408")
        Log.i("BelesR28","code41-reader rowExact=true dexSha256=$digest PASS")
    }
    private suspend fun suite() {
        val a=admission("apply");check(a.session==R28ProfileTransition.testSession)
        check(!getDatabasePath(a.databaseName).exists()) { "suite-requires-new-profile" }
        check(!r28RuntimeGate(applicationContext))
        val normal=a.copy(session=R28ProfileTransition.normalSession,databaseName=R23ProfileTransition.normalDatabase,approval=R28ProfileTransition.approval)
        try { androidR28Request(applicationContext,normal);error("gate-accepted") } catch(e:TransitionProblem) { check(e.code=="r28-test-gate") }
        try { AndroidPublicCatalog(applicationContext,"${a.session}-absent").readExisting();error("missing-cache-accepted") }
        catch(e:TransitionProblem) { check(e.code=="missing-or-unsettled-cache") }
        val seed=AndroidPublicCatalog(applicationContext,"${a.session}-catalog").load()
        val r=androidR28Request(applicationContext,a)
        val id=seed.single { it.definition==R28ProfileTransition.entry.old }.id
        val initial=PreviewData(channels=seed,profileId=PublicM3u.profile,sources=listOf(R23ProfileTransition.defaultLabel),
            favorites=listOf(id,seed.last().id),history=listOf(id),hidden=setOf(seed.last().id),hiddenCategories=setOf("Spor"),
            categories=seed.flatMap { it.groups }.distinct()).withPref("lastChannel",PreviewCodec.id(id))
            .withPref("history","Kapalı").withPref("startup","Kapalı")
        val ops=SourceOperations { java.util.UUID.randomUUID().let { OpaqueId(it.mostSignificantBits,it.leastSignificantBits) } }
        val withLibrary=initial.copy(library=ops.bootstrap(initial).copy(records=listOf(r.context.seedSource)))
        val r23=VerifiedDefinitionTransition.apply(withLibrary,r.context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest) { error("no-channel-allocation") }
        val r26context=r.context.copy(session=R26ProfileTransition.testSession)
        val r26admission=R26Admission(true,2,"${r26context.session}.db",r26context.session,"apply")
        val base=R26ProfileTransition.prepare(r23,r26context,r26admission)
        val s=AndroidPreviewStore(applicationContext,key=a.key,databaseName=a.databaseName) {
            PreviewData(channels=emptyList(),profileId=PublicM3u.profile)
        }
        try {
            try { s.prepareR28(r);error("missing-row-accepted") } catch(e:TransitionProblem) { check(e.code=="missing-row") }
            check(s.readRow()==null);s.save(base)
            val before=requireNotNull(s.readRow());summary("suite-before",base,before)
            listOf(a.copy(debug=false),a.copy(key=1),a.copy(databaseName=R23ProfileTransition.normalDatabase),a.copy(action=null),normal.copy(approval=null)).forEach { bad ->
                try { s.prepareR28(r.copy(admission=bad));error("bad-gate-accepted") } catch(e:TransitionProblem) { check(e.code=="r28-disabled-profile") }
                check(s.readRow()==before)
            }
            val p=s.prepareR28(r);check(s.readRow()==before)
            check(!s.compareAndSet(before.copy(history="stale"),p.next));check(s.readRow()==before)
            try { s.executeR28(r,"stale");error("accepted") } catch(e:TransitionProblem) { check(e.code=="stale-preflight") }
            try { commitTransition(before,p.next) { x,y->s.compareAndSet(x,y) { error("after-write") } };error("accepted") }
            catch(e:IllegalStateException) { check(e.message=="after-write") };check(s.readRow()==before)
            coroutineScope {
                val ready=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>();var entered=false
                val job=launch { val prepared=s.prepareR28(r);ready.complete(Unit);proceed.await();commitTransition(prepared.expected,prepared.next) { x,y->entered=true;s.compareAndSet(x,y) } }
                ready.await();job.cancel();proceed.complete(Unit);job.join();check(!entered && s.readRow()==before)
            }
            coroutineScope {
                val entered=CompletableDeferred<Unit>();val proceed=CompletableDeferred<Unit>()
                val job=launch { commitTransition(before,p.next) { x,y->s.compareAndSet(x,y) { entered.complete(Unit);proceed.await() } } }
                entered.await();job.cancel();proceed.complete(Unit);job.join()
            }
            check(s.readRow()==p.next)
            Log.i("BelesR28","atomic stale-row stale-revision SQL-failure cancelled-preparation cancelled-boundary PASS")
            summary("suite-applied",p.after,p.next)
            val noOp=s.executeR28(r,r23Revision(p.next));check(noOp.next==noOp.expected)
            try { s.save(base);error("stale-save-accepted") } catch(e:IllegalArgumentException) { check(e.message=="stale-source-journal") }
            check(s.readRow()==p.next)
            check(s.load()==p.after);check(ops.default(p.after,assets.open("trlist.m3u").bufferedReader().use { it.readText() })==p.after)
            check(ops.rebuild(p.after,p.after.library!!)==p.after)
            listOf("apply","rollback","legacy-exit").forEach { action ->
                try { R23ProfileTransition.prepare(p.after,r.context.copy(session=R23ProfileTransition.testSession),
                    R23Admission(true,2,"${R23ProfileTransition.testSession}.db",R23ProfileTransition.testSession,action),ApprovedR21.manifest) { error("allocate") };error("old-action-accepted") }
                catch(e:TransitionProblem) { check(e.code=="r28-layer-must-rollback-first") }
            }
            listOf("preflight","apply","rollback").forEach { action ->
                try { R26ProfileTransition.prepare(p.after,r26context,r26admission.copy(action=action));error("r26-action-accepted") }
                catch(e:TransitionProblem) { check(e.code=="r28-layer-must-rollback-first") }
            }
            val intervening=p.after.copy(favorites=initial.favorites.reversed(),history=initial.history+seed.last().id)
                .withPref("format","Sığdır (Fit)").withPref("opacity","49")
            s.save(intervening)
            val rollback=r.copy(admission=a.copy(action="rollback"))
            val popped=s.executeR28(rollback,r23Revision(requireNotNull(s.readRow())))
            check(popped.expected.copy(sources=popped.next.sources)==popped.next)
            check(popped.after.channels==base.channels && popped.after.library==base.library)
            check(popped.after.favorites==intervening.favorites && popped.after.history==intervening.history && popped.after.preferences==intervening.preferences)
            summary("suite-rollback",popped.after,popped.next);oldReader(popped.next)
            check(s.load()==popped.after);check(ops.default(popped.after,assets.open("trlist.m3u").bufferedReader().use { it.readText() })==popped.after)
            // Prove complete LIFO in memory; the separate R28 test DB remains on the three-update base.
            val r26popped=R26ProfileTransition.prepare(popped.after,r26context,r26admission.copy(action="rollback"))
            check(r26popped.library!!.r26==null && r26popped.library!!.transition!!.applied)
            val r23popped=VerifiedDefinitionTransition.rollback(r26popped,r.context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest)
            check(!r23popped.library!!.transition!!.applied)
            val legacy=VerifiedDefinitionTransition.legacyExit(r23popped,r.context.copy(session=R23ProfileTransition.testSession),ApprovedR21.manifest)
            check(legacy.library!!.transition==null)
            Log.i("BelesR28","LIFO R28-R26-R23-legacy inMemory=true PASS")
            val noRollback=s.executeR28(rollback,r23Revision(popped.next));check(noRollback.expected==noRollback.next)
            val final=s.executeR28(r,r23Revision(popped.next));summary("suite-reapply",final.after,final.next)
            File(filesDir,"${R28ProfileTransition.testSession}-suite-proof").writeText(r28RuntimeProof(applicationContext))
            Log.i("BelesR28","SUITE PASS noOp default rebuild reopen staleSave LIFO rollback code41 gates realProfileOpened=false r21Opened=false r22Opened=false r23Opened=false")
        } finally { s.close() }
    }
}
