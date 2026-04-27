@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.HashMapCacheEntryRef
import com.github.dave08.kacheable.HashMapCacheReturn
import com.github.dave08.kacheable.IsMemberCacheReturn
import com.github.dave08.kacheable.SetMembershipCacheEntryRef
import com.github.dave08.kacheable.SetMembershipCachePartRef

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

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: HashMapCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.keyGroups, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: SetMembershipCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidateSetMembership(entryRef.name, entryRef.keyGroups) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: SetMembershipCachePartRef) {
    partRefs.forEach { partRef ->
        invalidateSetMembership(partRef.name, partRef.keyGroups) {}
    }
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.keyGroups, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    partRef: SetMembershipCachePartRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.keyGroups, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null) {
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        } else {
            invalidate(partRef.name, partRef.keyGroups, storageLayout) {}
        }
    }
}

@ExperimentalKacheableApi
@Suppress("unused")
fun <T> BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: () -> T): T {
    invalidate(*partRefs)
    return block()
}
