package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheCorrelationProvider
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CachePartitionContext
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.LoadConcurrencySettings
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.PartitionCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.blocking.BlockingCachePartitionContext
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.BlockingPartitionCacheAccess
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorages
import com.github.dave08.kacheable.internal.storage.hash.BlockingHashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.hash.PartitionCacheRoutes
import com.github.dave08.kacheable.internal.storage.hash.PartitionLoaderAdmission
import com.github.dave08.kacheable.internal.storage.set.BlockingSetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.BlockingStringTypedStorage
import com.github.dave08.kacheable.store.CacheCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import com.github.dave08.kacheable.toTelemetryKind
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class BlockingKacheableImpl(
    private val storages: BlockingTypedStorages,
    private val jsonParser: Json,
    private val telemetryRuntime: CacheTelemetryRuntime,
    private val configs: Map<String, CacheConfig>,
    private val partitionRoutes: PartitionCacheRoutes,
) : BlockingKacheable, BlockingPartitionCacheAccess {
    constructor(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
        loadConcurrency: LoadConcurrencySettings,
        telemetry: CacheTelemetry = NoopCacheTelemetry,
        correlationProvider: CacheCorrelationProvider? = null,
    ) : this(
        store,
        configs,
        namingStrategy,
        jsonParser,
        loadConcurrency,
        CacheTelemetryRuntime(telemetry, correlationProvider),
    )

    private constructor(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        jsonParser: Json,
        loadConcurrency: LoadConcurrencySettings,
        telemetryRuntime: CacheTelemetryRuntime,
        loadCoordinator: BlockingLoadConcurrencyCoordinator = BlockingLoadConcurrencyCoordinator(loadConcurrency),
    ) : this(
        storages = createStorages(
            store,
            configs,
            namingStrategy,
            loadCoordinator,
            telemetryRuntime,
        ),
        jsonParser = jsonParser,
        telemetryRuntime = telemetryRuntime,
        configs = configs,
        partitionRoutes = PartitionCacheRoutes.create(
            configs, { BlockingPartitionStoreBridge(store) }, namingStrategy,
            CacheLoadCoordinator(CacheResilienceConfig(), LoadConcurrencySettings()),
            telemetryRuntime,
            PartitionLoaderAdmission { cacheName, group, observation, block ->
                loadCoordinator.withPermit(cacheName, group, observation) { runBlocking { block() } }
            },
        ),
    )

    override fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: (K, BlockingCachePartitionContext<K, V, P>) -> V,
    ): V = runBlocking {
        partitionRoutes.load(ref, cacheIf) { key, context -> block(key, context.blocking()) }
    }

    override fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: () -> V,
    ): V = partitionRoutes.loadBlocking(ref, cacheIf, block) {
        invoke(ref.entryRef, ref.returnView, cacheIf, block)
    }

    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R {
        keys.forEach { (name, _) ->
            require(configs[name]?.partition == null) {
                "Generation-checked caches require typed whole-partition or whole-cache invalidation."
            }
        }
        val started = if (telemetryRuntime.enabled) System.nanoTime() else 0L
        return try {
            storages.string.invalidate(*keys, block = block).also {
                keys.map { it.first }.distinct().forEach { cacheName ->
                    telemetryRuntime.maintenanceResult(
                        cacheName,
                        CacheStorageKind.String,
                        CacheMaintenanceOperation.InvalidateEntry,
                        CacheMaintenanceResult.Success,
                        started,
                    )
                }
            }
        } catch (t: Throwable) {
            keys.map { it.first }.distinct().forEach { cacheName ->
                telemetryRuntime.maintenanceResult(
                    cacheName,
                    CacheStorageKind.String,
                    CacheMaintenanceOperation.InvalidateEntry,
                    CacheMaintenanceResult.Failed,
                    started,
                )
            }
            throw t
        }
    }

    override fun invalidate(entryRef: StoredCacheEntryRef<*>) {
        require(configs[entryRef.name]?.partition == null) {
            "Generation-checked caches only support whole-partition or whole-cache invalidation."
        }
        telemetryRuntime.maintenanceBlocking(
            entryRef.name,
            entryRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateEntry,
        ) {
            @Suppress("UNCHECKED_CAST")
            (storages.any(entryRef.storage) as BlockingTypedStorage<CacheStorage>)
                .invalidate(entryRef as StoredCacheEntryRef<CacheStorage>)
        }
    }

    override fun invalidate(partRef: CacheEntryPartRef) {
        require(partRef.secondaryPatternPartArgs == null || configs[partRef.name]?.partition == null) {
            "Generation-checked caches only support whole-partition or whole-cache invalidation."
        }
        telemetryRuntime.maintenanceBlocking(
            partRef.name,
            partRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidatePart,
        ) {
            partitionRoutes.invalidateBlocking(partRef) {
                @Suppress("UNCHECKED_CAST")
                (storages.any(partRef.storage) as BlockingTypedStorage<CacheStorage>)
                    .invalidate(partRef as StoredCachePartRef<CacheStorage>)
            }
        }
    }

    override fun invalidate(allRef: StoredCacheAllRef<*>) {
        telemetryRuntime.maintenanceBlocking(
            allRef.name,
            allRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateAll,
        ) {
            partitionRoutes.invalidateBlocking(allRef) {
                @Suppress("UNCHECKED_CAST")
                (storages.any(allRef.storage) as BlockingTypedStorage<CacheStorage>)
                    .invalidate(allRef as StoredCacheAllRef<CacheStorage>)
            }
        }
    }

    override fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        telemetryRuntime.maintenanceBlocking(
            entryRef.name,
            entryRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateEntry,
        ) {
            storages.set.invalidate(entryRef, returnView)
        }
    }

    override fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        telemetryRuntime.maintenanceBlocking(
            partRef.name,
            partRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidatePart,
        ) {
            storages.set.invalidate(partRef, returnView)
        }
    }

    override fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R
    ): R {
        require(configs[name]?.partition == null) {
            "Generation-checked caches require indexed/hash partition entry references."
        }
        return storages.string.invoke(
            name = name,
            codec = cacheValueCodec(type, jsonParser),
            params = params,
            saveResultIf = cacheIf,
            block = block,
        )
    }

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        require(configs[entryRef.name]?.partition == null) {
            "Generation-checked caches require a typed partition entry reference."
        }
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as BlockingTypedStorage<S>).invoke(entryRef, returnView, cacheIf, block)
    }

    override fun <R> invoke(
        name: String,
        codec: CacheCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        require(configs[name]?.partition == null) {
            "Generation-checked caches require indexed/hash partition entry references."
        }
        return storages.string.invoke(
            name = name,
            codec = codec,
            params = params,
            saveResultIf = cacheIf,
            block = block,
        )
    }

    companion object {
        private fun createStorages(
            store: BlockingKacheableStore,
            configs: Map<String, CacheConfig>,
            namingStrategy: CacheNamingStrategy,
            loadCoordinator: BlockingLoadConcurrencyCoordinator,
            telemetryRuntime: CacheTelemetryRuntime,
        ): BlockingTypedStorages {
            configs.values.filter { it.partition != null }.forEach { config ->
                require(store is BlockingVersionedHashOperations) {
                    "Partition cache '${config.name}' requires a store that implements BlockingVersionedHashOperations."
                }
                require(config.resilience == null || config.resilience == CacheResilienceConfig()) {
                    "Partition cache '${config.name}' does not support coroutine resilience policies in the blocking runtime."
                }
            }
            return BlockingTypedStorages(
                string = BlockingStringTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
                hashMap = BlockingHashMapTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
                set = BlockingSetTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
            )
        }
    }
}

private fun <K, V, P : KeyPart<K>> CachePartitionContext<K, V, P>.blocking(): BlockingCachePartitionContext<K, V, P> {
    val context = this
    return object : BlockingCachePartitionContext<K, V, P>() {
        override val keyPart: P get() = context.keyPart
        override fun entry(key: K) = runBlocking { context.entry(key) }
        override fun entries(keys: Iterable<K>) = runBlocking { context.entries(keys) }
        override fun enumerateEntries(codec: CacheCodec<K>) = runBlocking { context.enumerateEntries(codec) }
        override fun enumerateKeys(codec: CacheCodec<K>) = runBlocking { context.enumerateKeys(codec) }
    }
}
