package com.omer.belestiviclone.shared.preview

data class R38CRequest(val admission: R38CAdmission, val context: PilotContext)
data class R38CPrepared(val before: PreviewData, val after: PreviewData, val expected: PreviewRow, val next: PreviewRow)

/** Off-writer planning; the writer subsequently checks the caller's entire expected row even on HOLD/no-op. */
fun planR38C(request: R38CRequest, expected: PreviewRow): R38CPrepared {
    if (!R38CProfileTransition.admitted(request.admission) || expected.key != 2) throw TransitionProblem("r38c-disabled-profile")
    if (PreviewCodec.unpack(expected.sources).firstOrNull() !in setOf("BT-SOURCES-4", R38CProfileTransition.envelope))
        throw TransitionProblem("r38c-wrong-layer")
    val before = R31ReadOnlySnapshot.decode(expected)
    val after = R38CProfileTransition.prepare(before, request.context, request.admission)
    val next = expected.copy(sources = LibraryCodec.encode(after))
    check(R31ReadOnlySnapshot.decode(next) == after) { "r38c-lossy-envelope" }
    return R38CPrepared(before, after, expected, next)
}
