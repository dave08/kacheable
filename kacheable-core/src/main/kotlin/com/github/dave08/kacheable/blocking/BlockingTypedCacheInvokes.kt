@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.HashMapCacheEntryRef
import com.github.dave08.kacheable.HashMapCacheReturn
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.SetMembershipCacheEntryRef

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: HashMapCacheEntryRef,
    returnsAs: HashMapCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    keyGroups = entryRef.keyGroups,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun BlockingKacheable.invoke(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: IsMemberCacheReturn,
    cacheIf: (Boolean) -> Boolean = { true },
    block: () -> Boolean,
): Boolean = invokeSetMembership(
    name = entryRef.name,
    keyGroups = entryRef.keyGroups,
    cacheFalse = returnsAs.cacheFalse,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <E : Enum<E>> BlockingKacheable.invoke(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
    cacheIf: (E) -> Boolean = { true },
    block: () -> E,
): E = invokeSetClassification(
    name = entryRef.name,
    keyGroups = entryRef.keyGroups,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)
