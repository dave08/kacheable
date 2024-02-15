package com.github.dave08.kacheable.blocking

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface BlockingKacheable {
    fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R

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