@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: StoredCacheEntryRef<*>) {
    entryRefs.forEach { entryRef ->
        when (entryRef.storage) {
            CacheStorage.Set -> invalidateSetMembership(entryRef.name, entryRef.cacheArgs) {}
            CacheStorage.String, CacheStorage.HashMap ->
                invalidate(entryRef.name, entryRef.cacheArgs, requireNotNull(entryRef.storageLayout)) {}
        }
    }
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    entryRef: StoredCacheEntryRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    partRef: StoredCachePartRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        if (partRef is StoredCachePartRef<*> && partRef.storage == CacheStorage.Set) {
            invalidateSetMembership(partRef.name, partRef.cacheArgs) {}
        } else if (partRef.storageLayout == null) {
            invalidate(partRef.name to partRef.args.toParamsArray().toList()) {}
        } else {
            invalidate(partRef.name, partRef.cacheArgs, requireNotNull(partRef.storageLayout), partRef.secondaryPatternPartArgs) {}
        }
    }
}

@ExperimentalKacheableApi
@Suppress("unused")
suspend fun <T> Kacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: suspend () -> T): T {
    invalidate(*partRefs)
    return block()
}
