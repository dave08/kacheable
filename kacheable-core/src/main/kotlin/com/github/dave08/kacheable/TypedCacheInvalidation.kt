@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: StringCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: HashMapCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: SetMembershipCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidateSetMembership(entryRef.name, entryRef.cacheArgs) {}
    }
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: SetMembershipCachePartRef) {
    partRefs.forEach { partRef ->
        invalidateSetMembership(partRef.name, partRef.cacheArgs) {}
    }
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    partRef: SetMembershipCachePartRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        val storageLayout = partRef.storageLayout
        if (storageLayout == null) {
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        } else {
            invalidate(partRef.name, partRef.cacheArgs, storageLayout, partRef.secondaryPatternPartArgs) {}
        }
    }
}

@ExperimentalKacheableApi
@Suppress("unused")
suspend fun <T> Kacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: suspend () -> T): T {
    invalidate(*partRefs)
    return block()
}
