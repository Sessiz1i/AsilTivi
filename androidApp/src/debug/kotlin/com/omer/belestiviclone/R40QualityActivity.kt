package com.omer.belestiviclone

import android.util.Log
import android.os.SystemClock
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.sharedui.PreviewState
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Fresh synthetic DB only; actual product Compose, SampleVideo and VideoTexture path. */
class R40QualityActivity : MainActivity() {
    private var fixtureStore: AndroidPreviewStore? = null
    private var initial: PreviewRow? = null
    private lateinit var session: String
    private var once = false
    private lateinit var seedData: PreviewData
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun event(name: String, detail: String = "") = Log.i("BelesR40", "monoNs=${SystemClock.elapsedRealtimeNanos()} task=$taskId activity=${RenderTrace.identity(this)} session=$session db=$session.db key=2 thread=${Thread.currentThread().id} event=$name $detail")
    override fun r40Store(session: String): PreviewStore {
        this.session = session
        val db = "$session.db"; val cache = File(filesDir, "$session-catalog")
        check(!getDatabasePath(db).exists() && !getDatabasePath("$db-wal").exists() && !getDatabasePath("$db-shm").exists() && !cache.exists() && !File("${cache.path}.bak").exists()) { "R40-fresh-only" }
        val bytes = assets.open("r38a-c013.private.json").use { it.readBytes() }
        check(hash(bytes.toString(Charsets.UTF_8)) == R38AFixtureBinding.sha256)
        val f = JSONArray(bytes.toString(Charsets.UTF_8)).getJSONObject(0)
        val d = f.getJSONObject("new"); val definition = R38CProfileTransition.entry.new
        check(f.getString("candidateId") == "R20-C013" && definition.address.raw == d.getString("address"))
        check(d.getJSONArray("headers").length() == 0 && d.getJSONArray("options").length() == 0 && d.getJSONArray("unknownRequiredDirectives").length() == 0 && d.getBoolean("isLossless") && listOf("mimeType","protocol","drm","authorityScope").all(d::isNull))
        val codec = PreviewCodec.pack(listOf(definition.address.raw,"","",""))
        val full = PreviewCodec.pack(listOf(definition.address.raw,"","","","","","","","true"))
        check(hash(codec) == f.getString("newLibraryCodecSha256") && hash(full) == f.getString("newFullDefinitionSha256"))
        val seed = assets.open("trlist.m3u").use { it.readBytes() }.toString(Charsets.UTF_8)
        check(hash(seed) == VerifiedDefinitionTransition.seedSha256)
        val uuid = UUID.randomUUID(); val id = OpaqueId(uuid.mostSignificantBits, uuid.leastSignificantBits)
        check(id != R38CProfileTransition.targetId)
        val channel = R38FixtureModel.channel(PublicM3u.parse(seed).rows,38,f.getString("rawProviderHint"),R38CProfileTransition.entry.old,definition,id)
        val data = PreviewData(favorites=listOf(id), history=emptyList(), channels=listOf(channel), categories=channel.groups,
            sources=emptyList(), profileId=OpaqueId(0x523430,if(session.endsWith("-a")) 1 else 2))
            .withPref("history","Kapalı").withPref("startup","Açık").withPref("lastChannel",PreviewCodec.id(id)).withPref("format","Orijinal")
        // This cache contains the new fixture only. No existing normal/test DB or cache is read or copied.
        seedData = data
        check(cache.createNewFile()); cache.writeText(LibraryCodec.channels(data.channels),Charsets.UTF_8)
        val store = AndroidPreviewStore(applicationContext,key=2,databaseName=db) { data }; fixtureStore=store
        event("fresh-fixture", "testId=${PreviewCodec.id(id)} fullDefinitionSha256=${hash(full)} uriSha256=${hash(definition.address.raw)} cacheSha256=${hash(LibraryCodec.channels(data.channels))} normalStoreOpened=false")
        return object : PreviewStore {
            override suspend fun load(): PreviewData {
                val loaded=requireNotNull(fixtureStore).load();check(loaded.channels==data.channels)
                val row=requireNotNull(fixtureStore?.readRow());if(initial==null) initial=row
                event("fixture-load","revision=${r23Revision(row)} modelSha256=${hash(LibraryCodec.encode(loaded))}")
                return loaded
            }
            override suspend fun save(data: PreviewData) { requireNotNull(fixtureStore).save(data); event("fixture-save","quality=${data.pref("quality")} revision=${r23Revision(requireNotNull(fixtureStore?.readRow()))}") }
        }
    }


    override suspend fun r40Ready(state: PreviewState) {
        if (once) return; once=true
        val began=SystemClock.elapsedRealtimeNanos()
        event("budget-start","maxProfiles=2 profileMaxNs=90000000000 settleMaxNs=25000000000")
        try {
            state.info=true;state.panel=false
            if (session.endsWith("-a")) {
                change(state,"480p",25000)
                change(state,"1080p FHD",25000)
                event("settings-route-start")
                state.settings();delay(500);state.section=2;state.dialog="quality";delay(1500)
                change(state,"720p HD",0);state.back();state.info=true;delay(4000)
                event("settings-route-end","newInstanceExpected=true")
                event("two-instance-start")
                state.startMulti(2);state.multi!!.slots[1]=state.selected;state.multiRevision++
                delay(4500);state.back();delay(1500);event("two-instance-end")
            } else {
                // Default is observed before a request. Unsupported levels are never fake upscale.
                delay(3000);event("default-window-end","quality=${state.data.pref("quality")}")
                change(state,"480p",20000)
                change(state,"1080p FHD",25000)
                change(state,"360p",6000)
                change(state,"4K UHD",6000)
                change(state,"legacy-unknown",2000)
                val old=state.selected
                val uuid=UUID.randomUUID();val next=OpaqueId(uuid.mostSignificantBits,uuid.leastSignificantBits)
                val alias=state.channel!!.copy(id=next,name="C013 synthetic second slot")
                state.commit(state.data.copy(channels=state.data.channels+alias));while(state.busy)delay(10)
                state.select(next);delay(2000)
                event("channel-change-end","oldTestId=${PreviewCodec.id(old)} newTestId=${PreviewCodec.id(next)} sameFrozenDefinition=true")
                event("settings-route-start")
                state.settings();delay(500);state.section=2;state.dialog="quality";delay(1000)
                change(state,"720p HD",0);state.back();state.info=true;delay(2500)
                event("settings-route-end","newInstanceExpected=true")
                event("two-instance-start")
                state.startMulti(2);state.multi!!.slots[1]=state.selected;state.multiRevision++
                delay(3000);state.back();delay(1500);event("two-instance-end")
            }
            // Separate profile persistence/reopen equivalent, using only the newly created Room row.
            state.panel=false;state.page="live";state.back();delay(500)
            val expected=state.data;val before=requireNotNull(fixtureStore?.readRow())
            fixtureStore!!.close()
            // load() always asks for a decode base, even when a row exists. This base belongs only
            // to this newly generated fixture. Read the existing row first; no initializer write.
            fixtureStore=AndroidPreviewStore(applicationContext,key=2,databaseName="$session.db") { seedData }
            check(fixtureStore!!.readRow()==before)
            val reopened=fixtureStore!!.load();check(reopened==expected);check(fixtureStore!!.readRow()==before)
            check(reopened.channels.all { it.id != R38CProfileTransition.targetId })
            event("profile-reopen-equivalent","quality=${reopened.pref("quality")} sameRow=true normalStoreOpened=false")
            check(SystemClock.elapsedRealtimeNanos()-began < 90_000_000_000L)
            event("budget-end","durationNs=${SystemClock.elapsedRealtimeNanos()-began}")
            event("product-exit-request");state.exitRequested=true
        } catch (e:CancellationException) { throw e }
        catch (e:Exception) { event("HOLD","type=${e.javaClass.simpleName}");state.exitRequested=true }
    }
    private suspend fun change(state:PreviewState,value:String,settleMs:Long) {
        val requested=SystemClock.elapsedRealtimeNanos()
        event("quality-pref-request","quality=$value requestedNs=$requested deadlineNs=${requested+settleMs*1_000_000}")
        state.pref("quality",value);while(state.busy)delay(10)
        check(state.data.pref("quality")==value)
        event("quality-pref-visible","quality=$value")
        val remainingNs=requested+settleMs*1_000_000-SystemClock.elapsedRealtimeNanos()
        if (remainingNs>0) delay(remainingNs/1_000_000)
        event("quality-window-end","quality=$value requestedNs=$requested deadlineNs=${requested+settleMs*1_000_000}")
    }
    override fun onDestroy() { super.onDestroy();fixtureStore?.close();fixtureStore=null;if(::session.isInitialized)event("destroy") }
}
