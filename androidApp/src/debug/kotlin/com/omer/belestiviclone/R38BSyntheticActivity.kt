package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import dalvik.system.DexClassLoader
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONObject

/** DUMP-only, no product navigation/player/network and no normal database admission. */
class R38BSyntheticActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun hash(value: String) = hash(value.toByteArray(Charsets.UTF_8))
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun event(name: String, vararg facts: Pair<String, Any>) {
        val info=JSONObject().put("event",name).put("pid",android.os.Process.myPid()).put("task",taskId)
        facts.forEach { info.put(it.first,it.second) };Log.i("BelesR38B","state=$info")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!R38BEntry.allowed(BuildConfig.DEBUG,intent.getStringExtra("candidate"),intent.getStringExtra("isolatedProfile"),
                intent.action,intent.getStringExtra("r38bAction"),intent.getStringExtra("database"),intent.getIntExtra("key",-1),javaClass.name,savedInstanceState!=null)) {
            event("entry-rejected","storesOpened" to 0); finish(); return
        }
        scope.launch {
            try { suite();event("SUITE_PASS","normalWrite" to false,"playerSelects" to 0) }
            catch (e: Exception) { event("HOLD","type" to e.javaClass.simpleName,"reason" to (e.message?.takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) } ?: "suite-assertion")) }
            finally { withContext(Dispatchers.Main) { event("finish-request");finish() } }
        }
    }
    override fun onDestroy() { super.onDestroy();event("onDestroy");scope.cancel() }
    private fun read(row: PreviewRow) = R31ReadOnlySnapshot.decode(row)
    private fun sixSame(a: PreviewRow,b: PreviewRow) = check(a.copy(sources=b.sources)==b) { "user-columns-changed" }
    private fun journals(s: String): List<String> {
        val x=PreviewCodec.unpack(s)
        return if(x.first()=="BT-SOURCES-1") emptyList() else listOf(x[2])+journals(x[1])
    }
    private suspend fun rejected(code: String, block: suspend () -> Unit) {
        val failure=runCatching { block() }.exceptionOrNull();check(failure!=null) { "unexpected-accept" }
        event(code,"rejected" to true,"type" to failure.javaClass.simpleName)
    }
    private suspend fun fixture(case: String, text: String, block: suspend (R38BSyntheticFixture,AndroidPreviewStore,PreviewRow) -> Unit) {
        val session="toplu-r38b-20261016-c013-$case";val f=R38BSyntheticFixture(text,session)
        check(!getDatabasePath(f.admission.databaseName).exists()) { "fixture-not-fresh" }
        val s=R38BProfileTransition.construct(f.admission) { db,key -> AndroidPreviewStore(applicationContext,key,db) { error("fixture-initializer-forbidden") } }
        try {
            s.save(f.base()); val row=requireNotNull(s.readRow())
            check(PreviewCodec.unpack(row.sources).first()=="BT-SOURCES-4")
            event("fixture-created","case" to case,"db" to f.admission.databaseName,"key" to 2,"syntheticId" to PreviewCodec.id(f.target),"normalIdUsed" to false,"revision" to r23Revision(row))
            block(f,s,row)
        } finally { s.close();event("store-close","case" to case) }
    }
    private suspend fun suite() {
        val seed=assets.open("trlist.m3u").use { it.readBytes() };check(hash(seed)==VerifiedDefinitionTransition.seedSha256) { "seed-drift" }
        val bytes=assets.open("r38a-c013.private.json").use { it.readBytes() };check(hash(bytes)==R38AFixtureBinding.sha256) { "frozen-input-drift" }
        val d=org.json.JSONArray(bytes.toString(Charsets.UTF_8)).getJSONObject(0)
        check(d.getJSONObject("old").getString("address")==R38BProfileTransition.entry.old.address.raw && d.getJSONObject("new").getString("address")==R38BProfileTransition.entry.new.address.raw) { "definition-drift" }
        val text=seed.toString(Charsets.UTF_8)
        check(R38BProfileTransition.sessions.all { !getDatabasePath("$it.db").exists() }) { "suite-not-fresh" }
        fixture("main",text) { f,s,base ->
            val start=android.os.SystemClock.elapsedRealtimeNanos()
            val applied=s.executeR38B(f.request,base,r23Revision(base));sixSame(base,applied.next)
            val commitMs=(android.os.SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0
            check(commitMs<10000) { "commit-bound-exceeded" }
            check(journals(base.sources)==journals(PreviewCodec.unpack(applied.next.sources)[1]))
            val next=applied.next;check(requireNotNull(s.readRow())==next)
            val noop=s.executeR38B(f.request,next,r23Revision(next));check(noop.next==next)
            val active=read(next)
            listOf("preflight","apply","rollback").forEach { action ->
                val c23=f.context.copy(session=R23ProfileTransition.testSession)
                val c26=f.context.copy(session=R26ProfileTransition.testSession)
                val c28=f.context.copy(session=R28ProfileTransition.testSession)
                val c33=f.context.copy(session=R33ProfileTransition.testSession)
                rejected("wrong-layer-r23-$action") { R23ProfileTransition.prepare(active,c23,R23Admission(true,2,"${c23.session}.db",c23.session,action),ApprovedR21.manifest) { error("allocator-forbidden") } }
                rejected("wrong-layer-r26-$action") { R26ProfileTransition.prepare(active,c26,R26Admission(true,2,"${c26.session}.db",c26.session,action)) }
                rejected("wrong-layer-r28-$action") { R28ProfileTransition.prepare(active,c28,R28Admission(true,2,"${c28.session}.db",c28.session,action)) }
                rejected("wrong-layer-r33-$action") { R33ProfileTransition.prepare(active,c33,R33Admission(true,2,"${c33.session}.db",c33.session,action)) }
            }
            check(s.readRow()==next)
            rejected("old-activity-save") { s.save(read(base)) };check(s.readRow()==next)
            rejected("generic-cas-wrong-layer") { s.compareAndSet(next,next) };check(s.readRow()==next)
            listOf(f.admission.copy(databaseName=R23ProfileTransition.normalDatabase), f.admission.copy(key=1),
                f.admission.copy(session=null),f.admission.copy(manifest="wrong")).forEach { a ->
                rejected("store-admission") { s.executeR38B(f.request.copy(admission=a),next,r23Revision(next)) }
            }
            val changed=f.intervening(read(next));s.save(changed);val user=requireNotNull(s.readRow())
            check(user.sources==next.sources)
            rejected("apply-noop-stale-full-row") { s.executeR38B(f.request,next,r23Revision(next)) };check(s.readRow()==user)
            val rollback=f.request.copy(admission=f.admission.copy(action="rollback"))
            val popped=s.executeR38B(rollback,user,r23Revision(user));sixSame(user,popped.next)
            check(popped.after.channels==read(base).channels && popped.after.library==read(base).library)
            check(popped.next.sources==base.sources && journals(popped.next.sources)==journals(base.sources))
            check(s.readRow()==popped.next);oldReader(popped.next)
            rejected("stale-upper-save-after-pop") { s.save(changed) };check(s.readRow()==popped.next)
            val rollbackNoop=s.executeR38B(rollback,popped.next,r23Revision(popped.next));check(rollbackNoop.next==popped.next)
            s.save(popped.after.withPref("opacity","50"));val last=requireNotNull(s.readRow())
            rejected("rollback-noop-stale-full-row") { s.executeR38B(rollback,popped.next,r23Revision(popped.next)) };check(s.readRow()==last)
            event("apply-userstate-lifo","sixColumnsExact" to true,"prior3JournalsByteExact" to true,"oldC013Exact" to true,"idsOrderRelationsExact" to true,"commitMs" to commitMs,"finalDigest" to r23Revision(last))
        }
        (0..6).forEach { column -> fixture("stale$column",text) { f,s,base ->
            val applied=s.executeR38B(f.request,base,r23Revision(base));val expected=applied.next
            val current=read(expected);val other=f.seed.last().id
            val changed=when(column) {
                0 -> current.copy(favorites=listOf(other))
                1 -> current.copy(hidden=setOf(f.target))
                2 -> current.copy(history=listOf(other))
                3 -> current.copy(categories=current.categories.reversed())
                4 -> current.copy(hiddenCategories=setOf("Ulusal"))
                5 -> current.withPref("opacity","17")
                else -> current.copy(sources=current.sources+"sentetik-user-source-label")
            }
            s.save(changed)
            val raced=requireNotNull(s.readRow());check(raced!=expected)
            rejected("stale-column-$column") { s.executeR38B(f.request,expected,r23Revision(expected)) }
            rejected("hold-stale-column-$column") { s.executeR38B(f.request.copy(context=f.context.copy(seed=emptyList())),expected,r23Revision(expected)) }
            check(s.readRow()==raced);event("stale-guard","column" to column,"fullRowRetained" to true)
        } }
        fixture("fault",text) { f,s,base ->
            var sql=false
            rejected("fault-after-real-sql") { s.executeR38B(f.request,base,r23Revision(base),afterSql={ sql=true;error("injected-after-sql") }) }
            check(sql && s.readRow()==base);event("fault-rollback","sqlWriteReached" to sql,"all7ExactOldRow" to true)
        }
        fixture("cancel",text) { f,s,base ->
            var boundary=false;var sql=false
            coroutineScope {
                launch { try {
                    s.executeR38B(f.request,base,r23Revision(base),beforeBoundary={ boundary=true;currentCoroutineContext().cancel();currentCoroutineContext().ensureActive() },afterSql={sql=true})
                    error("cancel-accepted")
                } catch (_: CancellationException) { } }.join()
            }
            check(boundary && !sql && s.readRow()==base)
            rejected("cancel-after-sql-before-commit") { s.executeR38B(f.request,base,r23Revision(base),afterSql={ sql=true;throw CancellationException("injected-before-commit") }) }
            check(sql && s.readRow()==base);event("cancel-rollback","realCoroutineCanceledBeforeBoundary" to true,"sqlCancelReached" to true,"all7ExactOldRow" to true)
        }
        fixture("boundary",text) { f,s,base ->
            var entered=false;var result:R38BPrepared?=null
            val start=android.os.SystemClock.elapsedRealtimeNanos()
            coroutineScope {
                lateinit var task:Job
                task=launch(start=CoroutineStart.LAZY) {
                    result=s.executeR38B(f.request,base,r23Revision(base),afterSql={entered=true;task.cancel();check(currentCoroutineContext().isActive)})
                };task.start();task.join()
            }
            val row=requireNotNull(s.readRow());check(entered && row!=base && row.sources==LibraryCodec.encode(R38BProfileTransition.prepare(read(base),f.context,f.admission)))
            val ms=(android.os.SystemClock.elapsedRealtimeNanos()-start)/1_000_000.0;check(ms<10000)
            // Cancellation may be delivered as withContext returns; durable row resolves the outcome.
            event("bounded-commit","cancelAtBoundary" to true,"durableOutcome" to "COMMITTED","returnedValue" to (result!=null),"durationMs" to ms,"sixColumnsExact" to (base.copy(sources=row.sources)==row))
        }
        fixture("hold",text) { f,s,base ->
            rejected("hold-full-row") { s.executeR38B(f.request.copy(context=f.context.copy(seed=emptyList())),base,r23Revision(base)) }
            rejected("wrong-revision") { s.executeR38B(f.request,base,"wrong") }
            check(s.readRow()==base);event("hold-retained","all7ExactOldRow" to true)
        }
    }
    private fun oldReader(row: PreviewRow) {
        val bytes=assets.open("r38b-code49-reader.dex").use { it.readBytes() };check(hash(bytes)==R38BReaderBinding.dexSha256)
        val dex=File(codeCacheDir,"r38b-code49-reader.dex")
        check(!dex.exists()) { "reader-cache-not-fresh" }
        dex.outputStream().use { check(dex.setReadOnly());it.write(bytes) };check(dex.readBytes().contentEquals(bytes))
        val filter=object:ClassLoader(javaClass.classLoader) { override fun loadClass(name:String,resolve:Boolean):Class<*> {
            if(name.startsWith("com.omer.belestiviclone.shared.")) throw ClassNotFoundException(name)
            return super.loadClass(name,resolve)
        } }
        val loader=DexClassLoader(dex.path,codeCacheDir.path,null,filter)
        val classes=listOf("com.omer.belestiviclone.shared.evidence.R38BCode49Bridge","com.omer.belestiviclone.shared.preview.LibraryCodec",
            "com.omer.belestiviclone.shared.preview.PreviewData","com.omer.belestiviclone.shared.catalog.PlaybackDefinition","com.omer.belestiviclone.shared.preview.R28Journal")
        classes.forEach { check(loader.loadClass(it).classLoader===loader) { "parent-newclass-leak" } }
        val input=arrayOf(row.sources,row.favorites,row.hidden,row.history,row.categories,row.hiddenCategories,row.preferences)
        @Suppress("UNCHECKED_CAST") val output=loader.loadClass(classes.first()).getMethod("read",Array<String>::class.java).invoke(null,input as Any) as Array<String>
        check(output.take(7)==input.toList() && output[7]=="408" && output[10]==PreviewCodec.pack(read(row).channels.map { PreviewCodec.id(it.id) }))
        event("code49-reader","all7Exact" to true,"digest" to r23Revision(row),"idsOrderExact" to true,"prior3JournalBytesExact" to true,
            "dexSha256" to hash(bytes),"loader" to loader.toString(),"loadedClasses" to classes.joinToString(","),"parentSharedBlocked" to true,"envelope" to PreviewCodec.unpack(row.sources)[0])
    }
}
