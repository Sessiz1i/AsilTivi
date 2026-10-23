package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class F7MultiScreenStateTest {
    private class FakeStore : PreviewStore {
        var value = PreviewData()
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) {
            value = data
            writes++
        }
    }

    @Test
    fun allFourLayoutModesSetSlotCountsAndClampSelection() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)
        assertEquals("multi", state.page)
        assertEquals(MultiLayoutMode.TWO_SIDE_BY_SIDE, state.multi?.mode)
        assertEquals(2, state.multi?.count)

        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, state.multi?.mode)
        assertEquals(3, state.multi?.count)
        state.multi!!.selected = 2

        state.startMulti(MultiLayoutMode.ONE_BIG_THREE_SMALL)
        assertEquals(MultiLayoutMode.ONE_BIG_THREE_SMALL, state.multi?.mode)
        assertEquals(4, state.multi?.count)
        state.multi!!.selected = 3

        state.startMulti(MultiLayoutMode.TWO_SIDE_BY_SIDE)
        assertEquals(0, state.multi?.selected)

        state.startMulti(MultiLayoutMode.FOUR_EQUAL)
        assertEquals(MultiLayoutMode.FOUR_EQUAL, state.multi?.mode)
        assertEquals(4, state.multi?.count)
    }

    @Test
    fun multiChannelPickerFiltersByCategoryAndSearchQueryAndAssignsSlot() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()
        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)

        // Open picker for slot 1 (second screen)
        state.openMultiChannelPicker(1)
        assertEquals("assign", state.dialog)
        assertEquals(1, state.multi?.selected)

        // Filter by category "Haber"
        state.chooseMultiPickerCategory("Haber")
        assertEquals("Haber", state.multiPickerCategory)
        assertEquals("", state.multiPickerQuery)
        val haberChannels = state.multiPickerChannels
        assertEquals(2, haberChannels.size)
        assertTrue(haberChannels.all { "Haber" in it.groups })

        // Filter by search query "ntv"
        state.multiPickerQuery = "ntv"
        val searched = state.multiPickerChannels
        assertEquals(1, searched.size)
        assertEquals("NTV", searched.single().name)

        // Assign channel to slot 1
        val ntvId = searched.single().id
        state.assignMultiChannel(ntvId)
        yield()
        assertNull(state.dialog)
        assertEquals(ntvId, state.multi?.slots?.get(1))
        assertEquals(ntvId, state.lastMultiLayout?.slots?.get(1))
    }

    @Test
    fun saveLoadAndDeleteNamedMultiLayoutsPersistInStore() = runBlocking {
        val store = FakeStore()
        val state = PreviewState(store, this)
        state.load()

        val trt1 = PreviewFixture.channels.first { it.name == "TRT 1" }.id
        val ahaber = PreviewFixture.channels.first { it.name == "A Haber" }.id
        val ntv = PreviewFixture.channels.first { it.name == "NTV" }.id

        state.startMulti(MultiLayoutMode.ONE_BIG_TWO_SMALL)
        state.multi!!.slots[0] = trt1
        state.multi!!.slots[1] = ahaber
        state.multi!!.slots[2] = ntv

        state.openSaveMultiLayoutDialog()
        assertEquals("saveMultiLayout", state.dialog)
        state.formName = "Haber Paketi"
        state.saveCurrentMultiLayout()
        yield()

        assertNull(state.dialog)
        assertEquals(1, state.savedMultiLayouts.size)
        val saved = state.savedMultiLayouts.single()
        assertEquals("Haber Paketi", saved.name)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, saved.mode)
        assertEquals(listOf(trt1, ahaber, ntv, null), saved.slots)

        // Verify persisted in Room/Store preferences
        val reloadedState = PreviewState(store, this)
        reloadedState.load()
        assertEquals(1, reloadedState.savedMultiLayouts.size)
        assertNotNull(reloadedState.lastMultiLayout)

        // Restore saved layout from live page in one touch
        reloadedState.loadSavedMultiLayout(reloadedState.savedMultiLayouts.single())
        assertEquals("multi", reloadedState.page)
        assertEquals(MultiLayoutMode.ONE_BIG_TWO_SMALL, reloadedState.multi?.mode)
        assertEquals(ahaber, reloadedState.multi?.slots?.get(1))
        assertEquals(ntv, reloadedState.multi?.slots?.get(2))

        // Delete saved layout
        reloadedState.deleteSavedMultiLayout("Haber Paketi")
        yield()
        assertTrue(reloadedState.savedMultiLayouts.isEmpty())
    }
}
