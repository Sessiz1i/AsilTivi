package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import java.io.File
import java.util.Base64
import kotlin.test.*

class R38RealSeedTest {
    @Test fun allThirtyRealRawRowsBindWithoutReplacingSourceMetadataWithResearchIds() {
        val rows = PublicM3u.parse(File("src/main/assets/trlist.m3u").readText()).rows
        val cases = File("src/test/resources/r38-fixture-cases.private.tsv").readLines().filter(String::isNotEmpty)
        assertEquals(30, cases.size)
        val found = mutableSetOf<String>()
        for ((index, line) in cases.withIndex()) {
            val f = line.split('\t'); assertEquals(6, f.size)
            assertTrue(found.add(f[0])); assertTrue(f[0] in R38Entry.candidates)
            fun d(encoded: String) = PlaybackDefinition(Address.fromForm(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)))
            val original = rows.single { it.rowOrder == f[1].toLong() }
            val channel = R38FixtureModel.channel(rows, f[1].toLong(), f[3], d(f[4]), d(f[5]), OpaqueId(0x523338, index.toLong()))
            assertEquals(original.definition, d(f[4]))
            assertEquals(original.providerHint.orEmpty(), channel.providerId)
            assertNotEquals(f[2], channel.providerId) // Research ID stays external to raw source metadata.
            assertEquals(original.name, channel.name); assertEquals(original.logo, channel.logo)
            assertEquals(listOf(channel), LibraryCodec.channels(LibraryCodec.channels(listOf(channel))))
        }
        assertEquals(R38Entry.candidates, found)
    }
}
