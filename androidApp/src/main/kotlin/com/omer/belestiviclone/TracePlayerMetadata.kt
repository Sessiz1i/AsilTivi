package com.omer.belestiviclone

import java.util.concurrent.atomic.AtomicLong

/** Value snapshot only: neither the player, the provider, nor an Activity is retained. */
@ConsistentCopyVisibility
data class TracePlayerMetadata private constructor(
    val identity: String,
    val sequence: Long,
    val applicationThreadId: Long,
    val applicationLooperIdentity: String,
) {
    fun onApplicationLooper(currentLooperIdentity: String) = applicationLooperIdentity == currentLooperIdentity

    companion object {
        private val nextSequence = AtomicLong()
        fun capture(identity: String, applicationMetadata: () -> Pair<Long, String>): TracePlayerMetadata {
            val (thread, looper) = applicationMetadata()
            return TracePlayerMetadata(identity, nextSequence.incrementAndGet(), thread, looper)
        }
    }
}
