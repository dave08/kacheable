@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

@ExperimentalKacheableApi
sealed interface CacheStorageLayout {
    data object StringValue : CacheStorageLayout
    data object HashValue : CacheStorageLayout
}

@ExperimentalKacheableApi
sealed interface CacheStorage {
    data object String : CacheStorage, SupportsValueReturn
    data object HashMap : CacheStorage, SupportsValueReturn, SupportsMapReturn
    data object Set : CacheStorage
    data object List : CacheStorage
    data object Int : CacheStorage
}

@ExperimentalKacheableApi
sealed interface SupportsValueReturn

@ExperimentalKacheableApi
sealed interface SupportsMapReturn

@ExperimentalKacheableApi
interface CacheReturn<R> {
    val serializer: KSerializer<R>
    val codec: CacheValueCodec<R>
}

@ExperimentalKacheableApi
interface HashMapCacheReturn<R> : CacheReturn<R>

@ExperimentalKacheableApi
interface SetCacheReturn<R>

@ExperimentalKacheableApi
data class ValueCacheReturn<R>(
    override val serializer: KSerializer<R>,
    override val codec: CacheValueCodec<R> = cacheValueCodec(serializer),
) : HashMapCacheReturn<R>

@ExperimentalKacheableApi
data class MapCacheReturn<K : Any, R>(
    override val serializer: KSerializer<Map<K, R>>,
    override val codec: CacheValueCodec<Map<K, R>> = cacheValueCodec(serializer),
) : HashMapCacheReturn<Map<K, R>>

@ExperimentalKacheableApi
data class IsMemberCacheReturn(
    val cacheFalse: Boolean = true,
) : SetCacheReturn<Boolean>

@ExperimentalKacheableApi
data class EnumMemberCacheReturn<E : Enum<E>>(
    val values: List<E>,
    val valueName: (E) -> String = { it.name },
) : SetCacheReturn<E> {
    val valueNames: List<String> = values.map(valueName)
}

@ExperimentalKacheableApi
inline fun <reified R> value(
    codec: CacheValueCodec<R> = cacheValueCodec(serializer<R>()),
): ValueCacheReturn<R> = ValueCacheReturn(serializer<R>(), codec)

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
): EnumMemberCacheReturn<E> = EnumMemberCacheReturn(values, valueName)
