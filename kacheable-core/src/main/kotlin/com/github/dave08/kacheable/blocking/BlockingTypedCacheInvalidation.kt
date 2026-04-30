@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.StoredCacheEntryRef

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: StoredCacheEntryRef<*>) {
    entryRefs.forEach { entryRef ->
        when (entryRef.storage) {
            CacheStorage.Set -> invalidateSetMembership(entryRef.name, entryRef.cacheArgs) {}
            CacheStorage.String, CacheStorage.HashMap ->
                invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storage) {}
        }
    }
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    entryRef: StoredCacheEntryRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    partRef: StoredCachePartRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    partRefs.forEach { partRef ->
        if (partRef.storage == CacheStorage.Set) {
            invalidateSetMembership(partRef.name, partRef.cacheArgs) {}
        } else {
            invalidate(partRef.name, partRef.cacheArgs, partRef.storage, partRef.secondaryPatternPartArgs) {}
        }
    }
}

@ExperimentalKacheableApi
@Suppress("unused")
fun <T> BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: () -> T): T {
    invalidate(*partRefs)
    return block()
}
