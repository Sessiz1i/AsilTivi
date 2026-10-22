package com.omer.belestiviclone

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.security.MessageDigest

/** DUMP-only, existing normal rows only. Never calls load, transition/proof, save or cache initialization. */
class R31SnapshotActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope.launch {
            try {
                val db = intent.getStringExtra("database")
                val key = intent.getIntExtra("key", -1)
                val command = intent.getStringExtra("r31Action")
                val exists = db == R31ReadOnlySnapshot.database && getDatabasePath(R31ReadOnlySnapshot.database).isFile
                R31ReadOnlySnapshot.admit(BuildConfig.DEBUG,intent.action,command,db,key,exists)
                val cache = AndroidPublicCatalog(applicationContext).readExisting()
                val store = AndroidPreviewStore(applicationContext,key=2,databaseName=R31ReadOnlySnapshot.database) { error("R31-initializer-forbidden") }
                try {
                    val (row,data) = R31ReadOnlySnapshot.read(BuildConfig.DEBUG,intent.action,command,db,key,exists) { requested ->
                        if (requested == 2) store.readRow() else {
                            val startup = AndroidPreviewStore(applicationContext,databaseName=R31ReadOnlySnapshot.database) { error("R31-initializer-forbidden") }
                            try { startup.readRow() } finally { startup.close() }
                        }
                    }
                    validate(data,cache)
                    summary(data,row)
                    check(store.readRow() == row) { "r31-concurrent-row-change" }
                    Log.i("BelesR31","snapshot PASS noWrite=true db=$db key=$key pid=${android.os.Process.myPid()} task=$taskId")
                } finally { store.close() }
            } catch (e: Exception) {
                Log.e("BelesR31","snapshot HOLD type=${e.javaClass.simpleName} noWrite=true")
            } finally { withContext(Dispatchers.Main) { finish() } }
        }
    }
    private fun validate(data: PreviewData, cache: List<PreviewChannel>) {
        require(PreviewCodec.unpack(LibraryCodec.encode(data)).firstOrNull() == "BT-SOURCES-4") { "r31-exact-envelope" }
        val lib = requireNotNull(data.library)
        require(lib.active == PublicM3u.source && lib.main.kind == SourceKind.DEFAULT && lib.main.parent == null &&
            !lib.main.deleted && lib.main.address == PublicM3u.url)
        require(data.channels.size == 408 && cache.size == 408 && data.channels.map { it.id } == cache.map { it.id })
        require(data.channels.map { it.id }.distinct().size == 408)
        val j23 = requireNotNull(lib.transition); val j26 = requireNotNull(lib.r26); val j28 = requireNotNull(lib.r28)
        require(j23.applied && j23.manifest == ApprovedR21.manifest && j26.previous == j23 && j28.previous == j26)
        require(j26.target.entry == R26ProfileTransition.entry && j28.target.entry == R28ProfileTransition.entry)
        require(j28.target.id == PreviewCodec.id("1572579962743047039,-8208355493221762900"))
        val entries = ApprovedR21.manifest.entries + R26ProfileTransition.entry + R28ProfileTransition.entry
        entries.forEach { entry ->
            val seed = cache.single { it.definition == entry.old }
            require(data.channels.single { it.id == seed.id }.definition == entry.new)
            require(lib.snapshot.channels.single { it.id.value == seed.id }.definition == entry.new)
            require(lib.main.rows.count { it.definition == entry.new } == 1)
        }
        cache.forEach { original ->
            val expected = entries.singleOrNull { it.old == original.definition }?.new ?: original.definition
            require(data.channels.single { it.id == original.id }.definition == expected)
        }
        require(cache.map { original ->
            data.channels.single { it.id == original.id }.copy(definition=original.definition)
        } == cache) // Exact metadata and all 407 other definitions; four approved definitions only.
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun summary(data: PreviewData, row: PreviewRow) {
        val lib = data.library!!; val target = lib.r28!!.target.id
        fun digest(items: List<String>) = hash(PreviewCodec.pack(items))
        val outer = PreviewCodec.unpack(row.sources); val middle = PreviewCodec.unpack(outer[1]); val inner = PreviewCodec.unpack(middle[1])
        val info = JSONObject().put("revision",r23Revision(row)).put("envelope",outer[0]).put("db",R31ReadOnlySnapshot.database).put("key",2)
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
        Log.i("BelesR31","state=$info")
        (ApprovedR21.manifest.entries+R26ProfileTransition.entry+R28ProfileTransition.entry).forEach { entry ->
            val channel=data.channels.single { it.definition == entry.new }; val d=channel.definition!!
            val serialized=PreviewCodec.pack(listOf(d.address.raw,PreviewCodec.pack(d.headers.flatMap { listOf(it.name,it.value) }),d.mimeType.orEmpty(),PreviewCodec.pack(d.unknownRequiredDirectives)))
            Log.i("BelesR31","candidate=${entry.candidate} id=${PreviewCodec.id(channel.id)} uriSha256=${hash(d.address.raw)} definitionSha256=${hash(serialized)}")
        }
    }
}
