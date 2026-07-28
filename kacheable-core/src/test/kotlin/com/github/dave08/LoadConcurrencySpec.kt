package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheDiagnosticStage
import com.github.dave08.kacheable.CacheLoadRejectedException
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheObservation
import com.github.dave08.kacheable.CacheOperation
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.InMemoryCacheTelemetry
import com.github.dave08.kacheable.LoadConcurrencyConfig
import com.github.dave08.kacheable.LoadConcurrencySettings
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.exact
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.loadConcurrencyGroup
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

private val loadId = keyPart<Int>("id")

private class LoadConcurrencyFixture {
    val database = loadConcurrencyGroup("database", LoadConcurrencyConfig(maxConcurrentLoads = 1))
    val first = cacheKey("load-concurrency-first", returns<String>(), exact(loadId), loadConcurrency = database)
    val second = cacheKey("load-concurrency-second", returns<String>(), exact(loadId), loadConcurrency = database)

    fun cache(
        settings: LoadConcurrencySettings = LoadConcurrencySettings(),
        backgroundScope: kotlinx.coroutines.CoroutineScope? = null,
    ): Kacheable = Kacheable(
        store = InMemoryKacheableStore(),
        backgroundScope = backgroundScope,
        loadConcurrency = settings,
    )
}

val LoadConcurrencySpec by testSuite {
    testFixture {
        LoadConcurrencyFixture()
    } asContextForEach {
        test("cache keys in one load concurrency group share its declared limit") {
            withTimeout(5.seconds) {
                val firstStarted = CompletableDeferred<Unit>()
                val releaseFirst = CompletableDeferred<Unit>()
                val secondStarted = CompletableDeferred<Unit>()
                val cache = cache()

                coroutineScope {
                    val leadingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(first(1)) {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                            "first"
                        }
                    }
                    firstStarted.await()
                    val queuedLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(second(1)) {
                            secondStarted.complete(Unit)
                            "second"
                        }
                    }

                    assertFalse(secondStarted.isCompleted)
                    releaseFirst.complete(Unit)
                    assertEquals("first", leadingLoad.await())
                    assertEquals("second", queuedLoad.await())
                    assertTrue(secondStarted.isCompleted)
                }
            }
        }

        test("global group override replaces the load concurrency group default") {
            withTimeout(5.seconds) {
                val releaseLoads = CompletableDeferred<Unit>()
                val firstStarted = CompletableDeferred<Unit>()
                val secondStarted = CompletableDeferred<Unit>()
                val cache = cache(
                    LoadConcurrencySettings(
                        overrides = mapOf(database to LoadConcurrencyConfig(maxConcurrentLoads = 2)),
                    ),
                )

                coroutineScope {
                    val firstLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(first(1)) {
                            firstStarted.complete(Unit)
                            releaseLoads.await()
                            "first"
                        }
                    }
                    val secondLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(second(1)) {
                            secondStarted.complete(Unit)
                            releaseLoads.await()
                            "second"
                        }
                    }

                    firstStarted.await()
                    secondStarted.await()
                    releaseLoads.complete(Unit)
                    assertEquals(listOf("first", "second"), listOf(firstLoad.await(), secondLoad.await()))
                }
            }
        }

        test("global default load concurrency applies independently to ungrouped cache names") {
            withTimeout(5.seconds) {
                val releaseLoads = CompletableDeferred<Unit>()
                val firstStarted = CompletableDeferred<Unit>()
                val secondStarted = CompletableDeferred<Unit>()
                val ungroupedFirst = cacheKey("ungrouped-first", returns<String>(), exact(loadId))
                val ungroupedSecond = cacheKey("ungrouped-second", returns<String>(), exact(loadId))
                val cache = cache(
                    LoadConcurrencySettings(default = LoadConcurrencyConfig(maxConcurrentLoads = 1)),
                )

                coroutineScope {
                    val firstLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(ungroupedFirst(1)) {
                            firstStarted.complete(Unit)
                            releaseLoads.await()
                            "first"
                        }
                    }
                    val secondLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(ungroupedSecond(1)) {
                            secondStarted.complete(Unit)
                            releaseLoads.await()
                            "second"
                        }
                    }

                    firstStarted.await()
                    secondStarted.await()
                    releaseLoads.complete(Unit)
                    assertEquals(listOf("first", "second"), listOf(firstLoad.await(), secondLoad.await()))
                }
            }
        }

        test("background load limit preserves capacity for a foreground load") {
            withTimeout(5.seconds) {
                val backgroundGroup = loadConcurrencyGroup(
                    "background-database",
                    LoadConcurrencyConfig(
                        maxConcurrentLoads = 2,
                        maxConcurrentBackgroundLoads = 1,
                    ),
                )
                val backgroundFirst = cacheKey(
                    "background-load-first",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = backgroundGroup,
                )
                val backgroundSecond = cacheKey(
                    "background-load-second",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = backgroundGroup,
                )
                val foreground = cacheKey(
                    "foreground-load",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = backgroundGroup,
                )
                val firstBackgroundStarted = CompletableDeferred<Unit>()
                val secondBackgroundStarted = CompletableDeferred<Unit>()
                val foregroundStarted = CompletableDeferred<Unit>()
                val releaseFirstBackground = CompletableDeferred<Unit>()
                val releaseForeground = CompletableDeferred<Unit>()

                coroutineScope {
                    val cache = cache(backgroundScope = this)
                    assertEquals(
                        "fallback-1",
                        cache.cache(
                            backgroundFirst(1),
                            missPolicy = CacheMissPolicy.loadInBackground { "fallback-1" },
                        ) {
                            firstBackgroundStarted.complete(Unit)
                            releaseFirstBackground.await()
                            "first"
                        },
                    )
                    firstBackgroundStarted.await()
                    assertEquals(
                        "fallback-2",
                        cache.cache(
                            backgroundSecond(1),
                            missPolicy = CacheMissPolicy.loadInBackground { "fallback-2" },
                        ) {
                            secondBackgroundStarted.complete(Unit)
                            "second"
                        },
                    )

                    val foregroundLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(foreground(1)) {
                            foregroundStarted.complete(Unit)
                            releaseForeground.await()
                            "foreground"
                        }
                    }

                    foregroundStarted.await()
                    assertFalse(secondBackgroundStarted.isCompleted)
                    releaseForeground.complete(Unit)
                    assertEquals("foreground", foregroundLoad.await())
                    releaseFirstBackground.complete(Unit)
                    secondBackgroundStarted.await()
                }
            }
        }

        test("nested cache loads inherit background execution and preserve foreground capacity") {
            withTimeout(5.seconds) {
                val databaseGroup = loadConcurrencyGroup(
                    "nested-background-database",
                    LoadConcurrencyConfig(
                        maxConcurrentLoads = 2,
                        maxConcurrentBackgroundLoads = 1,
                    ),
                )
                val firstProbe = cacheKey("nested-background-probe-first", returns<String>(), exact(loadId))
                val secondProbe = cacheKey("nested-background-probe-second", returns<String>(), exact(loadId))
                val firstDatabaseLoad = cacheKey(
                    "nested-background-db-first",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = databaseGroup,
                )
                val secondDatabaseLoad = cacheKey(
                    "nested-background-db-second",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = databaseGroup,
                )
                val foregroundDatabaseLoad = cacheKey(
                    "nested-background-db-foreground",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = databaseGroup,
                )
                val firstNestedStarted = CompletableDeferred<Unit>()
                val secondNestedStarted = CompletableDeferred<Unit>()
                val foregroundStarted = CompletableDeferred<Unit>()
                val releaseFirstNested = CompletableDeferred<Unit>()
                val releaseForeground = CompletableDeferred<Unit>()

                coroutineScope {
                    val cache = cache(backgroundScope = this)
                    cache.cache(
                        firstProbe(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "first-fallback" },
                    ) {
                        cache.cache(firstDatabaseLoad(1)) {
                            firstNestedStarted.complete(Unit)
                            releaseFirstNested.await()
                            "first-database"
                        }
                    }
                    firstNestedStarted.await()

                    cache.cache(
                        secondProbe(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "second-fallback" },
                    ) {
                        cache.cache(secondDatabaseLoad(1)) {
                            secondNestedStarted.complete(Unit)
                            "second-database"
                        }
                    }

                    val foregroundLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(foregroundDatabaseLoad(1)) {
                            foregroundStarted.complete(Unit)
                            releaseForeground.await()
                            "foreground-database"
                        }
                    }

                    foregroundStarted.await()
                    assertFalse(secondNestedStarted.isCompleted)
                    releaseForeground.complete(Unit)
                    assertEquals("foreground-database", foregroundLoad.await())
                    releaseFirstNested.complete(Unit)
                    secondNestedStarted.await()
                }
            }
        }

        test("foreground admission overtakes queued background work") {
            withTimeout(5.seconds) {
                val priorityGroup = loadConcurrencyGroup(
                    "priority-admission",
                    LoadConcurrencyConfig(
                        maxConcurrentLoads = 1,
                        maxConcurrentBackgroundLoads = 1,
                    ),
                )
                val firstBackground = cacheKey(
                    "priority-background-first",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val secondBackground = cacheKey(
                    "priority-background-second",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val foreground = cacheKey(
                    "priority-foreground",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val firstStarted = CompletableDeferred<Unit>()
                val secondStarted = CompletableDeferred<Unit>()
                val foregroundStarted = CompletableDeferred<Unit>()
                val secondQueued = CompletableDeferred<Unit>()
                val foregroundQueued = CompletableDeferred<Unit>()
                val releaseFirst = CompletableDeferred<Unit>()
                val releaseForeground = CompletableDeferred<Unit>()
                val telemetry = AdmissionSignalTelemetry(
                    mapOf(
                        "priority-background-second" to secondQueued,
                        "priority-foreground" to foregroundQueued,
                    ),
                )

                coroutineScope {
                    val cache = Kacheable(
                        InMemoryKacheableStore(),
                        telemetry = telemetry,
                        backgroundScope = this,
                    )
                    cache.cache(
                        firstBackground(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "first-fallback" },
                    ) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                        "first-background"
                    }
                    firstStarted.await()
                    cache.cache(
                        secondBackground(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "second-fallback" },
                    ) {
                        secondStarted.complete(Unit)
                        "second-background"
                    }
                    secondQueued.await()

                    val foregroundLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(foreground(1)) {
                            foregroundStarted.complete(Unit)
                            releaseForeground.await()
                            "foreground"
                        }
                    }
                    foregroundQueued.await()

                    releaseFirst.complete(Unit)
                    foregroundStarted.await()
                    assertFalse(secondStarted.isCompleted)
                    releaseForeground.complete(Unit)
                    assertEquals("foreground", foregroundLoad.await())
                    secondStarted.await()
                }
            }
        }

        test("foreground priority eventually admits waiting background work") {
            withTimeout(5.seconds) {
                val priorityGroup = loadConcurrencyGroup(
                    "priority-aging",
                    LoadConcurrencyConfig(
                        maxConcurrentLoads = 1,
                        maxConcurrentBackgroundLoads = 1,
                    ),
                )
                val blocker = cacheKey(
                    "priority-aging-blocker",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val background = cacheKey(
                    "priority-aging-background",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val foreground = cacheKey(
                    "priority-aging-foreground",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = priorityGroup,
                )
                val blockerStarted = CompletableDeferred<Unit>()
                val releaseBlocker = CompletableDeferred<Unit>()
                val backgroundCompleted = CompletableDeferred<Unit>()
                val allQueued = CompletableDeferred<Unit>()
                val executionOrder = mutableListOf<String>()

                coroutineScope {
                    val cache = Kacheable(
                        InMemoryKacheableStore(),
                        telemetry = AdmissionCountTelemetry(expectedWaiters = 10, allQueued),
                        backgroundScope = this,
                    )
                    val active = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(blocker(1)) {
                            blockerStarted.complete(Unit)
                            releaseBlocker.await()
                            "blocker"
                        }
                    }
                    blockerStarted.await()
                    cache.cache(
                        background(1),
                        missPolicy = CacheMissPolicy.loadInBackground { "fallback" },
                    ) {
                        executionOrder += "background"
                        backgroundCompleted.complete(Unit)
                        "background"
                    }
                    val foregroundLoads = (1..9).map { id ->
                        async(start = CoroutineStart.UNDISPATCHED) {
                            cache.cache(foreground(id)) {
                                executionOrder += "foreground-$id"
                                "foreground-$id"
                            }
                        }
                    }
                    allQueued.await()

                    releaseBlocker.complete(Unit)
                    active.await()
                    foregroundLoads.forEach { it.await() }
                    backgroundCompleted.await()

                    assertEquals("background", executionOrder[8])
                    assertEquals(9, executionOrder.count { it.startsWith("foreground-") })
                }
            }
        }

        test("full load queue rejects without starting another loader") {
            withTimeout(5.seconds) {
                val noQueue = loadConcurrencyGroup(
                    "no-queue",
                    LoadConcurrencyConfig(maxConcurrentLoads = 1, maxQueuedLoads = 0),
                )
                val leading = cacheKey("no-queue-leading", returns<String>(), exact(loadId), loadConcurrency = noQueue)
                val rejected = cacheKey("no-queue-rejected", returns<String>(), exact(loadId), loadConcurrency = noQueue)
                val leadingStarted = CompletableDeferred<Unit>()
                val releaseLeading = CompletableDeferred<Unit>()
                var rejectedLoaderCalls = 0
                val cache = cache()

                coroutineScope {
                    val leadingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(leading(1)) {
                            leadingStarted.complete(Unit)
                            releaseLeading.await()
                            "leading"
                        }
                    }
                    leadingStarted.await()

                    assertFailsWith<CacheLoadRejectedException> {
                        cache.cache(rejected(1)) {
                            rejectedLoaderCalls++
                            "unexpected"
                        }
                    }

                    assertEquals(0, rejectedLoaderCalls)
                    releaseLeading.complete(Unit)
                    assertEquals("leading", leadingLoad.await())
                }
            }
        }

        test("load queue timeout rejects a queued loader") {
            withTimeout(5.seconds) {
                val timedQueue = loadConcurrencyGroup(
                    "timed-queue",
                    LoadConcurrencyConfig(
                        maxConcurrentLoads = 1,
                        queueTimeout = 100.milliseconds,
                    ),
                )
                val leading = cacheKey("timed-queue-leading", returns<String>(), exact(loadId), loadConcurrency = timedQueue)
                val rejected = cacheKey("timed-queue-rejected", returns<String>(), exact(loadId), loadConcurrency = timedQueue)
                val leadingStarted = CompletableDeferred<Unit>()
                val releaseLeading = CompletableDeferred<Unit>()
                val cache = cache()

                coroutineScope {
                    val leadingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(leading(1)) {
                            leadingStarted.complete(Unit)
                            releaseLeading.await()
                            "leading"
                        }
                    }
                    leadingStarted.await()

                    assertFailsWith<CacheLoadRejectedException> {
                        cache.cache(rejected(1)) { "unexpected" }
                    }

                    releaseLeading.complete(Unit)
                    assertEquals("leading", leadingLoad.await())
                }
            }
        }

        test("cancelling a queued load does not strand admission capacity") {
            withTimeout(5.seconds) {
                val cancellationGroup = loadConcurrencyGroup(
                    "cancelled-queue",
                    LoadConcurrencyConfig(maxConcurrentLoads = 1),
                )
                val leading = cacheKey(
                    "cancelled-queue-leading",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = cancellationGroup,
                )
                val cancelled = cacheKey(
                    "cancelled-queue-waiter",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = cancellationGroup,
                )
                val following = cacheKey(
                    "cancelled-queue-following",
                    returns<String>(),
                    exact(loadId),
                    loadConcurrency = cancellationGroup,
                )
                val leadingStarted = CompletableDeferred<Unit>()
                val cancelledQueued = CompletableDeferred<Unit>()
                val followingStarted = CompletableDeferred<Unit>()
                val releaseLeading = CompletableDeferred<Unit>()
                val cache = Kacheable(
                    InMemoryKacheableStore(),
                    telemetry = AdmissionSignalTelemetry(
                        mapOf("cancelled-queue-waiter" to cancelledQueued),
                    ),
                )

                coroutineScope {
                    val leadingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(leading(1)) {
                            leadingStarted.complete(Unit)
                            releaseLeading.await()
                            "leading"
                        }
                    }
                    leadingStarted.await()

                    val cancelledLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(cancelled(1)) { "unexpected" }
                    }
                    cancelledQueued.await()
                    cancelledLoad.cancel()
                    cancelledLoad.join()

                    val followingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                        cache.cache(following(1)) {
                            followingStarted.complete(Unit)
                            "following"
                        }
                    }
                    releaseLeading.complete(Unit)

                    assertEquals("leading", leadingLoad.await())
                    assertEquals("following", followingLoad.await())
                    assertTrue(followingStarted.isCompleted)
                }
            }
        }

        test("telemetry diagnostics include the typed load concurrency group") {
            val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 20)
            val cache = Kacheable(InMemoryKacheableStore(), telemetry = telemetry)

            cache.cache(first(1)) { "first" }

            assertEquals(
                setOf(database.name),
                telemetry.recentEvents().mapNotNull { it.context.loadConcurrencyGroup }.toSet(),
            )
            assertTrue(
                telemetry.recentEvents().none { it.stage is CacheDiagnosticStage.LoadWaitStarted },
                "An uncontended permit must not be reported as a queued waiter",
            )
        }

        test("same group name with conflicting declared defaults is rejected") {
            val conflictingGroup = loadConcurrencyGroup(
                database.name,
                LoadConcurrencyConfig(maxConcurrentLoads = 2),
            )
            val conflictingCache = cacheKey(
                "load-concurrency-conflict",
                returns<String>(),
                exact(loadId),
                loadConcurrency = conflictingGroup,
            )
            val cache = cache()

            cache.cache(first(1)) { "first" }

            assertFailsWith<IllegalArgumentException> {
                cache.cache(conflictingCache(1)) { "unexpected" }
            }
        }

        test("typed load concurrency group cannot be combined with the legacy per-cache limit") {
            val cache = Kacheable(
                store = InMemoryKacheableStore(),
                configs = mapOf(
                    "load-concurrency-first" to CacheConfig(
                        "load-concurrency-first",
                        resilience = CacheResilienceConfig(maxConcurrentLoads = 1),
                    ),
                ),
            )

            assertFailsWith<IllegalArgumentException> {
                cache.cache(first(1)) { "unexpected" }
            }
        }

        test("blocking cache keys in one group share its declared limit") {
            val waitsStarted = CountDownLatch(1)
            val firstLoaderStarted = CountDownLatch(1)
            val secondLoaderStarted = CountDownLatch(1)
            val releaseFirst = CountDownLatch(1)
            val telemetry = WaitingTelemetry(waitsStarted)
            val cache = BlockingKacheable(
                InMemoryBlockingKacheableStore(),
                telemetry = telemetry,
            )
            val executor = Executors.newFixedThreadPool(2)

            try {
                val firstResult = executor.submit<String> {
                    cache(first(1)) {
                        firstLoaderStarted.countDown()
                        releaseFirst.await()
                        "first"
                    }
                }
                assertTrue(firstLoaderStarted.await(5, TimeUnit.SECONDS))
                val secondResult = executor.submit<String> {
                    cache(second(1)) {
                        secondLoaderStarted.countDown()
                        "second"
                    }
                }

                assertTrue(waitsStarted.await(5, TimeUnit.SECONDS))
                assertEquals(1, secondLoaderStarted.count)
                releaseFirst.countDown()
                assertEquals("first", firstResult.get(5, TimeUnit.SECONDS))
                assertEquals("second", secondResult.get(5, TimeUnit.SECONDS))
            } finally {
                releaseFirst.countDown()
                executor.shutdownNow()
            }
        }
    }
}

private class WaitingTelemetry(
    private val waitsStarted: CountDownLatch,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                waitsStarted.countDown()
            }
        }
}

private class AdmissionSignalTelemetry(
    private val signals: Map<String, CompletableDeferred<Unit>>,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (reason == CacheWaitReason.ConcurrencyLimit) {
                    signals[operation.cacheName]?.complete(Unit)
                }
            }
        }
}

private class AdmissionCountTelemetry(
    private val expectedWaiters: Int,
    private val allQueued: CompletableDeferred<Unit>,
) : CacheTelemetry {
    private val waiters = AtomicInteger()

    override fun begin(operation: CacheOperation): CacheObservation =
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (
                    reason == CacheWaitReason.ConcurrencyLimit &&
                    waiters.incrementAndGet() == expectedWaiters
                ) {
                    allQueued.complete(Unit)
                }
            }
        }
}
