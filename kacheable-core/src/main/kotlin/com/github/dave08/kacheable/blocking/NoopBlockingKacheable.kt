package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheValueCodec
import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.ExperimentalKacheableApi
import kotlinx.serialization.KSerializer

internal object NoopBlockingKacheable : BlockingKacheable {
    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R =
        block()

    @ExperimentalKacheableApi
    override fun <R> invalidate(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        block: () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override fun <R> invalidateSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        block: () -> R,
    ): R = block()

    @ExperimentalKacheableApi
    override fun <R> invalidateSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
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
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()

    @ExperimentalKacheableApi
    override fun invokeSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: () -> Boolean,
    ): Boolean = block()

    @ExperimentalKacheableApi
    override fun <R : Any> invokeSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
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
