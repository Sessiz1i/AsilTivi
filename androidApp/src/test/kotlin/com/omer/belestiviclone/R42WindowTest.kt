package com.omer.belestiviclone

import kotlin.test.*
import org.junit.Test

class R42WindowTest {
    private val ids=setOf("C002","C003","C009","C020")
    private fun ticks()=(1..3).map { n -> ids.map { R42Sample(it,"player-$it","attempt-$it",n*1000L,n*25L,true,true) } }
    @Test fun requiresThreeSimultaneousDistinctAdvancingOwners() {
        assertTrue(r42CommonWindow(ids,ticks()))
        assertFalse(r42CommonWindow(ids,ticks().map { listOf(it.first()) }))
        assertFalse(r42CommonWindow(ids,ticks().map { it.map { s -> s.copy(player="one") } }))
        assertFalse(r42CommonWindow(ids,ticks().map { it.map { s -> s.copy(attempt="one") } }))
        assertFalse(r42CommonWindow(ids,ticks().take(2)))
    }
    @Test fun pausedStalledFramesOwnerChangesAndPositionRegressionsReject() {
        for (replacement in listOf(ticks()[2][0].copy(playing=false),ticks()[2][0].copy(ready=false),
            ticks()[2][0].copy(position=0),ticks()[2][0].copy(frames=0),ticks()[2][0].copy(player="new"),
            ticks()[2][0].copy(attempt="new"))) {
            val bad=ticks().toMutableList();bad[2]=listOf(replacement)+bad[2].drop(1)
            assertFalse(r42CommonWindow(ids,bad))
        }
    }
}
