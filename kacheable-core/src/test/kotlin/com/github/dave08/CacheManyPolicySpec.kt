package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheLoadContext
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheDiagnosticStage
import com.github.dave08.kacheable.InMemoryCacheTelemetry
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.KeyPart
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

val CacheManyPolicySpec by testSuite {
    testFixture { ManyRefreshFixture() } asContextForEach {
        test("batch refresh accepts a fresh value published before its coordination recheck") {
            arrangeOldValue()

            val result = runRefresh(CacheRefreshPolicy.refreshIf { it == "old" }) {
                error("a fresh coordination value must avoid the loader")
            }

            assertEquals(mapOf(1 to "newer"), result)
            assertEquals(CacheOperationResult.Refreshed, telemetry.completedResult())
        }

        test("failed batch refresh falls back to the latest value from its coordination recheck") {
            arrangeOldValue()

            val result = runRefresh(CacheRefreshPolicy.refreshIf { true }) {
                error("refresh failed")
            }

            assertEquals(mapOf(1 to "newer"), result)
            assertEquals(CacheOperationResult.Stale, telemetry.completedResult())
        }
    }

    test("batch staleOnFailure recheck preserves a concurrently published null") {
        val name = "many-failure-null-policy"
        val store = InMemoryKacheableStore()
        val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
        val cache = Kacheable(
            store,
            configs = mapOf(name to CacheConfig(
                name,
                nullPlaceholder = "<null>",
                resilience = CacheResilienceConfig(staleOnFailure = true),
            )),
            telemetry = telemetry,
        )
        val values = cacheKey(name, returns<String?>(), exact(keyPart<Int>("id")))

        val result = cache.cache(values.many(1)) { _, _ ->
            store.map["$name:1"] = "<null>"
            error("loader failed after another writer published null")
        }

        assertEquals(mapOf(1 to null), result)
        assertEquals(CacheOperationResult.Stale, telemetry.completedResult())
    }

    test("batch staleOnTimeout recheck preserves a concurrently published value") {
        val name = "many-timeout-value-policy"
        val store = InMemoryKacheableStore()
        val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
        val cache = Kacheable(
            store,
            configs = mapOf(name to CacheConfig(
                name,
                resilience = CacheResilienceConfig(staleOnTimeout = true),
            )),
            telemetry = telemetry,
        )
        val values = cacheKey(name, returns<String>(), exact(keyPart<Int>("id")))

        val result = cache.cache(values.many(1)) { _, _ ->
            store.map["$name:1"] = "\"winner\""
            throw CacheLoadTimeoutException("loader timed out after another writer published")
        }

        assertEquals(mapOf(1 to "winner"), result)
        assertEquals(CacheOperationResult.Stale, telemetry.completedResult())
    }

    test("failed batch refresh recheck returns a value published during the loader") {
        val name = "many-refresh-failure-policy"
        val store = InMemoryKacheableStore()
        val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
        store.map["$name:1"] = "\"old\""
        val cache = Kacheable(store, telemetry = telemetry)
        val values = cacheKey(name, returns<String>(), exact(keyPart<Int>("id")))

        val result = cache.cache(
            values.many(1),
            missPolicy = CacheMissPolicy.load(),
            refreshPolicy = CacheRefreshPolicy.refreshIf { true },
        ) { _, _ ->
            store.map["$name:1"] = "\"newer\""
            error("refresh failed after another writer published")
        }

        assertEquals(mapOf(1 to "newer"), result)
        assertEquals(CacheOperationResult.Stale, telemetry.completedResult())
    }

    test("batch miss failure fallback reports its caller-visible outcome") {
        val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
        val cache = Kacheable(InMemoryKacheableStore(), telemetry = telemetry)
        val values = cacheKey("many-failure-fallback-policy", returns<String>(), exact(keyPart<Int>("id")))

        val result = cache.cache(
            values.many(1),
            missPolicy = CacheMissPolicy.load { "fallback" },
        ) { _, _ ->
            error("load failed")
        }

        assertEquals(mapOf(1 to "fallback"), result)
        assertEquals(CacheOperationResult.FailureFallback, telemetry.completedResult())
    }

    test("background batch miss returns unpersisted fallbacks and stores only loader results") {
        val store = InMemoryKacheableStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = Kacheable(store, backgroundScope = scope)
        val values = cacheKey("many-background-policy", returns<String>(), exact(keyPart<Int>("id")))
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()

        try {
            val result = cache.cache(
                values.many(1, 2),
                missPolicy = CacheMissPolicy.loadInBackground { "fallback" },
            ) { keys, _ ->
                loaderStarted.complete(Unit)
                releaseLoader.await()
                keys.associateWith { "loaded-$it" }
            }

            loaderStarted.await()
            assertEquals(mapOf(1 to "fallback", 2 to "fallback"), result)
            assertNull(store.map["many-background-policy:1"])
            assertNull(store.map["many-background-policy:2"])

            releaseLoader.complete(Unit)
            scope.coroutineContext[Job]!!.children.toList().joinAll()

            assertEquals("loaded-1", cache.cache(values(1)) { error("background result was not stored") })
            assertEquals("loaded-2", cache.cache(values(2)) { error("background result was not stored") })
        } finally {
            scope.cancel()
        }
    }

    test("batch cacheIf rejection returns values without storing them") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(store)
        val values = cacheKey("many-rejected-policy", returns<String>(), exact(keyPart<Int>("id")))
        var loads = 0

        val first = cache.cache(values.many(1, 2), cacheIf = { false }) { keys, _ ->
            loads++
            keys.associateWith { "first-$it" }
        }
        val second = cache.cache(values.many(1, 2)) { keys, _ ->
            loads++
            keys.associateWith { "second-$it" }
        }

        assertEquals(mapOf(1 to "first-1", 2 to "first-2"), first)
        assertEquals(mapOf(1 to "second-1", 2 to "second-2"), second)
        assertEquals(2, loads)
    }

    test("enum batches use classification sets shared with scalar reads") {
        val store = InMemoryKacheableStore()
        val cache = Kacheable(store)
        val reactions = cacheKey(
            "many-enum-policy",
            returns<SongLike>(),
            key = partitioned(key = keyPart<Int>("account")),
        )

        val result = cache.cache(reactions.many(1, 2)) { _, _ ->
            mapOf(1 to SongLike.LIKE, 2 to SongLike.DISLIKE)
        }

        assertEquals(mapOf(1 to SongLike.LIKE, 2 to SongLike.DISLIKE), result)
        store.assertSetMember("many-enum-policy:${SongLike.LIKE.name}", 1)
        store.assertSetMember("many-enum-policy:${SongLike.DISLIKE.name}", 2)
        assertEquals(SongLike.LIKE, cache.cache(reactions(1)) { error("classification was not shared") })
        assertEquals(SongLike.DISLIKE, cache.cache(reactions(2)) { error("classification was not shared") })
    }

    test("batch load contexts expire when their loader attempt returns") {
        val cache = Kacheable(InMemoryKacheableStore())
        val values = cacheKey("many-context-policy", returns<String>(), exact(keyPart<Int>("id")))
        lateinit var escaped: CacheLoadContext<Int, String, KeyPart<Int>>

        cache.cache(values.many(1)) { _, context ->
            escaped = context
            mapOf(1 to "one")
        }

        val failure = try {
            escaped.entry(2)
            null
        } catch (error: Throwable) {
            error
        }
        assertIs<IllegalStateException>(failure)
    }
}

private class ManyRefreshFixture {
    private val store = RefreshingBatchStore()
    private val blockerStarted = CompletableDeferred<Unit>()
    private val releaseBlocker = CompletableDeferred<Unit>()
    private val cacheName = "many-refresh-policy"
    val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
    val values = cacheKey(cacheName, returns<String>(), exact(keyPart<Int>("id")))
    private val cache = Kacheable(
        store,
        configs = mapOf(
            cacheName to CacheConfig(
                cacheName,
                resilience = CacheResilienceConfig(
                    singleFlight = SingleFlightMode.Local,
                    maxConcurrentLoads = 1,
                ),
            ),
        ),
        telemetry = telemetry,
    )

    fun arrangeOldValue() = store.arrangeOldValue("$cacheName:1")

    suspend fun runRefresh(
        refreshPolicy: CacheRefreshPolicy<String>,
        loader: suspend (List<Int>) -> Map<Int, String>,
    ): Map<Int, String> = coroutineScope {
        val blocker = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(values(99)) {
                blockerStarted.complete(Unit)
                releaseBlocker.await()
                "blocker"
            }
        }
        blockerStarted.await()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            cache.cache(
                values.many(1),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = refreshPolicy,
                storeResultIf = { true },
            ) { keys, _ -> loader(keys) }
        }

        store.awaitInitialTargetRead()
        store.publishNewerValue()
        releaseBlocker.complete(Unit)

        refresh.await().also { blocker.await() }
    }
}

private fun InMemoryCacheTelemetry.completedResult(): CacheOperationResult =
    recentEvents().mapNotNull { event ->
        (event.stage as? CacheDiagnosticStage.Completed)?.result
    }.last()

private class RefreshingBatchStore(
    private val backingStore: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backingStore {
    private val initialTargetRead = CompletableDeferred<Unit>()
    private lateinit var targetKey: String

    override suspend fun getValues(keys: List<String>): Map<String, String> {
        val result = backingStore.getValues(keys)
        if (targetKey in keys && result.containsKey(targetKey)) {
            initialTargetRead.complete(Unit)
        }
        return result
    }

    fun arrangeOldValue(targetKey: String) {
        this.targetKey = targetKey
        backingStore.map[targetKey] = "\"old\""
    }

    suspend fun awaitInitialTargetRead() = initialTargetRead.await()

    fun publishNewerValue() {
        backingStore.map[targetKey] = "\"newer\""
    }
}
