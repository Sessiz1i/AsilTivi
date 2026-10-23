package com.omer.belestiviclone.shared.diagnostics

fun interface NetworkDiagnosticsRunner {
    suspend fun run(
        testStreamUrl: String,
        onProgress: (DiagnosticsReport) -> Unit
    ): DiagnosticsReport
}
