package com.omer.belestiviclone

internal enum class LogoFailureKind(val retryAfterMs: Long, val automaticRetry: Boolean) {
    NETWORK(30_000, true), HTTP(300_000, false), DECODE(300_000, false), UNSUPPORTED(300_000, false),
}
internal data class LogoFailure(val kind: LogoFailureKind, val retryAtMs: Long)

/** Bounded negative cache. A cache clear invalidates in-flight results as well as saved failures. */
internal class LogoRequestPolicy(private val capacity: Int = 128) {
    init { require(capacity > 0) }
    private val failures = linkedMapOf<String, LogoFailure>()
    var generation: Int = 0; private set
    @Synchronized fun mayRequest(key: String, nowMs: Long) = failures[key]?.let { nowMs >= it.retryAtMs } ?: true
    @Synchronized fun failure(key: String): LogoFailure? = failures[key]
    @Synchronized fun record(key: String, kind: LogoFailureKind, nowMs: Long, epoch: Int): Boolean {
        if (epoch != generation) return false
        failures.remove(key)
        failures[key] = LogoFailure(kind, nowMs + kind.retryAfterMs)
        while (failures.size > capacity) failures.remove(failures.keys.first())
        return true
    }
    @Synchronized fun clear(): Int { failures.clear(); generation++; return generation }
}
