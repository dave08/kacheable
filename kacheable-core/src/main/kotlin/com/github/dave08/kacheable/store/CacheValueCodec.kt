package com.github.dave08.kacheable.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface CacheValueCodec<R> {
    fun encode(value: R): String

    fun decode(value: String): R
}

fun <R> cacheValueCodec(
    serializer: KSerializer<R>,
    json: Json = Json,
): CacheValueCodec<R> = KotlinxCacheValueCodec(serializer, json)

fun rawStringCacheValueCodec(): CacheValueCodec<String> = RawStringCacheValueCodec

private class KotlinxCacheValueCodec<R>(
    private val serializer: KSerializer<R>,
    private val json: Json,
) : CacheValueCodec<R> {
    override fun encode(value: R): String = json.encodeToString(serializer, value)

    override fun decode(value: String): R = json.decodeFromString(serializer, value)
}

private data object RawStringCacheValueCodec : CacheValueCodec<String> {
    override fun encode(value: String): String = value

    override fun decode(value: String): String = value
}
