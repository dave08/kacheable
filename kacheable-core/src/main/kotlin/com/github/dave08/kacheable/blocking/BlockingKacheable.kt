package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Blocking cache runtime.
 *
 * Prefer the typed `cacheKey(...)` APIs for new code. The raw string-key calls remain available
 * for simple exact caches and migration cases.
 */
interface BlockingKacheable {
    /**
     * Runs [block] and invalidates the raw cache entries after the block succeeds.
     */
    fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R

    /**
     * Reads a raw cache entry identified by [name] and [params], or runs [block] and caches its result.
     *
     * [cacheIf] is evaluated only for a newly computed result. Return `false` to return the value
     * to the caller without writing it to the cache.
     */
    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean = { true },
        block: () -> R
    ): R

    /**
     * Reads a raw cache entry using [type] for serialization, or runs [block] and caches its result.
     *
     * [cacheIf] is evaluated only for a newly computed result. Return `false` to return the value
     * to the caller without writing it to the cache.
     */
    fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean = { true },
        block: () -> R
    ): R
}

/**
 * Reads a raw cache entry identified by [name] and [params], inferring the serializer from [R].
 */
inline operator fun <reified R> BlockingKacheable.invoke(
    name: String,
    vararg params: Any,
    noinline cacheIf: (R) -> Boolean = { true },
    noinline block: () -> R
): R =
    invoke(name, serializer<R>(), *params, cacheIf = cacheIf, block = block)

/**
 * Named equivalent of [invoke] for raw cache entries.
 */
inline fun <reified R> BlockingKacheable.cache(
    name: String,
    vararg params: Any,
    noinline cacheIf: (R) -> Boolean = { true },
    noinline block: () -> R
): R =
    invoke(name, serializer(), *params, cacheIf = cacheIf, block = block)
