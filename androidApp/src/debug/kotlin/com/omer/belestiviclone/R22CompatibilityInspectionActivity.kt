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

/** Shell-only, exact R22 profile, absent from release/navigation. No other DB can be selected. */
class R22CompatibilityInspectionActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = VerifiedDefinitionTransition.compatibilitySession
    private lateinit var seed: List<PreviewChannel>
    private lateinit var request: PilotRequest
    private val ops = SourceOperations { java.util.UUID.randomUUID().let { OpaqueId(it.mostSignificantBits, it.leastSignificantBits) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            try {
                require(intent.getStringExtra("isolatedProfile") == session)
                val catalog = AndroidPublicCatalog(applicationContext, "$session-catalog")
                request = androidR21Pilot(applicationContext, session, catalog, false)
                seed = request.context.seed
                when (intent.getStringExtra("check")) {
                    "suite" -> suite()
                    "inspect" -> useStore { summary("inspect", it.load(), requireNotNull(it.readRow())); oldReader(requireNotNull(it.readRow()), true) }
                    else -> error("unknown-check")
                }
            } catch (e: Exception) { Log.e("BelesR22Check", "FAILED type=${e.javaClass.simpleName} code=${(e as? TransitionProblem)?.code ?: "check-failed"}") }
            finally { withContext(Dispatchers.Main) { finish() } }
        }
    }
    private fun hash(value: String) = hash(value.toByteArray())
    private fun hash(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private suspend fun <T> useStore(action: String? = null, block: suspend (AndroidPreviewStore) -> T): T {
        val factory: (suspend () -> PilotRequest)? = action?.let { { request.copy(rollback = it == "rollback", legacyExit = it == "legacy-exit") } }
        val store = AndroidPreviewStore(applicationContext, key = 2, databaseName = "$session.db", pilot = factory) {
            PreviewData(channels = seed, profileId = PublicM3u.profile)
        }
        return try { block(store) } finally { store.close() }
    }
    private fun oldReader(row: PreviewRow, compatible: Boolean) {
        val bytes = assets.open("r19b-reader.dex").use { it.readBytes() }
        check(hash(bytes) == "7b9339c92aa7460e720c83d378aba9d3bb856b55edf67a9e0b08e4bf537221ad")
        val dex = File(codeCacheDir, "r22-r19b-reader.dex")
        if (!dex.exists()) {
            // Android 14 dynamic-code contract: read-only before writing; open descriptor stays writable.
            dex.outputStream().use { output -> check(dex.setReadOnly()); output.write(bytes) }
        }
        check(hash(dex.readBytes()) == hash(bytes))
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
        if (compatible) { check(result.take(7) == input.toList()); check(result[7] == "408") }
        else { check(result[0] != input[0] && result[7] == "0" && result[8] == "0") }
        Log.i("BelesR22Check", "oldReader compatible=$compatible rowExact=${result.take(7) == input.toList()} channels=${result[7]} memberships=${result[8]} categories=${result[9]} dexSha256=${hash(bytes)} PASS")
    }
    private fun summary(stage: String, data: PreviewData, row: PreviewRow) {
        val library = requireNotNull(data.library)
        val info = JSONObject().put("stage", stage).put("session", session).put("sourcesSha256", hash(row.sources))
            .put("envelope", PreviewCodec.unpack(row.sources).first()).put("applied", library.transition?.applied ?: JSONObject.NULL)
            .put("relationsSha256", hash(listOf(row.favorites, row.hidden, row.history, row.categories, row.hiddenCategories, row.preferences).joinToString("|")))
            .put("favorites", data.favorites.size).put("history", data.history.size).put("preferences", data.preferences.size)
            .put("channels", data.channels.size).put("memberships", library.snapshot.memberships.size).put("categories", library.snapshot.categories.size)
            .put("idsSha256", hash(PreviewCodec.pack(data.channels.map { PreviewCodec.id(it.id) })))
            .put("membershipsSha256", hash(library.snapshot.memberships.toString())).put("categoryIdsSha256", hash(library.snapshot.categories.toString()))
            .put("firstSeenSha256", hash(library.snapshot.channels.map { "${PreviewCodec.id(it.id.value)}:${it.firstSeen}" }.joinToString("|")))
        Log.i("BelesR22Check", "state=$info")
        ApprovedR21.manifest.entries.forEach { entry ->
            val ch = data.channels.single { it.definition == entry.old || it.definition == entry.new }
            Log.i("BelesR22Check", "stage=$stage target=${entry.candidate} id=${PreviewCodec.id(ch.id)} old=${ch.definition == entry.old}")
        }
    }
    private suspend fun suite() {
        val ids = ApprovedR21.manifest.entries.map { e -> seed.single { it.definition == e.old }.id }
        var initial = PreviewData(channels = seed, profileId = PublicM3u.profile, favorites = ids, history = ids.reversed(),
            categories = seed.flatMap { it.groups }.distinct(), hiddenCategories = setOf("Spor"), hidden = setOf(seed.last().id))
            .withPref("lastChannel", PreviewCodec.id(ids.last())).withPref("history", "Kapalı").withPref("startup", "Kapalı")
        initial = initial.copy(library = ops.bootstrap(initial).copy(records = listOf(request.context.seedSource)))
        useStore { check(it.readRow() == null) { "suite-requires-new-profile" }; it.save(initial); summary("before", initial, requireNotNull(it.readRow())) }
        val applied = useStore("apply") { it.load().also { data -> summary("applied", data, requireNotNull(it.readRow())); oldReader(requireNotNull(it.readRow()), false) } }
        val intervening = applied.copy(favorites = ids.reversed(), history = ids + seed.last().id, categories = applied.categories.reversed(), hiddenCategories = setOf("Haber"))
            .withPref("format", "Fit").withPref("opacity", "49")
        useStore { it.save(intervening); summary("intervening", intervening, requireNotNull(it.readRow())) }
        val prepared = VerifiedDefinitionTransition.legacyExit(intervening, request.context, ApprovedR21.manifest)
        useStore { store ->
            val old = requireNotNull(store.readRow()); val next = old.copy(sources = LibraryCodec.encode(prepared))
            check(!store.compareAndSet(old.copy(history = "stale"), next)); check(store.readRow() == old)
            Log.i("BelesR22Check", "atomic stale-row noWrite=true PASS")
            try { commitTransition(old, next) { a, b -> store.compareAndSet(a, b) { error("after-write") } }; error("not-thrown") }
            catch (e: IllegalStateException) { check(e.message == "after-write") }
            check(store.readRow() == old); Log.i("BelesR22Check", "atomic after-SQL-write-failure oldRowPreserved=true PASS")
            var entered = false
            coroutineScope {
                val ready = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
                val job = launch {
                    val staged = VerifiedDefinitionTransition.legacyExit(intervening, request.context, ApprovedR21.manifest)
                    check(LibraryCodec.encode(staged) == next.sources); ready.complete(Unit); proceed.await()
                    commitTransition(old, next) { a, b -> entered = true; store.compareAndSet(a, b) }
                }
                ready.await(); job.cancel(); proceed.complete(Unit); job.join()
            }
            check(!entered && store.readRow() == old); Log.i("BelesR22Check", "atomic cancelled-preparation noWrite=true PASS")
            coroutineScope {
                val writing = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
                val job = launch { commitTransition(old, next) { a, b -> store.compareAndSet(a, b) { writing.complete(Unit); proceed.await() } } }
                writing.await(); job.cancel(); proceed.complete(Unit); job.join()
            }
            check(store.readRow() == next); Log.i("BelesR22Check", "atomic cancelled-after-boundary singleCommit=true PASS")
            val data = store.load(); check(data == prepared); summary("applied-exit", data, next); oldReader(next, true)
            try { store.save(intervening); error("stale-accepted") } catch (e: IllegalArgumentException) { check(e.message == "stale-source-journal") }
            check(store.readRow() == next); Log.i("BelesR22Check", "stale-activity noWrite=true PASS")
        }
        useStore("legacy-exit") { val before = it.readRow(); check(it.load() == prepared); check(it.readRow() == before); Log.i("BelesR22Check", "second-exit noWrite=true PASS") }
        useStore { store ->
            val reopened = store.load(); check(reopened == prepared)
            val text = assets.open("trlist.m3u").bufferedReader().use { it.readText() }
            check(ops.rebuild(reopened, reopened.library!!) == prepared)
            val default = ops.default(reopened, text); check(default == prepared); store.save(default)
            summary("reopen-default-rebuild", default, requireNotNull(store.readRow()))
        }
        useStore("apply") { val data = it.load(); check(data.channels.map { c -> c.id } == prepared.channels.map { c -> c.id }); summary("reapply", data, requireNotNull(it.readRow())) }
        useStore("rollback") { summary("rolledback", it.load(), requireNotNull(it.readRow())) }
        useStore("legacy-exit") { val data = it.load(); check(data == prepared); summary("rolledback-exit", data, requireNotNull(it.readRow())); oldReader(requireNotNull(it.readRow()), true) }
        useStore { val data = it.load(); check(data == prepared); summary("final-compatible", data, requireNotNull(it.readRow())) }
        Log.i("BelesR22Check", "SUITE PASS realProfileOpened=false r21ProfileOpened=false")
    }
}
