package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.security.MessageDigest

/** Exact debug DUMP entry. Existing normal rows/cache only; no load, writer hooks, player or rollback. */
class R38CNormalActivity : Activity() {
    private val avazId = PreviewCodec.id("-5963231892924248479,-8432906197569350364")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command=intent.getStringExtra("r38cAction")
        val a=R38CAdmission(BuildConfig.DEBUG,intent.getIntExtra("key",-1),intent.getStringExtra("database").orEmpty(),
            intent.getStringExtra("r38cSession"),command,intent.getStringExtra("r38cManifest").orEmpty(),
            intent.getStringExtra("r38cApproval"),intent.getStringExtra("r38cRecord"),intent.getStringExtra("r38cPrivate"),
            intent.getStringExtra("r38cManifestSha"),intent.getStringExtra("candidate"))
        if(!R38CNormalEntry.allowed(a,intent.action,javaClass.name,savedInstanceState!=null,
            getDatabasePath(R31ReadOnlySnapshot.database).isFile)) {
            Log.i("BelesR38CNormal","entry-rejected storesOpened=0");finish();return
        }
        scope.launch {
            var outcome="NOT_COMMITTED"
            try {
                val manifest=asset("r38c-normal-manifest.json",R38CManifestBinding.manifestSha256)
                val approval=JSONObject(asset("r38c-approval.json",R38CManifestBinding.approvalSha256))
                asset("r38c-approval-record.md",R38CManifestBinding.recordSha256)
                check(approval.getString("status")=="APPROVED_EXPLICIT_USER_DECISION" && approval.getBoolean("humanApprovalRecorded")) { "approval-status" }
                asset("r38a-c013.private.json",R38CManifestBinding.privateSha256)
                val frozen=org.json.JSONArray(asset("r38a-c013.private.json",R38CManifestBinding.privateSha256)).getJSONObject(0)
                fun portable(name:String):PlaybackDefinition {
                    val d=frozen.getJSONObject(name)
                    check(d.getJSONArray("headers").length()==0 && d.getJSONArray("options").length()==0 && d.getJSONArray("unknownRequiredDirectives").length()==0 && d.getBoolean("isLossless") && listOf("mimeType","protocol","drm","authorityScope").all(d::isNull)) { "private-definition-drift" }
                    return PlaybackDefinition(Address.fromForm(d.getString("address")))
                }
                check(portable("old")==R38CProfileTransition.entry.old && portable("new")==R38CProfileTransition.entry.new) { "full-definition-drift" }
                val text=asset("trlist.m3u",VerifiedDefinitionTransition.seedSha256)
                val cache=AndroidPublicCatalog(applicationContext).readExisting()
                val c=PilotContext(a.session!!,VerifiedDefinitionTransition.seedSha256,cache,
                    SourceOperations { error("allocator-forbidden") }.parsed(PublicM3u.source,"trlist",SourceKind.DEFAULT,PublicM3u.url,text))
                val store=R38CProfileTransition.construct(a) { db,key -> AndroidPreviewStore(applicationContext,key,db) { error("initializer-forbidden") } }
                try {
                    val startup=AndroidPreviewStore(applicationContext,databaseName=a.databaseName) { error("initializer-forbidden") }
                    val key1=try { requireNotNull(startup.readRow()) } finally { startup.close() }
                    val row=requireNotNull(store.readRow()) { "missing-row" }
                    var expectedFinal=row
                    val data=R31ReadOnlySnapshot.decode(row)
                    val journal=R38CProfileTransition.audit(data,c,a)
                    check(journal.target.id==R38CProfileTransition.targetId && avazId in data.hidden) { "target-visibility-binding" }
                    priorJournals(row,JSONObject(manifest).getJSONObject("previousJournals"))
                    // The full seven-column row is read twice before any write. Never retry a changed row.
                    check(store.readRow()==row) { "concurrent-row-change" }
                    summary(data,row,R38CProfileTransition.targetId)
                    if(command=="apply") {
                        check(intent.getStringExtra("r38cRevision")==r23Revision(row)) { "stale-preflight-revision" }
                        check(intent.getStringExtra("r38cKey1")==r23Revision(key1) && intent.getStringExtra("r38cCache")==hash(LibraryCodec.channels(cache))) { "stale-key1-cache" }
                        val started=android.os.SystemClock.elapsedRealtimeNanos()
                        outcome="HOLD"
                        val prepared=store.executeR38C(R38CRequest(a,c),row,r23Revision(row))
                        val durable=requireNotNull(store.readRow())
                        check(durable==prepared.next && durable==row.copy(sources=prepared.next.sources)) { "ambiguous-readback" }
                        outcome=if(durable==row) "NO_OP" else "COMMITTED"
                        expectedFinal=durable
                        check(PreviewCodec.unpack(durable.sources)[0]==R38CProfileTransition.envelope) { "readback-envelope" }
                        val post=R31ReadOnlySnapshot.decode(durable)
                        R38CProfileTransition.audit(post,c,a)
                        priorJournals(durable,JSONObject(manifest).getJSONObject("previousJournals"))
                        summary(post,durable,R38CProfileTransition.targetId)
                        Log.i("BelesR38CNormal","transaction outcome=$outcome sixColumnsExact=true durationMs=${(android.os.SystemClock.elapsedRealtimeNanos()-started)/1_000_000.0}")
                    }
                    val checkStartup=AndroidPreviewStore(applicationContext,databaseName=a.databaseName) { error("initializer-forbidden") }
                    val finalKey1=try { requireNotNull(checkStartup.readRow()) } finally { checkStartup.close() }
                    check(key1==finalKey1 && cache==AndroidPublicCatalog(applicationContext).readExisting()) { "key1-cache-concurrent-change" }
                    check(store.readRow()==expectedFinal) { "concurrent-final-row-change" }
                    Log.i("BelesR38CNormal","key1Revision=${r23Revision(key1)} cacheSha256=${hash(LibraryCodec.channels(cache))}")
                    Log.i("BelesR38CNormal","snapshot PASS command=$command outcome=$outcome noInitializer=true")
                } finally { store.close() }
            } catch(e:Exception) {
                Log.e("BelesR38CNormal","snapshot HOLD outcome=$outcome type=${e.javaClass.simpleName} reason=${e.message?.takeIf { it.matches(Regex("[a-zA-Z0-9-]+")) } ?: "ambiguous-audit"}")
            } finally { withContext(Dispatchers.Main) { finish() } }
        }
    }
    private fun asset(name:String,sha:String):String {
        val text=assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }
        check(hash(text)==sha) { "asset-binding" };return text
    }
    private fun priorJournals(row:PreviewRow,expected:JSONObject) {
        var parts=PreviewCodec.unpack(row.sources)
        if(parts[0]==R38CProfileTransition.envelope) parts=PreviewCodec.unpack(parts[1])
        listOf("r28JournalSha256","r26JournalSha256","r23JournalSha256").forEach { name ->
            check(hash(parts[2])==expected.getString(name)) { "prior-journal-bytes" };parts=PreviewCodec.unpack(parts[1])
        }
    }
    override fun onDestroy() { super.onDestroy();scope.cancel() }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun summary(data: PreviewData, row: PreviewRow, target: OpaqueId) {
        val lib = data.library!!
        fun digest(items: List<String>) = hash(PreviewCodec.pack(items))
        val top = PreviewCodec.unpack(row.sources); val outer = if(top[0]==R38CProfileTransition.envelope) PreviewCodec.unpack(top[1]) else top; val middle = PreviewCodec.unpack(outer[1]); val inner = PreviewCodec.unpack(middle[1])
        val info = JSONObject().put("targetId",PreviewCodec.id(target)).put("avazId",PreviewCodec.id(avazId)).put("avazHidden",avazId in data.hidden).put("revision",r23Revision(row)).put("envelope",top[0]).put("db",R31ReadOnlySnapshot.database).put("key",2)
            .put("pid",android.os.Process.myPid()).put("task",taskId).put("activeSource",PreviewCodec.id(lib.active.value))
            .put("sourcesSha256",hash(row.sources)).put("r28JournalSha256",hash(outer[2])).put("r26JournalSha256",hash(middle[2])).put("r23JournalSha256",hash(inner[2]))
            .put("channels",data.channels.size).put("memberships",lib.snapshot.memberships.size).put("categories",lib.snapshot.categories.size)
            .put("favorites",data.favorites.size).put("history",data.history.size).put("idsSha256",digest(data.channels.map { PreviewCodec.id(it.id) }))
            .put("other407Sha256",hash(LibraryCodec.channels(data.channels.filter { it.id != target })))
            .put("metadataSha256",hash(LibraryCodec.channels(data.channels.map { it.copy(definition=null) })))
            .put("membershipsSha256",digest(lib.snapshot.memberships.map { PreviewCodec.pack(listOf(PreviewCodec.id(it.id.value),PreviewCodec.id(it.channelId.value),PreviewCodec.id(it.sourceId.value),PreviewCodec.id(it.categoryId.value),it.isCurrent.toString())) }))
            .put("categoryIdsSha256",digest(lib.snapshot.categories.map { "${PreviewCodec.id(it.id.value)}:${it.label}:${it.firstSeen}" }))
            .put("firstSeenSha256",digest(lib.snapshot.channels.map { "${PreviewCodec.id(it.id.value)}:${it.firstSeen}" }))
            .put("startupEnabled",data.pref("startup") == "Açık").put("historyEnabled",data.pref("history") == "Açık")
            .put("lastChannelSha256",hash(data.preferences["lastChannel"].orEmpty()))
            .put("otherPreferencesSha256",digest(data.preferences.filterKeys { it != "lastChannel" }.flatMap { listOf(it.key,it.value) }))
        listOf("favorites" to row.favorites,"hidden" to row.hidden,"history" to row.history,"categories" to row.categories,
            "hiddenCategories" to row.hiddenCategories,"preferences" to row.preferences).forEach { (n,v) -> info.put(n+"Sha256",hash(v)) }
        val expectedHistory=if(data.pref("history")=="Açık") listOf(target)+data.history.filter { it!=target } else data.history
        val expectedPreferences=data.preferences + ("lastChannel" to PreviewCodec.id(target))
        val old=R38CProfileTransition.entry.old
        val normalized=if(lib.r38c==null) data else data.copy(channels=data.channels.map { if(it.id==target) it.copy(definition=old) else it },
            library=lib.copy(r38c=null,records=lib.records.map { s -> s.copy(rows=s.rows.map { if(s.id==PublicM3u.source && it.rowOrder==38L) it.copy(definition=old) else it }) },
                snapshot=lib.snapshot.copy(channels=lib.snapshot.channels.map { if(it.id.value==target) it.copy(definition=old) else it })))
        info.put("normalizedSourcesSha256",hash(LibraryCodec.encode(normalized)))
            .put("allSourceIdentityRawCountSha256",digest(lib.records.map { PreviewCodec.pack(listOf(PreviewCodec.id(it.id.value),it.name,it.kind.name,it.address,it.parent?.let { p->PreviewCodec.id(p.value) }.orEmpty(),it.deleted.toString(),it.rawCount.toString(),it.skippedCount.toString())) }))
        info.put("expectedUiHistorySha256",digest(expectedHistory.map(PreviewCodec::id))).put("expectedUiHistoryCount",expectedHistory.size)
            .put("expectedUiPreferencesSha256",digest(expectedPreferences.flatMap { listOf(it.key,it.value) }))
            .put("targetLibraryCodecSha256",hash(PreviewCodec.pack(listOf(data.channels.single { it.id==target }.definition!!.address.raw,"","",""))))
            .put("targetUriSha256",hash(data.channels.single { it.id==target }.definition!!.address.raw))
            .put("normalManifestSha256",R38CManifestBinding.manifestSha256).put("approvalSha256",R38CManifestBinding.approvalSha256)
        Log.i("BelesR38CNormal","state=$info")
        (ApprovedR21.manifest.entries+R26ProfileTransition.entry+R28ProfileTransition.entry).forEach { entry ->
            val channel=data.channels.single { it.definition == entry.new }; val d=channel.definition!!
            val serialized=PreviewCodec.pack(listOf(d.address.raw,PreviewCodec.pack(d.headers.flatMap { listOf(it.name,it.value) }),d.mimeType.orEmpty(),PreviewCodec.pack(d.unknownRequiredDirectives)))
            Log.i("BelesR38CNormal","candidate=${entry.candidate} id=${PreviewCodec.id(channel.id)} uriSha256=${hash(d.address.raw)} definitionSha256=${hash(serialized)}")
        }
    }
}
