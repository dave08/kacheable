package  com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface Kacheable {
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    @ExperimentalKacheableApi
    suspend fun <R> invalidate(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        storageLayout: CacheStorageLayout,
        block: suspend () -> R,
    ): R

    @ExperimentalKacheableApi
    suspend fun <R> invalidateSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: suspend () -> R,
    ): R

    @ExperimentalKacheableApi
    suspend fun <R> invalidateSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
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
        cacheArgs: PrimarySecondaryCacheArgs,
        storageLayout: CacheStorageLayout,
        saveResultIf: (R) -> Boolean = { true },
        block: suspend () -> R
    ): R

    @ExperimentalKacheableApi
    suspend fun invokeSetMembership(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean = true,
        saveResultIf: (Boolean) -> Boolean = { true },
        block: suspend () -> Boolean,
    ): Boolean

    @ExperimentalKacheableApi
    suspend fun <R : Any> invokeSetClassification(
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean = { true },
        block: suspend () -> R,
    ): R

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
