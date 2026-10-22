package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.catalog.*
import kotlinx.coroutines.*
import kotlin.test.*

class PublicProfileTest {
    private class Store(var value: PreviewData, var fail: Boolean = false): PreviewStore {
        var writes = 0
        override suspend fun load(): PreviewData { if (fail) error("unavailable"); return value }
        override suspend fun save(data: PreviewData) { if (fail) error("unavailable"); value = data; writes++ }
    }
    private val channel = PreviewChannel(OpaqueId(411, 9), "TRT 1", "Ulusal", "https://x.test/logo.png", definition = PlaybackDefinition(Address.fromForm("https://x.test/real.m3u8")))
    private fun publicData() = PreviewData(favorites = emptyList(), history = emptyList(), channels = listOf(channel), categories = listOf("Ulusal"), profileId = PublicM3u.profile)
    @Test fun newPublicProfileStartsAllAndDemoSixFavoritesAndPreferencesRemainExact() = runBlocking {
        val before = PreviewData().copy(favorites = PreviewFixture.channels.take(6).map { it.id }).withPref("size", "Küçük")
        val demo = Store(before); val real = Store(publicData())
        val s = PreviewState(demo, this, real); s.load()
        assertTrue(s.isPublic); assertEquals("Tümü", s.category); assertEquals(channel, s.channel); assertTrue(s.data.favorites.isEmpty())
        s.switchProfile(false); assertEquals(before, s.data); assertEquals(before, demo.value); assertEquals(0, demo.writes)
    }
    @Test fun favoriteWriteAffectsOnlyPublicKeyAndSurvivesReload() = runBlocking {
        val demo = Store(PreviewData()); val real = Store(publicData()); val before = demo.value
        val s = PreviewState(demo, this, real); s.load(); s.commit(s.data.toggleFavorite(channel.id)); yield()
        assertEquals(listOf(channel.id), real.value.favorites); assertEquals(0, demo.writes); assertEquals(before, demo.value)
        val reopened = PreviewState(demo, this, real); reopened.load(); assertEquals(listOf(channel.id), reopened.data.favorites)
    }
    @Test fun failedPublicSavePreservesLastSuccessfulCatalogAndDemo() = runBlocking {
        val demo = Store(PreviewData()); val real = Store(publicData()); val s = PreviewState(demo, this, real); s.load()
        val before = s.data; real.fail = true; s.commit(before.toggleFavorite(channel.id)); yield()
        assertEquals(before, s.data); assertEquals(before, real.value); assertEquals(0, demo.writes)
    }
    @Test fun unavailablePublicStoreDoesNotCreateFakePublicCatalog() = runBlocking {
        val demo = Store(PreviewData()); val real = Store(publicData(), true); val s = PreviewState(demo, this, real); s.load()
        assertFalse(s.isPublic); assertEquals(demo.value, s.data); assertTrue(s.notice.contains("Kamu listesi"))
    }
}
