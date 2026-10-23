package com.omer.belestiviclone.shared.diagnostics

data class NetworkState(
    val typeName: String = "Bilinmiyor", // Wi-Fi, Ethernet, Hücresel, Yok
    val localIp: String = "",
    val isConnected: Boolean = false,
    val details: String = ""
)

data class DnsState(
    val host: String = "google.com",
    val resolvedIp: String? = null,
    val latencyMs: Long = -1,
    val isSuccess: Boolean = false,
    val error: String? = null
)

data class StreamServerState(
    val url: String = "",
    val httpCode: Int = 0,
    val latencyMs: Long = -1,
    val isSuccess: Boolean = false,
    val error: String? = null
)

data class DiagnosticsReport(
    val network: NetworkState = NetworkState(),
    val dns: DnsState = DnsState(),
    val stream: StreamServerState = StreamServerState(),
    val inProgress: Boolean = false
) {
    val isOverallSuccess: Boolean
        get() = network.isConnected && dns.isSuccess && stream.isSuccess
}
