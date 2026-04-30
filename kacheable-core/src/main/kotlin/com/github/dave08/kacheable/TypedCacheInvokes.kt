@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
suspend operator fun <S, R> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: ValueCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R where S : CacheStorage, S : SupportsValueReturn = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storage = entryRef.storage,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun <S, K : Any, R> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: MapCacheReturn<K, R>,
    cacheIf: (Map<K, R>) -> Boolean = { true },
    block: suspend () -> Map<K, R>,
): Map<K, R> where S : CacheStorage, S : SupportsMapReturn = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storage = entryRef.storage,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun <S> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: suspend () -> Boolean,
): Boolean where S : CacheStorage, S : SupportsMembershipReturn = invokeSetMembership(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    cacheFalse = returnsAs.cacheFalse,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun <S, E : Enum<E>> Kacheable.invoke(
    entryRef: StoredCacheEntryRef<S>,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: suspend () -> E,
): E where S : CacheStorage, S : SupportsMembershipReturn = invokeSetClassification(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)
