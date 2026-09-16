package com.github.dave08.kacheable

enum class CachePublication { Replace, IfAbsent }
enum class CacheCoordination { Entry, Partition }

/**
 * Opt-in generation-checked loading for a hash partition.
 *
 * A loader reads and publishes against one partition version. Concurrent changes restart the
 * attempt, up to [maxLoadAttempts]; cancellation is never retried. Leaving
 * [CacheConfig.partition] unset preserves ordinary cache behavior.
 */
sealed interface CachePartitionPolicy {
    val maxLoadAttempts: Int

    /** Load only the requested key, with independently chosen publication and coordination. */
    data class OnDemand(
        val publication: CachePublication = CachePublication.Replace,
        val coordination: CacheCoordination = CacheCoordination.Entry,
        override val maxLoadAttempts: Int = 3,
    ) : CachePartitionPolicy {
        init {
            require(maxLoadAttempts > 0) { "maxLoadAttempts must be positive." }
        }
    }

    /**
     * Load missing integer keys in order from [first] through the requested key.
     * Partition coordination serializes dependent loaders; existing entries are never replaced.
     */
    data class SequentialFrom(
        val first: Int = 0,
        override val maxLoadAttempts: Int = 3,
    ) : CachePartitionPolicy {
        init {
            require(maxLoadAttempts > 0) { "maxLoadAttempts must be positive." }
        }
    }
}

internal val CacheConfig.publication: CachePublication
    get() = when (val policy = partition) {
        null -> CachePublication.Replace
        is CachePartitionPolicy.OnDemand -> policy.publication
        is CachePartitionPolicy.SequentialFrom -> CachePublication.IfAbsent
    }

internal val CacheConfig.coordination: CacheCoordination
    get() = when (val policy = partition) {
        null -> CacheCoordination.Entry
        is CachePartitionPolicy.OnDemand -> policy.coordination
        is CachePartitionPolicy.SequentialFrom -> CacheCoordination.Partition
    }

internal val CacheConfig.maxLoadAttempts: Int
    get() = partition?.maxLoadAttempts ?: 3
