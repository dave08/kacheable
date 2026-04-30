@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.MapCacheReturn
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.SupportsMembershipReturn
import com.github.dave08.kacheable.SupportsMapReturn
import com.github.dave08.kacheable.SupportsValueReturn
import com.github.dave08.kacheable.ValueCacheReturn

@ExperimentalKacheableApi
operator fun <S, R> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: ValueCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R where S : CacheStorage, S : SupportsValueReturn = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storageLayout = requireNotNull(entryRef.storageLayout) {
        "This storage does not support value-style cache returns."
    },
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <S, K : Any, R> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: MapCacheReturn<K, R>,
    cacheIf: (Map<K, R>) -> Boolean = { true },
    block: () -> Map<K, R>,
): Map<K, R> where S : CacheStorage, S : SupportsMapReturn = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storageLayout = requireNotNull(entryRef.storageLayout) {
        "This storage does not support map-style cache returns."
    },
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <S> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: () -> Boolean,
): Boolean where S : CacheStorage, S : SupportsMembershipReturn = invokeSetMembership(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    cacheFalse = returnsAs.cacheFalse,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <S, E : Enum<E>> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: () -> E,
): E where S : CacheStorage, S : SupportsMembershipReturn = invokeSetClassification(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)
