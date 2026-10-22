package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import kotlin.test.*

class SourceLibraryTest {
    private var serial = 0L
    private val ops = SourceOperations { OpaqueId(900, ++serial) }
    private val base = PreviewData(channels = emptyList(), favorites = emptyList(), history = emptyList(), categories = emptyList(), profileId = PublicM3u.profile)
    private fun m3u(name: String = "One", uri: String = "https://media.test/one", group: String = "News") = "#EXTM3U\n#EXTINF:-1 group-title=\"$group\",$name\n$uri\n"
    @Test fun exactDuplicateRegistrationFailsWithoutRenamingAndSameNameDifferentUrlWorks() {
        val first = ops.save(base, "Same", "https://list.test/a", m3u())
        assertFailsWith<SourceProblem> { ops.save(first, "Renamed", "https://LIST.test/a", m3u()) }
        val second = ops.save(first, "Same", "https://list.test/b", m3u(uri = "https://media.test/two"))
        assertEquals(listOf("Same", "Same"), second.library!!.saved.map { it.name })
        assertNotEquals(second.library!!.saved[0].id, second.library!!.saved[1].id)
    }
    @Test fun editRetainsSourceChannelAndFavoriteIdentityAndConflictingEditFails() {
        val first = ops.save(base, "One", "https://list.test/a", m3u())
        val id = first.library!!.active; val channel = first.filtered("Tümü", "").single().id
        val favored = first.copy(favorites = listOf(channel))
        val renamed = ops.save(favored, "New", "https://list.test/a", m3u("New channel"), id)
        assertEquals(id, renamed.library!!.active); assertEquals(channel, renamed.filtered("Tümü", "").single().id)
        assertEquals(listOf(channel), renamed.favorites); assertEquals(1, renamed.library!!.saved.size)
        val second = ops.save(renamed, "Other", "https://list.test/b", m3u())
        assertFailsWith<SourceProblem> { ops.save(second, "Conflict", "https://list.test/b", m3u(), id) }
    }
    @Test fun supplementDeduplicatesExactPlaybackAndMergesExactGroupsWithMainMetadataPriority() {
        val main = ops.save(base, "Main", "https://list.test/a", m3u("Main name", group = "News"))
        val id = main.filtered("Tümü", "").single().id
        val extra = ops.save(main, "Extra", "https://list.test/extra", m3u("Extra name", group = "news"), extra = true)
        assertEquals(1, extra.filtered("Tümü", "").size)
        assertEquals(listOf("News", "news"), extra.filtered("Tümü", "").single().groups)
        assertEquals("Main name", extra.filtered("Tümü", "").single().name)
        assertEquals(id, extra.filtered("Tümü", "").single().id)
        val removed = ops.removeExtra(extra)
        assertEquals(listOf("News"), removed.filtered("Tümü", "").single().groups)
        val again = ops.save(removed, "New extra", "https://list.test/extra2", m3u(group = "Sport"), extra = true)
        assertEquals(1, again.library!!.records.count { it.parent == again.library!!.active })
    }
    @Test fun extraOnlyFavoriteBecomesUnavailableAndReturnsWithSameIdentity() {
        val main = ops.save(base, "Main", "https://list.test/a", m3u())
        val extra = ops.save(main, "Extra", "https://list.test/extra", m3u("Two", "https://media.test/two"), extra = true)
        val id = extra.filtered("Tümü", "").first { it.name == "Two" }.id
        val removed = ops.removeExtra(extra.copy(favorites = listOf(id), history = listOf(id)))
        assertEquals(listOf(id), removed.favorites); assertEquals(listOf(id), removed.history)
        assertNull(playbackUri(removed.filtered("Favoriler", "").single()))
        val again = ops.save(removed, "Extra", "https://list.test/extra", m3u("Two", "https://media.test/two"), extra = true)
        assertEquals(id, again.filtered("Favoriler", "").single().id); assertNotNull(playbackUri(again.filtered("Favoriler", "").single()))
    }
    @Test fun activeDeleteIsBlockedAndOtherDeleteOnlyRemovesItsOwnExtra() {
        val a = ops.save(base, "A", "https://list.test/a", m3u())
        val extraA = ops.save(a, "Extra A", "https://list.test/extra", m3u(), extra = true)
        assertFailsWith<SourceProblem> { ops.delete(extraA, extraA.library!!.active) }
        val b = ops.save(extraA, "B", "https://list.test/b", m3u())
        val deleted = ops.delete(b, a.library!!.active)
        assertEquals("B", deleted.library!!.main.name); assertEquals(1, deleted.library!!.saved.size)
        assertTrue(deleted.library!!.records.filter { it.id == a.library!!.active || it.parent == a.library!!.active }.all { it.deleted })
    }
    @Test fun fileCopyAndIdentitySurviveEnvelopeReopenWithoutExternalDocument() {
        val file = ops.save(base, "selected.m3u", "", m3u(), file = true)
        // Room restores its category/preference columns before decoding the source column.
        val reopened = LibraryCodec.decode(LibraryCodec.encode(file), file.copy(channels = emptyList(), library = null))
        assertEquals(file, reopened)
        assertEquals(file.filtered("Tümü", ""), ops.activate(reopened, reopened.library!!.active).filtered("Tümü", ""))
        assertEquals(SourceKind.FILE, reopened.library!!.main.kind)
    }
    @Test fun badContentUnsupportedDefinitionsAndEmptyListsFailWhilePartialListReportsSkipped() {
        listOf("", "{}", "#EXTM3U", "garbage", "#EXTM3U\n#EXTINF:-1,Bad\nfile:///bad", "#EXTM3U\n#EXTINF:-1,Bad\n#KODIPROP:unknown=value\nhttps://media.test/a").forEach {
            assertFailsWith<SourceProblem> { ops.save(base, "Bad", "https://list.test/a", it) }
        }
        val partial = ops.save(base, "Partial", "https://list.test/a", m3u() + "#EXTINF:-1,Bad\nfile:///bad")
        assertEquals(1, partial.library!!.main.skippedCount); assertEquals(1, partial.filtered("Tümü", "").size)
    }
    @Test fun legacySourcesRemainReadableAndNewEnvelopeKeepsUnknownFavoriteMetadata() {
        assertEquals(listOf("Old"), LibraryCodec.decode(PreviewCodec.pack(listOf("Old")), base).sources)
        val unknown = base.copy(channels = listOf(PreviewChannel(OpaqueId(1, 8), "Unknown", "None", "", available = false)))
        assertEquals(unknown, LibraryCodec.decode(LibraryCodec.encode(unknown), base))
    }
}
