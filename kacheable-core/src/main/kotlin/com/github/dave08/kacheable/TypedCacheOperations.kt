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
    keyGroups = entryRef.keyGroups,
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
    keyGroups = entryRef.keyGroups,
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
    keyGroups = entryRef.keyGroups,
    values = returnsAs.values,
    valueName = returnsAs.valueName,
    saveResultIf = cacheIf,
    block = block,
)

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: HashMapCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.keyGroups, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: SetMembershipCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidateSetMembership(entryRef.name, entryRef.keyGroups) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: SetMembershipCachePartRef) {
    partRefs.forEach { partRef ->
        invalidateSetMembership(partRef.name, partRef.keyGroups) {}
    }
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.keyGroups, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    partRef: SetMembershipCachePartRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.keyGroups, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
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
suspend fun <T> Kacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: suspend () -> T): T {
    invalidate(*partRefs)
    return block()
}
