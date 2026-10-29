package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.*
import java.io.File
import kotlin.test.*

/** Source management must work on the packaged default list without any transition layer. */
class SourceOperationsTest {
    private var serial = 0L
    private val ops = SourceOperations { OpaqueId(700, ++serial) }
    private val text = listOf(File("../androidApp/src/main/assets/TiviListe.m3u"), File("androidApp/src/main/assets/TiviListe.m3u")).first { it.isFile }.readText()
    private fun publicBase(): PreviewData {
        val parsed = PublicM3u.parse(text)
        val n = parsed.rows.size
        val channels = PublicM3u.catalog(parsed, IdentitySupply(List(n) { ChannelId(OpaqueId(1, it.toLong())) },
            List(n) { MembershipId(OpaqueId(2, it.toLong())) }, List(n) { CategoryId(OpaqueId(3, it.toLong())) }))
        return PreviewData(favorites = emptyList(), history = emptyList(), categories = channels.flatMap { it.groups }.distinct(),
            channels = channels, profileId = PublicM3u.profile)
    }
    private fun m3u(name: String = "One", uri: String = "https://media.test/one") = "#EXTM3U\n#EXTINF:-1 group-title=\"News\",$name\n$uri\n"

    @Test fun packagedListCarriesUpdatedDefinitionsAndDropsTrtAvaz() {
        val channels = publicBase().channels
        assertTrue(channels.any { it.name == "TRT Avaz" })
        listOf("TV8", "TRT Haber", "24 TV", "TRT Türk").forEach { name ->
            assertFalse("uzunmuhalefet" in channels.single { it.name == name }.definition!!.address.raw, name)
        }
        assertFalse("workers.dev" in channels.single { it.name.startsWith("Ekot") }.definition!!.address.raw)
    }

    @Test fun addUrlSourceSupplementDeleteAndRestoreDefaultAllSucceed() {
        val base = publicBase()
        val favorite = base.channels.first { it.name == "TV8" }.id
        val start = ops.default(base.copy(favorites = listOf(favorite)), text)
        assertEquals(PublicM3u.source, start.library!!.active)
        assertEquals(base.channels.size, start.channels.count { it.available })
        assertEquals(favorite, start.channels.first { it.name == "TV8" }.id)

        val url = ops.save(start, "Liste", "https://list.test/a.m3u", m3u())
        assertEquals("Liste", url.library!!.main.name)
        assertEquals(1, url.channels.count { it.available })
        val withExtra = ops.save(url, "Ek", "https://list.test/extra.m3u", m3u("Two", "https://media.test/two"), extra = true)
        assertEquals(2, withExtra.channels.count { it.available })
        val noExtra = ops.removeExtra(withExtra)
        assertEquals(1, noExtra.channels.count { it.available })

        val restored = ops.default(noExtra, text)
        assertEquals(PublicM3u.source, restored.library!!.active)
        assertEquals(base.channels.size, restored.channels.count { it.available })
        assertEquals(listOf(favorite), restored.favorites)
        assertEquals(favorite, restored.channels.first { it.name == "TV8" && it.available }.id)

        val deleted = ops.delete(restored, url.library!!.active)
        assertTrue(deleted.library!!.saved.isEmpty())
        val file = ops.save(deleted, "dosya.m3u", "", m3u("File"), file = true)
        assertEquals(SourceKind.FILE, file.library!!.main.kind)
    }

    @Test fun legacyJournalEnvelopeDecodesToInnerLibraryAndStaysEditable() {
        val current = ops.default(publicBase(), text)
        val inner = LibraryCodec.encode(current)
        fun wrap(tag: String, value: String) = PreviewCodec.pack(listOf(tag, value, "journal-$tag"))
        val legacy = wrap("BT-SOURCES-7", wrap("BT-SOURCES-4", wrap("BT-SOURCES-3", wrap("BT-SOURCES-2", inner))))
        val decoded = LibraryCodec.decode(legacy, current.copy(channels = emptyList(), library = null))
        assertEquals(current, decoded)
        val next = ops.save(decoded, "Liste", "https://list.test/a.m3u", m3u())
        assertEquals("BT-SOURCES-1", PreviewCodec.unpack(LibraryCodec.encode(next)).first())
        assertEquals(PublicM3u.source, ops.default(next, text).library!!.active)
    }

    @Test fun unknownLegacyEnvelopeFailsSoStoreCanFallBack() {
        assertFails { LibraryCodec.decode(PreviewCodec.pack(listOf("BT-SOURCES-9", "x", "y")), PreviewData()) }
    }
}
