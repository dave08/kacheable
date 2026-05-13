package com.github.dave08.kacheable.store

import kotlin.time.Duration

/**
 * Optional store capability for coordinating cache misses across multiple JVMs.
 */
interface DistributedSingleFlightStore {
    suspend fun <R> runWithDistributedSingleFlight(
        key: String,
        lockLease: Duration,
        waitTimeout: Duration,
        pollInterval: Duration,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R
}
