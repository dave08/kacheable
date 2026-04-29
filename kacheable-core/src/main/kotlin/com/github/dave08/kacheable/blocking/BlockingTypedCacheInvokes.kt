@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.HashMapCacheEntryRef
import com.github.dave08.kacheable.HashMapCacheReturn
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.SetMembershipCacheEntryRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StringCacheEntryRef
import com.github.dave08.kacheable.ValueCacheReturn

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: StoredCacheEntryRef<*>,
    returnsAs: CacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: StringCacheEntryRef,
    returnsAs: ValueCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
    storageLayout = entryRef.storageLayout,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: HashMapCacheEntryRef,
    returnsAs: HashMapCacheReturn<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(
    name = entryRef.name,
    codec = returnsAs.codec,
    cacheArgs = entryRef.cacheArgs,
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
    cacheArgs = entryRef.cacheArgs,
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
    cacheArgs = entryRef.cacheArgs,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)
