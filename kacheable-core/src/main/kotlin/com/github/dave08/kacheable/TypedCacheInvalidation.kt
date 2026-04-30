@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.TypedCacheRuntime

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg entryRefs: StoredCacheEntryRef<*>) {
    val runtime = this as TypedCacheRuntime
    entryRefs.forEach { runtime.invalidate(it) }
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    entryRef: StoredCacheEntryRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    (this as TypedCacheRuntime).invalidate(entryRef, returnsAs)
}

@ExperimentalKacheableApi
suspend fun <E : Enum<E>> Kacheable.invalidate(
    partRef: StoredCachePartRef<CacheStorage.Set>,
    returnsAs: EnumMemberCacheReturn<E>,
) {
    (this as TypedCacheRuntime).invalidate(partRef, returnsAs)
}

@ExperimentalKacheableApi
suspend fun Kacheable.invalidate(vararg partRefs: CacheEntryPartRef) {
    val runtime = this as TypedCacheRuntime
    partRefs.forEach { runtime.invalidate(it) }
}

@ExperimentalKacheableApi
@Suppress("unused")
suspend fun <T> Kacheable.invalidate(vararg partRefs: CacheEntryPartRef, block: suspend () -> T): T {
    invalidate(*partRefs)
    return block()
}
