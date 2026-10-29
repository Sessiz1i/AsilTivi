package com.omer.belestiviclone.shared.preview

import android.content.Context
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Entity(tableName = "preview_state")
data class PreviewRow(@PrimaryKey val key: Int = 1, val favorites: String, val hidden: String, val history: String, val categories: String, val hiddenCategories: String, val preferences: String, val sources: String)
@Dao
interface PreviewDao {
    @Query("SELECT * FROM preview_state WHERE `key` = :key") suspend fun read(key: Int): PreviewRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(row: PreviewRow)
}
@Database(entities = [PreviewRow::class], version = 1, exportSchema = true)
abstract class PreviewDatabase : RoomDatabase() { abstract fun state(): PreviewDao }

class AndroidPreviewStore(context: Context, private val key: Int = 1, private val databaseName: String = "belestivi_preview_v1.db",
    private val initial: suspend () -> PreviewData = { PreviewData() }) : PreviewStore {
    private val db = Room.databaseBuilder<PreviewDatabase>(context.applicationContext, databaseName)
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    fun close() = db.close()
    override suspend fun load(): PreviewData {
        val base = initial()
        val row = db.state().read(key) ?: return base.also { save(it) }
        return decode(row, base)
    }
    private fun decode(row: PreviewRow, base: PreviewData): PreviewData {
        val prefs = PreviewCodec.unpack(row.preferences); require(prefs.size % 2 == 0)
        val rawCats = PreviewCodec.unpack(row.categories)
        val rawHiddenCats = PreviewCodec.unpack(row.hiddenCategories).toSet()
        val dedupCats = CategoryNormalizer.deduplicateCategories(rawCats, normalizeAliases = true)
        val dedupHiddenCats = rawHiddenCats.mapNotNull { h ->
            dedupCats.firstOrNull { CategoryNormalizer.matches(it, h) } ?: h
        }.toSet()
        val user = base.copy(
            favorites = PreviewCodec.unpack(row.favorites).map(PreviewCodec::id),
            hidden = PreviewCodec.unpack(row.hidden).map(PreviewCodec::id).toSet(),
            history = PreviewCodec.unpack(row.history).map(PreviewCodec::id),
            categories = dedupCats, hiddenCategories = dedupHiddenCats,
            preferences = (base.preferences + prefs.chunked(2).associate { it[0] to it[1] }),
        )
        // An unreadable source envelope must not lock the profile: favorites, history and
        // settings are kept and the packaged default list is used until the next source save.
        return runCatching { LibraryCodec.decode(row.sources, user) }.getOrElse { user }
    }
    override suspend fun save(data: PreviewData) {
        val row = encode(data)
        db.useWriterConnection { connection -> connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
            db.state().write(row)
        } }
    }
    private fun encode(data: PreviewData): PreviewRow {
        fun ids(values: Collection<com.omer.belestiviclone.shared.catalog.OpaqueId>) = PreviewCodec.pack(values.map(PreviewCodec::id))
        return PreviewRow(key = key, favorites = ids(data.favorites), hidden = ids(data.hidden), history = ids(data.history), categories = PreviewCodec.pack(data.categories), hiddenCategories = PreviewCodec.pack(data.hiddenCategories.toList()), preferences = PreviewCodec.pack(data.preferences.flatMap { listOf(it.key, it.value) }), sources = LibraryCodec.encode(data))
    }
}
