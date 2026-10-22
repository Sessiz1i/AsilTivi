package com.omer.belestiviclone

import android.os.SystemClock
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.sharedui.PreviewState
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Fresh owned rows only. No normal reader, public catalogue, migration, restore, or fallback. */
class R42FourActivity : MainActivity() {
    private lateinit var session: String
    private lateinit var seed: PreviewData
    private var store: AndroidPreviewStore? = null
    private var readyOnce = false
    private var loadedOnce = false
    private fun id(): OpaqueId { val u=UUID.randomUUID();return OpaqueId(u.mostSignificantBits,u.leastSignificantBits) }
    private fun event(name: String, detail: String="") = Log.i("BelesR42",
        "monoNs=${SystemClock.elapsedRealtimeNanos()} session=$session db=$session.db key=2 event=$name $detail")
    override fun r42Store(session: String): PreviewStore {
        this.session=session
        val db="$session.db";val cache=File(filesDir,"$session-catalog")
        check(listOf(db,"$db-wal","$db-shm").none { getDatabasePath(it).exists() } && !cache.exists() && !File("${cache.path}.bak").exists())
        check(R42Observation.snapshot().isEmpty())
        val entries=ApprovedR21.manifest.entries+R26ProfileTransition.entry+R28ProfileTransition.entry
        check(entries.map { it.candidate } == listOf("R20-C002","R20-C003","R20-C009","R20-C020"))
        check(entries.map { it.new.address.raw }.distinct().size == 4)
        val channels=entries.map { PreviewChannel(id(),it.candidate,"R42 isolated","ikon_tv",definition=it.new) } +
            listOf(PreviewChannel(id(),"R20-C013","R42 reserve","ikon_tv",definition=R38CProfileTransition.entry.new))
        seed=PreviewData(channels=channels,categories=channels.map { it.category }.distinct(),favorites=emptyList(),
            history=emptyList(),sources=emptyList(),profileId=id()).withPref("startup","Kapalı").withPref("history","Kapalı")
        channels.forEachIndexed { slot,ch ->
            event("frozen-definition","slot=$slot candidate=${ch.name} syntheticId=${PreviewCodec.id(ch.id)} realId=NOT_BOUND_HERE " +
                "uriSha256=${r40Hash(ch.definition!!.address.raw)} full9Sha256=${r40FullHash(ch.definition!!)} " +
                "libraryCodecSha256=${r40Hash(LibraryCodec.channels(listOf(ch)))}")
        }
        store=AndroidPreviewStore(applicationContext,key=2,databaseName=db) { seed }
        return object : PreviewStore {
            override suspend fun load(): PreviewData {
                try {
                    val s=requireNotNull(store)
                    if (loadedOnce) check(s.readRow()!=null)
                    val data=s.load();check(data.channels==seed.channels && s.readRow()!=null);loadedOnce=true
                    event("fresh-load","normalStoreOpened=false");return data
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    event("HOLD","reason=owned-load type=${e.javaClass.simpleName} noFallback=true")
                    withContext(Dispatchers.Main.immediate) { finish() }
                    throw CancellationException("R42-owned-load-HOLD").apply { initCause(e) }
                }
            }
            override suspend fun save(data: PreviewData) { requireNotNull(store).save(data);event("owned-save","normalStoreOpened=false") }
        }
    }
    override suspend fun r42Ready(state: PreviewState) {
        if (readyOnce) return;readyOnce=true
        val start=SystemClock.elapsedRealtimeNanos()
        event("route-start","limitNs=90000000000 reserve=C013-for-panel-replacement hostBaselineRequired=true")
        try {
            withTimeout(85_000) {
                check(!state.sessionOnly)
                state.select(seed.channels.first().id);state.startMulti(if(session.endsWith("-a")) 2 else 4)
                seed.channels.take(4).forEachIndexed { i,ch -> state.multi!!.slots[i]=ch.id };state.multiRevision++
                if (session.endsWith("-a")) {
                    observe("two-initial",seed.channels.take(2))
                    state.startMulti(4);observe("four-first",seed.channels.take(4))
                    state.startMulti(2);observe("two-hidden-released",seed.channels.take(2))
                    check(state.multi!!.slots==seed.channels.take(4).map { it.id })
                    state.startMulti(4);observe("four-return-new-owners",seed.channels.take(4))
                } else {
                    observe("four-edit-entry",seed.channels.take(4))
                    state.multi!!.selected=2;state.multiRevision++;gain("selected-two",seed.channels[2].id)
                    state.multi!!.selected=3;state.multi!!.slots[3]=null;state.multiRevision++
                    observe("empty-selected-silent",seed.channels.take(3));gain("empty-selected",null)
                    state.multi!!.slots[3]=seed.channels[4].id;state.multiRevision++
                    observe("single-panel-replaced",seed.channels.take(3)+seed.channels[4])
                    state.multi!!.slots[3]=seed.channels[3].id;state.multiRevision++
                    observe("single-panel-restored",seed.channels.take(4))
                    state.settings();delay(500);check(R42Observation.snapshot().isEmpty());event("settings-released")
                    state.back();observe("settings-return",seed.channels.take(4))
                }
                val returning=state.multi!!.returnChannel()
                state.back();delay(500);check(state.multi==null && state.selected==returning)
                observe("single-return",listOf(seed.channels.single { it.id==returning }))
                state.back();delay(500);check(R42Observation.snapshot().isEmpty());event("all-multi-released")
            }
            event("route-end","durationNs=${SystemClock.elapsedRealtimeNanos()-start}")
        } catch (e: TimeoutCancellationException) { event("HOLD","reason=route-budget classification=NOT_DETERMINED") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { event("HOLD","reason=measurement type=${e.javaClass.simpleName} classification=NOT_DETERMINED") }
        finally {
            withContext(NonCancellable) {
                state.multi=null;state.page="home";state.dialog=null;delay(500)
                event("product-exit-request","remainingPlayers=${R42Observation.snapshot().size} durationNs=${SystemClock.elapsedRealtimeNanos()-start}")
                state.exitRequested=true
            }
        }
    }
    private suspend fun gain(name: String, audible: OpaqueId?) {
        delay(250)
        val rows=R42Observation.snapshot()
        check(rows.all { it.getDouble("gain") == if(it.optString("channelId")==audible?.let(PreviewCodec::id)) 1.0 else 0.0 })
        event("gain","name=$name physicalAudio=NOT_RUN rows=${JSONArray(rows)}")
    }
    private suspend fun observe(name: String, channels: List<PreviewChannel>) {
        val expected=channels.map { PreviewCodec.id(it.id) }.toSet()
        val start=SystemClock.elapsedRealtimeNanos();var readyNs=0L
        val ticks=mutableListOf<List<R42Sample>>()
        event("window-start","name=$name expected=${expected.size} prepareDeadlineNs=25000000000 observationLimitNs=15000000000")
        while (true) {
            val now=SystemClock.elapsedRealtimeNanos();val rows=R42Observation.snapshot()
            event("snapshot","name=$name rows=${JSONArray(rows)}")
            check(rows.none { it.getBoolean("error") }) { "player-error" }
            val allReady=rows.size==expected.size && rows.map { it.optString("channelId") }.toSet()==expected &&
                rows.all { it.getBoolean("ready") && it.getBoolean("playing") && it.getLong("firstFrameNs")>0 &&
                    it.getLong("firstFrameNs")-it.getLong("selectedNs")<=25_000_000_000L &&
                    it.getString("videoDecoder")!="unobserved" && it.getString("audioDecoder")!="unobserved" &&
                    it.has("videoInput") && it.has("audioInput") }
            if (allReady && readyNs==0L) readyNs=now
            check(if(readyNs==0L) now-start<=25_000_000_000L else now-readyNs<=15_000_000_000L) { "deadline" }
            if (allReady) {
                ticks += rows.map { R42Sample(it.getString("channelId"),it.getString("player"),it.getString("attempt"),
                    it.getLong("positionMs"),it.getLong("frames"),it.getBoolean("playing"),it.getBoolean("ready")) }
                if(ticks.size>3) ticks.removeAt(0)
                if(r42CommonWindow(expected,ticks)) { event("window-measured","name=$name commonTicks=3 elapsedNs=${now-start}");return }
            } else ticks.clear()
            delay(1000)
        }
    }
    override fun onDestroy() { super.onDestroy();store?.close();store=null;if(::session.isInitialized)event("destroy") }
}
