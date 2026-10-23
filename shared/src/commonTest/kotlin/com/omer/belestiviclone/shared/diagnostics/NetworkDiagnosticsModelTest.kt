package com.omer.belestiviclone.shared.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkDiagnosticsModelTest {

    @Test
    fun diagnosticsReportEvaluatesOverallSuccess() {
        val successReport = DiagnosticsReport(
            network = NetworkState(typeName = "Wi-Fi", localIp = "192.168.1.20", isConnected = true),
            dns = DnsState(isSuccess = true, latencyMs = 15),
            stream = StreamServerState(isSuccess = true, httpCode = 200, latencyMs = 35),
            inProgress = false
        )
        assertTrue(successReport.isOverallSuccess)

        val failedDns = successReport.copy(dns = DnsState(isSuccess = false, error = "Timeout"))
        assertFalse(failedDns.isOverallSuccess)

        val failedNetwork = successReport.copy(network = NetworkState(isConnected = false))
        assertFalse(failedNetwork.isOverallSuccess)

        val failedStream = successReport.copy(stream = StreamServerState(isSuccess = false, httpCode = 503))
        assertFalse(failedStream.isOverallSuccess)
    }
}
