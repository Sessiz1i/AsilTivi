package com.omer.belestiviclone

import com.omer.belestiviclone.shared.catalog.*
import com.omer.belestiviclone.shared.preview.*
import kotlin.test.*

class R38FixtureModelTest {
    private val old = PlaybackDefinition(Address.fromForm("https://old.invalid/a.m3u8"))
    private val new = PlaybackDefinition(Address.fromForm("https://new.invalid/a.m3u8?q=unaltered"))
    private val row = ChannelContribution(PublicM3u.source, 38, old, CategoryLabel.fromRaw("Müzik"), "Ad", "logo", "metadata")
    @Test fun sourceMetadataAndExactDefinitionSurviveWithExplicitSyntheticId() {
        val id = OpaqueId(55, 77)
        val c = R38FixtureModel.channel(listOf(row), 38, "metadata", old, new, id)
        assertEquals(id, c.id); assertEquals(new, c.definition)
        assertEquals("Ad", c.name); assertEquals("Müzik", c.category)
        assertEquals("logo", c.logo); assertEquals("metadata", c.providerId)
        assertEquals(listOf(c), LibraryCodec.channels(LibraryCodec.channels(listOf(c))))
    }
    @Test fun ambiguousRowCollisionAndDefinitionDriftFailClosed() {
        fun fails(rows: List<ChannelContribution>, index: Long = 38, meta: String = "metadata",
            expected: PlaybackDefinition = old, target: PlaybackDefinition = new) {
            assertFailsWith<IllegalArgumentException> { R38FixtureModel.channel(rows, index, meta, expected, target, OpaqueId(1, 1)) }
        }
        fails(listOf(row), 39); fails(listOf(row), meta = "other")
        fails(listOf(row, row.copy(rowOrder = 39)))
        fails(listOf(row, row.copy(rowOrder = 39, definition = new)))
        fails(listOf(row), expected = new)
        fails(listOf(row), target = new.copy(drm = DrmDefinition("widevine")))
        fails(listOf(row), target = new.copy(unknownRequiredDirectives = listOf("required")))
        fails(listOf(row.copy(sourceId = SourceId(OpaqueId(9, 9)))))
    }
}
