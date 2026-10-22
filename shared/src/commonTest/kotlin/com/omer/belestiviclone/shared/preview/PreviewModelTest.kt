package com.omer.belestiviclone.shared.preview

import com.omer.belestiviclone.shared.catalog.OpaqueId
import kotlin.test.*

class PreviewModelTest {
    @Test fun heldKeyRepeatAtNewFocusTargetDoesNotBecomeAnotherPress() {
        val opener = OkGesture(); opener.down(0, true)
        assertEquals(OkAction.LONG, opener.down(400, true, platformLongPress = true, repeat = true))
        for (hasLong in listOf(false, true)) {
            val nextTarget = OkGesture()
            assertEquals(OkAction.NONE, nextTarget.down(450, hasLong, platformLongPress = true, repeat = true))
            assertEquals(OkAction.NONE, nextTarget.up(700, hasLong))
            nextTarget.down(800, hasLong)
            assertEquals(OkAction.SHORT, nextTarget.up(850, hasLong))
        }
    }
    @Test fun platformLongPressBeforeFallbackFiresOnceWithoutReleaseSelection() {
        val gate = OkGesture(); gate.down(0, true)
        assertEquals(OkAction.LONG, gate.down(400, true, platformLongPress = true))
        assertEquals(OkAction.NONE, gate.down(450, true, platformLongPress = true))
        assertEquals(OkAction.NONE, gate.up(460, true))
    }
    @Test fun platformLongPressWithoutLongHandlerStillHasOnlyOneShortAction() {
        val gate = OkGesture(); gate.down(0, false)
        assertEquals(OkAction.NONE, gate.down(400, false, platformLongPress = true))
        assertEquals(OkAction.SHORT, gate.up(410, false))
        assertEquals(OkAction.NONE, gate.up(420, false))
    }
    @Test fun onlyOfficialFixtureCanReachNetwork() {
        val sample = PreviewFixture.channels.first()
        assertEquals(PreviewFixture.videoUri, sampleVideoUri(sample))
        assertNull(sampleVideoUri(null))
        assertNull(sampleVideoUri(sample.copy(video = false)))
        assertNull(sampleVideoUri(PreviewFixture.channels.last().copy(video = true)))
    }
    @Test fun longRepeatFiresOnceAndReleaseDoesNotPlay() {
        val gate = OkGesture()
        assertEquals(OkAction.NONE, gate.down(0, true))
        assertEquals(OkAction.NONE, gate.down(100, true))
        assertEquals(OkAction.LONG, gate.down(510, true))
        assertEquals(OkAction.NONE, gate.down(700, true))
        assertEquals(OkAction.NONE, gate.up(900, true))
    }
    @Test fun longWithoutRepeatStillOpensMenu() {
        val gate = OkGesture(); gate.down(0, true)
        assertEquals(OkAction.LONG, gate.up(700, true))
        assertEquals(OkAction.NONE, gate.up(710, true))
    }
    @Test fun focusLossDiscardsReleaseAndShortFiresOnce() {
        val gate = OkGesture(); gate.down(0, true); gate.reset()
        assertEquals(OkAction.NONE, gate.up(100, true))
        gate.down(200, true); assertEquals(OkAction.SHORT, gate.up(250, true))
        assertEquals(OkAction.NONE, gate.up(260, true))
    }
    @Test fun fixtureIdsAreDistinctAndStable() {
        assertEquals(12, PreviewFixture.channels.map { it.id }.toSet().size)
        assertEquals(OpaqueId(0x50524556, 1), PreviewFixture.channels.first().id)
        assertEquals(1, PreviewFixture.channels.count { it.video })
    }
    @Test fun codecKeepsExactLabelsAndEmptyValues() {
        val labels = listOf("", "Haber", " haber ", "ç:|\n😀", "0:")
        assertEquals(labels, PreviewCodec.unpack(PreviewCodec.pack(labels)))
    }
    @Test fun opaqueCodecKeepsAllSignedBits() {
        listOf(OpaqueId(Long.MIN_VALUE, Long.MAX_VALUE), OpaqueId(-1, -1), OpaqueId(0, 0)).forEach { assertEquals(it, PreviewCodec.id(PreviewCodec.id(it))) }
    }
    @Test fun malformedCodecDoesNotReturnEmptyData() {
        listOf("1", "-1:x", "8:x", "a:x").forEach { assertFails { PreviewCodec.unpack(it) } }
    }
    @Test fun searchStaysInsideCategoryAndClearRestoresIt() {
        val data = PreviewData()
        assertTrue(data.filtered("Haber", "TRT").isEmpty())
        assertEquals(2, data.filtered("Haber", "").size)
        assertEquals(listOf("NTV"), data.filtered("Haber", "nt").map { it.name })
    }
    @Test fun movePreservesOtherRelativeOrder() {
        val data = PreviewData(); val id = data.favorites[2]
        assertEquals(listOf(id) + (data.favorites - id), data.move(id, 0).favorites)
        assertEquals((data.favorites - id) + id, data.move(id, 99).favorites)
    }
    @Test fun moveDraftDoesNotMutateOriginal() {
        val data = PreviewData(); val before = data.favorites.toList()
        data.move(before.last(), 0)
        assertEquals(before, data.favorites)
    }
    @Test fun hidingPreservesFavoriteMembershipAndOrder() {
        val data = PreviewData(); val hidden = data.copy(hidden = setOf(data.favorites.first()))
        assertEquals(data.favorites, hidden.favorites)
        assertEquals(4, hidden.filtered("Favoriler", "").size)
        assertEquals(data.filtered("Favoriler", ""), hidden.copy(hidden = emptySet()).filtered("Favoriler", ""))
    }
    @Test fun historyOffDoesNotDeleteFixture() {
        val data = PreviewData(); val off = data.withPref("history", "Kapalı")
        assertEquals(data.history, off.history)
        assertEquals(data.history, off.withPref("history", "Açık").history)
    }
    @Test fun hiddenCategoryKeepsItsPositionAndChannelMembership() {
        val data = PreviewData(); val hidden = data.copy(hiddenCategories = setOf("Haber"))
        assertEquals(data.categories, hidden.categories)
        assertEquals(data.filtered("Tümü", ""), hidden.filtered("Tümü", ""))
    }
    @Test fun startupResolverUsesLastThenFavoriteThenAll() {
        val data = PreviewData(); val last = PreviewFixture.channels.last().id
        assertEquals(last, data.startup(last))
        assertEquals(data.favorites.first(), data.startup(null))
        assertEquals(data.favorites.first(), data.withPref("startup", "Kapalı").startup(last))
        assertEquals(PreviewFixture.channels.first().id, data.copy(favorites = emptyList()).withPref("startup", "Kapalı").startup(last))
        assertNull(data.copy(favorites = emptyList(), hidden = PreviewFixture.channels.map { it.id }.toSet()).withPref("startup", "Kapalı").startup(last))
    }
    @Test fun multiLowerAssignmentsSurviveLayoutButSelectionClamps() {
        val m = MultiSession(PreviewFixture.channels.first().id); m.layout(4)
        m.slots[3] = PreviewFixture.channels.last().id; m.selected = 3; m.layout(2)
        assertEquals(0, m.selected); m.layout(4)
        assertEquals(PreviewFixture.channels.last().id, m.slots[3])
    }
    @Test fun emptyMultiReturnUsesEntry() {
        val id = PreviewFixture.channels.first().id; val m = MultiSession(id)
        m.selected = 1; assertEquals(id, m.returnChannel())
        m.slots[1] = PreviewFixture.channels.last().id; assertEquals(PreviewFixture.channels.last().id, m.returnChannel())
    }
    @Test fun startupFavoritesPrioritizesFavoritesEvenIfLastChannelProvided() {
        val data = PreviewData().withPref("startupFavorites", "Açık")
        val last = PreviewFixture.channels.last().id
        assertEquals(data.favorites.first(), data.startup(last))
    }
    @Test fun defaultPreferencesContainV41Features() {
        val data = PreviewData()
        assertEquals("Beleş TiVi Klasik", data.pref("theme"))
        assertEquals("Kapalı", data.pref("sleepTimer"))
        assertEquals("Normal", data.pref("subtitleSize"))
        assertEquals("Beyaz", data.pref("subtitleColor"))
        assertEquals("%75", data.pref("subtitleBgOpacity"))
        assertEquals("Kapalı", data.pref("startupFavorites"))
        assertEquals("Kapalı", data.pref("backgroundAudio"))
        assertEquals("Açık", data.pref("autoPip"))
    }
}
