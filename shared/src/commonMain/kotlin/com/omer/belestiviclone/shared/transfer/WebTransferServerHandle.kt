package com.omer.belestiviclone.shared.transfer

interface WebTransferServerHandle {
    fun start(preferredPort: Int = 8080): TransferServerInfo
    fun stop()
    val isRunning: Boolean
    val serverInfo: TransferServerInfo?
}
