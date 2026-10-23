package com.omer.belestiviclone.shared.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebTransferModelTest {

    @Test
    fun transferServerInfoBuildsCorrectUrl() {
        val info = TransferServerInfo(localIp = "192.168.1.50", port = 8080)
        assertEquals("http://192.168.1.50:8080", info.url)
        assertTrue(info.isRunning)
    }

    @Test
    fun createsPayloadsCorrectly() {
        val m3uPayload = TransferPayload(
            kind = TransferPayloadKind.M3U_URL,
            name = "Test M3U",
            url = "http://example.com/playlist.m3u"
        )
        assertEquals(TransferPayloadKind.M3U_URL, m3uPayload.kind)
        assertEquals("Test M3U", m3uPayload.name)
        assertEquals("http://example.com/playlist.m3u", m3uPayload.url)

        val xtreamPayload = TransferPayload(
            kind = TransferPayloadKind.XTREAM,
            name = "Xtream Servis",
            serverUrl = "http://xtream.xyz:8080",
            username = "user1",
            password = "pwd"
        )
        assertEquals(TransferPayloadKind.XTREAM, xtreamPayload.kind)
        assertEquals("user1", xtreamPayload.username)

        val stalkerPayload = TransferPayload(
            kind = TransferPayloadKind.STALKER,
            name = "Stalker Servis",
            portalUrl = "http://stalker.xyz/c/",
            mac = "00:1A:79:00:11:22"
        )
        assertEquals(TransferPayloadKind.STALKER, stalkerPayload.kind)
        assertEquals("00:1A:79:00:11:22", stalkerPayload.mac)
    }
}
