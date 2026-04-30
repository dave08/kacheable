package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer

internal object NoopBlockingKacheable : BlockingKacheable {
    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R =
        block()

    @ExperimentalKacheableApi
    override fun <R> invalidate(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        storage: CacheStorage,
        secondaryPatternPartArgs: List<CacheArgs>?,
        block: () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override fun <R> invalidateSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override fun <R> invalidateSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
        block: () -> R,
    ): R = block()

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()

    @ExperimentalKacheableApi
    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        cacheArgs: PrimarySecondaryCacheArgs,
        storage: CacheStorage,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()

    @ExperimentalKacheableApi
    override fun invokeSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: () -> Boolean,
    ): Boolean = block()

    @ExperimentalKacheableApi
    override fun <R : Any> invokeSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = block()

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()
}

fun BlockingKacheableNoOp(): BlockingKacheable = NoopBlockingKacheable
