package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Fresh, DUMP-only single attempt. Never reads normal DB/cache or transition/proof files. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class R38PlaybackActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: AndroidPreviewPlayer? = null
    private var store: AndroidPreviewStore? = null
    private var session: String? = null
    private var candidate: String? = null
    private val attempt = UUID.randomUUID().toString()
    private var testId: OpaqueId? = null
    private var phase = "admission"
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun sha(s: String) = sha(s.toByteArray(Charsets.UTF_8))
    private fun event(name: String, extra: JSONObject = JSONObject()) {
        val j = extra.put("event", name).put("monoNs", SystemClock.elapsedRealtimeNanos())
            .put("pid", android.os.Process.myPid()).put("task", taskId).put("activity", RenderTrace.identity(this))
            .put("session", session).put("database", session?.let { "$it.db" }).put("key", 2)
            .put("candidateId", candidate).put("attempt", attempt).put("testId", testId?.let(PreviewCodec::id))
            .put("player", player?.let { RenderTrace.identity(it.exo) }).put("normalStoreOpened", false).put("phase", phase)
        Log.i("BelesR38", "state=$j")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        candidate = intent.getStringExtra("candidate")
        session = intent.getStringExtra("isolatedProfile")
        if (savedInstanceState != null || !R38Entry.allowed(BuildConfig.DEBUG, candidate, session, intent.action,
                intent.getStringExtra("r38Action"), intent.getStringExtra("database"), intent.getIntExtra("key", -1), javaClass.name)) {
            event("entry-rejected", JSONObject().put("storesOpened", 0)); finish(); return
        }
        scope.launch {
            try { measure() }
            catch (e: TimeoutCancellationException) { event("TIMEOUT_25S") }
            catch (e: CancellationException) { event("CANCELLED"); throw e }
            catch (e: Exception) { event("HOLD", JSONObject().put("type", e.javaClass.simpleName)) }
            finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    release()
                    store?.close(); store = null
                    event("close"); finish()
                }
            }
        }
    }
    private fun definition(o: JSONObject): PlaybackDefinition {
        check(o.getJSONArray("headers").length() == 0 && o.getJSONArray("unknownRequiredDirectives").length() == 0)
        check(listOf("mimeType", "protocol", "drm", "authorityScope").all(o::isNull))
        check(o.getJSONArray("options").length() == 0 && o.getBoolean("isLossless"))
        val raw = o.getString("address"); val d = PlaybackDefinition(Address.fromForm(raw)); check(d.address.raw == raw)
        return d
    }
    private fun codec(d: PlaybackDefinition) = PreviewCodec.pack(listOf(d.address.raw, "", "", ""))
    private fun full(d: PlaybackDefinition) = PreviewCodec.pack(listOf(d.address.raw, "", "", "", "", "", "", "", "true"))
    private suspend fun measure() {
        val s = requireNotNull(session); val db = "$s.db"
        phase = "fresh-store-guard"
        check(!getDatabasePath(db).exists() && !getDatabasePath("$db-wal").exists() && !getDatabasePath("$db-shm").exists())
        val bytes = assets.open("r38-definitions.private.json").use { it.readBytes() }
        phase = "fixture-schema"
        check(sha(bytes) == R38FixtureBinding.sha256)
        val all = JSONArray(bytes.toString(Charsets.UTF_8))
        val entries = (0 until all.length()).map(all::getJSONObject)
        check(entries.size == 30 && entries.map { it.getString("candidateId") }.toSet() == R38Entry.candidates)
        val e = entries.single { it.getString("candidateId") == candidate }
        check(e.getString("publisherStatus") == "CURRENT_EXACT" && e.isNull("oldChannelId"))
        val old = definition(e.getJSONObject("old")); val new = definition(e.getJSONObject("new"))
        phase = "definition-hashes"
        check(sha(old.address.raw) == e.getString("oldUriSha256") && sha(new.address.raw) == e.getString("newUriSha256"))
        check(sha(codec(old)) == e.getString("oldLibraryCodecSha256") && sha(codec(new)) == e.getString("newLibraryCodecSha256"))
        check(sha(full(old)) == e.getString("oldFullDefinitionSha256") && sha(full(new)) == e.getString("newFullDefinitionSha256"))
        val seed = assets.open("trlist.m3u").use { it.readBytes() }; check(sha(seed) == VerifiedDefinitionTransition.seedSha256)
        phase = "seed-source-row"
        val rows = PublicM3u.parse(seed.toString(Charsets.UTF_8)).rows
        val uuid = UUID.randomUUID(); testId = OpaqueId(uuid.mostSignificantBits, uuid.leastSignificantBits)
        val ch = R38FixtureModel.channel(rows, e.getLong("rawIndex"), e.getString("rawProviderHint"), old, new, testId!!)
        check(e.getString("sourceId") == PreviewCodec.id(PublicM3u.source.value))
        val encoded = LibraryCodec.channels(listOf(ch))
        check(LibraryCodec.channels(encoded).single() == ch)
        val actualCodec = PreviewCodec.unpack(PreviewCodec.unpack(encoded).single())[5]
        check(actualCodec == codec(new)) // Actual LibraryCodec, not research JSON.
        val data = PreviewData(favorites = emptyList(), history = emptyList(), categories = ch.groups,
            channels = listOf(ch), sources = emptyList(), profileId = OpaqueId(0x523338, e.getLong("rawIndex")))
            .withPref("history", "Kapalı").withPref("startup", "Kapalı")
        store = AndroidPreviewStore(applicationContext, key = 2, databaseName = db) { data }
        phase = "isolated-store-roundtrip"
        val loaded = store!!.load(); check(loaded == data)
        val before = requireNotNull(store!!.readRow())
        event("fixture", JSONObject().put("assetSha256", sha(bytes)).put("seedSha256", sha(seed))
            .put("sourceId", e.getString("sourceId")).put("rawIndex", e.getLong("rawIndex"))
            .put("researchMetadataId", e.getString("metadataId")).put("sourceProviderHintSha256", sha(ch.providerId))
            .put("uriSha256", sha(new.address.raw)).put("oldLibraryCodecSha256", sha(codec(old)))
            .put("newLibraryCodecSha256", sha(actualCodec)).put("oldFullDefinitionSha256", sha(full(old)))
            .put("newFullDefinitionSha256", sha(full(new))).put("publisherEvidenceSha256", e.getString("publisherEvidenceSha256"))
            .put("modelSha256", sha(LibraryCodec.encode(loaded))).put("revision", r23Revision(before)))
        val texture = TextureView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply { text = "R38 · ${e.getString("name")} · ayrı test" })
        layout.addView(texture, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply { text = "ÇIKIŞ"; setOnClickListener { finish() } })
        setContentView(layout)
        val first = CompletableDeferred<Unit>(); val ready = CompletableDeferred<Unit>()
        player = AndroidPreviewPlayer(applicationContext, RenderTrace(this, s, db))
        phase = "player"
        val p = player!!
        p.exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onRenderedFirstFrame(eventTime: AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                if (!first.isCompleted) { event("first-frame"); first.complete(Unit) }
            }
            override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, playbackState: Int) {
                if (playbackState == Player.STATE_READY && !ready.isCompleted) { event("READY"); ready.complete(Unit) }
            }
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) {
                event("player-error", JSONObject().put("code", error.errorCodeName))
                val failure = IllegalStateException("player-error")
                first.completeExceptionally(failure); ready.completeExceptionally(failure)
            }
        })
        p.exo.setVideoTextureView(texture)
        event("select", JSONObject().put("uriSha256", sha(new.address.raw)))
        p.select(ch)
        withTimeout(25000) { first.await(); ready.await(); while (!p.exo.isPlaying) delay(50) }
        val tracks = JSONArray()
        p.exo.currentTracks.groups.forEach { g -> (0 until g.length).filter(g::isTrackSelected).forEach { i ->
            val f = g.getTrackFormat(i)
            tracks.put(JSONObject().put("type", g.type).put("mime", f.sampleMimeType).put("codec", f.codecs)
                .put("width", f.width).put("height", f.height).put("bitrate", f.bitrate).put("language", f.language)
                .put("channels", f.channelCount).put("sampleRate", f.sampleRate))
        } }
        event("selected-tracks", JSONObject().put("tracks", tracks))
        val positions = mutableListOf<Long>()
        repeat(3) {
            check(p.exo.playbackState == Player.STATE_READY && p.exo.isPlaying)
            positions += p.exo.currentPosition
            event("position", JSONObject().put("positionMs", positions.last()).put("playing", true))
            if (it < 2) delay(1000)
        }
        check(positions.zipWithNext().all { (a, b) -> b > a })
        check(store!!.readRow() == before)
        event("PASS", JSONObject().put("positions", JSONArray(positions)).put("rowUnchanged", true))
        delay(1500) // Bounded window for the host PNG, within ten seconds of READY/first-frame.
    }
    private fun release() {
        val p = player ?: return
        event("release-begin"); p.release(); event("release-end"); player = null
    }
    override fun onDestroy() { scope.cancel(); release(); store?.close(); store = null; super.onDestroy() }
}
