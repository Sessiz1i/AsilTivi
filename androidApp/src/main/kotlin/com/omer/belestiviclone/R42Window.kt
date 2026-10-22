package com.omer.belestiviclone

internal data class R42Sample(val channel: String, val player: String, val attempt: String,
    val position: Long, val frames: Long, val playing: Boolean, val ready: Boolean)

/** Three common snapshots; never combine sequential owners, attempts, or timeline periods. */
internal fun r42CommonWindow(expected: Set<String>, samples: List<List<R42Sample>>): Boolean {
    if (samples.size != 3 || expected.size !in setOf(1,2,3,4)) return false
    if (samples.any { rows -> rows.size != expected.size || rows.map { it.channel }.toSet() != expected ||
        rows.map { it.player }.toSet().size != expected.size || rows.map { it.attempt }.toSet().size != expected.size ||
        rows.any { !it.playing || !it.ready || it.attempt.isEmpty() } }) return false
    return expected.all { channel ->
        val rows=samples.map { tick -> tick.single { it.channel == channel } }
        rows.map { it.player to it.attempt }.distinct().size == 1 &&
            rows.zipWithNext().all { (a,b) -> b.position > a.position && b.frames > a.frames }
    }
}
