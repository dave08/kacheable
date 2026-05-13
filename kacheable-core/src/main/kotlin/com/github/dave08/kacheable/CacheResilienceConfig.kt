package com.github.dave08.kacheable

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Controls how Kacheable behaves when a cache entry is missing and the loader must run.
 */
data class CacheResilienceConfig(
    val singleFlight: SingleFlightMode = SingleFlightMode.None,
    val loadTimeout: Duration? = null,
    val maxConcurrentLoads: Int? = null,
    val staleOnFailure: Boolean = false,
    val staleOnTimeout: Boolean = false,
) {
    init {
        require(loadTimeout == null || loadTimeout > ZERO) {
            "loadTimeout must be positive when configured."
        }
        require(maxConcurrentLoads == null || maxConcurrentLoads > 0) {
            "maxConcurrentLoads must be positive when configured."
        }
    }
}

enum class SingleFlightMode {
    None,
    Local,
    Redis,
}
