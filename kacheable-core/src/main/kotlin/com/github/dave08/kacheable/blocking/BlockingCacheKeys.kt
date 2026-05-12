package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheInvalidationRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.CacheAllRef
import com.github.dave08.kacheable.CacheEntryRef
import com.github.dave08.kacheable.CachePartRef
import com.github.dave08.kacheable.RawCacheEntryRef
import com.github.dave08.kacheable.RawCacheRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.internal.BlockingTypedCacheRuntime

/**
 * Caches a typed cache entry, returning cached data when present or [block]'s result otherwise.
 *
 * [cacheIf] is evaluated only for newly computed results.
 */
operator fun <R> BlockingKacheable.invoke(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = (this as BlockingTypedCacheRuntime).invoke(entryRef.entryRef, entryRef.returnView, cacheIf, block)

/**
 * Named equivalent of invoking a typed cache entry.
 */
fun <R> BlockingKacheable.cache(
    entryRef: CacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(entryRef, cacheIf, block)

/**
 * Invalidates one or more typed cache entries.
 */
fun BlockingKacheable.invalidate(vararg entryRefs: CacheEntryRef<*>) {
    val runtime = this as BlockingTypedCacheRuntime
    entryRefs.forEach { runtime.invalidateCacheRef(it) }
}

fun <R> BlockingKacheable.invalidate(
    vararg entryRefs: CacheEntryRef<*>,
    block: () -> R,
): R {
    val result = block()
    invalidate(*entryRefs)
    return result
}

fun BlockingKacheable.invalidate(vararg partRefs: CachePartRef<*>) {
    val runtime = this as BlockingTypedCacheRuntime
    partRefs.forEach { runtime.invalidateCacheRef(it) }
}

fun <R> BlockingKacheable.invalidate(
    vararg partRefs: CachePartRef<*>,
    block: () -> R,
): R {
    val result = block()
    invalidate(*partRefs)
    return result
}

fun BlockingKacheable.invalidate(vararg refs: CacheInvalidationRef) {
    val runtime = this as BlockingTypedCacheRuntime
    refs.forEach { runtime.invalidateCacheRef(it) }
}

fun BlockingKacheable.invalidate(refs: Iterable<CacheInvalidationRef>) {
    val runtime = this as BlockingTypedCacheRuntime
    refs.forEach { runtime.invalidateCacheRef(it) }
}

fun <R> BlockingKacheable.invalidate(
    vararg refs: CacheInvalidationRef,
    block: () -> R,
): R {
    val result = block()
    invalidate(*refs)
    return result
}

fun <R> BlockingKacheable.invalidate(
    refs: Iterable<CacheInvalidationRef>,
    block: () -> R,
): R {
    val result = block()
    invalidate(refs)
    return result
}

@Suppress("UNCHECKED_CAST")
private fun BlockingTypedCacheRuntime.invalidateCacheRef(entryRef: CacheEntryRef<*>) {
    val returnView = entryRef.returnView
    if (entryRef.entryRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(entryRef.entryRef as StoredCacheEntryRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(entryRef.entryRef)
    }
}

@Suppress("UNCHECKED_CAST")
private fun BlockingTypedCacheRuntime.invalidateCacheRef(partRef: CachePartRef<*>) {
    val returnView = partRef.returnView
    if (partRef.partRef.storage == CacheStorage.Set && returnView is EnumMemberCacheReturn<*>) {
        invalidate(partRef.partRef as StoredCachePartRef<CacheStorage.Set>, returnView as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(partRef.partRef)
    }
}

private fun BlockingTypedCacheRuntime.invalidateCacheRef(allRef: CacheAllRef<*>) {
    invalidate(allRef.allRef)
}

private fun BlockingTypedCacheRuntime.invalidateCacheRef(ref: CacheInvalidationRef) {
    when (ref) {
        is RawCacheEntryRef -> invalidate(ref.entryRef)
        is RawCacheRef -> invalidate(ref.allRef)
        is CacheEntryRef<*> -> invalidateCacheRef(ref)
        is CachePartRef<*> -> invalidateCacheRef(ref)
        is CacheAllRef<*> -> invalidateCacheRef(ref)
    }
}
