package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Suspended cache runtime.
 *
 * Prefer the typed `cacheKey(...)` APIs for new code. The raw string-key calls remain available
 * for simple exact caches and migration cases.
 */
interface Kacheable {
    /**
     * Runs [block] and invalidates the raw cache entries after the block succeeds.
     */
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    /**
     * Reads a raw cache entry identified by [name] and [params], or runs [block] and caches its result.
     *
     * [cacheIf] is evaluated only for a newly computed result. Return `false` to return the value
     * to the caller without writing it to the cache.
     */
    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R

    /**
     * Reads a raw cache entry using [type] for serialization, or runs [block] and caches its result.
     *
     * [cacheIf] is evaluated only for a newly computed result. Return `false` to return the value
     * to the caller without writing it to the cache.
     */
    suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R

    suspend fun invalidate(entryRef: StoredCacheEntryRef<*>)

    suspend fun invalidate(partRef: CacheEntryPartRef)

    suspend fun invalidate(allRef: StoredCacheAllRef<*>)

    suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = invoke(
        entryRef = entryRef,
        returnView = returnView,
        missPolicy = CacheMissPolicy.load(),
        refreshPolicy = CacheRefreshPolicy.neverRefresh(),
        storeResultIf = cacheIf,
    ) { block() }

    /**
     * Reads a typed cache entry or applies [missPolicy] when no hot cache or snapshot value exists.
     *
     * The [block] lambda remains the authoritative loader. Miss policies only decide whether the
     * caller waits for it, gets a fallback first, or receives a fallback after a loader failure.
     *
     * For storage decisions, use the overload with `storeResultIf`.
     */
    suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        block: suspend () -> R,
    ): R = invoke(
        entryRef = entryRef,
        returnView = returnView,
        missPolicy = missPolicy,
        refreshPolicy = CacheRefreshPolicy.neverRefresh(),
        storeResultIf = { true },
    ) { block() }

    /**
     * Reads a typed cache entry, optionally refreshes stale cached values, or applies [missPolicy]
     * when no value exists. The loader receives the previous cached value on refresh and `null` on
     * a true miss.
     *
     * [storeResultIf] is evaluated for loader results from both misses and refreshes. It never
     * changes the value returned to the caller, and it is not applied to miss-policy fallback values.
     *
     * Example:
     *
     * ```kotlin
     * cache.cache(
     *     productCardCache(productId),
     *     missPolicy = CacheMissPolicy.loadInBackground {
     *         ProductCard.placeholder(productId)
     *     },
     *     refreshPolicy = CacheRefreshPolicy.refreshIf(inBackground = true) { cached ->
     *         cached.isStale(clock.now())
     *     },
     *     storeResultIf = { it.isUsable },
     * ) { previous ->
     *     catalogClient.fetchProductCard(productId, previous?.version)
     * }
     * ```
     */
    suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R
}

/**
 * Reads a raw cache entry identified by [name] and [params], inferring the serializer from [R].
 */
suspend inline operator fun <reified R> Kacheable.invoke(
    name: String,
    vararg params: Any,
    noinline cacheIf: (R) -> Boolean = { true },
    noinline block: suspend () -> R
): R =
    invoke(name, serializer<R>(), *params, cacheIf = cacheIf, block = block)

/**
 * Named equivalent of [invoke] for raw cache entries.
 */
suspend inline fun <reified R> Kacheable.cache(
    name: String,
    vararg params: Any,
    noinline cacheIf: (R) -> Boolean = { true },
    noinline block: suspend () -> R
): R =
    invoke(name, serializer(), *params, cacheIf = cacheIf, block = block)
