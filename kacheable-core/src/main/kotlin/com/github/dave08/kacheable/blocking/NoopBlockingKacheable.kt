@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.internal.BlockingTypedCacheRuntime
import com.github.dave08.kacheable.store.CacheValueCodec
import kotlinx.serialization.KSerializer

internal object NoopBlockingKacheable : BlockingKacheable, BlockingTypedCacheRuntime {
    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R =
        block()

    override fun invalidate(entryRef: StoredCacheEntryRef<*>) = Unit

    override fun invalidate(partRef: CacheEntryPartRef) = Unit

    override fun invalidate(allRef: StoredCacheAllRef<*>) = Unit

    override fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) = Unit

    override fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) = Unit

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R = block()

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        saveResultIf: (R) -> Boolean,
        block: () -> R
    ): R = block()
}

fun BlockingKacheableNoOp(): BlockingKacheable = NoopBlockingKacheable
