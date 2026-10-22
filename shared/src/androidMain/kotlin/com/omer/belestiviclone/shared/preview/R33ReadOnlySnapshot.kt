package com.omer.belestiviclone.shared.preview

/** Existing row + pure exact-state audit only. No store, initializer, writer or apply dependency. */
object R33ReadOnlySnapshot {
    fun inspect(a: R33Admission, row: PreviewRow, context: PilotContext): PreviewData {
        require(R33ProfileTransition.allowed(a) && a.action == "preflight" && row.key == 2) { "r33-disabled-inspection" }
        require(PreviewCodec.unpack(row.sources).firstOrNull() in setOf("BT-SOURCES-4", "BT-SOURCES-5")) { "r33-exact-envelope" }
        val data = R31ReadOnlySnapshot.decode(row)
        R33ProfileTransition.audit(data, context, a)
        return data
    }
}
