package  com.github.dave08.kacheable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface Kacheable {
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R
}

suspend inline operator fun <reified R> Kacheable.invoke(
    name: String,
    vararg params: Any,
    noinline saveResultIf: (R) -> Boolean = { true },
    noinline block: suspend () -> R
): R =
    invoke(name, serializer<R>(), *params, saveResultIf = saveResultIf, block = block)

suspend inline fun <reified R> Kacheable.cache(
    name: String,
    vararg params: Any,
    noinline shouldSaveResult: (R) -> Boolean = { true },
    noinline block: suspend () -> R
): R =
    invoke(name, serializer(), *params, saveResultIf = shouldSaveResult, block = block)
