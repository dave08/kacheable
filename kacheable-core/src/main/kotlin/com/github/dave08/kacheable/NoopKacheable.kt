package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer

internal object NoopKacheable : Kacheable {
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
        codec: CacheValueCodec<R>,
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
