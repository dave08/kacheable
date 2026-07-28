@file:OptIn(kotlin.time.ExperimentalTime::class)
@file:Suppress("DEPRECATION")

package com.github.dave08.kacheable

import com.github.dave08.kacheable.internal.KacheableImpl
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

/**
 * Creates a suspended cache runtime over [store].
 *
 * The normal shape is still lambda-first:
 *
 * ```kotlin
 * cache(songCache(songId)) { repository.song(songId) }
 * ```
 *
 * [backgroundScope] is optional. Provide it when the host application wants to own lifecycle for
 * snapshot restore/flush jobs or `CacheMissPolicy.loadInBackground` background loaders. When it
 * is `null`, Kacheable creates an internal supervisor scope only if background work is actually
 * needed.
 */
fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    namingStrategy: CacheNamingStrategy = defaultCacheNamingStrategy(),
    jsonParser: Json = Json,
    defaultResilience: CacheResilienceConfig = CacheResilienceConfig(),
    snapshotStore: CacheSnapshotStore = NoopCacheSnapshotStore,
    backgroundScope: CoroutineScope? = null,
    telemetry: CacheTelemetry = NoopCacheTelemetry,
    correlationProvider: CacheCorrelationProvider? = null,
    loadConcurrency: LoadConcurrencySettings = LoadConcurrencySettings(),
): Kacheable = KacheableImpl(
    store,
    configs,
    namingStrategy,
    jsonParser,
    defaultResilience,
    loadConcurrency,
    snapshotStore,
    backgroundScope,
    telemetry = telemetry,
    correlationProvider = correlationProvider,
)

@Deprecated(
    message = "Use the Kacheable factory overload that takes CacheNamingStrategy.",
    replaceWith = ReplaceWith(
        expression = "Kacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser)",
        imports = ["com.github.dave08.kacheable.asCacheNamingStrategy"],
    ),
)
fun Kacheable(
    store: KacheableStore,
    configs: Map<String, CacheConfig> = emptyMap(),
    getNameStrategy: GetNameStrategy,
    jsonParser: Json = Json,
    defaultResilience: CacheResilienceConfig = CacheResilienceConfig(),
): Kacheable = Kacheable(store, configs, getNameStrategy.asCacheNamingStrategy(), jsonParser, defaultResilience)
