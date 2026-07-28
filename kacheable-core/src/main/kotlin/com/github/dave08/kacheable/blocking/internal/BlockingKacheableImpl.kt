package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheCorrelationProvider
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.LoadConcurrencySettings
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.toTelemetryKind
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorage
import com.github.dave08.kacheable.internal.storage.BlockingTypedStorages
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.storage.hash.BlockingHashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.BlockingSetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.BlockingStringTypedStorage
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class BlockingKacheableImpl(
    private val storages: BlockingTypedStorages,
    private val jsonParser: Json,
    private val telemetryRuntime: CacheTelemetryRuntime,
) : BlockingKacheable {
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
    ) : this(
        storages = createStorages(
            store,
            configs,
            namingStrategy,
            BlockingLoadConcurrencyCoordinator(loadConcurrency),
            telemetryRuntime,
        ),
        jsonParser = jsonParser,
        telemetryRuntime = telemetryRuntime,
    )

    override fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: () -> R): R {
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
        telemetryRuntime.maintenanceBlocking(
            partRef.name,
            partRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidatePart,
        ) {
            @Suppress("UNCHECKED_CAST")
            (storages.any(partRef.storage) as BlockingTypedStorage<CacheStorage>)
                .invalidate(partRef as StoredCachePartRef<CacheStorage>)
        }
    }

    override fun invalidate(allRef: StoredCacheAllRef<*>) {
        telemetryRuntime.maintenanceBlocking(
            allRef.name,
            allRef.storage.toTelemetryKind(),
            CacheMaintenanceOperation.InvalidateAll,
        ) {
            @Suppress("UNCHECKED_CAST")
            (storages.any(allRef.storage) as BlockingTypedStorage<CacheStorage>)
                .invalidate(allRef as StoredCacheAllRef<CacheStorage>)
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
    ): R = storages.string.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    override fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        @Suppress("UNCHECKED_CAST")
        return (storages.any(entryRef.storage) as BlockingTypedStorage<S>).invoke(entryRef, returnView, cacheIf, block)
    }

    override fun <R> invoke(
        name: String,
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: () -> R,
    ): R = storages.string.invoke(
        name = name,
        codec = codec,
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    companion object {
        private fun createStorages(
            store: BlockingKacheableStore,
            configs: Map<String, CacheConfig>,
            namingStrategy: CacheNamingStrategy,
            loadCoordinator: BlockingLoadConcurrencyCoordinator,
            telemetryRuntime: CacheTelemetryRuntime,
        ): BlockingTypedStorages {
            return BlockingTypedStorages(
                string = BlockingStringTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
                hashMap = BlockingHashMapTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
                set = BlockingSetTypedStorage(store, configs, namingStrategy, loadCoordinator, telemetryRuntime),
            )
        }
    }
}
