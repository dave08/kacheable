package com.github.dave08.kacheable.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Reversible encoding for cached values or enumerable logical keys. */
interface CacheCodec<T> {
    fun encode(value: T): String
    fun decode(value: String): T
}

/** Standard serialization shared by values and enumerable key metadata. */
fun <T> cacheCodec(serializer: KSerializer<T>, json: Json = Json): CacheCodec<T> =
    KotlinxCacheCodec(serializer, json)

fun rawStringCacheCodec(): CacheCodec<String> = RawStringCacheCodec

private class KotlinxCacheCodec<T>(
    private val serializer: KSerializer<T>,
    private val json: Json,
) : CacheCodec<T> {
    override fun encode(value: T): String = json.encodeToString(serializer, value)
    override fun decode(value: String): T = json.decodeFromString(serializer, value)
}

private data object RawStringCacheCodec : CacheCodec<String> {
    override fun encode(value: String): String = value
    override fun decode(value: String): String = value
}
