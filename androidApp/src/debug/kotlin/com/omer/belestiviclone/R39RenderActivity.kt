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
class R39RenderActivity : MainActivity() {
    private var fixtureStore: AndroidPreviewStore? = null
    private var initial: PreviewRow? = null
    private lateinit var session: String
    private var once = false
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun event(name: String, detail: String = "") = Log.i("BelesR39", "monoNs=${SystemClock.elapsedRealtimeNanos()} task=$taskId activity=${RenderTrace.identity(this)} session=$session db=$session.db key=2 thread=${Thread.currentThread().id} event=$name $detail")
    override fun r39Store(session: String): PreviewStore {
        this.session = session
        val db = "$session.db"; val cache = File(filesDir, "$session-catalog")
        check(!getDatabasePath(db).exists() && !getDatabasePath("$db-wal").exists() && !getDatabasePath("$db-shm").exists() && !cache.exists() && !File("${cache.path}.bak").exists()) { "R39-fresh-only" }
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
            sources=emptyList(), profileId=OpaqueId(0x523339,if(session.endsWith("-a")) 1 else 2))
            .withPref("history","Kapalı").withPref("startup","Açık").withPref("lastChannel",PreviewCodec.id(id)).withPref("format","Orijinal")
        // This cache contains the new fixture only. No existing normal/test DB or cache is read or copied.
        check(cache.createNewFile()); cache.writeText(LibraryCodec.channels(data.channels),Charsets.UTF_8)
        val store = AndroidPreviewStore(applicationContext,key=2,databaseName=db) { data }; fixtureStore=store
        event("fresh-fixture", "testId=${PreviewCodec.id(id)} fullDefinitionSha256=${hash(full)} uriSha256=${hash(definition.address.raw)} cacheSha256=${hash(LibraryCodec.channels(data.channels))} normalStoreOpened=false")
        return object : PreviewStore {
            override suspend fun load(): PreviewData {
                val loaded=store.load();check(loaded==data)
                val row=requireNotNull(store.readRow());if(initial==null) initial=row else check(row==initial)
                event("fixture-load","revision=${r23Revision(row)} modelSha256=${hash(LibraryCodec.encode(loaded))}")
                return loaded
            }
            override suspend fun save(data: PreviewData) { store.save(data); event("fixture-save","revision=${r23Revision(requireNotNull(store.readRow()))}") }
        }
    }
    override suspend fun r39Ready(state: PreviewState) {
        if(once)return;once=true
        try {
            if(session.endsWith("-a")) {
                event("route1-start","cadenceMs=1000 windowNs=25000000000 scope=C013-single")
                delay(27000); state.panel=false; state.back(); delay(1000); audit("route1-end")
                event("route2-start","difference=product-settings-dispose-and-return-instance-chain")
                state.page="live";delay(2000);state.settings();delay(1000);state.back();delay(3000);audit("route2-end")
                event("route3-start","difference=two-product-players-same-frozen-C013-independent-surfaces")
                state.startMulti(2);state.multi!!.slots[1]=state.selected;state.multiRevision++
                delay(6000);state.back();delay(3000);audit("route3-end")
                event("route4-start","difference=same-TextureView-detach-reattach-Media3-listener-unmodified")
                val texture=requireNotNull(findTexture(window.decorView))
                val parent=texture.parent as android.view.ViewGroup
                val index=parent.indexOfChild(texture);val params=texture.layoutParams
                event("texture-detach-request","texture=${RenderTrace.identity(texture)} surface=${RenderTrace.identity(texture.surfaceTexture)}")
                parent.removeView(texture);delay(500)
                check(texture.parent==null);parent.addView(texture,index,params)
                delay(3000)
                check(texture.isAttachedToWindow && texture.isAvailable)
                event("texture-reattached","texture=${RenderTrace.identity(texture)} surface=${RenderTrace.identity(texture.surfaceTexture)}")
                audit("route4-end")
                event("route5-overlap-ready","difference=fresh-second-task-create-versus-first-pause-stop-alive-player")
                // Host opens fresh B within this bounded window; normal profile is never involved.
                delay(25000);audit("route5-end")
            } else {
                event("route5-second-task-start");delay(10000);audit("route5-second-task-end")
            }
            state.panel=false;state.page="live";state.back();delay(500)
            event("product-exit-request");state.exitRequested=true
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { event("HOLD","type=${e.javaClass.simpleName}");state.exitRequested=true }
    }
    private suspend fun audit(stage:String) {
        val row=requireNotNull(fixtureStore?.readRow());check(row==initial) { "R39-fixture-row-drift" }
        event(stage,"revision=${r23Revision(row)} rowUnchanged=true")
    }
    private fun findTexture(view:android.view.View):android.view.TextureView? {
        if(view is android.view.TextureView)return view
        if(view is android.view.ViewGroup) for(i in 0 until view.childCount) findTexture(view.getChildAt(i))?.let { return it }
        return null
    }
    override fun onDestroy() { super.onDestroy();fixtureStore?.close();fixtureStore=null;if(::session.isInitialized) event("destroy") }
}
