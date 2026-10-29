package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class ReferenceNavigationTest {
    private class Store : PreviewStore {
        var value = PreviewData()
        var writes = 0
        var fail = false
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) { if (fail) error("fixture failure"); value = data; writes++ }
    }
    @Test fun unmountedFormOpenerRetainsItsFocusIdentityAfterRemount() {
        val registry = FocusRegistry<Any>()
        val close = registry.requester("close") { Any() }
        val m3u = registry.requester("settings:M3U") { Any() }
        val xtream = registry.requester("settings:Xtream") { Any() }
        val remounted = registry.requester("settings:M3U") { error("must keep opener identity") }
        assertSame(m3u, remounted)
        assertSame(m3u, registry.returnTarget(m3u, listOf(close, remounted, xtream)))
        assertSame(xtream, registry.returnTarget(xtream, listOf(close, m3u, xtream)))
        assertSame(close, registry.returnTarget(Any(), listOf(close)))
        assertNull(registry.returnTarget(m3u, emptyList()))
    }
    @Test fun formBackAndBreadcrumbDropDraftWithoutWritingOrChangingParent() = runBlocking {
        for (back in listOf(true, false)) {
            val store = Store(); val s = PreviewState(store, this); s.load(); s.settings(); s.section = 0
            val before = s.data
            for (kind in listOf("m3u", "xtream", "supplement")) {
                s.sourceForm(kind)
                assertEquals("", s.formName); assertEquals("", s.formAddress)
                assertEquals("", s.formUser); assertEquals("", s.formPassword)
                s.formName = "discard fixture"; s.formAddress = "https://fixture.invalid/list"; s.formUser = "fixture"; s.formPassword = "fixture secret"
                if (back) s.back() else s.dismiss()
                assertNull(s.dialog); assertEquals("settings", s.page); assertEquals(0, s.section)
                assertEquals(before, s.data); assertEquals(0, store.writes)
                assertEquals("", s.formName); assertEquals("", s.formAddress); assertEquals("", s.formUser); assertEquals("", s.formPassword)
            }
        }
    }
    @Test fun emptyFormCannotSaveItsDisplayedPlaceholderAsSourceData() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); val before = s.data
        s.sourceForm("m3u"); s.saveSource(); yield()
        assertEquals(0, store.writes); assertEquals(before, s.data)
        // Faz P5 (C10): Boş URL girildiğinde "Kaynağa bağlanılamadı" hata modalı açılır, kaynak formu korunur
        assertEquals("sourceError", s.dialog)
        assertEquals("m3u", s.lastSourceDialog)
    }
    @Test fun categoryFooterRoutesPreserveCatalogMembershipAndPreferences() = runBlocking {
        for (route in listOf("multi", "settings")) {
            val store = Store(); val s = PreviewState(store, this); s.load(); s.page = "live"; s.categoryMenu = true
            val before = s.data; val category = s.category; val selected = s.selected
            s.categoryAction(route)
            assertFalse(s.categoryMenu); assertEquals(before, s.data); assertEquals(category, s.category); assertEquals(selected, s.selected); assertEquals(0, store.writes)
            if (route == "multi") { assertEquals("warning", s.dialog); s.back(); assertEquals("live", s.page) }
            else { assertEquals("settings", s.page); s.back(); assertEquals("live", s.page) }
        }
    }
    @Test fun categoryEditsKeepExactNamesAndOnlyCommitTheirOwnFields() = runBlocking {
        val store = Store().also { it.value = it.value.copy(categories = listOf("SPOR", "Spor", "Haber")) }
        val s = PreviewState(store, this); s.load(); val before = s.data
        s.moveCategory("SPOR", -1); s.moveCategory("Haber", 1); s.moveCategory("missing", 1)
        assertEquals(0, store.writes)
        s.toggleCategory("SPOR"); yield()
        assertEquals(before.copy(hiddenCategories = setOf("SPOR")), s.data)
        s.moveCategory("Spor", -1); yield()
        assertEquals(listOf("Spor", "SPOR", "Haber"), s.data.categories)
        assertEquals(setOf("SPOR"), s.data.hiddenCategories)
        s.showCategories(false); yield(); assertEquals(setOf("Spor", "SPOR", "Haber"), s.data.hiddenCategories)
        s.showCategories(true); yield(); assertEquals(emptySet(), s.data.hiddenCategories)
        assertEquals(before.copy(categories = listOf("Spor", "SPOR", "Haber")), s.data)
    }
    @Test fun failedCategoryCommitAndSubpageBackPreserveFixtureData() = runBlocking {
        val store = Store(); val s = PreviewState(store, this); s.load(); s.settings(); s.section = 0
        val before = s.data; store.fail = true
        s.dialog = "categories"; s.moveCategory(before.categories[1], -1); yield()
        assertEquals(before, s.data); assertEquals(before, store.value); assertEquals(0, store.writes)
        s.back(); assertNull(s.dialog); assertEquals("settings", s.page); assertEquals(0, s.section); assertEquals(before, s.data)
    }
}
