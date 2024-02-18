package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.Kacheable
import kotlinx.serialization.KSerializer

internal object NoopBlockingKacheable : BlockingKacheable {
    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R =
        block()

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()
}

fun BlockingKacheableNoOp(): BlockingKacheable = NoopBlockingKacheable