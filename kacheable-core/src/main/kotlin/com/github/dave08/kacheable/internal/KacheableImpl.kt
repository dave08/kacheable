@file:OptIn(kotlin.time.ExperimentalTime::class)


package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheManyRef
import com.github.dave08.kacheable.CacheLoadContext
import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheCorrelationProvider
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheSnapshotStore
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.LoadConcurrencySettings
import com.github.dave08.kacheable.NoopCacheSnapshotStore
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.PartitionCacheEntryRef
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.TypedStorages
import com.github.dave08.kacheable.internal.storage.hash.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.hash.PartitionCacheRoutes
import com.github.dave08.kacheable.internal.storage.set.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.StringTypedStorage
import com.github.dave08.kacheable.store.CacheCodec
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.VersionedHashOperations
import com.github.dave08.kacheable.store.cacheValueCodec
import com.github.dave08.kacheable.toTelemetryKind
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class KacheableImpl(
    store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
    private val jsonParser: Json,
    defaultResilience: CacheResilienceConfig,
    loadConcurrency: LoadConcurrencySettings,
    snapshotStore: CacheSnapshotStore = NoopCacheSnapshotStore,
    backgroundScope: CoroutineScope? = null,
    snapshotClock: Clock = Clock.System,
    telemetry: CacheTelemetry = NoopCacheTelemetry,
    correlationProvider: CacheCorrelationProvider? = null,
) : Kacheable, PartitionCacheAccess {
    init {
        validateConfiguration(store, configs, defaultResilience)
    }

    private val backgroundScopeProvider = BackgroundScopeProvider(backgroundScope)
    private val telemetryRuntime = CacheTelemetryRuntime(telemetry, correlationProvider)

    private val loadCoordinator = CacheLoadCoordinator(defaultResilience, loadConcurrency)
    private val partitionRoutes = PartitionCacheRoutes.create(configs, { store }, namingStrategy, loadCoordinator, telemetryRuntime)

    private val snapshotCoordinator = if (configs.values.any { it.snapshot != null }) {
        CacheSnapshotCoordinator(
            store = store,
            snapshotStore = snapshotStore,
            configs = configs,
            namingStrategy = namingStrategy,
            scope = backgroundScopeProvider.get(),
            clock = snapshotClock,
            telemetryRuntime = telemetryRuntime,
        )
    } else {
        null
    }

    private val storages: TypedStorages =
        createTypedStorages(
            store,
            configs,
            namingStrategy,
            loadCoordinator,
            snapshotCoordinator,
            backgroundScopeProvider::get,
            telemetryRuntime,
        )

    init {
        snapshotCoordinator?.start()
    }

    private val manyRuntime = CacheManyRuntime(
        store, configs, namingStrategy, loadCoordinator, telemetryRuntime, snapshotCoordinator, backgroundScopeProvider::get,
    )

    override suspend fun <K, V, P : KeyPart<K>> invoke(
        ref: CacheManyRef<K, V, P>,
        missPolicy: CacheMissPolicy<V>,
        refreshPolicy: CacheRefreshPolicy<V>,
        storeResultIf: (V) -> Boolean,
        block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
    ): Map<K, V> {
        if (ref.keys.isEmpty()) return emptyMap()
        val guarded = configs[ref.entry(ref.keys.first()).entryRef.name]?.partition != null
        if (guarded) {
            require(missPolicy is CacheMissPolicy.Load && missPolicy.fallbackOnFailure == null &&
                refreshPolicy is CacheRefreshPolicy.NeverRefresh) { "Guarded partitions do not support miss or refresh policies." }
            return checkNotNull(partitionRoutes.loadMany(ref, storeResultIf, block))
        }
        return manyRuntime.load(ref, missPolicy, refreshPolicy, storeResultIf, block)
    }

    override suspend fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend (K, CacheLoadContext<K, V, P>) -> V,
    ): V = partitionRoutes.load(ref, cacheIf, block)

    override suspend fun <K, V, P : KeyPart<K>> loadPartition(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend () -> V,
    ): V = partitionRoutes.load(ref, cacheIf, block) {
        invoke(ref.entryRef, ref.returnView, cacheIf, block)
    }

    private fun requireOrdinary(name: String) {
        require(configs[name]?.partition == null) {
            "GenerationChecked cache '$name' requires a typed partition entry call."
        }
    }

    override suspend fun <R> invalidate(
        vararg keys: Pair<String, List<Any>>,
        block: suspend () -> R,
    ): R {
        keys.forEach { requireOrdinary(it.first) }
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

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<*>) {
        telemetryRuntime.maintenance(
            entryRef.name,
            entryRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateEntry,
        ) {
            @Suppress("UNCHECKED_CAST")
            (storages.any(entryRef.storage) as TypedStorage<CacheStorage>)
                .invalidate(entryRef as StoredCacheEntryRef<CacheStorage>)
        }
    }

    override suspend fun invalidate(partRef: CacheEntryPartRef) {
        telemetryRuntime.maintenance(
            partRef.name,
            partRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidatePart,
        ) {
            partitionRoutes.invalidate(partRef) {
                @Suppress("UNCHECKED_CAST")
                (storages.any(partRef.storage) as TypedStorage<CacheStorage>)
                    .invalidate(partRef as StoredCachePartRef<CacheStorage>)
            }
        }
    }

    override suspend fun invalidate(allRef: StoredCacheAllRef<*>) {
        telemetryRuntime.maintenance(
            allRef.name,
            allRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateAll,
        ) {
            partitionRoutes.invalidate(allRef) {
                @Suppress("UNCHECKED_CAST")
                (storages.any(allRef.storage) as TypedStorage<CacheStorage>)
                    .invalidate(allRef as StoredCacheAllRef<CacheStorage>)
            }
        }
    }

    override suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        telemetryRuntime.maintenance(
            entryRef.name,
            entryRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateEntry,
        ) {
            storages.set.invalidate(entryRef, returnView)
        }
    }

    override suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        telemetryRuntime.maintenance(
            partRef.name,
            partRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidatePart,
        ) {
            storages.set.invalidate(partRef, returnView)
        }
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = run {
        requireOrdinary(name)
        storages.string.invoke(
            name = name,
            codec = cacheValueCodec(type, jsonParser),
            params = params,
            saveResultIf = cacheIf,
            block = block,
        )
    }

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R {
        requireOrdinary(entryRef.name)
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as TypedStorage<S>).invoke(
            entryRef = entryRef,
            returnView = returnView,
            missPolicy = missPolicy,
            refreshPolicy = refreshPolicy,
            storeResultIf = storeResultIf,
            block = block,
        )
    }

    override suspend fun <R> invoke(
        name: String,
        codec: CacheCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = run {
        requireOrdinary(name)
        storages.string.invoke(
            name = name,
            codec = codec,
            params = params,
            saveResultIf = cacheIf,
            block = block,
        )
    }

    companion object {
        private fun createTypedStorages(
            store: KacheableStore,
            configs: Map<String, CacheConfig>,
            namingStrategy: CacheNamingStrategy,
            loadCoordinator: CacheLoadCoordinator,
            snapshotCoordinator: CacheSnapshotCoordinator?,
            backgroundScope: () -> CoroutineScope,
            telemetryRuntime: CacheTelemetryRuntime,
        ): TypedStorages {
            return TypedStorages(
                string = StringTypedStorage(
                    store,
                    configs,
                    loadCoordinator,
                    namingStrategy,
                    snapshotCoordinator,
                    backgroundScope,
                    telemetryRuntime,
                ),
                hashMap = HashMapTypedStorage(
                    store,
                    configs,
                    loadCoordinator,
                    namingStrategy,
                    snapshotCoordinator,
                    backgroundScope,
                    telemetryRuntime,
                ),
                set = SetTypedStorage(
                    store,
                    configs,
                    loadCoordinator,
                    namingStrategy,
                    backgroundScope,
                    telemetryRuntime,
                ),
            )
        }
    }
}

private class BackgroundScopeProvider(
    private val provided: CoroutineScope?,
) {
    private val created: CoroutineScope by lazy {
        provided ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    fun get(): CoroutineScope = created
}

private fun validateConfiguration(
    store: KacheableStore,
    configs: Map<String, CacheConfig>,
    defaultResilience: CacheResilienceConfig,
) {
    configs.values.filter { it.partition != null }.forEach { config ->
        require(store is VersionedHashOperations) {
            "Partition cache '${config.name}' requires a store that implements VersionedHashOperations."
        }
        val resilience = config.resilience ?: defaultResilience
        require(!resilience.staleOnFailure && !resilience.staleOnTimeout) {
            "Partition cache '${config.name}' does not support stale fallback policies."
        }
    }
    val redisSingleFlightConfigured =
        defaultResilience.singleFlight == SingleFlightMode.Redis ||
            configs.values.any { it.resilience?.singleFlight == SingleFlightMode.Redis }

    if (redisSingleFlightConfigured && store !is DistributedSingleFlightStore) {
        throw IllegalArgumentException(
            "Redis single-flight requires a store that implements DistributedSingleFlightStore.",
        )
    }
}
