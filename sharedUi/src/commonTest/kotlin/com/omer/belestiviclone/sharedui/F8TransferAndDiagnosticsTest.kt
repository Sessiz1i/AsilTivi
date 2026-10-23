package com.omer.belestiviclone.sharedui

import com.omer.belestiviclone.shared.diagnostics.*
import com.omer.belestiviclone.shared.preview.*
import com.omer.belestiviclone.shared.transfer.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.*

class F8TransferAndDiagnosticsTest {

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
        withTimeout(5000) {
            while (state.busy) delay(5)
        }
        yield()
    }

    private class FakeTransferServer(
        private val onPayload: (TransferPayload) -> Unit
    ) : WebTransferServerHandle {
        override var isRunning = false
        override var serverInfo: TransferServerInfo? = null

        override fun start(preferredPort: Int): TransferServerInfo {
            isRunning = true
            val info = TransferServerInfo(localIp = "192.168.1.150", port = preferredPort, isRunning = true)
            serverInfo = info
            return info
        }

        override fun stop() {
            isRunning = false
            serverInfo = null
        }

        fun emit(payload: TransferPayload) {
            onPayload(payload)
        }
    }

    @Test
    fun webTransferDialogStartsAndStopsServerSafely() {
        runBlocking {
            val store = FakeStore()
            var lastServer: FakeTransferServer? = null
            val state = PreviewState(
                store = store,
                scope = this,
                webTransferServerFactory = { onPayload ->
                    FakeTransferServer(onPayload).also { lastServer = it }
                }
            )
            state.load()

            state.openWebTransfer()
            assertEquals("webTransfer", state.dialog)

            state.startWebTransferServer()
            assertNotNull(state.webTransferServer)
            assertTrue(state.webTransferServer!!.isRunning)
            assertEquals("http://192.168.1.150:8080", state.webTransferInfo?.url)

            // Dismiss closes the server
            state.dismiss()
            assertNull(state.dialog)
            assertNull(state.webTransferServer)
            assertNull(state.webTransferInfo)
            assertFalse(lastServer!!.isRunning)
        }
    }

    @Test
    fun handleTransferPayloadAppendsM3uChannels() {
        runBlocking {
            val store = FakeStore()
            val state = PreviewState(store = store, scope = this)
            state.load()

            val m3uText = """
                #EXTM3U
                #EXTINF:-1 group-title="Ulusal",Test Kanal 1
                https://stream.example.com/live1.m3u8
                #EXTINF:-1 group-title="Haber",Test Kanal 2
                https://stream.example.com/live2.m3u8
            """.trimIndent()

            state.handleTransferPayload(
                TransferPayload(
                    kind = TransferPayloadKind.M3U_TEXT,
                    name = "Ofis Listesi",
                    content = m3uText
                )
            )

            settle(state)

            assertEquals(14, state.data.channels.size)
            assertTrue(state.data.channels.any { it.name == "Test Kanal 1" })
            assertTrue(state.data.channels.any { it.name == "Test Kanal 2" })
            assertTrue(state.notice.contains("Ofis Listesi") || state.notice.contains("içerik yüklendi"))
            assertNotNull(state.lastTransferSuccess)
        }
    }

    @Test
    fun networkDiagnosticsRunsAndReportsOverallStatus() {
        runBlocking {
            val store = FakeStore()
            val runner = NetworkDiagnosticsRunner { testUrl, onProgress ->
                val report = DiagnosticsReport(
                    network = NetworkState("Wi-Fi", "192.168.1.55", true, "Wi-Fi · IP: 192.168.1.55"),
                    dns = DnsState("google.com", "142.250.180.206", 12, true),
                    stream = StreamServerState(testUrl, 200, 28, true),
                    inProgress = false
                )
                onProgress(report)
                report
            }

            val state = PreviewState(
                store = store,
                scope = this,
                networkDiagnosticsRunner = runner
            )
            state.load()

            state.openNetworkDiagnostics()
            assertEquals("networkDiagnostics", state.dialog)

            state.runNetworkDiagnostics()
            // Wait for coroutine job
            kotlinx.coroutines.delay(50)

            val report = state.diagnosticsReport
            assertTrue(report.network.isConnected)
            assertTrue(report.dns.isSuccess)
            assertTrue(report.stream.isSuccess)
            assertTrue(report.isOverallSuccess)
            assertEquals("Wi-Fi", report.network.typeName)
            assertEquals(12, report.dns.latencyMs)
            assertEquals(200, report.stream.httpCode)
        }
    }
}
