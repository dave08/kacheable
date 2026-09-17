package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.AdmissionAwareDistributedSingleFlightStore
import com.github.dave08.kacheable.store.DistributedLoadLease
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.VersionedHashOperations
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val GuardedCoordinationRecheckSpec by testSuite {
    test("a guarded scalar queued for admission returns a concurrently published winner") {
        withTimeout(5.seconds) {
            coroutineScope {
                val store = LeaseTestStore()
                val queued = CompletableDeferred<Unit>()
                val releaseBlocker = CompletableDeferred<Unit>()
                val blockerStarted = CompletableDeferred<Unit>()
                val group = loadConcurrencyGroup(
                    "guarded-recheck-admission",
                    LoadConcurrencyConfig(maxConcurrentLoads = 1),
                )
                val pages = guardedPages(loadConcurrency = group)
                val blocker = cacheKey(
                    "guarded-recheck-blocker",
                    returns<String>(),
                    exact(keyPart<Int>("id")),
                    loadConcurrency = group,
                )
                val waitingCache = guardedCache(
                    store = store,
                    telemetry = AdmissionQueuedTelemetry(queued),
                )
                val publishingCache = uncoordinatedGuardedCache(store.backing)
                var waitingLoads = 0

                val blockingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                    waitingCache.cache(blocker(0)) {
                        blockerStarted.complete(Unit)
                        releaseBlocker.await()
                        "blocker"
                    }
                }
                blockerStarted.await()
                val waitingLoad = async(start = CoroutineStart.UNDISPATCHED) {
                    waitingCache.cache(pages(PARTITION, 1)) { _, _ ->
                        waitingLoads++
                        "waiting-loader"
                    }
                }
                queued.await()

                assertEquals(
                    "winner",
                    publishingCache.cache(pages(PARTITION, 1)) { _, _ -> "winner" },
                )
                releaseBlocker.complete(Unit)

                assertEquals("winner", waitingLoad.await())
                assertEquals(0, waitingLoads)
                assertEquals(0, store.acquisitions)
                assertEquals("blocker", blockingLoad.await())
            }
        }
    }

    test("a guarded selection rechecks winners published while acquiring its lease") {
        val store = LeaseTestStore()
        val pages = guardedPages()
        val publishingCache = uncoordinatedGuardedCache(store.backing)
        store.beforeLease = {
            publishingCache.cache(pages.many(PARTITION, listOf(1, 2))) { keys, _ ->
                keys.associateWith { "winner-$it" }
            }
        }
        val cache = guardedCache(store)
        var loads = 0

        val result = cache.cache(pages.many(PARTITION, listOf(1, 2))) { keys, _ ->
            loads++
            keys.associateWith { "loader-$it" }
        }

        assertEquals(mapOf(1 to "winner-1", 2 to "winner-2"), result)
        assertEquals(0, loads)
        assertEquals(1, store.acquisitions)
        assertEquals(1, store.releases)
    }

    test("a guarded scalar retries against the replacement generation created during lease acquisition") {
        val store = LeaseTestStore()
        val pages = guardedPages()
        val publishingCache = uncoordinatedGuardedCache(store.backing)
        store.beforeLease = {
            publishingCache.invalidate(pages.partition(PARTITION))
            publishingCache.cache(pages(PARTITION, 1)) { _, _ -> "fresh-winner" }
        }
        val cache = guardedCache(store)
        var loads = 0

        val result = cache.cache(pages(PARTITION, 1)) { _, _ ->
            loads++
            "stale-loader"
        }

        assertEquals("fresh-winner", result)
        assertEquals(0, loads)
        assertEquals(1, store.acquisitions)
        assertEquals(1, store.releases)
    }
}

private const val GUARDED_RECHECK_CACHE = "guarded-coordination-recheck"
private const val PARTITION = "delivery"

private fun guardedPages(loadConcurrency: LoadConcurrencyGroup? = null) = cacheKey(
    GUARDED_RECHECK_CACHE,
    returns<String>(),
    key = partitioned(
        partition = keyPart<String>("partition"),
        key = keyPart<Int>("entry"),
    ),
    loadConcurrency = loadConcurrency,
)

private fun guardedCache(
    store: KacheableStore,
    telemetry: CacheTelemetry = NoopCacheTelemetry,
) = Kacheable(
    store = store,
    configs = mapOf(GUARDED_RECHECK_CACHE to guardedRecheckConfig(SingleFlightMode.Redis)),
    telemetry = telemetry,
)

private fun uncoordinatedGuardedCache(store: KacheableStore) = Kacheable(
    store = store,
    configs = mapOf(GUARDED_RECHECK_CACHE to guardedRecheckConfig(SingleFlightMode.None)),
)

private fun guardedRecheckConfig(singleFlight: SingleFlightMode) = CacheConfig(
    name = GUARDED_RECHECK_CACHE,
    partition = CachePartitionPolicy.OnDemand(
        publication = CachePublication.IfAbsent,
        coordination = CacheCoordination.Partition,
    ),
    resilience = CacheResilienceConfig(singleFlight = singleFlight),
)

private class AdmissionQueuedTelemetry(
    private val queued: CompletableDeferred<Unit>,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation = object : CacheObservation {
        override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
            if (
                operation.cacheName == GUARDED_RECHECK_CACHE &&
                reason == CacheWaitReason.ConcurrencyLimit
            ) {
                queued.complete(Unit)
            }
        }
    }
}

private class LeaseTestStore(
    val backing: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backing,
    VersionedHashOperations by backing,
    AdmissionAwareDistributedSingleFlightStore {
    var beforeLease: suspend () -> Unit = {}
    var acquisitions: Int = 0
        private set
    var releases: Int = 0
        private set
    private var nextOwner = 0
    private var activeOwner: Int? = null

    override suspend fun tryAcquireDistributedLoadLease(
        key: String,
        lockLease: Duration,
    ): DistributedLoadLease {
        beforeLease()
        beforeLease = {}
        check(activeOwner == null) { "Test lease '$key' is already owned by $activeOwner" }
        val owner = ++nextOwner
        activeOwner = owner
        acquisitions++
        return object : DistributedLoadLease {
            private var released = false

            override suspend fun release() {
                check(!released) { "Test lease '$key' was released twice" }
                check(activeOwner == owner) {
                    "Test lease '$key' owner $owner cannot release active owner $activeOwner"
                }
                released = true
                activeOwner = null
                releases++
            }
        }
    }

    override suspend fun <R> runWithDistributedSingleFlight(
        key: String,
        lockLease: Duration,
        waitTimeout: Duration,
        pollInterval: Duration,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R = error("Guarded recheck tests must use the admission-aware lease path for '$key'.")
}
