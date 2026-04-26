package  com.github.dave08.kacheable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface Kacheable {
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    @ExperimentalKacheableApi
    suspend fun <R> invalidate(
        name: String,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        block: suspend () -> R,
    ): R

    @ExperimentalKacheableApi
    suspend fun <R> invalidateSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        block: suspend () -> R,
    ): R

    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R

    @ExperimentalKacheableApi
    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        keyGroups: CacheKeyGroups,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R

    @ExperimentalKacheableApi
    suspend fun invokeSetMembership(
        name: String,
        keyGroups: CacheKeyGroups,
        cacheFalse: Boolean = true,
        saveResultIf: (Boolean) -> Boolean = { true },
        block: suspend () -> Boolean,
    ): Boolean

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
