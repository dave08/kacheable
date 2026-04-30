@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.blocking.internal.BlockingTypedCacheRuntime

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: StoredCacheEntryRef<*>) {
    val runtime = this as BlockingTypedCacheRuntime
    entryRefs.forEach { runtime.invalidate(it) }
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    entryRef: StoredCacheEntryRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    (this as BlockingTypedCacheRuntime).invalidate(entryRef, returnsAs)
}

@ExperimentalKacheableApi
fun <E : Enum<E>> BlockingKacheable.invalidate(
    partRef: StoredCachePartRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    (this as BlockingTypedCacheRuntime).invalidate(partRef, returnsAs)
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    val runtime = this as BlockingTypedCacheRuntime
    partRefs.forEach { runtime.invalidate(it) }
}

@ExperimentalKacheableApi
@Suppress("unused")
fun <T> BlockingKacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: () -> T): T {
    invalidate(*partRefs)
    return block()
}
