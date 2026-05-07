@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@ExperimentalKacheableApi
sealed interface CacheStorage {
    data object String : CacheStorage, SupportsValueView, SupportsPrimaryKeyStorage
    data object HashMap : CacheStorage,
        SupportsValueView,
        SupportsMapView,
        SupportsPrimaryKeyStorage,
        SupportsPrimarySecondaryKeyStorage

    data object Set : CacheStorage,
        SupportsMembershipView,
        SupportsPrimaryKeyStorage,
        SupportsPrimarySecondaryKeyStorage
}

@ExperimentalKacheableApi
sealed interface CacheStorageCapability

@ExperimentalKacheableApi
sealed interface SupportsValueView : CacheStorageCapability

@ExperimentalKacheableApi
sealed interface SupportsMapView : CacheStorageCapability

@ExperimentalKacheableApi
sealed interface SupportsMembershipView : CacheStorageCapability

@ExperimentalKacheableApi
sealed interface SupportsPrimaryKeyStorage : CacheStorageCapability

@ExperimentalKacheableApi
sealed interface SupportsPrimarySecondaryKeyStorage : CacheStorageCapability

@ExperimentalKacheableApi
interface CacheReturn<R, C : CacheStorageCapability> {
    val serializer: KSerializer<R>
    val codec: CacheValueCodec<R>
}

@ExperimentalKacheableApi
interface HashMapCacheReturn<R> : CacheReturn<R, SupportsMapView>

@ExperimentalKacheableApi
interface SetCacheReturn<R> : CacheReturn<R, SupportsMembershipView>

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
data class MapCacheReturn<K : Any, R>(
    override val serializer: KSerializer<Map<K, R>>,
    override val codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer),
) : HashMapCacheReturn<Map<K, R>>

@ExperimentalKacheableApi
data class IsMemberCacheReturn(
    val cacheFalse: Boolean = true,
    override val serializer: KSerializer<Boolean> = serializer<Boolean>(),
    override val codec: CacheValueCodec<Boolean> = cacheValueCodec(serializer<Boolean>()),
) : SetCacheReturn<Boolean>

@ExperimentalKacheableApi
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

@ExperimentalKacheableApi
inline fun <reified R> value(
    codec: CacheValueCodec<R> = cacheValueCodec(serializer<R>()),
): ValueCacheReturn<R> = ValueCacheReturn(serializer<R>(), codec)

@PublishedApi
internal inline fun <reified R> lazyValue(): ValueCacheReturn<R> =
    ValueCacheReturn { serializer<R>() }

@ExperimentalKacheableApi
fun <R> value(
    serializer: KSerializer<R>,
    codec: CacheValueCodec<R> = cacheValueCodec(serializer),
): ValueCacheReturn<R> = ValueCacheReturn(serializer, codec)

@ExperimentalKacheableApi
inline fun <reified K : Any, reified R> map(
    codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer<Map<K, R>>()),
): MapCacheReturn<K, R> = MapCacheReturn(serializer<Map<K, R>>(), codec)

@ExperimentalKacheableApi
fun <K : Any, R> map(
    serializer: KSerializer<Map<K, R>>,
    codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer),
): MapCacheReturn<K, R> = MapCacheReturn(serializer, codec)

@ExperimentalKacheableApi
fun isMember(cacheFalse: Boolean = true): IsMemberCacheReturn = IsMemberCacheReturn(cacheFalse)

@ExperimentalKacheableApi
inline fun <reified E : Enum<E>> enumMember(
    values: List<E> = enumValues<E>().toList(),
    noinline valueName: (E) -> String = { it.name },
): EnumMemberCacheReturn<E> = EnumMemberCacheReturn(values, valueName, { serializer<E>() })
