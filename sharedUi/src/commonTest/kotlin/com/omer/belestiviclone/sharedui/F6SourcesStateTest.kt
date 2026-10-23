package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.preview.*
import kotlinx.coroutines.*
import kotlin.test.*

class F6SourcesStateTest {
    private class FakeStore : PreviewStore {
        var value = PreviewData()
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(data: PreviewData) {
            value = data
            writes++
        }
    }

    private suspend fun settle(state: PreviewState) {
        withTimeout(5000) { while (state.busy) delay(1) }
        yield()
    }

    @Test
    fun xtreamConnectFlowSuccessAndClearErrorMessages() = runBlocking {
        val loader = object : SourceLoader {
            override suspend fun defaultText() = CountryListsCatalog.byCode("TR")!!.builtinM3u
            override suspend fun fetch(address: String): String = when {
                "username=wrong" in address -> """{"user_info":{"auth":0,"status":"Active"}}"""
                "action=get_live_categories" in address -> """[{"category_id":"1","category_name":"Ulusal"}]"""
                "action=get_live_streams" in address -> """[{"stream_id":77,"name":"Kanal D HD","epg_channel_id":"KanalD.tr","category_id":"1"}]"""
                "player_api.php" in address -> """{"user_info":{"username":"ali","auth":1,"status":"Active","allowed_output_formats":["m3u8"]}}"""
                else -> error("network down")
            }
        }
        val store = FakeStore()
        val state = PreviewState(store, this, sourceLoader = loader)
        state.load()

        // Missing credentials check
        state.sourceForm("xtream")
        state.formAddress = "http://panel.test:8080"
        state.formUser = ""
        state.formPassword = ""
        state.saveXtreamSource()
        assertTrue(state.notice.contains("Kullanıcı adı ve şifre"))

        // Invalid credentials check
        state.formUser = "wrong"
        state.formPassword = "123"
        state.saveXtreamSource()
        settle(state)
        assertTrue(state.notice.contains("Kimlik doğrulama başarısız"))

        // Valid Xtream login
        state.sourceForm("xtream")
        state.formName = "Kambak IPTV"
        state.formAddress = "http://panel.test:8080"
        state.formUser = "ali"
        state.formPassword = "secret"
        state.saveXtreamSource()
        settle(state)

        assertNull(state.dialog)
        assertEquals("Kambak IPTV", state.profileName)
        assertEquals(SourceKind.XTREAM, state.activeSourceKind)
        assertEquals(1, state.savedSources.size)
        assertEquals("Kanal D HD", state.data.filtered("Tümü", "").single().name)
    }

    @Test
    fun stalkerConnectFlowAndMacValidationWork() = runBlocking {
        val loader = object : SourceLoader {
            override suspend fun defaultText() = CountryListsCatalog.byCode("TR")!!.builtinM3u
            override suspend fun fetch(address: String) = ""
            override suspend fun fetch(address: String, headers: Map<String, String>): String = when {
                "action=handshake" in address -> """{"js":{"token":"TKN123"}}"""
                "action=get_genres" in address -> """{"js":[{"id":"5","title":"Haber"}]}"""
                "action=get_all_channels" in address -> """{"js":{"data":[{"id":"1","name":"CNN Türk","tv_genre_id":"5","cmd":"ffrt http://portal.test:8080/ch/1.m3u8","xmltv_id":"CNNTurk.tr"}]}}"""
                else -> error("unexpected")
            }
        }
        val store = FakeStore()
        val state = PreviewState(store, this, sourceLoader = loader)
        state.load()

        state.sourceForm("stalker")
        state.formAddress = "http://portal.test:8080/c"
        state.formMac = "hatali-mac"
        state.saveStalkerSource()
        assertTrue(state.notice.contains("MAC adresi"))

        state.formName = "Stalker Sunucum"
        state.formMac = "00:1A:79:11:22:33"
        state.saveStalkerSource()
        settle(state)

        assertNull(state.dialog)
        assertEquals("Stalker Sunucum", state.profileName)
        assertEquals(SourceKind.STALKER, state.activeSourceKind)
        assertEquals("CNN Türk", state.data.filtered("Haber", "").single().name)
    }

    @Test
    fun countryListsToggleFilterAndStandaloneActivationWork() = runBlocking {
        val loader = object : SourceLoader {
            override suspend fun defaultText() = CountryListsCatalog.byCode("TR")!!.builtinM3u
            override suspend fun fetch(address: String) = CountryListsCatalog.byCode("TR")!!.builtinM3u
        }
        val store = FakeStore()
        val state = PreviewState(store, this, sourceLoader = loader)
        state.load()

        // Toggle KKTC on
        state.toggleCountryList("KKTC")
        yield()
        assertTrue("KKTC" in state.enabledCountryCodes)
        assertTrue(state.data.filtered("KKTC", "").isNotEmpty())

        // Filter by Germany/Diaspora (auto-enables DE and switches category)
        state.filterByCountry("DE")
        yield()
        assertTrue("DE" in state.enabledCountryCodes)
        assertEquals("Almanya / Avrupa", state.category)
        assertEquals("live", state.page)
        assertTrue(state.data.filtered("Almanya / Avrupa", "").isNotEmpty())

        // Standalone Country Source activation
        state.activateCountrySource("AZ")
        settle(state)
        assertEquals(SourceKind.COUNTRY, state.activeSourceKind)
        assertTrue(state.data.filtered("AZERBAYCAN", "").isNotEmpty())

        // Restore default
        state.restoreDefault()
        settle(state)
        assertEquals(SourceKind.DEFAULT, state.activeSourceKind)
    }
}
