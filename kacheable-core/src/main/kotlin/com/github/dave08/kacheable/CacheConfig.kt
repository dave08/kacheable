package com.github.dave08.kacheable

import kotlin.time.Duration

/**
 * Per-cache runtime configuration.
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
)
