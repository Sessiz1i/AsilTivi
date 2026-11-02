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

    @Test
    fun githubPrivateRemoteRequestAndAutoSyncPreserveFavorites() = runBlocking {
        // 1. Test buildRemoteRequest for Private Repo, Public Repo, and Blob URL
        assertNull(PublicM3u.buildRemoteRequest("   "))

        val privateReq = PublicM3u.buildRemoteRequest(
            repoOrUrl = "omerolgun/tiviliste",
            branchAndPath = "main/TiviListe.m3u",
            token = "github_pat_123456"
        )
        assertNotNull(privateReq)
        assertEquals("https://api.github.com/repos/omerolgun/tiviliste/contents/TiviListe.m3u?ref=main", privateReq.url)
        assertEquals("Bearer github_pat_123456", privateReq.headers["Authorization"])
        assertEquals("application/vnd.github.raw+json", privateReq.headers["Accept"])

        val publicReq = PublicM3u.buildRemoteRequest(
            repoOrUrl = "omerolgun/tiviliste",
            branchAndPath = "dev/listeler/TiviListe.m3u",
            token = ""
        )
        assertNotNull(publicReq)
        assertEquals("https://raw.githubusercontent.com/omerolgun/tiviliste/dev/listeler/TiviListe.m3u", publicReq.url)
        assertNull(publicReq.headers["Authorization"])

        val blobReq = PublicM3u.buildRemoteRequest(
            repoOrUrl = "https://github.com/omerolgun/tiviliste/blob/main/TiviListe.m3u",
            token = "ghp_secret"
        )
        assertNotNull(blobReq)
        assertEquals("https://raw.githubusercontent.com/omerolgun/tiviliste/main/TiviListe.m3u", blobReq.url)
        assertEquals("Bearer ghp_secret", blobReq.headers["Authorization"])

        // 2. Test saveGitHubSyncConfig & favorite preservation across URL update
        var remoteM3u = """
            #EXTM3U
            #EXTINF:0 CUID="1" tvg-name="TRT 1" group-title="ULUSAL",TRT 1
            https://tv-trt1.medya.trt.com.tr/master.m3u8
            #EXTINF:0 CUID="2" tvg-name="Flash Haber" group-title="HABER",Flash Haber
            https://eski-bozuk-yayin.test/flash.m3u8
        """.trimIndent()
        var lastRequestedUrl = ""
        var lastAuthHeader = ""

        val loader = object : SourceLoader {
            override suspend fun defaultText() = remoteM3u
            override suspend fun fetch(address: String) = remoteM3u
            override suspend fun fetch(address: String, headers: Map<String, String>): String {
                lastRequestedUrl = address
                lastAuthHeader = headers["Authorization"].orEmpty()
                return remoteM3u
            }
        }
        val store = FakeStore()
        val state = PreviewState(store, this, sourceLoader = loader)
        state.load()

        state.sourceForm("githubSync")
        assertEquals("githubSync", state.dialog)
        state.formAddress = "omerolgun/tiviliste"
        state.formName = "main/TiviListe.m3u"
        state.formPassword = "github_pat_testtoken"
        state.formUser = "Açık"
        state.saveGitHubSyncConfig(fetchNow = true)
        settle(state)

        assertNull(state.dialog)
        assertEquals("https://api.github.com/repos/omerolgun/tiviliste/contents/TiviListe.m3u?ref=main", lastRequestedUrl)
        assertEquals("Bearer github_pat_testtoken", lastAuthHeader)
        assertEquals(2, state.data.filtered("Tümü", "").size)

        // Add Flash Haber to favorites, then simulate fixing its stream URL on GitHub
        val oldFlash = state.data.filtered("Tümü", "").first { it.name == "Flash Haber" }
        state.toggleFavoriteWithToast(oldFlash.id)
        settle(state)
        assertTrue(oldFlash.id in state.data.favorites)

        remoteM3u = """
            #EXTM3U
            #EXTINF:0 CUID="1" tvg-name="TRT 1" group-title="ULUSAL",TRT 1
            https://tv-trt1.medya.trt.com.tr/master.m3u8
            #EXTINF:0 CUID="2" tvg-name="Flash Haber" group-title="HABER",Flash Haber
            https://yeni-calisan-yayin.test/flash_yeni.m3u8
        """.trimIndent()

        state.refreshSource()
        settle(state)

        val favChannels = state.data.filtered("Favoriler", "")
        val updatedFlash = favChannels.firstOrNull { it.name == "Flash Haber" }
        assertNotNull(updatedFlash)
        assertEquals("https://yeni-calisan-yayin.test/flash_yeni.m3u8", playbackUri(updatedFlash))
    }
}
