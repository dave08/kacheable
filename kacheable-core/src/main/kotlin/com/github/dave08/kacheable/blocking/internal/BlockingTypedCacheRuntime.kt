package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.store.CacheValueCodec

internal interface BlockingTypedCacheRuntime {
    fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R

    fun invalidate(entryRef: StoredCacheEntryRef<*>)

    fun invalidate(partRef: CacheEntryPartRef)

    fun invalidate(allRef: StoredCacheAllRef<*>)

    fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    )

    fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R

    fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R
}
