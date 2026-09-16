package com.github.dave08.kacheable.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Source-compatible name for [CacheCodec]. Recompile clients when upgrading. */
typealias CacheValueCodec<R> = CacheCodec<R>

/** Compatibility factory for the shared serialization codec. */
fun <R> cacheValueCodec(serializer: KSerializer<R>, json: Json = Json): CacheValueCodec<R> =
    cacheCodec(serializer, json)

/** Compatibility factory that preserves String values exactly as supplied. */
fun rawStringCacheValueCodec(): CacheValueCodec<String> = rawStringCacheCodec()
