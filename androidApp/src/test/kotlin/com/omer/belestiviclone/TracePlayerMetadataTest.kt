package com.omer.belestiviclone

import kotlin.test.*

class TracePlayerMetadataTest {
    private class Dependency {
        var released = false
        var reads = 0
        fun metadata(): Pair<Long, String> {
            check(!released) { "dependency-used-after-release" }
            reads++
            return 17L to "application-looper"
        }
    }
    @Test fun capturedContextRemainsUsableAfterDependencyRelease() {
        val dependency = Dependency()
        val metadata = TracePlayerMetadata.capture("player", dependency::metadata)
        dependency.released = true
        assertFailsWith<IllegalStateException> { dependency.metadata() }
        val emitted = mutableListOf<TracePlayerMetadata>()
        fun emit() { assertTrue(metadata.onApplicationLooper("application-looper")); emitted += metadata }
        emit(); emit() // A later event requires only this value; the provider is already unusable.
        assertEquals(1, dependency.reads)
        assertEquals(listOf(metadata, metadata), emitted)
        assertEquals(17L, metadata.applicationThreadId)
        assertFalse(metadata.onApplicationLooper("other-looper"))
    }
    @Test fun newInstancesHaveDistinctSequencesEvenWhenIdentityHashCollides() {
        val first = TracePlayerMetadata.capture("same-hash") { 17L to "loop" }
        val second = TracePlayerMetadata.capture("same-hash") { 17L to "loop" }
        assertTrue(second.sequence > first.sequence)
        assertNotEquals(first, second)
    }
}
