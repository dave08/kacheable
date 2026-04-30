@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.MapCacheReturn
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.SupportsMembershipView
import com.github.dave08.kacheable.SupportsMapView
import com.github.dave08.kacheable.SupportsValueView
import com.github.dave08.kacheable.ValueCacheReturn
import com.github.dave08.kacheable.blocking.internal.BlockingTypedCacheRuntime

@ExperimentalKacheableApi
operator fun <S, R> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: ValueCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R where S : CacheStorage, S : SupportsValueView =
    (this as BlockingTypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
operator fun <S, K : Any, R> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: MapCacheReturn<K, R>,
    cacheIf: (Map<K, R>) -> Boolean = { true },
    block: () -> Map<K, R>,
): Map<K, R> where S : CacheStorage, S : SupportsMapView =
    (this as BlockingTypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
operator fun <S> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: () -> Boolean,
): Boolean where S : CacheStorage, S : SupportsMembershipView =
    (this as BlockingTypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
operator fun <S, E : Enum<E>> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: () -> E,
): E where S : CacheStorage, S : SupportsMembershipView =
    (this as BlockingTypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)
