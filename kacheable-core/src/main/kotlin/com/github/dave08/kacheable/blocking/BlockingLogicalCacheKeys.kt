@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheInvalidationRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.LogicalCacheEntryRef
import com.github.dave08.kacheable.LogicalCachePartRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.internal.BlockingTypedCacheRuntime

@ExperimentalKacheableApi
operator fun <R> BlockingKacheable.invoke(
    entryRef: LogicalCacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = (this as BlockingTypedCacheRuntime).invoke(entryRef.entryRef, entryRef.returnsAs, cacheIf, block)

@ExperimentalKacheableApi
fun <R> BlockingKacheable.cache(
    entryRef: LogicalCacheEntryRef<R>,
    cacheIf: (R) -> Boolean = { true },
    block: () -> R,
): R = invoke(entryRef, cacheIf, block)

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg entryRefs: LogicalCacheEntryRef<*>) {
    val runtime = this as BlockingTypedCacheRuntime
    entryRefs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
fun <R> BlockingKacheable.invalidate(
    vararg entryRefs: LogicalCacheEntryRef<*>,
    block: () -> R,
): R {
    val result = block()
    invalidate(*entryRefs)
    return result
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg partRefs: LogicalCachePartRef<*>) {
    val runtime = this as BlockingTypedCacheRuntime
    partRefs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
fun <R> BlockingKacheable.invalidate(
    vararg partRefs: LogicalCachePartRef<*>,
    block: () -> R,
): R {
    val result = block()
    invalidate(*partRefs)
    return result
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(vararg refs: CacheInvalidationRef) {
    val runtime = this as BlockingTypedCacheRuntime
    refs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
fun BlockingKacheable.invalidate(refs: Iterable<CacheInvalidationRef>) {
    val runtime = this as BlockingTypedCacheRuntime
    refs.forEach { runtime.invalidateLogical(it) }
}

@ExperimentalKacheableApi
fun <R> BlockingKacheable.invalidate(
    vararg refs: CacheInvalidationRef,
    block: () -> R,
): R {
    val result = block()
    invalidate(*refs)
    return result
}

@ExperimentalKacheableApi
fun <R> BlockingKacheable.invalidate(
    refs: Iterable<CacheInvalidationRef>,
    block: () -> R,
): R {
    val result = block()
    invalidate(refs)
    return result
}

@Suppress("UNCHECKED_CAST")
private fun BlockingTypedCacheRuntime.invalidateLogical(entryRef: LogicalCacheEntryRef<*>) {
    val returnsAs = entryRef.returnsAs
    if (entryRef.entryRef.storage == CacheStorage.Set && returnsAs is EnumMemberCacheReturn<*>) {
        invalidate(entryRef.entryRef as StoredCacheEntryRef<CacheStorage.Set>, returnsAs as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(entryRef.entryRef)
    }
}

@Suppress("UNCHECKED_CAST")
private fun BlockingTypedCacheRuntime.invalidateLogical(partRef: LogicalCachePartRef<*>) {
    val returnsAs = partRef.returnsAs
    if (partRef.partRef.storage == CacheStorage.Set && returnsAs is EnumMemberCacheReturn<*>) {
        invalidate(partRef.partRef as StoredCachePartRef<CacheStorage.Set>, returnsAs as EnumMemberCacheReturn<Any>)
    } else {
        invalidate(partRef.partRef)
    }
}

private fun BlockingTypedCacheRuntime.invalidateLogical(ref: CacheInvalidationRef) {
    when (ref) {
        is LogicalCacheEntryRef<*> -> invalidateLogical(ref)
        is LogicalCachePartRef<*> -> invalidateLogical(ref)
    }
}
