package com.omer.belestiviclone.shared.preview

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class R31ReadOnlySnapshotTest {
    private val data = PreviewData(channels = emptyList(), favorites = emptyList(), history = emptyList(), profileId = PublicM3u.profile)
    private val row = PreviewRow(2, "", "", "", PreviewCodec.pack(data.categories), "",
        PreviewCodec.pack(data.preferences.flatMap { listOf(it.key, it.value) }), LibraryCodec.encode(data))
    @Test fun admissionRejectsBeforeReadingAnyRow() = runBlocking {
        var reads = 0
        val cases = listOf(
            listOf(false, R31ReadOnlySnapshot.action, "snapshot", R31ReadOnlySnapshot.database, 2, true),
            listOf(true, "wrong-action", "snapshot", R31ReadOnlySnapshot.database, 2, true),
            listOf(true, R31ReadOnlySnapshot.action, "apply", R31ReadOnlySnapshot.database, 2, true),
            listOf(true, R31ReadOnlySnapshot.action, "snapshot", "other.db", 2, true),
            listOf(true, R31ReadOnlySnapshot.action, "snapshot", R31ReadOnlySnapshot.database, 1, true),
            listOf(true, R31ReadOnlySnapshot.action, "snapshot", R31ReadOnlySnapshot.database, 2, false))
        for (c in cases) assertFailsWith<IllegalArgumentException> {
            R31ReadOnlySnapshot.read(c[0] as Boolean,c[1] as String,c[2] as String,c[3] as String,c[4] as Int,c[5] as Boolean) { reads++; row }
        }
        assertEquals(0, reads)
    }
    @Test fun missingRowsCannotTriggerInitializationAndExistingRowsStayExact() = runBlocking {
        val existing = mutableMapOf(1 to row.copy(key=1), 2 to row)
        suspend fun snapshot() = R31ReadOnlySnapshot.read(true,R31ReadOnlySnapshot.action,"snapshot",R31ReadOnlySnapshot.database,2,true) { existing[it] }
        val before = existing.toMap()
        assertEquals(row to data, snapshot()); assertEquals(before, existing)
        existing.remove(2); val missing = existing.toMap()
        assertFailsWith<IllegalArgumentException> { snapshot() }; assertEquals(missing, existing)
        existing.clear()
        assertFailsWith<IllegalArgumentException> { snapshot() }; assertTrue(existing.isEmpty())
    }
    @Test fun lossyOrMalformedUserFieldsFailWithoutChangingInput() {
        val duplicate = row.copy(hiddenCategories=PreviewCodec.pack(listOf("Spor","Spor")))
        val before = duplicate.copy()
        assertFailsWith<IllegalArgumentException> { R31ReadOnlySnapshot.decode(duplicate) }
        assertEquals(before, duplicate)
        assertFailsWith<IllegalArgumentException> { R31ReadOnlySnapshot.decode(row.copy(preferences=PreviewCodec.pack(listOf("odd")))) }
    }
}
