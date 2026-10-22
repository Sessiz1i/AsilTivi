package com.omer.belestiviclone.shared.preview

data class R38BRequest(val admission: R38BAdmission, val context: PilotContext)
data class R38BPrepared(val before: PreviewData, val after: PreviewData, val expected: PreviewRow, val next: PreviewRow)

/** Off-writer planning; the writer subsequently checks the caller's entire expected row even on HOLD/no-op. */
fun planR38B(request: R38BRequest, expected: PreviewRow): R38BPrepared {
    if (!R38BProfileTransition.allowed(request.admission) || expected.key != 2) throw TransitionProblem("r38b-disabled-profile")
    if (PreviewCodec.unpack(expected.sources).firstOrNull() !in setOf("BT-SOURCES-4", R38BProfileTransition.envelope))
        throw TransitionProblem("r38b-wrong-layer")
    val before = R31ReadOnlySnapshot.decode(expected)
    val after = R38BProfileTransition.prepare(before, request.context, request.admission)
    val next = expected.copy(sources = LibraryCodec.encode(after))
    check(R31ReadOnlySnapshot.decode(next) == after) { "r38b-lossy-envelope" }
    return R38BPrepared(before, after, expected, next)
}
