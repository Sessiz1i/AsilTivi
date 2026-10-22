package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Shell-only debug evidence. It cannot select or open the real profile. No product navigation. */
class R21PilotInspectionActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            try {
                require(intent.getStringExtra("isolatedProfile") == VerifiedDefinitionTransition.session)
                if (intent.getStringExtra("check") == "atomic") atomicChecks() else inspect()
            } catch (e: Exception) { Log.e("BelesPilotCheck", "FAILED type=${e.javaClass.simpleName} code=${e.message?.take(100)}") }
            finally { withContext(Dispatchers.Main) { finish() } }
        }
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private suspend fun inspect() {
        val session = VerifiedDefinitionTransition.session
        val db = AndroidPreviewStore(applicationContext, key = 2, databaseName = "$session.db")
        try {
            val row = requireNotNull(db.readRow())
            val seed = AndroidPublicCatalog(applicationContext, "$session-catalog").load()
            val data = LibraryCodec.decode(row.sources, PreviewData(channels = seed, profileId = PublicM3u.profile))
            val report = JSONObject().put("session", session).put("sourcesSha256", hash(row.sources))
                .put("envelope", PreviewCodec.unpack(row.sources).first()).put("applied", data.library?.transition?.applied)
                .put("favorites", JSONArray(PreviewCodec.unpack(row.favorites))).put("history", JSONArray(PreviewCodec.unpack(row.history)))
                .put("preferences", JSONArray(PreviewCodec.unpack(row.preferences))).put("categoriesSha256", hash(row.categories))
                .put("hiddenSha256", hash(row.hidden)).put("hiddenCategoriesSha256", hash(row.hiddenCategories))
                .put("idsSha256", hash(PreviewCodec.pack(data.channels.map { PreviewCodec.id(it.id) })))
                .put("membershipsSha256", hash(data.library?.snapshot?.memberships.toString()))
                .put("categoryIdsSha256", hash(data.library?.snapshot?.categories.toString()))
            Log.i("BelesPilotCheck", "state=$report")
            ApprovedR21.manifest.entries.forEach { entry ->
                val ch = data.channels.single { it.definition == entry.old || it.definition == entry.new }
                val d = ch.definition!!
                val canonical = PreviewCodec.pack(listOf(d.address.raw, PreviewCodec.pack(d.headers.flatMap { listOf(it.name, it.value) }), d.mimeType.orEmpty(), PreviewCodec.pack(d.unknownRequiredDirectives)))
                Log.i("BelesPilotCheck", "target=${entry.candidate} channel=${PreviewCodec.id(ch.id)} definitionSha256=${hash(canonical)} firstSeen=${data.library?.snapshot?.channels?.single { it.id.value == ch.id }?.firstSeen}")
            }
        } finally { db.close() }
    }
    private suspend fun atomicChecks() {
        // Fresh test-owned database, never the migration DB or another round's profile.
        val name = "toplu-r21-atomic-${System.currentTimeMillis()}.db"
        val store = AndroidPreviewStore(applicationContext, key = 2, databaseName = name)
        try {
            store.save(PreviewData())
            val old = requireNotNull(store.readRow())
            val next = old.copy(sources = "new-source-and-journal")
            check(!store.compareAndSet(old.copy(history = "stale"), next))
            check(store.readRow() == old)
            Log.i("BelesPilotCheck", "atomic stale-row noWrite=true PASS")
            try { commitTransition(old, next) { expected, update -> store.compareAndSet(expected, update) { error("injected-after-write") } }; error("fault-not-raised") }
            catch (e: IllegalStateException) { check(e.message == "injected-after-write") }
            check(store.readRow() == old)
            Log.i("BelesPilotCheck", "atomic after-SQL-write-failure oldRowPreserved=true PASS")
            var entered = false
            coroutineScope {
                val job = launch(start = CoroutineStart.LAZY) { commitTransition(old, next) { expected, update -> entered = true; store.compareAndSet(expected, update) } }
                job.cancelAndJoin()
            }
            check(!entered && store.readRow() == old)
            Log.i("BelesPilotCheck", "atomic cancelled-preparation noWrite=true PASS")
            coroutineScope {
                val writing = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
                val job = launch { commitTransition(old, next) { expected, update -> store.compareAndSet(expected, update) { writing.complete(Unit); proceed.await() } } }
                writing.await(); job.cancel(); proceed.complete(Unit); job.join()
            }
            check(store.readRow() == next)
            Log.i("BelesPilotCheck", "atomic cancelled-after-boundary singleCommit=true PASS")
        } finally { store.close() }
    }
}
