package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheCodec
import kotlinx.serialization.KSerializer

internal object NoopKacheable : Kacheable {
    override suspend fun <K, V, P : KeyPart<K>> invoke(
        ref: CacheManyRef<K, V, P>,
        missPolicy: CacheMissPolicy<V>,
        refreshPolicy: CacheRefreshPolicy<V>,
        storeResultIf: (V) -> Boolean,
        block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
    ): Map<K, V> {
        val keys = ref.keys.distinct()
        if (keys.isEmpty()) return emptyMap()
        if (missPolicy is CacheMissPolicy.LoadInBackground) return keys.associateWith { missPolicy.fallback() }
        val context = com.github.dave08.kacheable.internal.SelectedLoadContext<K, V, P>(ref.keyPart) { emptyMap() }
        val values = try {
            block(keys, context).toMap().also { result ->
                require(result.keys.all { it in keys }) { "Batch loader returned an unrequested key." }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            val fallback = (missPolicy as CacheMissPolicy.Load).fallbackOnFailure ?: throw failure
            return keys.associateWith { fallback(failure) }
        } finally { context.close() }
        val fallback = (missPolicy as CacheMissPolicy.Load).fallbackOnFailure
        return buildMap {
            for (key in keys) {
                if (values.containsKey(key)) put(key, values.getValue(key))
                else if (fallback != null) put(key, fallback(UnresolvedCacheKeyException(key)))
            }
        }
    }

    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        block()

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<*>) = Unit

    override suspend fun invalidate(partRef: CacheEntryPartRef) = Unit

    override suspend fun invalidate(allRef: StoredCacheAllRef<*>) = Unit

    override suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) = Unit

    override suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) = Unit

    override suspend fun <R> invoke(
        name: String,
        codec: CacheCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R = when (missPolicy) {
        is CacheMissPolicy.Load -> runCatching { block(null) }
            .getOrElse { error ->
                missPolicy.fallbackOnFailure?.invoke(error) ?: throw error
            }

        is CacheMissPolicy.LoadInBackground -> missPolicy.fallback()
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()
}

fun KacheableNoOp(): Kacheable = NoopKacheable
