package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheKeyGroups
import com.github.dave08.kacheable.CacheStorageLayout
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface BlockingKacheable {
    fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R

    @ExperimentalKacheableApi
    fun <R> invalidate(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        block: () -> R,
    ): R

    @ExperimentalKacheableApi
    fun <R> invalidateSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        block: () -> R,
    ): R

    @ExperimentalKacheableApi
    fun <R> invalidateSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        valueNames: List<String>,
        block: () -> R,
    ): R

    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean = { true },
        block: () -> R
    ): R

    @ExperimentalKacheableApi
    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean = { true },
        block: () -> R
    ): R

    @ExperimentalKacheableApi
    fun invokeSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        cacheFalse: Boolean = true,
        saveResultIf: (Boolean) -> Boolean = { true },
        block: () -> Boolean,
    ): Boolean

    @ExperimentalKacheableApi
    fun <R : Any> invokeSetClassification(
        name: String,
        keyGroups: CacheKeyGroups,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean = { true },
        block: () -> R,
    ): R

    fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean = { true },
        block: () -> R
    ): R
}

inline operator fun <reified R> BlockingKacheable.invoke(
    name: String,
    vararg params: Any,
    noinline saveResultIf: (R) -> Boolean = { true },
    noinline block: () -> R
): R =
    invoke(name, serializer<R>(), *params, saveResultIf = saveResultIf, block = block)

inline fun <reified R> BlockingKacheable.cache(
    name: String,
    vararg params: Any,
    noinline shouldSaveResult: (R) -> Boolean = { true },
    noinline block: () -> R
): R =
    invoke(name, serializer(), *params, saveResultIf = shouldSaveResult, block = block)
