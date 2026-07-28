package com.github.dave08

import com.github.dave08.kacheable.CacheDiagnosticStage
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheLoadResult
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheLoadTrigger
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheMaintenanceMetric
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheOperation
import com.github.dave08.kacheable.CacheReadResult
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.CacheTelemetrySnapshot
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.CacheSummarySort
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.InMemoryCacheTelemetry
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.snapshots
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.withCacheCorrelation
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val telemetryBackgroundId = keyPart<Int>("id")
private val telemetryBackgroundCache = cacheKey(
    "background-telemetry",
    returns<String>(),
    key = exact(telemetryBackgroundId),
)

private class CacheTelemetryFixture {
    val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 100)
    val store = InMemoryKacheableStore()
    val cache = Kacheable(store, telemetry = telemetry)

    fun cacheWith(
        resilience: CacheResilienceConfig,
    ): Kacheable = Kacheable(
        store = InMemoryKacheableStore(),
        defaultResilience = resilience,
        telemetry = telemetry,
    )
}

val CacheTelemetrySpec by testSuite {
    testFixture {
        CacheTelemetryFixture()
    } asContextForEach {
        test("in-memory telemetry distinguishes loaded values from usable cached values") {
            var loads = 0

            repeat(2) {
                cache.cache("telemetry-value", 1) {
                    loads++
                    "value"
                }
            }

            telemetry.snapshot().assertOperationCounts(
                cacheName = "telemetry-value",
                loaded = 1,
                cached = 1,
            )
            assertEquals(1, loads)
        }

        test("nested cache operations retain their parent operation identifier") {
            val correlatedCache = Kacheable(
                store,
                telemetry = telemetry,
                correlationProvider = { "home-request-42" },
            )

            correlatedCache.cache("outer-cache", 1) {
                correlatedCache.cache("inner-cache", 1) { "inner" }
                "outer"
            }

            val starts = telemetry.recentEvents().filter { it.stage == CacheDiagnosticStage.Started }
            val outer = starts.single { it.context.cacheName == "outer-cache" }
            val inner = starts.single { it.context.cacheName == "inner-cache" }
            assertEquals(outer.context.operationId, inner.context.parentOperationId)
        }

        test("nested cache operations retain their external correlation identifier") {
            val expectedCorrelationId = "home-request-42"
            val correlatedCache = Kacheable(
                store,
                telemetry = telemetry,
                correlationProvider = { expectedCorrelationId },
            )

            correlatedCache.cache("outer-cache", 1) {
                correlatedCache.cache("inner-cache", 1) { "inner" }
                "outer"
            }

            val correlationIds = telemetry.recentEvents()
                .filter { it.stage == CacheDiagnosticStage.Started }
                .map { it.context.correlationId }
                .toSet()
            assertEquals(setOf(expectedCorrelationId), correlationIds)
        }

        test("request-scoped correlation is inherited by sibling cache operations") {
            val expectedCorrelationId = "home-request-siblings"

            withCacheCorrelation(expectedCorrelationId) {
                coroutineScope {
                    awaitAll(
                        async { cache.cache("sibling-cache-one", 1) { "one" } },
                        async { cache.cache("sibling-cache-two", 1) { "two" } },
                    )
                }
            }

            val starts = telemetry.recentEvents().filter { it.stage == CacheDiagnosticStage.Started }
            assertEquals(
                setOf(expectedCorrelationId),
                starts.map { it.context.correlationId }.toSet(),
            )
            assertTrue(starts.all { it.context.parentOperationId == null })
        }

        test("ranked summary identifies the cache with the most retained wait time") {
            telemetry.recordDiagnosticOperation("slow-cache", waitNanos = 50, loaderNanos = 10)
            telemetry.recordDiagnosticOperation("fast-cache", waitNanos = 5, loaderNanos = 20)

            val summary = telemetry.summary(sortBy = CacheSummarySort.TotalWait)

            assertEquals(listOf("slow-cache", "fast-cache"), summary.rows.map { it.cacheName })
            assertEquals(50, summary.rows.first().totalWaitDurationNanos)
            assertEquals(50, summary.rows.first().admissionWaitDurationNanos)
            assertEquals(0, summary.rows.first().redisSingleFlightWaitDurationNanos)
        }

        test("ranked summary separates admission and single-flight wait totals") {
            telemetry.recordDiagnosticOperation(
                "admission-cache",
                waitNanos = 40,
                loaderNanos = 5,
                waitReason = CacheWaitReason.ConcurrencyLimit,
            )
            telemetry.recordDiagnosticOperation(
                "redis-joiner-cache",
                waitNanos = 20,
                loaderNanos = 5,
                waitReason = CacheWaitReason.RedisSingleFlight,
            )

            val summary = telemetry.summary(sortBy = CacheSummarySort.RedisSingleFlightWait)

            assertEquals("redis-joiner-cache", summary.rows.first().cacheName)
            assertEquals(20, summary.rows.first().redisSingleFlightWaitDurationNanos)
            assertEquals(0, summary.rows.first().admissionWaitDurationNanos)
        }

        test("in-memory telemetry exposes current and peak loader and waiter activity") {
            val observation = telemetry.begin(
                CacheOperation("activity-cache", CacheStorageKind.String),
            )

            observation.loadWaitStarted(CacheWaitReason.ConcurrencyLimit, CacheLoadRole.Leader)
            observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)

            val active = assertNotNull(telemetry.snapshot()["activity-cache"].singleOrNull())
            assertEquals(1, active.waiters.current)
            assertEquals(1, active.waiters.peak)
            assertEquals(1, active.foregroundLoaders.current)
            assertEquals(1, active.foregroundLoaders.peak)

            observation.loadWait(CacheWaitReason.ConcurrencyLimit, CacheLoadRole.Leader, 1)
            observation.loaderCompleted(
                CacheLoadTrigger.Miss,
                CacheExecution.Foreground,
                CacheLoadResult.Success,
                1,
            )

            val completed = assertNotNull(telemetry.snapshot()["activity-cache"].singleOrNull())
            assertEquals(0, completed.waiters.current)
            assertEquals(0, completed.foregroundLoaders.current)
            assertEquals(1, completed.waiters.peak)
            assertEquals(1, completed.foregroundLoaders.peak)
        }

        test("reset can restart diagnostic identifiers for isolated local sessions") {
            val first = telemetry.begin(CacheOperation("first", CacheStorageKind.String))
            telemetry.reset(resetIdentifiers = true)

            val afterReset = telemetry.begin(CacheOperation("after-reset", CacheStorageKind.String))

            assertEquals(first.operationId, afterReset.operationId)
        }

        test("diagnostic event retention drops the oldest events at its configured bound") {
            val retainedEventCount = 3
            val boundedTelemetry = InMemoryCacheTelemetry(recentEventCapacity = retainedEventCount)
            val boundedCache = Kacheable(InMemoryKacheableStore(), telemetry = boundedTelemetry)

            boundedCache.cache("bounded-cache", 1) { "value" }

            assertEquals(retainedEventCount, boundedTelemetry.recentEvents().size)
        }

        test("diagnostic events never retain cache arguments") {
            val sensitiveArgument = "sensitive-argument"

            cache.cache("bounded-cache", sensitiveArgument) { "value" }

            assertTrue(telemetry.recentEvents().none { sensitiveArgument in it.toString() })
        }

        test("snapshot flow starts with the current immutable aggregate state") {
            cache.cache("flow-cache", 1) { "value" }

            val firstSnapshot = telemetry.snapshots(1.seconds).first()

            firstSnapshot.assertOperationCounts(
                cacheName = "flow-cache",
                loaded = 1,
                cached = 0,
            )
        }

        test("local single-flight reports one joiner and one loader execution") {
            val singleFlightCache = cacheWith(
                CacheResilienceConfig(singleFlight = SingleFlightMode.Local),
            )
            val loaderStarted = CompletableDeferred<Unit>()
            val releaseLoader = CompletableDeferred<Unit>()

            coroutineScope {
                val leader = async(start = CoroutineStart.UNDISPATCHED) {
                    singleFlightCache.cache("single-flight-telemetry", 1) {
                        loaderStarted.complete(Unit)
                        releaseLoader.await()
                        "value"
                    }
                }
                loaderStarted.await()
                val joiner = async(start = CoroutineStart.UNDISPATCHED) {
                    singleFlightCache.cache("single-flight-telemetry", 1) { "unexpected" }
                }
                releaseLoader.complete(Unit)
                awaitAll(leader, joiner)
            }

            telemetry.snapshot().assertSingleFlight(
                cacheName = "single-flight-telemetry",
                loaderExecutions = 1,
                localJoiners = 1,
            )
        }

        test("loader completion is reported before cache storage finishes") {
            val blockingStore = WriteBlockingStore()
            val observedCache = Kacheable(blockingStore, telemetry = telemetry)

            coroutineScope {
                val invocation = async(start = CoroutineStart.UNDISPATCHED) {
                    observedCache.cache("loader-boundary", 1) { "value" }
                }
                blockingStore.writeStarted.await()

                telemetry.assertLoaderCompletedBeforeStorageWrite()

                blockingStore.releaseWrite.complete(Unit)
                invocation.await()
            }
        }

        test("background fallback completes before its correlated loader finishes") {
            val loaderStarted = CompletableDeferred<Unit>()
            val releaseLoader = CompletableDeferred<Unit>()

            coroutineScope {
                val backgroundCache = Kacheable(
                    InMemoryKacheableStore(),
                    telemetry = telemetry,
                    backgroundScope = this,
                )
                val result = backgroundCache.cache(
                    telemetryBackgroundCache(1),
                    missPolicy = CacheMissPolicy.loadInBackground { "fallback" },
                ) {
                    loaderStarted.complete(Unit)
                    releaseLoader.await()
                    "loaded"
                }

                loaderStarted.await()
                assertEquals("fallback", result)
                telemetry.assertBackgroundFallbackCompleted()
                releaseLoader.complete(Unit)
            }

            telemetry.assertLoaderFinishedAfterBackgroundFallback()
        }

        test("telemetry implementation failures cannot change cache behavior") {
            val brokenTelemetry = CacheTelemetry { error("telemetry failed") }
            val cacheWithBrokenTelemetry = Kacheable(
                InMemoryKacheableStore(),
                telemetry = brokenTelemetry,
            )

            val result = cacheWithBrokenTelemetry.cache("safe-cache", 1) { "value" }

            assertEquals("value", result)
        }

        test("blocking cache operations report loaded and usable cached outcomes") {
            val blockingFixture = BlockingCacheFixture(telemetry = telemetry)

            repeat(2) {
                blockingFixture.cache("blocking-telemetry", 1) { "value" }
            }

            telemetry.snapshot().assertOperationCounts(
                cacheName = "blocking-telemetry",
                loaded = 1,
                cached = 1,
            )
        }

        test("typed entry invalidation reports a successful maintenance operation") {
            val entry = telemetryBackgroundCache(2)
            cache.cache(entry) { "value" }

            cache.invalidate(entry)

            val series = assertNotNull(
                telemetry.snapshot()["background-telemetry"].singleOrNull(),
                "Missing telemetry series for background-telemetry",
            )
            assertEquals(
                1,
                series.maintenance[
                    CacheMaintenanceMetric(
                        CacheMaintenanceOperation.InvalidateEntry,
                        CacheMaintenanceResult.Success,
                    )
                ],
            )
        }
    }
}

private fun InMemoryCacheTelemetry.recordDiagnosticOperation(
    cacheName: String,
    waitNanos: Long,
    loaderNanos: Long,
    waitReason: CacheWaitReason = CacheWaitReason.ConcurrencyLimit,
) {
    val observation = begin(CacheOperation(cacheName, CacheStorageKind.String))
    observation.loadWaitStarted(waitReason, CacheLoadRole.Leader)
    observation.loadWait(waitReason, CacheLoadRole.Leader, waitNanos)
    observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)
    observation.loaderCompleted(
        CacheLoadTrigger.Miss,
        CacheExecution.Foreground,
        CacheLoadResult.Success,
        loaderNanos,
    )
    observation.complete(CacheOperationResult.Loaded, waitNanos + loaderNanos)
}

private class WriteBlockingStore(
    private val delegate: KacheableStore = InMemoryKacheableStore(),
) : KacheableStore by delegate {
    val writeStarted = CompletableDeferred<Unit>()
    val releaseWrite = CompletableDeferred<Unit>()

    override suspend fun set(key: String, value: String) {
        writeStarted.complete(Unit)
        releaseWrite.await()
        delegate.set(key, value)
    }
}

private fun CacheTelemetrySnapshot.assertOperationCounts(
    cacheName: String,
    loaded: Long,
    cached: Long,
) {
    val series = assertNotNull(this[cacheName].singleOrNull(), "Missing telemetry series for $cacheName")
    assertEquals(loaded, series.operations.getValue(CacheOperationResult.Loaded), "$cacheName loaded operations")
    assertEquals(cached, series.operations.getValue(CacheOperationResult.CachedValue), "$cacheName cached operations")
    assertTrue(
        series.storageReads.getValue(CacheReadResult.Present) >= cached,
        "$cacheName should have at least one present physical read per cached result",
    )
}

private fun CacheTelemetrySnapshot.assertSingleFlight(
    cacheName: String,
    loaderExecutions: Long,
    localJoiners: Long,
) {
    val series = assertNotNull(this[cacheName].singleOrNull(), "Missing telemetry series for $cacheName")
    assertEquals(loaderExecutions, series.loaderDuration.count, "$cacheName loader executions")
    val joiners = series.loadWaits
        .filterKeys { (reason, _) -> reason == CacheWaitReason.LocalSingleFlight }
        .values
        .sum()
    assertEquals(localJoiners, joiners, "$cacheName local single-flight joiners")
}

private fun InMemoryCacheTelemetry.assertBackgroundFallbackCompleted() {
    assertTrue(
        recentEvents().any {
            (it.stage as? CacheDiagnosticStage.Completed)?.result ==
                CacheOperationResult.BackgroundFallback
        },
        "Expected the caller-visible background fallback to be completed",
    )
}

private fun InMemoryCacheTelemetry.assertLoaderCompletedBeforeStorageWrite() {
    val events = recentEvents()
    assertTrue(
        events.any { it.stage is CacheDiagnosticStage.LoaderCompleted },
        "Expected loader completion while the storage write is blocked",
    )
    assertTrue(
        events.none { it.stage is CacheDiagnosticStage.StorageWrite },
        "Storage write completion must not be reported while the write is blocked",
    )
}

private fun InMemoryCacheTelemetry.assertLoaderFinishedAfterBackgroundFallback() {
    val events = recentEvents()
    val completedIndex = events.indexOfFirst {
        (it.stage as? CacheDiagnosticStage.Completed)?.result ==
            CacheOperationResult.BackgroundFallback
    }
    val loaderIndex = events.indexOfFirst { it.stage is CacheDiagnosticStage.LoaderCompleted }
    assertTrue(completedIndex >= 0, "Missing background fallback completion event")
    assertTrue(loaderIndex > completedIndex, "Expected the background loader event after fallback completion")
}
