package com.github.dave08.kacheable

/**
 * Controls what Kacheable does when neither the hot cache nor a restored snapshot has a value.
 *
 * A miss policy decides what the caller receives while the loader lambda is producing the real
 * result. It does not decide whether that result is stored; use `storeResultIf`/`cacheIf` for that.
 *
 * Example:
 *
 * ```kotlin
 * cache(
 *     productCardCache(productId),
 *     missPolicy = CacheMissPolicy.loadInBackground {
 *         ProductCard.placeholder(productId)
 *     },
 *     storeResultIf = { it.isUsable },
 * ) {
 *     catalogClient.fetchProductCard(productId)
 * }
 * ```
 */
sealed interface CacheMissPolicy<R> {
    /**
     * Normal read-through caching. Run the loader in the request path and return the loaded value.
     * If [fallbackOnFailure] is provided, it is returned when the loader fails or times out.
     *
     * Use this for ordinary cache misses, or when the caller should wait for the authoritative
     * value but receive a degraded response if the loader cannot produce one.
     */
    data class Load<R>(
        val fallbackOnFailure: (suspend (Throwable) -> R)? = null,
    ) : CacheMissPolicy<R>

    /**
     * Return [fallback] immediately, then run the loader in the background. The fallback value is not
     * cached because it did not come from the loader.
     *
     * Use this when the caller can safely receive a cheap placeholder or previous domain default
     * while an expensive result is generated for later callers.
     */
    data class LoadInBackground<R>(
        val fallback: suspend () -> R,
    ) : CacheMissPolicy<R>

    companion object {
        /**
         * Wait for the loader on a miss.
         *
         * When [fallbackOnFailure] is `null`, loader failures are rethrown. When it is provided,
         * Kacheable returns that fallback only for loader failure or timeout. Successful loader
         * results are returned normally and are considered for storage.
         */
        fun <R> load(
            fallbackOnFailure: (suspend (Throwable) -> R)? = null,
        ): CacheMissPolicy<R> = Load(fallbackOnFailure)

        /**
         * Return [fallback] immediately on a miss and run the loader in the background.
         *
         * The fallback is never stored by this policy. Only the loader result can be stored, and
         * only when the call's store predicate allows it.
         */
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
 *
 * Example:
 *
 * ```kotlin
 * cache(
 *     productCardCache(productId),
 *     missPolicy = CacheMissPolicy.load(),
 *     refreshPolicy = CacheRefreshPolicy.refreshIf(inBackground = true) { cached ->
 *         cached.generatedAt < clock.now() - 30.minutes
 *     },
 *     storeResultIf = { it.isUsable },
 * ) { previous ->
 *     catalogClient.fetchProductCard(productId, previousVersion = previous?.version)
 * }
 * ```
 */
sealed interface CacheRefreshPolicy<R> {
    /**
     * Return present cached values without refreshing them. Backend expiry still applies; an expired
     * value that cannot be read is a miss.
     */
    class NeverRefresh<R> internal constructor() : CacheRefreshPolicy<R>

    /**
     * Refresh cached values for which [isStale] returns true. When [inBackground] is true, Kacheable
     * returns the cached value immediately and refreshes in the background. Otherwise, it waits for
     * the refresh and returns the refreshed value, falling back to the cached value on refresh failure.
     *
     * Refresh failures never overwrite or remove the previous cached value.
     */
    data class RefreshIf<R>(
        val inBackground: Boolean = false,
        val isStale: (cached: R) -> Boolean,
    ) : CacheRefreshPolicy<R>

    companion object {
        /**
         * Do not refresh values while they are present in the cache.
         */
        fun <R> neverRefresh(): CacheRefreshPolicy<R> = NeverRefresh()

        /**
         * Refresh only cached values for which [isStale] returns true.
         *
         * With [inBackground] set to `false`, the caller waits for the refresh and receives the
         * refreshed value when it succeeds. With [inBackground] set to `true`, the caller receives
         * the cached value immediately and the refresh updates storage for future callers.
         */
        fun <R> refreshIf(
            inBackground: Boolean = false,
            isStale: (cached: R) -> Boolean,
        ): CacheRefreshPolicy<R> = RefreshIf(inBackground, isStale)
    }
}
