package com.omer.belestiviclone

import android.util.AtomicFile
import android.util.Log
import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** DUMP protected shell entry executing the actual MainActivity/PreviewApp/SampleVideo path. */
class R30RenderActivity : MainActivity() {
    private var fixtureStore: AndroidPreviewStore? = null
    override fun r30Store(session: String): PreviewStore {
        check(R30Entry.admit(BuildConfig.DEBUG, session, intent.action, intent.getStringExtra("r30Action"), javaClass.name) == session)
        val dbName = "$session.db"
        val cacheName = "$session-catalog"
        val existingOnly = intent.getBooleanExtra("r31ExistingOnly", false)
        if (existingOnly) {
            check(getDatabasePath(dbName).isFile && File(filesDir, cacheName).isFile &&
                !File(filesDir, "$cacheName.bak").exists()) { "R31-existing-fixture-required" }
        }
        val store = AndroidPreviewStore(applicationContext, key = 2, databaseName = dbName) {
            withContext(Dispatchers.IO) {
                val asset = assets.open("trlist.m3u").use { it.readBytes() }
                check(hash(asset) == VerifiedDefinitionTransition.seedSha256)
                val parsed = PublicM3u.parse(asset.toString(Charsets.UTF_8))
                val n = parsed.rows.size
                val seed = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(0x523330, it.toLong())) },
                    List(n) { MembershipId(OpaqueId(0x523331, it.toLong())) }, List(n) { CategoryId(OpaqueId(0x523332, it.toLong())) }))
                val fixture = R30FixtureModel.create(session, seed)
                val encoded = LibraryCodec.channels(fixture.channels)
                val cache = AtomicFile(File(filesDir, cacheName))
                if (cache.baseFile.exists() || File(cache.baseFile.path + ".bak").exists()) {
                    check(cache.readFully().toString(Charsets.UTF_8) == encoded) { "R30-cache-binding" }
                } else {
                    check(!getDatabasePath(dbName).exists()) { "R30-missing-cache-existing-db" }
                    val out = cache.startWrite()
                    try { out.write(encoded.toByteArray(Charsets.UTF_8)); cache.finishWrite(out) }
                    catch (e: Exception) { cache.failWrite(out); throw e }
                }
                fixture
            }
        }
        fixtureStore = store
        Log.i("BelesRender", "event=fixture-store pid=${android.os.Process.myPid()} task=$taskId activity=${RenderTrace.identity(this)} session=$session db=$dbName key=2 cache=$cacheName normalStoreOpened=false")
        return object : PreviewStore {
            override suspend fun load(): PreviewData {
                if (existingOnly) checkNotNull(store.readRow()) { "R31-existing-fixture-row-required" }
                return store.load().also { snapshot("load", session, store, it) }
            }
            override suspend fun save(data: PreviewData) { store.save(data); snapshot("save", session, store, data) }
        }
    }
    private suspend fun snapshot(stage: String, session: String, store: AndroidPreviewStore, data: PreviewData) {
        val row = requireNotNull(store.readRow())
        val channels = data.channels
        val expected = if (session.endsWith("a-20261015")) R28ProfileTransition.entry.new else ApprovedR21.manifest.entries.single { it.candidate == "R20-C003" }.new
        check(channels.size == 1 && channels.single().definition == expected && data.library == null)
        val info = JSONObject().put("stage", stage).put("pid", android.os.Process.myPid()).put("task", taskId)
            .put("activity", RenderTrace.identity(this)).put("session", session).put("db", "$session.db").put("key", row.key)
            .put("cache", "$session-catalog").put("revision", r23Revision(row)).put("envelope", PreviewCodec.unpack(row.sources).first())
            .put("modelSha256", hash(LibraryCodec.encode(data).toByteArray(Charsets.UTF_8)))
            .put("preferencesSha256", hash(row.preferences.toByteArray(Charsets.UTF_8)))
            .put("relationsSha256", hash(PreviewCodec.pack(listOf(row.favorites, row.history, row.hidden, row.categories, row.hiddenCategories)).toByteArray(Charsets.UTF_8)))
            .put("journal", "none-synthetic").put("favorites", data.favorites.size).put("history", data.history.size)
            .put("historyEnabled", data.pref("history")).put("startup", data.pref("startup")).put("format", data.pref("format"))
            .put("testID", PreviewCodec.id(channels.single().id)).put("uriSha256", hash(expected.address.raw.toByteArray(Charsets.UTF_8)))
        Log.i("BelesR30Fixture", "state=$info")
    }
    override fun onDestroy() { super.onDestroy(); fixtureStore?.close(); fixtureStore = null }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
