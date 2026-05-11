package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.store.CacheValueCodec

@OptIn(ExperimentalKacheableApi::class)
internal interface TypedCacheRuntime {
    suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R

    suspend fun invalidate(entryRef: StoredCacheEntryRef<*>)

    suspend fun invalidate(partRef: CacheEntryPartRef)

    suspend fun invalidate(allRef: StoredCacheAllRef<*>)

    suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    suspend fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R

    suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R
}
