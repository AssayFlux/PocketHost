package com.assayflux.pockethost.relay

/** Bounded exponential retry schedule for transient Relay startup failures. */
internal object RelayRetryPolicy {
    fun delaySeconds(attempt: Int): Long {
        require(attempt >= 0) { "Retry attempt cannot be negative." }
        val exponent = attempt.coerceAtMost(6)
        return (5L shl exponent).coerceAtMost(300L)
    }
}
