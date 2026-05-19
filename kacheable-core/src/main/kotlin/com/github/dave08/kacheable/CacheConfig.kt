package com.github.dave08.kacheable

import kotlin.time.Duration

/**
 * Per-cache runtime configuration.
 *
 * `CacheConfig` is where cache-family behavior lives: expiry, nullable-value storage, loader
 * resilience, and optional snapshots. Call sites keep the lambda and request-time policies.
 *
 * Example:
 *
 * ```kotlin
 * val cache = Kacheable(
 *     store = redisStore,
 *     configs = mapOf(
 *         "product-cards" to CacheConfig(
 *             name = "product-cards",
 *             expiryType = ExpiryType.after_write,
 *             expiry = 30.minutes,
 *             snapshot = persistentSnapshot(
 *                 restore = SnapshotRestore.BackgroundWithOnDemandChunks,
 *             ),
 *         ),
 *     ),
 * )
 * ```
 */
data class CacheConfig(
    val name: String,
    val expiryType: ExpiryType = ExpiryType.none,
    val expiry: Duration = Duration.INFINITE,
    /**
     * Serialized marker used to cache a nullable result.
     *
     * If this is `null`, real null results are returned to the caller but not written to the cache.
    */
    val nullPlaceholder: String? = null,
    val resilience: CacheResilienceConfig? = null,
    /**
     * Optional durable snapshot behavior for this cache family.
     *
     * Snapshots are useful for expensive indexed/hash-style caches that should survive a cold
     * Redis or in-memory restart. Kacheable still treats the loader lambda as the source of truth:
     * restored snapshot values are just warm cache entries, and misses still run the configured
     * miss policy.
     */
    val snapshot: CacheSnapshotConfig? = null,
)
