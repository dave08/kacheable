package com.github.dave08.kacheable

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * Capacity and admission policy for cache loaders sharing one concurrency boundary.
 *
 * [maxConcurrentBackgroundLoads] applies to the suspending runtime. Blocking caches do not expose
 * background miss/refresh policies and therefore use only the total and queue settings.
 * Suspending queues prioritize foreground work while periodically admitting background work to
 * avoid starvation.
 */
data class LoadConcurrencyConfig(
    val maxConcurrentLoads: Int,
    val maxConcurrentBackgroundLoads: Int? = null,
    val maxQueuedLoads: Int? = null,
    val queueTimeout: Duration? = null,
) {
    init {
        require(maxConcurrentLoads > 0) {
            "maxConcurrentLoads must be positive."
        }
        require(
            maxConcurrentBackgroundLoads == null ||
                maxConcurrentBackgroundLoads in 1..maxConcurrentLoads,
        ) {
            "maxConcurrentBackgroundLoads must be positive and no greater than maxConcurrentLoads."
        }
        require(maxQueuedLoads == null || maxQueuedLoads >= 0) {
            "maxQueuedLoads must not be negative."
        }
        require(queueTimeout == null || queueTimeout > ZERO) {
            "queueTimeout must be positive when configured."
        }
    }
}

/**
 * Typed identity for cache loaders that compete for the same underlying resource.
 *
 * Equality is based on [name], allowing configuration adapters to resolve a declared group by its
 * external configuration name. Kacheable rejects conflicting defaults for declarations that reuse
 * the same name.
 */
class LoadConcurrencyGroup internal constructor(
    val name: String,
    val defaults: LoadConcurrencyConfig,
) {
    override fun equals(other: Any?): Boolean =
        other is LoadConcurrencyGroup && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = name
}

fun loadConcurrencyGroup(
    name: String,
    defaults: LoadConcurrencyConfig,
): LoadConcurrencyGroup {
    require(name.isNotBlank()) { "Load concurrency group name must not be blank." }
    return LoadConcurrencyGroup(name, defaults)
}

/**
 * Runtime concurrency settings.
 *
 * [default] applies independently to every ungrouped cache family. A grouped cache instead uses an
 * explicit entry from [overrides], or the defaults declared by its [LoadConcurrencyGroup].
 */
data class LoadConcurrencySettings(
    val default: LoadConcurrencyConfig? = null,
    val overrides: Map<LoadConcurrencyGroup, LoadConcurrencyConfig> = emptyMap(),
)

class CacheLoadRejectedException(message: String) : RuntimeException(message)
