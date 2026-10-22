package com.omer.belestiviclone

import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.catalog.OpaqueId
import com.omer.belestiviclone.sharedui.PreviewState
import kotlinx.coroutines.*
import kotlin.test.*

class R30FixtureTest {
    private val entries = listOf(R28ProfileTransition.entry, ApprovedR21.manifest.entries.single { it.candidate == "R20-C003" })
    private val seed = entries.mapIndexed { i, e -> PreviewChannel(OpaqueId(9, i.toLong()), e.candidate, "Haber", "ikon_tv", definition = e.old) }
    @Test fun fixturesUseDisjointNewIdsExactDefinitionsAndLosslessCodec() {
        val fixtures = R30Entry.sessions.map { R30FixtureModel.create(it, seed) }
        assertEquals(2, fixtures.flatMap { it.channels }.map { it.id }.distinct().size)
        fixtures.forEachIndexed { index, data ->
            assertEquals(entries[index].new, data.channels.single().definition)
            assertTrue(data.channels.none { it.id in seed.map { ch -> ch.id } })
            assertEquals(data, LibraryCodec.decode(LibraryCodec.encode(data), data.copy(channels = emptyList(), sources = emptyList())))
            assertEquals("Kapalı", data.pref("history")); assertTrue(data.history.isEmpty()); assertNull(data.library)
        }
    }
    @Test fun startupHistoryAndManualPauseRemainBoundToSyntheticStore() = runBlocking {
        for (session in R30Entry.sessions) {
            var saved = R30FixtureModel.create(session, seed)
            var writes = 0
            val store = object : PreviewStore {
                override suspend fun load() = saved
                override suspend fun save(data: PreviewData) { saved = data; writes++ }
            }
            val state = PreviewState(store, this, store); state.load()
            assertTrue(state.loaded); assertEquals("live", state.page)
            assertEquals(saved.channels.single().id, state.selected)
            state.paused = true; state.played(state.selected); yield()
            assertTrue(state.paused); assertTrue(saved.history.isEmpty()); assertEquals(0, writes)
            val reopened = PreviewState(store, this, store); reopened.load()
            assertEquals(state.selected, reopened.selected); assertFalse(reopened.paused)
            assertTrue(saved.history.isEmpty()) // Manual pause is session state, not a persisted preference.
        }
    }
    @Test fun fixtureCannotBeRequestedForNormalOrOldSession() {
        for (session in listOf("belestivi_preview_v1", "toplu-r28-ekoturk-20261015-r1", "toplu-r30-render-c-20261015"))
            assertFailsWith<IllegalArgumentException> { R30FixtureModel.create(session, seed) }
    }
}
