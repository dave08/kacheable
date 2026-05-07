package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.TypedCacheRuntime
import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer

internal object NoopKacheable : Kacheable, TypedCacheRuntime {
    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        block()

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<*>) = Unit

    override suspend fun invalidate(partRef: CacheEntryPartRef) = Unit

    override suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) = Unit

    override suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnsAs: EnumMemberCacheReturn<E>,
    ) = Unit

    override suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnsAs: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = block()

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = block()
}

fun KacheableNoOp(): Kacheable = NoopKacheable
