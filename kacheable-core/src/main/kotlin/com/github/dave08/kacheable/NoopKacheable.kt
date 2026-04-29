package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer

internal object NoopKacheable : Kacheable {
    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        block()

    @ExperimentalKacheableApi
    override suspend fun <R> invalidate(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        storageLayout: CacheStorageLayout,
        secondaryPatternPartArgs: List<CacheArgs>?,
        block: suspend () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override suspend fun <R> invalidateSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: suspend () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override suspend fun <R> invalidateSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
        block: suspend () -> R,
    ): R = block()

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()

    @ExperimentalKacheableApi
    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        cacheArgs: PrimarySecondaryCacheArgs,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()

    @ExperimentalKacheableApi
    override suspend fun invokeSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: suspend () -> Boolean,
    ): Boolean = block()

    @ExperimentalKacheableApi
    override suspend fun <R : Any> invokeSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = block()

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()
}

fun KacheableNoOp(): Kacheable = NoopKacheable
