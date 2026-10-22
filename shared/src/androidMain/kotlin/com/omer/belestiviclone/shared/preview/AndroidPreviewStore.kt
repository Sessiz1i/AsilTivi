package com.omer.belestiviclone.shared.preview

import android.content.Context
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import com.omer.belestiviclone.shared.catalog.OpaqueId
import java.security.MessageDigest

data class PilotRequest(val context: PilotContext, val rollback: Boolean = false, val legacyExit: Boolean = false)

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
    private val pilot: (suspend () -> PilotRequest)? = null, private val initial: suspend () -> PreviewData = { PreviewData() }) : PreviewStore {
    private val db = Room.databaseBuilder<PreviewDatabase>(context.applicationContext, databaseName)
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    /** No normal context, load/initializer, retry or snapshot restore. All planning precedes the writer. */
    suspend fun executeR38B(request: R38BRequest, expected: PreviewRow, revision: String,
        beforeBoundary: suspend () -> Unit = {}, afterSql: suspend () -> Unit = {}): R38BPrepared {
        if (!R38BProfileTransition.allowed(request.admission) || request.admission.databaseName != databaseName ||
            request.admission.key != key || request.admission.action == "preflight") throw TransitionProblem("r38b-disabled-profile")
        val plan = runCatching { planR38B(request, expected) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        beforeBoundary()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
                    if (expected.key != key || r23Revision(expected) != revision || db.state().read(key) != expected)
                        throw TransitionProblem("r38b-stale-full-row")
                    val prepared = plan.getOrThrow() // HOLD still checked expected full row under IMMEDIATE.
                    if (prepared.next != expected) {
                        check(expected.copy(sources = prepared.next.sources) == prepared.next)
                        db.state().write(prepared.next)
                        afterSql() // Synthetic-only actual SQL fault/cancel injection; throw rolls back.
                    }
                    prepared
                }
            }
        }
    }

    suspend fun executeR38C(request: R38CRequest, expected: PreviewRow, revision: String): R38CPrepared {
        if (!R38CProfileTransition.allowed(request.admission) || request.admission.databaseName != databaseName ||
            request.admission.key != key || request.admission.action == "preflight") throw TransitionProblem("r38c-disabled-profile")
        val plan = runCatching { planR38C(request, expected) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
                    if (expected.key != key || r23Revision(expected) != revision || db.state().read(key) != expected)
                        throw TransitionProblem("r38c-stale-full-row")
                    val prepared = plan.getOrThrow() // HOLD still checked expected full row under IMMEDIATE.
                    if (prepared.next != expected) {
                        check(expected.copy(sources = prepared.next.sources) == prepared.next)
                        db.state().write(prepared.next)
                    }
                    prepared
                }
            }
        }
    }

    suspend fun executeR38CSynthetic(request: R38CRequest, expected: PreviewRow, revision: String,
        beforeBoundary: suspend () -> Unit = {}, afterSql: suspend () -> Unit = {}): R38CPrepared {
        if (!R38CProfileTransition.allowedSynthetic(request.admission) || request.admission.databaseName != databaseName ||
            request.admission.key != key || request.admission.action == "preflight") throw TransitionProblem("r38c-disabled-profile")
        val plan = runCatching { planR38C(request, expected) }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        beforeBoundary()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
                    if (expected.key != key || r23Revision(expected) != revision || db.state().read(key) != expected)
                        throw TransitionProblem("r38c-stale-full-row")
                    val prepared = plan.getOrThrow() // HOLD still checked expected full row under IMMEDIATE.
                    if (prepared.next != expected) {
                        check(expected.copy(sources = prepared.next.sources) == prepared.next)
                        db.state().write(prepared.next)
                        afterSql() // Synthetic-only actual SQL fault/cancel injection; throw rolls back.
                    }
                    prepared
                }
            }
        }
    }

    suspend fun readRow(): PreviewRow? = db.state().read(key)
    fun close() = db.close()
    /** No initializer, seeding, or normal-startup action. Only the explicit R33 admission. */
    suspend fun prepareR33(request: R33Request): R33Prepared {
        if (!R33ProfileTransition.allowed(request.admission) || request.admission.key != key || request.admission.databaseName != databaseName)
            throw TransitionProblem("r33-disabled-profile")
        val expected = readRow() ?: throw TransitionProblem("missing-row")
        if (PreviewCodec.unpack(expected.sources).firstOrNull() !in setOf("BT-SOURCES-4", "BT-SOURCES-5"))
            throw TransitionProblem("r33-unproven-envelope")
        val before = decode(expected, PreviewData(channels = emptyList(), profileId = PublicM3u.profile))
        val after = R33ProfileTransition.prepare(before, request.context, request.admission)
        val next = encode(after)
        check(expected.copy(sources = next.sources) == next) { "user-columns-changed" }
        check(decode(next, before.copy(library = null, channels = emptyList())) == after) { "r33-lossy-envelope" }
        return R33Prepared(before, after, expected, next)
    }
    suspend fun executeR33(request: R33Request, expectedRevision: String): R33Prepared {
        if (request.admission.action == "preflight") throw TransitionProblem("r33-no-write-action")
        val prepared = prepareR33(request)
        if (r23Revision(prepared.expected) != expectedRevision) throw TransitionProblem("stale-preflight")
        commitTransition(prepared.expected, prepared.next) { old, new ->
            if (old != new) compareAndSet(old, new) else db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) { db.state().read(key) == old }
            }
        }
        return prepared
    }
    /** No initializer, seeding, or normal-startup action. Only the explicit R28 admission. */
    suspend fun prepareR28(request: R28Request): R28Prepared {
        if (!R28ProfileTransition.allowed(request.admission) || request.admission.key != key || request.admission.databaseName != databaseName)
            throw TransitionProblem("r28-disabled-profile")
        val expected = readRow() ?: throw TransitionProblem("missing-row")
        if (PreviewCodec.unpack(expected.sources).firstOrNull() !in setOf("BT-SOURCES-3", "BT-SOURCES-4"))
            throw TransitionProblem("r28-unproven-envelope")
        val before = decode(expected, PreviewData(channels = emptyList(), profileId = PublicM3u.profile))
        val after = R28ProfileTransition.prepare(before, request.context, request.admission)
        val next = encode(after)
        check(expected.copy(sources = next.sources) == next) { "user-columns-changed" }
        check(decode(next, before.copy(library = null, channels = emptyList())) == after) { "r28-lossy-envelope" }
        return R28Prepared(before, after, expected, next)
    }
    suspend fun executeR28(request: R28Request, expectedRevision: String): R28Prepared {
        if (request.admission.action == "preflight") throw TransitionProblem("r28-no-write-action")
        val prepared = prepareR28(request)
        if (r23Revision(prepared.expected) != expectedRevision) throw TransitionProblem("stale-preflight")
        commitTransition(prepared.expected, prepared.next) { old, new ->
            if (old != new) compareAndSet(old, new) else db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) { db.state().read(key) == old }
            }
        }
        return prepared
    }
    /** No initializer, seeding, or normal-startup action. Only the explicit R26 admission. */
    suspend fun prepareR26(request: R26Request): R26Prepared {
        if (!R26ProfileTransition.allowed(request.admission) || request.admission.key != key || request.admission.databaseName != databaseName)
            throw TransitionProblem("r26-disabled-profile")
        val expected = readRow() ?: throw TransitionProblem("missing-row")
        if (PreviewCodec.unpack(expected.sources).firstOrNull() !in setOf("BT-SOURCES-2", "BT-SOURCES-3"))
            throw TransitionProblem("r26-unproven-envelope")
        val before = decode(expected, PreviewData(channels = emptyList(), profileId = PublicM3u.profile))
        val after = R26ProfileTransition.prepare(before, request.context, request.admission)
        val next = encode(after)
        check(expected.copy(sources = next.sources) == next) { "user-columns-changed" }
        check(decode(next, before.copy(library = null, channels = emptyList())) == after) { "r26-lossy-envelope" }
        return R26Prepared(before, after, expected, next)
    }
    suspend fun executeR26(request: R26Request, expectedRevision: String): R26Prepared {
        if (request.admission.action == "preflight") throw TransitionProblem("r26-no-write-action")
        val prepared = prepareR26(request)
        if (r23Revision(prepared.expected) != expectedRevision) throw TransitionProblem("stale-preflight")
        commitTransition(prepared.expected, prepared.next) { old, new ->
            if (old != new) compareAndSet(old, new) else db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) { db.state().read(key) == old }
            }
        }
        return prepared
    }
    /** Explicit R23 action only: no initial(), no cache creation, no row seeding. */
    suspend fun prepareR23(request: R23Request): R23Prepared {
        if (!R23ProfileTransition.allowed(request.admission) || request.admission.key != key || request.admission.databaseName != databaseName)
            throw TransitionProblem("r23-disabled-profile")
        val expected = readRow() ?: throw TransitionProblem("missing-row")
        val envelope = PreviewCodec.unpack(expected.sources).firstOrNull()
        if (envelope !in setOf("BT-PROFILE-1", "BT-SOURCES-1", "BT-SOURCES-2")) throw TransitionProblem("unproven-envelope")
        val before = decode(expected, PreviewData(channels = emptyList(), profileId = PublicM3u.profile))
        fun allocate() = java.util.UUID.randomUUID().let { OpaqueId(it.mostSignificantBits, it.leastSignificantBits) }
        val after = R23ProfileTransition.prepare(before, request.context, request.admission, ApprovedR21.manifest, ::allocate)
        val next = encode(after)
        check(expected.copy(sources = next.sources) == next) { "user-columns-changed" }
        return R23Prepared(before, after, expected, next)
    }
    suspend fun executeR23(request: R23Request, expectedRevision: String): R23Prepared {
        if (request.admission.action == "preflight") throw TransitionProblem("r23-no-write-action")
        val prepared = prepareR23(request)
        if (r23Revision(prepared.expected) != expectedRevision) throw TransitionProblem("stale-preflight")
        // Even a no-op checks the current complete row under IMMEDIATE, without a write.
        commitTransition(prepared.expected, prepared.next) { old, new ->
            if (old != new) compareAndSet(old, new) else db.useWriterConnection { connection ->
                connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) { db.state().read(key) == old }
            }
        }
        return prepared
    }
    override suspend fun load(): PreviewData {
        if (pilot != null) require(listOf(VerifiedDefinitionTransition.session, VerifiedDefinitionTransition.compatibilitySession).any { pilotStoreAllowed(key, databaseName, it) }) { "disabled-profile" }
        val request = pilot?.invoke()
        if (request != null) require(pilotStoreAllowed(key, databaseName, request.context.session) &&
            (!request.legacyExit || request.context.session == VerifiedDefinitionTransition.compatibilitySession)) { "disabled-profile" }
        val base = initial()
        val row = db.state().read(key) ?: run {
            if (request?.legacyExit == true) throw TransitionProblem("missing-row")
            encode(base).also { save(base) }
        }
        return if (request == null) decode(row, base) else transition(row, base, request)
    }
    private fun decode(row: PreviewRow, base: PreviewData): PreviewData {
        val prefs = PreviewCodec.unpack(row.preferences); require(prefs.size % 2 == 0)
        return LibraryCodec.decode(row.sources, base.copy(
            favorites = PreviewCodec.unpack(row.favorites).map(PreviewCodec::id),
            hidden = PreviewCodec.unpack(row.hidden).map(PreviewCodec::id).toSet(),
            history = PreviewCodec.unpack(row.history).map(PreviewCodec::id),
            categories = PreviewCodec.unpack(row.categories), hiddenCategories = PreviewCodec.unpack(row.hiddenCategories).toSet(),
            preferences = prefs.chunked(2).associate { it[0] to it[1] }.also { require(it.keys.containsAll(PreviewData().preferences.keys)) },
        ))
    }
    override suspend fun save(data: PreviewData) {
        val row = encode(data)
        db.useWriterConnection { connection -> connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
            // An older activity must not erase a committed transition or undo a rollback.
            val current = db.state().read(key)
            require(current != null || (data.library?.r38b == null && data.library?.r38c == null)) { "r38b-missing-current-row" }
            current?.sources?.let { encoded ->
                val envelope = PreviewCodec.unpack(encoded)
                if (envelope.firstOrNull() == R38CProfileTransition.envelope) {
                    val currentModel = LibraryCodec.decode(encoded, PreviewData())
                    require(data.library?.r38c == currentModel.library!!.r38c &&
                        LibraryCodec.encode(data.copy(sources = currentModel.sources)) == encoded) { "r38c-stale-source-journal" }
                } else if (data.library?.r38c != null) {
                    throw TransitionProblem("r38c-stale-source-journal")
                } else if (envelope.firstOrNull() == R38BProfileTransition.envelope) {
                    val currentModel = LibraryCodec.decode(encoded, PreviewData())
                    // User source labels may change; definitions, IDs, metadata and every journal
                    // must still be the current committed layer, including the C013 binding.
                    require(data.library?.r38b == currentModel.library!!.r38b &&
                        LibraryCodec.encode(data.copy(sources = currentModel.sources)) == encoded) { "stale-source-journal" }
                } else if (data.library?.r38b != null) {
                    throw TransitionProblem("r38b-stale-source-journal")
                } else if (envelope.firstOrNull() == "BT-SOURCES-5") {
                    val library = LibraryCodec.decode(encoded, PreviewData()).library!!
                    require(data.library?.r33 == library.r33 && data.library?.r28 == library.r28 && data.library?.r26 == library.r26 && data.library?.transition == library.transition) { "stale-source-journal" }
                } else if (envelope.firstOrNull() == "BT-SOURCES-4") {
                    require(data.library?.r33 == null) { "stale-source-journal" }
                    val library = LibraryCodec.decode(encoded, PreviewData()).library!!
                    require(data.library?.r28 == library.r28 && data.library?.r26 == library.r26 && data.library?.transition == library.transition) { "stale-source-journal" }
                } else if (envelope.firstOrNull() == "BT-SOURCES-3") {
                    require(data.library?.r33 == null && data.library?.r28 == null) { "stale-source-journal" }
                    val library = LibraryCodec.decode(encoded, PreviewData()).library!!
                    require(data.library?.r26 == library.r26 && data.library?.transition == library.transition) { "stale-source-journal" }
                } else if (envelope.firstOrNull() == "BT-SOURCES-2") {
                    require(data.library?.r33 == null && data.library?.r28 == null && data.library?.r26 == null) { "stale-source-journal" }
                    require(data.library?.transition == TransitionJournalCodec.decode(envelope[2])) { "stale-source-journal" }
                } else {
                    require(data.library?.r33 == null && data.library?.r28 == null && data.library?.r26 == null) { "stale-source-journal" }
                    require(data.library?.transition == null) { "stale-source-journal" }
                }
            }
            db.state().write(row)
        } }
    }
    private fun encode(data: PreviewData): PreviewRow {
        fun ids(values: Collection<com.omer.belestiviclone.shared.catalog.OpaqueId>) = PreviewCodec.pack(values.map(PreviewCodec::id))
        return PreviewRow(key = key, favorites = ids(data.favorites), hidden = ids(data.hidden), history = ids(data.history), categories = PreviewCodec.pack(data.categories), hiddenCategories = PreviewCodec.pack(data.hiddenCategories.toList()), preferences = PreviewCodec.pack(data.preferences.flatMap { listOf(it.key, it.value) }), sources = LibraryCodec.encode(data))
    }
    /** Full expected-row equality is the revision token, including user columns and source journal. */
    suspend fun compareAndSet(expected: PreviewRow, next: PreviewRow, afterWrite: suspend () -> Unit = {}): Boolean =
        db.useWriterConnection { connection -> connection.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
            require(listOf(expected, next).none { PreviewCodec.unpack(it.sources).firstOrNull() in setOf(R38BProfileTransition.envelope, R38CProfileTransition.envelope) }) { "r38b-use-exact-cas" }
            require(expected.key == key && next.key == key) { "row-key" }
            if (db.state().read(key) != expected) false else {
                db.state().write(next)
                afterWrite() // Native debug fault test throws here to verify SQLite transaction rollback.
                true
            }
        } }
    private suspend fun transition(expected: PreviewRow, base: PreviewData, request: PilotRequest): PreviewData {
        require(pilotStoreAllowed(key, databaseName, request.context.session)) { "disabled-profile" }
        val before = decode(expected, base)
        fun allocate() = java.util.UUID.randomUUID().let { OpaqueId(it.mostSignificantBits, it.leastSignificantBits) }
        val next = if (request.legacyExit) VerifiedDefinitionTransition.legacyExit(before, request.context, ApprovedR21.manifest)
            else if (request.rollback) VerifiedDefinitionTransition.rollback(before, request.context, ApprovedR21.manifest)
            else VerifiedDefinitionTransition.apply(before, request.context, ApprovedR21.manifest, ::allocate)
        val row = encode(next)
        if (next != before) commitTransition(expected, row) { old, new -> compareAndSet(old, new) }
        fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        val unchanged = expected.copy(sources = row.sources) == row
        android.util.Log.i("BelesTransition", "session=${request.context.session} action=${if (request.legacyExit) "legacy-exit" else if (request.rollback) "rollback" else "apply"} result=${if (next == before) "no-op" else "committed"} relationsEqual=$unchanged beforeSources=${hash(expected.sources)} afterSources=${hash(row.sources)}")
        next.library?.transition?.targets?.forEach { target ->
            val actual = next.channels.single { it.id == target.id }.definition!!
            android.util.Log.i("BelesTransition", "candidate=${target.entry.candidate} channel=${PreviewCodec.id(target.id)} definitionSha256=${hash(LibraryCodec.definition(actual))}")
        }
        return next
    }
}

/** Length-prefixed complete-row digest, never raw user data. */
fun r23Revision(row: PreviewRow): String = MessageDigest.getInstance("SHA-256").digest(PreviewCodec.pack(listOf(
    row.key.toString(), row.favorites, row.hidden, row.history, row.categories, row.hiddenCategories, row.preferences, row.sources
)).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
