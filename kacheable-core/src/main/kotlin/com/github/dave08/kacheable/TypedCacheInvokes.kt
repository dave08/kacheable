@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.TypedCacheRuntime

@ExperimentalKacheableApi
suspend operator fun <S, R> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: ValueCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R where S : CacheStorage, S : SupportsValueView =
    (this as TypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
suspend operator fun <S, K : Any, R> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: MapCacheReturn<K, R>,
    cacheIf: (Map<K, R>) -> Boolean = { true },
    block: suspend () -> Map<K, R>,
): Map<K, R> where S : CacheStorage, S : SupportsMapView =
    (this as TypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
suspend operator fun <S> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: suspend () -> Boolean,
): Boolean where S : CacheStorage, S : SupportsMembershipView =
    (this as TypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)

@ExperimentalKacheableApi
suspend operator fun <S, E : Enum<E>> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: suspend () -> E,
): E where S : CacheStorage, S : SupportsMembershipView =
    (this as TypedCacheRuntime).invoke(entryRef, returnsAs, cacheIf, block)
