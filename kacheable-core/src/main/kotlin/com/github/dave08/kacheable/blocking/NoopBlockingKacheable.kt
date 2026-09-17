package com.github.dave08.kacheable.blocking

import com.github.dave08.kacheable.CacheArgs
import com.github.dave08.kacheable.CacheManyRef
import com.github.dave08.kacheable.CacheEntry
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.store.CacheCodec
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.KSerializer

internal object NoopBlockingKacheable : BlockingKacheable {
    override fun <K, V, P : KeyPart<K>> invoke(
        ref: CacheManyRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: (List<K>, BlockingCacheLoadContext<K, V, P>) -> Map<K, V>,
    ): Map<K, V> {
        val keys = ref.keys.distinct()
        if (keys.isEmpty()) return emptyMap()
        val context = NoopBlockingLoadContext<K, V, P>(ref.keyPart)
        val values = try {
            block(keys, context).toMap().also { result ->
                require(result.keys.all { it in keys }) { "Batch loader returned an unrequested key." }
            }
        } finally {
            context.close()
        }
        return buildMap {
            keys.forEach { key ->
                if (values.containsKey(key)) put(key, values.getValue(key))
            }
        }
    }

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
        codec: CacheCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R
    ): R = block()

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R = block()

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R
    ): R = block()
}

private class NoopBlockingLoadContext<K, V, P : KeyPart<K>>(
    override val keyPart: P,
) : BlockingCacheLoadContext<K, V, P>() {
    private val active = AtomicBoolean(true)

    fun close() {
        active.set(false)
    }

    override fun entry(key: K): CacheEntry<V> {
        check(active.get()) { "Cache contexts are valid only during their loader attempt." }
        return CacheEntry.Missing
    }

    override fun entries(keys: Iterable<K>): Map<K, V> {
        check(active.get()) { "Cache contexts are valid only during their loader attempt." }
        return emptyMap()
    }
}

fun BlockingKacheableNoOp(): BlockingKacheable = NoopBlockingKacheable
