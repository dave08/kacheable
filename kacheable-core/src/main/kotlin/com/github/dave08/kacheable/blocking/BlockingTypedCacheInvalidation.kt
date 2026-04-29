@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.HashMapCacheEntryRef
import com.github.dave08.kacheable.SetMembershipCacheEntryRef
import com.github.dave08.kacheable.SetMembershipCachePartRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StringCacheEntryRef

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: StringCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: StoredCacheEntryRef<*>) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: HashMapCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidate(entryRef.name, entryRef.cacheArgs, entryRef.storageLayout) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: SetMembershipCacheEntryRef) {
    entryRefs.forEach { entryRef ->
        invalidateSetMembership(entryRef.name, entryRef.cacheArgs) {}
    }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: SetMembershipCachePartRef) {
    partRefs.forEach { partRef ->
        invalidateSetMembership(partRef.name, partRef.cacheArgs) {}
    }
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    entryRef: SetMembershipCacheEntryRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(entryRef.name, entryRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    partRef: SetMembershipCachePartRef,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    invalidateSetClassification(partRef.name, partRef.cacheArgs, returnsAs.valueNames) {}
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
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
fun <T> BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: () -> T): T {
    invalidate(*partRefs)
    return block()
}
