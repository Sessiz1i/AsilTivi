package com.omer.belestiviclone.shared.preview

/** The only dependency is an existing-row reader. No store, initializer, writer or transition API. */
object R31ReadOnlySnapshot {
    const val action = "com.omer.belestiviclone.R31_SNAPSHOT"
    const val database = "belestivi_preview_v1.db"
    fun admit(debug: Boolean, action: String?, command: String?, database: String?, key: Int, exists: Boolean) {
        require(debug && action == this.action && command == "snapshot" && database == this.database && key == 2) { "r31-disabled-snapshot" }
        require(exists) { "r31-missing-database" }
    }
    suspend fun read(debug: Boolean, action: String?, command: String?, database: String?, key: Int, exists: Boolean,
        readRow: suspend (Int) -> PreviewRow?): Pair<PreviewRow, PreviewData> {
        admit(debug, action, command, database, key, exists)
        // Product startup also reads key1. Refuse to let it initialize a missing row.
        requireNotNull(readRow(1)) { "r31-missing-startup-row" }
        val row = requireNotNull(readRow(key)) { "r31-missing-public-row" }
        require(row.key == 2) { "r31-row-key" }
        return row to decode(row)
    }
    fun decode(row: PreviewRow): PreviewData {
        val prefs = PreviewCodec.unpack(row.preferences)
        require(prefs.size % 2 == 0 && prefs.chunked(2).map { it[0] }.distinct().size == prefs.size / 2)
        val data = LibraryCodec.decode(row.sources, PreviewData(channels = emptyList(), profileId = PublicM3u.profile,
            favorites = PreviewCodec.unpack(row.favorites).map(PreviewCodec::id),
            hidden = PreviewCodec.unpack(row.hidden).map(PreviewCodec::id).toSet(),
            history = PreviewCodec.unpack(row.history).map(PreviewCodec::id),
            categories = PreviewCodec.unpack(row.categories), hiddenCategories = PreviewCodec.unpack(row.hiddenCategories).toSet(),
            preferences = prefs.chunked(2).associate { it[0] to it[1] }.also { require(it.keys.containsAll(PreviewData().preferences.keys)) }))
        fun ids(values: Collection<com.omer.belestiviclone.shared.catalog.OpaqueId>) = PreviewCodec.pack(values.map(PreviewCodec::id))
        val encoded = PreviewRow(row.key, ids(data.favorites), ids(data.hidden), ids(data.history), PreviewCodec.pack(data.categories),
            PreviewCodec.pack(data.hiddenCategories.toList()), PreviewCodec.pack(data.preferences.flatMap { listOf(it.key, it.value) }), LibraryCodec.encode(data))
        require(encoded == row) { "r31-lossy-decode" }
        return data
    }
}
