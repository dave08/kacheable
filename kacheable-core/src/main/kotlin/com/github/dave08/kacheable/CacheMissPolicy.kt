package com.github.dave08.kacheable

/**
 * Controls what Kacheable does when neither the hot cache nor a restored snapshot has a value.
 */
sealed interface CacheMissPolicy<R> {
    /**
     * Normal read-through caching. Run the loader in the request path and return the loaded value.
     * If [fallbackOnFailure] is provided, it is returned when the loader fails or times out.
     */
    data class Load<R>(
        val fallbackOnFailure: (suspend (Throwable) -> R)? = null,
    ) : CacheMissPolicy<R>

    /**
     * Return [fallback] immediately, then run the loader in the background. The fallback value is not
     * cached because it did not come from the loader.
     */
    data class LoadInBackground<R>(
        val fallback: suspend () -> R,
    ) : CacheMissPolicy<R>

    companion object {
        fun <R> load(
            fallbackOnFailure: (suspend (Throwable) -> R)? = null,
        ): CacheMissPolicy<R> = Load(fallbackOnFailure)

        fun <R> loadInBackground(
            fallback: suspend () -> R,
        ): CacheMissPolicy<R> = LoadInBackground(fallback)
    }
}

/**
 * Controls whether Kacheable should rerun the loader when a cached value already exists.
 *
 * CacheConfig expiry is still handled by the backend. If a value has expired and cannot be read,
 * Kacheable treats it as a miss instead of applying this policy.
 */
sealed interface CacheRefreshPolicy<R> {
    /**
     * Return present cached values without refreshing them.
     */
    class NeverRefresh<R> internal constructor() : CacheRefreshPolicy<R>

    /**
     * Refresh cached values for which [isStale] returns true. When [inBackground] is true, Kacheable
     * returns the cached value immediately and refreshes in the background. Otherwise, it waits for
     * the refresh and returns the refreshed value, falling back to the cached value on refresh failure.
     */
    data class RefreshIf<R>(
        val inBackground: Boolean = false,
        val isStale: (cached: R) -> Boolean,
    ) : CacheRefreshPolicy<R>

    companion object {
        fun <R> neverRefresh(): CacheRefreshPolicy<R> = NeverRefresh()

        fun <R> refreshIf(
            inBackground: Boolean = false,
            isStale: (cached: R) -> Boolean,
        ): CacheRefreshPolicy<R> = RefreshIf(inBackground, isStale)
    }
}
