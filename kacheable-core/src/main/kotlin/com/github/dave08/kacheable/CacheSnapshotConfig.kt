package com.github.dave08.kacheable

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Durable snapshot behavior for one cache family.
 *
 * Snapshots are not a second source of truth. They are a cold-start optimization for expensive
 * cache families that would otherwise need to be rebuilt entirely through loader lambdas after a
 * Redis flush, deployment, or process restart.
 */
data class CacheSnapshotConfig(
    /**
     * How aggressively Kacheable restores this cache from the snapshot store at startup.
     */
    val restore: SnapshotRestore = SnapshotRestore.Blocking,
    /**
     * How often Kacheable exports the current hot cache state.
     *
     * Use `Duration.ZERO` to disable periodic flushing, which is useful in tests or when an app
     * wants to trigger snapshot export through an internal hook later.
     */
    val flushInterval: Duration = 15.minutes,
    /**
     * Whether Kacheable rotates the latest successful snapshot into a previous fallback slot.
     */
    val retention: SnapshotRetention = SnapshotRetention.LatestAndPrevious,
    /**
     * Number of SHA-256 hex characters used to group indexed entries into chunk files.
     *
     * Longer values create more, smaller chunks. Shorter values create fewer, larger chunks.
     */
    val chunkHashLength: Int = 2,
)

/**
 * Creates a durable snapshot configuration for a cache family.
 *
 * Example:
 *
 * ```kotlin
 * CacheConfig(
 *     name = "product-cards",
 *     snapshot = persistentSnapshot(
 *         restore = SnapshotRestore.BackgroundWithOnDemandChunks,
 *         flushInterval = 15.minutes,
 *         retention = SnapshotRetention.LatestAndPrevious,
 *     ),
 * )
 * ```
 */
fun persistentSnapshot(
    restore: SnapshotRestore = SnapshotRestore.Blocking,
    flushInterval: Duration = 15.minutes,
    retention: SnapshotRetention = SnapshotRetention.LatestAndPrevious,
    chunkHashLength: Int = 2,
): CacheSnapshotConfig = CacheSnapshotConfig(
    restore = restore,
    flushInterval = flushInterval,
    retention = retention,
    chunkHashLength = chunkHashLength,
)

/**
 * Startup restore mode for a snapshotted cache family.
 */
enum class SnapshotRestore {
    /**
     * Restore the configured snapshot before the `Kacheable(...)` factory returns.
     */
    Blocking,
    /**
     * Start restoring immediately in the background.
     */
    Background,
    /**
     * Restore in the background, and on a request miss try the matching snapshot chunk before
     * running the miss policy.
     */
    BackgroundWithOnDemandChunks,
}

/**
 * Snapshot slot retention and restore fallback policy.
 */
enum class SnapshotRetention {
    /**
     * Write and restore only the latest successful snapshot slot.
     */
    LatestOnly,
    /**
     * Keep the latest snapshot and rotate the previous latest into a fallback slot. Restore tries
     * the previous slot when the latest slot is missing or corrupt.
     */
    LatestAndPrevious,
}
