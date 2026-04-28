@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
suspend operator fun <R> Kacheable.invoke(
    entryRef: HashMapCacheEntryRef,
    returnsAs: HashMapCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: suspend () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun Kacheable.invoke(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: suspend () -> Boolean,
): Boolean = invokeSetMembership(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    cacheFalse = returnsAs.cacheFalse,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend operator fun <E : Enum<E>> Kacheable.invoke(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: suspend () -> E,
): E = invokeSetClassification(
    name = entryRef.name,
    cacheArgs = entryRef.cacheArgs,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)
