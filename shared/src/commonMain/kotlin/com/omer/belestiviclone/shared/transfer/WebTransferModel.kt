package com.omer.belestiviclone.shared.transfer

enum class TransferPayloadKind {
    M3U_URL,
    M3U_TEXT,
    XTREAM,
    STALKER
}

data class TransferPayload(
    val kind: TransferPayloadKind,
    val name: String = "",
    val url: String = "",
    val content: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val portalUrl: String = "",
    val mac: String = ""
)

data class TransferServerInfo(
    val localIp: String,
    val port: Int,
    val isRunning: Boolean = true,
    val error: String? = null
) {
    val url: String get() = "http://$localIp:$port"
}
