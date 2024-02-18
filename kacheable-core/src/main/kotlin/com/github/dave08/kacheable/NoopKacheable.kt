package com.github.dave08.kacheable

import kotlinx.serialization.KSerializer

internal object NoopKacheable : Kacheable {
    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        block()

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()
}

fun KacheableNoOp(): Kacheable = NoopKacheable