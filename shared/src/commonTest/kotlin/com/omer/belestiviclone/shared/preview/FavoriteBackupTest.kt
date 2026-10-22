package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlin.test.*

class FavoriteBackupTest {
    @Test fun roundtripMissingFavoriteAppendAndRepeatedImportPreserveExistingOrder() {
        val data = PreviewData(); val text = FavoriteBackup.encode(data)
        val archive = FavoriteBackup.decode(text)
        assertEquals(data.favorites, archive.favorites.map { it.id })
        val missing = data.copy(favorites = listOf(data.favorites[2], data.favorites[0]))
        val restored = FavoriteBackup.merge(missing, archive)
        assertEquals((missing.favorites + data.favorites).distinct(), restored.favorites)
        assertEquals(restored, FavoriteBackup.merge(restored, archive))
    }
    @Test fun malformedTypesMissingFieldsVersionsDuplicateKeysTrailingDataAndOversizeAreRejected() {
        val valid = FavoriteBackup.encode(PreviewData())
        listOf("{", "{}", valid + "x", valid.replace("\"schemaVersion\":1", "\"schemaVersion\":999"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""), valid.replace("\"app\":", "\"app\":\"duplicate\",\"app\":"),
            valid.replace("\"sources\":[]", "\"sources\":false"), " ".repeat(FavoriteBackup.maxBytes + 1)).forEach { text -> assertFails { FavoriteBackup.decode(text) } }
    }
    @Test fun unknownFieldsAreBoundedButIgnoredAndOtherProfileIsRejected() {
        val data = PreviewData(); val text = FavoriteBackup.encode(data).replace("\"schemaVersion\":1", "\"future\":{\"x\":[true,null]},\"schemaVersion\":1")
        assertEquals(data.favorites, FavoriteBackup.decode(text).favorites.map { it.id })
        assertFails { FavoriteBackup.merge(data.copy(profileId = OpaqueId(1, 7)), FavoriteBackup.decode(text)) }
    }
    @Test fun exportOmitsPlaybackSecretsAndUnknownIdsStayUnavailableWithoutNameMatching() {
        val data = PreviewData(); val archive = FavoriteArchive(data.profileId, listOf(FavoriteReference(OpaqueId(9, 9), "TRT 1", listOf(OpaqueId(5, 5)))))
        val merged = FavoriteBackup.merge(data, archive)
        assertEquals(data.favorites + OpaqueId(9, 9), merged.favorites)
        assertFalse(merged.channels.last().available); assertNull(playbackUri(merged.channels.last()))
        val text = FavoriteBackup.encode(merged)
        assertFalse(text.contains("http")); assertFalse(text.contains("password"))
        assertEquals(listOf(OpaqueId(5, 5)), FavoriteBackup.decode(text).favorites.last().sources)
    }
}
