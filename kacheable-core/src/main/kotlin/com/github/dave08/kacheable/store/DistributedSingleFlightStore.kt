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

/**
 * Optional refinement that lets Kacheable coordinate local load admission before claiming
 * distributed single-flight leadership.
 *
 * Implementations must return immediately with `null` when another process owns [key]. A returned
 * lease must release only its own ownership token.
 */
interface AdmissionAwareDistributedSingleFlightStore : DistributedSingleFlightStore {
    suspend fun tryAcquireDistributedLoadLease(
        key: String,
        lockLease: Duration,
    ): DistributedLoadLease?
}

interface DistributedLoadLease {
    suspend fun release()
}
