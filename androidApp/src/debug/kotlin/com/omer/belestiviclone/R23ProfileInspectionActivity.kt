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

/** Explicit shell DUMP action. Never seeds a normal profile or runs on normal startup. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class R23ProfileInspectionActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: AndroidPreviewPlayer? = null
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun admission(action: String): R23Admission {
        val isolated = intent.getStringExtra("isolatedProfile")
        require(isolated == null || isolated == R23ProfileTransition.testSession) { "wrong-session" }
        val session = if (isolated == null) R23ProfileTransition.normalSession else isolated
        return R23Admission(BuildConfig.DEBUG, 2, if (isolated == null) R23ProfileTransition.normalDatabase else "$isolated.db", session, action)
    }
    private fun store(a: R23Admission) = AndroidPreviewStore(applicationContext, key = a.key, databaseName = a.databaseName) { error("R23-initializer-forbidden") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent.getStringExtra("r23Action")
        scope.launch {
            try {
                when (command) {
                    "test-suite" -> { require(intent.getStringExtra("isolatedProfile") == R23ProfileTransition.testSession); suite() }
                    "preflight", "apply", "rollback", "legacy-exit", "inspect", "play", "default-check" -> {
                        val a = admission(if (command in listOf("inspect", "play", "default-check")) "preflight" else command!!)
                        // A missing real DB must not be created by Room opening it.
                        if (!applicationContext.getDatabasePath(a.databaseName).isFile) throw TransitionProblem("missing-database")
                        val request = androidR23Request(applicationContext, a)
                        val store = store(a)
                        try {
                            val prepared = if (command in listOf("apply", "rollback", "legacy-exit"))
                                store.executeR23(request, requireNotNull(intent.getStringExtra("expectedRevision")))
                                else store.prepareR23(request)
                            summary("$command-before", prepared.before, prepared.expected)
                            val written = requireNotNull(store.readRow())
                            if (command in listOf("apply", "rollback", "legacy-exit")) {
                                check(written == prepared.next)
                                summary("$command-after", prepared.after, written)
                                Log.i("BelesR23", "action=$command result=${if (prepared.expected == prepared.next) "no-op" else "committed"} userColumnsEqual=${prepared.expected.copy(sources = written.sources) == written}")
                            } else {
                                check(written == prepared.expected)
                                Log.i("BelesR23", "action=$command noWrite=true eligible=true expectedRevision=${r23Revision(written)}")
                            }
                            if (command == "default-check") {
                                val ops = SourceOperations { throw TransitionProblem("unexpected-allocation") }
                                check(ops.default(prepared.before, assets.open("trlist.m3u").bufferedReader().use { it.readText() }) == prepared.before)
                                check(ops.rebuild(prepared.before, requireNotNull(prepared.before.library)) == prepared.before)
                                Log.i("BelesR23", "default-rebuild noWrite=true sameModel=true PASS")
                            }
                            if (command == "play") play(prepared.before, request)
                        } finally { store.close() }
                    }
                    else -> throw TransitionProblem("missing-or-invalid-action")
                }
            } catch (e: Exception) {
                Log.e("BelesR23", "FAILED action=$command type=${e.javaClass.simpleName} code=${(e as? TransitionProblem)?.code ?: "check-failed"} noInitializer=true")
            } finally {
                withContext(Dispatchers.Main) { player?.release(); player = null; finish() }
            }
        }
    }
    private fun summary(stage: String, data: PreviewData, row: PreviewRow) {
        val lib = data.library
        fun digest(items: List<String>) = hash(PreviewCodec.pack(items))
        val targets = ApprovedR21.manifest.entries.map { e -> data.channels.single { it.definition in listOf(e.old, e.new) }.id }.toSet()
        val info = JSONObject().put("stage", stage).put("session", admission("preflight").session).put("revision", r23Revision(row))
            .put("sourcesSha256", hash(row.sources)).put("envelope", PreviewCodec.unpack(row.sources).first())
            .put("applied", lib?.transition?.applied ?: JSONObject.NULL).put("channels", data.channels.size)
            .put("favorites", data.favorites.size).put("history", data.history.size).put("memberships", lib?.snapshot?.memberships?.size ?: 0)
            .put("categories", lib?.snapshot?.categories?.size ?: 0)
            .put("idsSha256", digest(data.channels.map { PreviewCodec.id(it.id) }))
            .put("otherDefinitionsSha256", hash(LibraryCodec.channels(data.channels.filter { it.id !in targets })))
            .put("metadataSha256", digest(data.channels.map { PreviewCodec.pack(listOf(PreviewCodec.id(it.id), it.name, it.category, it.logo, PreviewCodec.pack(it.groups), it.providerId, it.available.toString(), it.video.toString(), PreviewCodec.pack(it.sourceReferences.map(PreviewCodec::id)))) }))
            .put("membershipsSha256", hash(lib?.snapshot?.memberships.toString())).put("categoryIdsSha256", hash(lib?.snapshot?.categories.toString()))
            .put("firstSeenSha256", digest(lib?.snapshot?.channels?.map { "${PreviewCodec.id(it.id.value)}:${it.firstSeen}" }.orEmpty()))
        listOf("favorites" to row.favorites, "hidden" to row.hidden, "history" to row.history, "categories" to row.categories,
            "hiddenCategories" to row.hiddenCategories, "preferences" to row.preferences).forEach { (name, value) -> info.put(name + "Sha256", hash(value)) }
        Log.i("BelesR23", "state=$info")
        ApprovedR21.manifest.entries.forEach { entry ->
            val ch = data.channels.single { it.definition in listOf(entry.old, entry.new) }
            val d = requireNotNull(ch.definition)
            Log.i("BelesR23", "stage=$stage candidate=${entry.candidate} id=${PreviewCodec.id(ch.id)} old=${d == entry.old} uriSha256=${hash(d.address.raw)} definitionSha256=${hash(PreviewCodec.pack(listOf(d.address.raw, PreviewCodec.pack(d.headers.flatMap { listOf(it.name, it.value) }), d.mimeType.orEmpty(), PreviewCodec.pack(d.unknownRequiredDirectives))))}")
        }
    }
    private suspend fun play(data: PreviewData, request: R23Request) {
        val entry = ApprovedR21.manifest.entries.single { it.candidate == intent.getStringExtra("candidate") }
        val id = request.context.seed.single { it.definition == entry.old }.id
        val channel = data.channels.single { it.id == id }
        check(channel.definition == entry.new)
        var firstFrame = false
        withContext(Dispatchers.Main) {
            val texture = TextureView(this@R23ProfileInspectionActivity)
            setContentView(texture)
            player = AndroidPreviewPlayer(applicationContext) { Log.i("BelesR23", "probe-played candidate=${entry.candidate} historyWrite=false") }
            player!!.exo.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onRenderedFirstFrame(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, output: Any, renderTimeMs: Long) {
                    firstFrame = true
                    Log.i("BelesR23", "probe-first-frame candidate=${entry.candidate} historyWrite=false")
                }
            })
            player!!.exo.setVideoTextureView(texture)
            player!!.select(channel)
        }
        delay(18000)
        withContext(Dispatchers.Main) {
            check(firstFrame && player!!.exo.playbackState == androidx.media3.common.Player.STATE_READY && player!!.exo.currentPosition > 0)
            Log.i("BelesR23", "probe-complete candidate=${entry.candidate} position=${player!!.exo.currentPosition} READY=true historyWrite=false")
        }
    }
    private fun oldReader(row: PreviewRow) {
        val bytes = assets.open("r19b-reader.dex").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(digest == "7b9339c92aa7460e720c83d378aba9d3bb856b55edf67a9e0b08e4bf537221ad")
        val dex = File(codeCacheDir, "r23-r19b-reader.dex")
        if (!dex.exists()) dex.outputStream().use { output -> check(dex.setReadOnly()); output.write(bytes) }
        check(dex.readBytes().contentEquals(bytes))
        val filtered = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("com.omer.belestiviclone.shared.")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        val loader = DexClassLoader(dex.path, codeCacheDir.path, null, filtered)
        val type = loader.loadClass("com.omer.belestiviclone.shared.evidence.R19Bridge")
        check(type.classLoader === loader && loader.loadClass("com.omer.belestiviclone.shared.preview.LibraryCodec").classLoader === loader)
        val input = arrayOf(row.sources, row.favorites, row.hidden, row.history, row.categories, row.hiddenCategories, row.preferences)
        @Suppress("UNCHECKED_CAST") val result = type.getMethod("read", Array<String>::class.java).invoke(null, input as Any) as Array<String>
        check(result.take(7) == input.toList() && result[7] == "408")
        Log.i("BelesR23", "old-reader rowExact=true dexSha256=$digest PASS")
    }
    private suspend fun suite() {
        val a = admission("apply")
        // Test-only creation is structurally separate from every real-profile command.
        check(!getDatabasePath(a.databaseName).exists()) { "suite-requires-new-profile" }
        try { AndroidPublicCatalog(applicationContext, "${R23ProfileTransition.testSession}-absent").readExisting(); error("missing-cache-accepted") }
        catch (e: TransitionProblem) { check(e.code == "missing-or-unsettled-cache") }
        val seed = AndroidPublicCatalog(applicationContext, "${R23ProfileTransition.testSession}-catalog").load()
        val request = androidR23Request(applicationContext, a)
        val ids = ApprovedR21.manifest.entries.map { e -> seed.single { it.definition == e.old }.id }
        val legacy = PreviewData(channels = seed, profileId = PublicM3u.profile, sources = listOf(R23ProfileTransition.defaultLabel),
            favorites = ids, history = ids.reversed(), categories = seed.flatMap { it.groups }.distinct(), hidden = setOf(seed.last().id), hiddenCategories = setOf("Spor"))
            .withPref("lastChannel", PreviewCodec.id(ids.last())).withPref("history", "Kapalı").withPref("startup", "Kapalı")
        val ops = SourceOperations { java.util.UUID.randomUUID().let { OpaqueId(it.mostSignificantBits, it.leastSignificantBits) } }
        val initial = legacy.copy(library = ops.bootstrap(legacy).copy(records = listOf(request.context.seedSource)))
        val store = AndroidPreviewStore(applicationContext, key = 2, databaseName = a.databaseName) { legacy }
        try {
            check(store.readRow() == null)
            try { store.prepareR23(request); error("missing-row-accepted") } catch (e: TransitionProblem) { check(e.code == "missing-row") }
            check(store.readRow() == null)
            store.save(initial)
            val before = requireNotNull(store.readRow()); summary("suite-before", initial, before)
            listOf(a.copy(debug = false), a.copy(key = 1), a.copy(databaseName = R23ProfileTransition.normalDatabase),
                a.copy(action = null), a.copy(session = R23ProfileTransition.normalSession)).forEach { bad ->
                try { store.prepareR23(request.copy(admission = bad)); error("bad-gate-accepted") }
                catch (e: TransitionProblem) { check(e.code == "r23-disabled-profile") }
                check(store.readRow() == before)
            }
            Log.i("BelesR23", "negative-gates missing-cache missing-row release key1 wrong-db missing-action wrong-session noWrite=true PASS")
            val prepared = store.prepareR23(request); check(store.readRow() == before)
            check(!store.compareAndSet(before.copy(history = "stale"), prepared.next)); check(store.readRow() == before)
            Log.i("BelesR23", "atomic stale-row noWrite=true PASS")
            try { commitTransition(before, prepared.next) { x, y -> store.compareAndSet(x, y) { error("after-write") } }; error("accepted") }
            catch (e: IllegalStateException) { check(e.message == "after-write") }
            check(store.readRow() == before); Log.i("BelesR23", "atomic write-failure oldRowPreserved=true PASS")
            coroutineScope {
                val ready = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>(); var entered = false
                val job = launch { val next = store.prepareR23(request); ready.complete(Unit); proceed.await(); commitTransition(next.expected, next.next) { x, y -> entered = true; store.compareAndSet(x, y) } }
                ready.await(); job.cancel(); proceed.complete(Unit); job.join(); check(!entered && store.readRow() == before)
            }
            Log.i("BelesR23", "atomic cancelled-preparation noWrite=true PASS")
            try { store.executeR23(request, "stale"); error("accepted") } catch (e: TransitionProblem) { check(e.code == "stale-preflight") }
            check(store.readRow() == before)
            coroutineScope {
                val entered = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
                val job = launch { commitTransition(before, prepared.next) { x, y -> store.compareAndSet(x, y) { entered.complete(Unit); proceed.await() } } }
                entered.await(); job.cancel(); proceed.complete(Unit); job.join()
            }
            check(store.readRow() == prepared.next); Log.i("BelesR23", "atomic cancelled-after-boundary singleCommit=true PASS")
            val applied = store.prepareR23(request).before; summary("suite-applied", applied, requireNotNull(store.readRow()))
            val noOp = store.executeR23(request, r23Revision(requireNotNull(store.readRow()))); check(noOp.expected == noOp.next)
            check(store.load() == applied); check(ops.default(applied, assets.open("trlist.m3u").bufferedReader().use { it.readText() }) == applied)
            val intervening = applied.copy(favorites = ids.reversed(), history = ids + seed.last().id).withPref("opacity", "49").withPref("format", "Sığdır (Fit)")
            store.save(intervening)
            suspend fun action(name: String): PreviewData {
                val r = request.copy(admission = a.copy(action = name))
                val result = store.executeR23(r, r23Revision(requireNotNull(store.readRow())))
                check(result.expected.copy(sources = result.next.sources) == result.next)
                summary("suite-$name", result.after, result.next)
                return result.after
            }
            val exited = action("legacy-exit"); oldReader(requireNotNull(store.readRow()))
            check(exited.favorites == intervening.favorites && exited.history == intervening.history && exited.preferences == intervening.preferences)
            check(store.load() == exited); action("legacy-exit"); action("apply"); action("rollback"); action("legacy-exit"); oldReader(requireNotNull(store.readRow()))
            action("apply") // Leave new definitions for separate native first-frame probes.
            Log.i("BelesR23", "SUITE PASS realProfileOpened=false r21Opened=false r22Opened=false")
        } finally { store.close() }
    }
}
