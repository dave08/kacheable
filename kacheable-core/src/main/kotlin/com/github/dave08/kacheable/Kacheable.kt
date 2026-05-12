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
