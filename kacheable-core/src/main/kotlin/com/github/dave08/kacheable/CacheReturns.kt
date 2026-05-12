package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Storage backend selected for a typed cache key.
 */
sealed interface CacheStorage {
    data object String : CacheStorage, SupportsValueView, SupportsPrimaryKeyStorage
    data object HashMap : CacheStorage,
        SupportsValueView,
        SupportsPrimaryKeyStorage,
        SupportsPrimarySecondaryKeyStorage

    data object Set : CacheStorage,
        SupportsMembershipView,
        SupportsPrimaryKeyStorage,
        SupportsPrimarySecondaryKeyStorage
}

sealed interface CacheStorageCapability

sealed interface SupportsValueView : CacheStorageCapability

sealed interface SupportsMembershipView : CacheStorageCapability

sealed interface SupportsPrimaryKeyStorage : CacheStorageCapability

sealed interface SupportsPrimarySecondaryKeyStorage : CacheStorageCapability

/**
 * Describes how a cached result is represented when using a selected storage backend.
 */
interface CacheReturn<R, C : CacheStorageCapability> {
    val serializer: KSerializer<R>
    val codec: CacheValueCodec<R>
}

interface SetCacheReturn<R> : CacheReturn<R, SupportsMembershipView>

/**
 * Serialized value return view.
 */
class ValueCacheReturn<R> : CacheReturn<R, SupportsValueView> {
    private val serializerProvider: () -> KSerializer<R>
    private val codecProvider: (KSerializer<R>) -> CacheValueCodec<R>

    constructor(
        serializer: KSerializer<R>,
        codec: CacheValueCodec<R> = cacheValueCodec(serializer),
    ) {
        serializerProvider = { serializer }
        codecProvider = { codec }
    }

    @PublishedApi
    internal constructor(
        serializerProvider: () -> KSerializer<R>,
    ) {
        this.serializerProvider = serializerProvider
        codecProvider = { serializer -> cacheValueCodec(serializer) }
    }

    override val serializer: KSerializer<R> by lazy { serializerProvider() }
    override val codec: CacheValueCodec<R> by lazy { codecProvider(serializer) }
}

/**
 * Boolean membership return view backed by set membership.
 */
data class IsMemberCacheReturn(
    val cacheFalse: Boolean = true,
    override val serializer: KSerializer<Boolean> = serializer<Boolean>(),
    override val codec: CacheValueCodec<Boolean> = cacheValueCodec(serializer<Boolean>()),
) : SetCacheReturn<Boolean>

/**
 * Enum membership return view backed by one set per enum value.
 */
class EnumMemberCacheReturn<E : Any>(
    val values: List<E>,
    val valueName: (E) -> String,
    private val serializerProvider: () -> KSerializer<E>,
    private val codecProvider: (KSerializer<E>) -> CacheValueCodec<E> = { serializer -> cacheValueCodec(serializer) },
) : SetCacheReturn<E> {
    constructor(
        values: List<E>,
        valueName: (E) -> String,
        serializer: KSerializer<E>,
        codec: CacheValueCodec<E> = cacheValueCodec(serializer),
    ) : this(values, valueName, { serializer }, { codec })

    override val serializer: KSerializer<E> by lazy(serializerProvider)
    override val codec: CacheValueCodec<E> by lazy { codecProvider(serializer) }
    val valueNames: List<String> = values.map(valueName)
}

@PublishedApi
internal inline fun <reified R> lazyValue(): ValueCacheReturn<R> =
    ValueCacheReturn { serializer<R>() }
