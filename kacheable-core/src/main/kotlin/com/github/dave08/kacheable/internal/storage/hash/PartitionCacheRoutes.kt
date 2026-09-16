package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CachePartitionContext
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.PartitionCacheEntryRef
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.VersionedHashOperations
import kotlinx.coroutines.runBlocking

/** Resolves configured hash behavior once; ordinary calls keep their existing storage path. */
internal class PartitionCacheRoutes private constructor(
    private val guarded: Map<String, PartitionCacheRuntime>,
) {
    suspend fun <K, V, P : KeyPart<K>> load(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend () -> V,
        ordinary: suspend () -> V,
    ): V {
        val runtime = guarded[ref.entryRef.name] ?: return ordinary()
        return runtime.load(ref, cacheIf, block)
    }

    /** Ordinary blocking calls remain synchronous; only guarded loading needs the coroutine engine. */
    fun <K, V, P : KeyPart<K>> loadBlocking(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: () -> V,
        ordinary: () -> V,
    ): V {
        val runtime = guarded[ref.entryRef.name] ?: return ordinary()
        return runBlocking { runtime.load(ref, cacheIf, block = { block() }) }
    }

    suspend fun <K, V, P : KeyPart<K>> load(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend (K, CachePartitionContext<K, V, P>) -> V,
    ): V = requireNotNull(guarded[ref.entryRef.name]) {
        "Contextual partition loads require a partition policy."
    }.load(ref, cacheIf, block)

    companion object {
        fun create(
            configs: Map<String, CacheConfig>,
            store: () -> KacheableStore,
            namingStrategy: CacheNamingStrategy,
            coordinator: CacheLoadCoordinator,
            telemetry: CacheTelemetryRuntime,
            admission: PartitionLoaderAdmission? = null,
        ): PartitionCacheRoutes {
            val guardedConfigs = configs.filterValues { it.partition != null }
            if (guardedConfigs.isEmpty()) return PartitionCacheRoutes(emptyMap())
            val cacheStore = store()
            val backend = requireNotNull(cacheStore as? VersionedHashOperations) {
                "Partition contexts require VersionedHashOperations."
            }
            return PartitionCacheRoutes(guardedConfigs.mapValues { (_, config) ->
                PartitionCacheRuntime(cacheStore, config, backend, namingStrategy, coordinator, telemetry, admission)
            })
        }
    }
}
