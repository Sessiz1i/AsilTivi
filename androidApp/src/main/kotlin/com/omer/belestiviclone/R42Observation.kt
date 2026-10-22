package com.omer.belestiviclone

/** Debug measurement owners only. Access stays on the Player application thread. */
internal object R42Observation {
    private val players = mutableSetOf<AndroidPreviewPlayer>()
    fun add(player: AndroidPreviewPlayer) { players += player }
    fun remove(player: AndroidPreviewPlayer) { players -= player }
    fun snapshot() = players.map { it.r42Snapshot() }
}
