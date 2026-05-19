@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheEntryPartRef
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheReturn
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheSnapshotStore
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.EnumMemberCacheReturn
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.NoopCacheSnapshotStore
import com.github.dave08.kacheable.StoredCacheEntryRef
import com.github.dave08.kacheable.StoredCacheAllRef
import com.github.dave08.kacheable.StoredCachePartRef
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.internal.storage.TypedStorage
import com.github.dave08.kacheable.internal.storage.TypedStorages
import com.github.dave08.kacheable.internal.storage.hash.HashMapTypedStorage
import com.github.dave08.kacheable.internal.storage.set.SetTypedStorage
import com.github.dave08.kacheable.internal.storage.string.StringTypedStorage
import com.github.dave08.kacheable.store.CacheValueCodec
import com.github.dave08.kacheable.store.cacheValueCodec
import com.github.dave08.kacheable.store.DistributedSingleFlightStore
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal class KacheableImpl(
    store: KacheableStore,
    configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
    private val jsonParser: Json,
    defaultResilience: CacheResilienceConfig,
    snapshotStore: CacheSnapshotStore = NoopCacheSnapshotStore,
    backgroundScope: CoroutineScope? = null,
    snapshotClock: Clock = Clock.System,
) : Kacheable {
    private val backgroundScopeProvider = BackgroundScopeProvider(backgroundScope)

    private val snapshotCoordinator = if (configs.values.any { it.snapshot != null }) {
        CacheSnapshotCoordinator(
            store = store,
            snapshotStore = snapshotStore,
            configs = configs,
            namingStrategy = namingStrategy,
            scope = backgroundScopeProvider.get(),
            clock = snapshotClock,
        )
    } else {
        null
    }

    private val storages: TypedStorages =
        createTypedStorages(
            store,
            configs,
            namingStrategy,
            defaultResilience,
            snapshotCoordinator,
            backgroundScopeProvider::get,
        )

    init {
        validateResilience(store, configs, defaultResilience)
        snapshotCoordinator?.start()
    }

    override suspend fun <R> invalidate(vararg keys: Pair<String, List<Any>>, block: suspend () -> R): R =
        storages.string.invalidate(*keys, block = block)

    override suspend fun invalidate(entryRef: StoredCacheEntryRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(entryRef.storage) as TypedStorage<CacheStorage>).invalidate(entryRef as StoredCacheEntryRef<CacheStorage>)
    }

    override suspend fun invalidate(partRef: CacheEntryPartRef) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(partRef.storage) as TypedStorage<CacheStorage>).invalidate(partRef as StoredCachePartRef<CacheStorage>)
    }

    override suspend fun invalidate(allRef: StoredCacheAllRef<*>) {
        @Suppress("UNCHECKED_CAST")
        (storages.any(allRef.storage) as TypedStorage<CacheStorage>).invalidate(allRef as StoredCacheAllRef<CacheStorage>)
    }

    override suspend fun <E : Any> invalidate(
        entryRef: StoredCacheEntryRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(entryRef, returnView)
    }

    override suspend fun <E : Any> invalidate(
        partRef: StoredCachePartRef<CacheStorage.Set>,
        returnView: EnumMemberCacheReturn<E>,
    ) {
        storages.set.invalidate(partRef, returnView)
    }

    override suspend fun <R> invoke(
        name: String,
        type: KSerializer<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R
    ): R = storages.string.invoke(
        name = name,
        codec = cacheValueCodec(type, jsonParser),
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    override suspend fun <S : CacheStorage, R> invoke(
        entryRef: StoredCacheEntryRef<S>,
        returnView: CacheReturn<R, *>,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        block: suspend (previous: R?) -> R,
    ): R {
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
        codec: CacheValueCodec<R>,
        vararg params: Any,
        cacheIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R = storages.string.invoke(
        name = name,
        codec = codec,
        params = params,
        saveResultIf = cacheIf,
        block = block,
    )

    companion object {
        private fun createTypedStorages(
            store: KacheableStore,
            configs: Map<String, CacheConfig>,
            namingStrategy: CacheNamingStrategy,
            defaultResilience: CacheResilienceConfig,
            snapshotCoordinator: CacheSnapshotCoordinator?,
            backgroundScope: () -> CoroutineScope,
        ): TypedStorages {
            val loadCoordinator = CacheLoadCoordinator(defaultResilience)
            return TypedStorages(
                string = StringTypedStorage(store, configs, loadCoordinator, namingStrategy, snapshotCoordinator, backgroundScope),
                hashMap = HashMapTypedStorage(store, configs, loadCoordinator, namingStrategy, snapshotCoordinator, backgroundScope),
                set = SetTypedStorage(store, configs, loadCoordinator, namingStrategy, backgroundScope),
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

private fun validateResilience(
    store: KacheableStore,
    configs: Map<String, CacheConfig>,
    defaultResilience: CacheResilienceConfig,
) {
    val redisSingleFlightConfigured =
        defaultResilience.singleFlight == SingleFlightMode.Redis ||
            configs.values.any { it.resilience?.singleFlight == SingleFlightMode.Redis }

    if (redisSingleFlightConfigured && store !is DistributedSingleFlightStore) {
        throw IllegalArgumentException(
            "Redis single-flight requires a store that implements DistributedSingleFlightStore.",
        )
    }
}
