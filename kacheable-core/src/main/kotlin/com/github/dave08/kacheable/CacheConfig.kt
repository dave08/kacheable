package com.github.dave08.kacheable

import kotlin.time.Duration

data class CacheConfig(
    val name: String,
    val expiryType: ExpiryType = ExpiryType.none,
    val expiry: Duration = Duration.INFINITE,
    /**
    If this is a real null, the cache entry will not be saved at all.
    This should ONLY be set if the function's return type is nullable!
     */
    val nullPlaceholder: String? = null,
)