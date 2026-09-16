package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheResilienceConfig
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.SingleFlightMode
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private data class MissPolicyValue(val id: Int, val value: String)

private val missPolicyOwnerKey = keyPart<Int>("owner")
private val missPolicyEntryKey = keyPart<String>("entry")
private val missPolicyCache = cacheKey(
    "miss-policy-cache",
    returns<MissPolicyValue>(),
    key = partitioned(partition = missPolicyOwnerKey, key = missPolicyEntryKey),
)
private val missPolicyFollowerCache = cacheKey(
    "miss-policy-followers",
    returns<Boolean>(),
    key = partitioned(partition = missPolicyOwnerKey, key = keyPart<Int>("account")),
)

val CacheMissPolicySpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("cacheIf overload keeps the old read-through behavior") {
            var calls = 0

            val first = cache.cache(
                missPolicyCache(1, "a"),
                cacheIf = { false },
            ) {
                calls++
                MissPolicyValue(1, "not-stored")
            }
            val second = cache.cache(missPolicyCache(1, "a")) {
                calls++
                MissPolicyValue(1, "stored")
            }

            assertEquals(MissPolicyValue(1, "not-stored"), first)
            assertEquals(MissPolicyValue(1, "stored"), second)
            assertEquals(2, calls)
        }

        test("load returns loaded values even when storeResultIf rejects storage") {
            var calls = 0

            val first = cache.cache(
                missPolicyCache(2, "a"),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.neverRefresh(),
                storeResultIf = { false },
            ) { previous ->
                assertNull(previous)
                calls++
                MissPolicyValue(2, "first")
            }
            val second = cache.cache(missPolicyCache(2, "a")) {
                calls++
                MissPolicyValue(2, "second")
            }

            assertEquals(MissPolicyValue(2, "first"), first)
            assertEquals(MissPolicyValue(2, "second"), second)
            assertEquals(2, calls)
        }

        test("loadInBackground returns fallback immediately and stores the loaded value later") {
            var calls = 0

            val first = cache.cache(
                missPolicyCache(3, "webp"),
                missPolicy = CacheMissPolicy.loadInBackground(
                    fallback = { MissPolicyValue(3, "original") },
                ),
            ) {
                calls++
                MissPolicyValue(3, "generated")
            }

            eventually {
                store.hashMap["miss-policy-cache:3"]?.get("webp") == """{"id":3,"value":"generated"}"""
            }

            val second = cache.cache(missPolicyCache(3, "webp")) {
                calls++
                MissPolicyValue(3, "regenerated")
            }

            assertEquals(MissPolicyValue(3, "original"), first)
            assertEquals(MissPolicyValue(3, "generated"), second)
            assertEquals(1, calls)
        }

        test("loadInBackground does not store fallback or rejected loaded values") {
            var calls = 0

            val result = cache.cache(
                missPolicyCache(4, "jpg"),
                missPolicy = CacheMissPolicy.loadInBackground(
                    fallback = { MissPolicyValue(4, "original") },
                ),
                refreshPolicy = CacheRefreshPolicy.neverRefresh(),
                storeResultIf = { false },
            ) { previous ->
                assertNull(previous)
                calls++
                MissPolicyValue(4, "generated")
            }

            eventually { calls == 1 }

            assertEquals(MissPolicyValue(4, "original"), result)
            assertNull(store.hashMap["miss-policy-cache:4"]?.get("jpg"))
        }

        test("load fallbackOnFailure returns fallback on failure without storing it") {
            val result = cache.cache(
                missPolicyCache(5, "png"),
                missPolicy = CacheMissPolicy.load(
                    fallbackOnFailure = { error -> MissPolicyValue(5, error.message ?: "fallback") },
                ),
            ) {
                error("generator failed")
            }

            assertEquals(MissPolicyValue(5, "generator failed"), result)
            assertNull(store.hashMap["miss-policy-cache:5"]?.get("png"))
        }

        test("load fallbackOnFailure stores successful loaded values") {
            var calls = 0

            val first = cache.cache(
                missPolicyCache(6, "png"),
                missPolicy = CacheMissPolicy.load(
                    fallbackOnFailure = { MissPolicyValue(6, "fallback") },
                ),
            ) {
                calls++
                MissPolicyValue(6, "generated")
            }
            val second = cache.cache(missPolicyCache(6, "png")) {
                calls++
                MissPolicyValue(6, "regenerated")
            }

            assertEquals(MissPolicyValue(6, "generated"), first)
            assertEquals(MissPolicyValue(6, "generated"), second)
            assertEquals(1, calls)
        }

        test("fallback policies also apply to set-backed typed caches") {
            var calls = 0

            val first = cache.cache(
                missPolicyFollowerCache(7, 42),
                missPolicy = CacheMissPolicy.loadInBackground(
                    fallback = { false },
                ),
            ) {
                calls++
                true
            }

            eventually {
                store.sets["miss-policy-followers:7"]?.contains("42") == true
            }

            val second = cache.cache(missPolicyFollowerCache(7, 42)) {
                calls++
                false
            }

            assertEquals(false, first)
            assertEquals(true, second)
            assertEquals(1, calls)
        }

        test("refreshIf in background returns previous immediately and stores refreshed value later") {
            cache.cache(missPolicyCache(8, "webp")) {
                MissPolicyValue(8, "previous")
            }

            val result = cache.cache(
                missPolicyCache(8, "webp"),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf(inBackground = true) { it.value == "previous" },
                storeResultIf = { true },
            ) { previous ->
                assertEquals(MissPolicyValue(8, "previous"), previous)
                MissPolicyValue(8, "refreshed")
            }

            assertEquals(MissPolicyValue(8, "previous"), result)
            eventually {
                store.hashMap["miss-policy-cache:8"]?.get("webp") == """{"id":8,"value":"refreshed"}"""
            }
        }

        test("refreshIf before return returns refreshed value or previous on failure") {
            cache.cache(missPolicyCache(9, "webp")) {
                MissPolicyValue(9, "previous")
            }

            val refreshed = cache.cache(
                missPolicyCache(9, "webp"),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { it.value == "previous" },
                storeResultIf = { true },
            ) { previous ->
                assertEquals(MissPolicyValue(9, "previous"), previous)
                MissPolicyValue(9, "refreshed")
            }

            val previousAfterFailure = cache.cache(
                missPolicyCache(9, "webp"),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.refreshIf { it.value == "refreshed" },
                storeResultIf = { true },
            ) { _ ->
                error("refresh failed")
            }

            assertEquals(MissPolicyValue(9, "refreshed"), refreshed)
            assertEquals(MissPolicyValue(9, "refreshed"), previousAfterFailure)
        }

        test("neverRefresh returns cached values without invoking the loader") {
            cache.cache(missPolicyCache(10, "webp")) {
                MissPolicyValue(10, "cached")
            }

            val result = cache.cache(
                missPolicyCache(10, "webp"),
                missPolicy = CacheMissPolicy.load(),
                refreshPolicy = CacheRefreshPolicy.neverRefresh(),
                storeResultIf = { true },
            ) { _ ->
                error("loader should not run")
            }

            assertEquals(MissPolicyValue(10, "cached"), result)
        }
    }

    testFixture {
        RefreshRaceFixture()
    } asContextForEach {
        test("refreshIf passes the latest still-stale value to the loader after waiting") {
            var loaderPrevious: MissPolicyValue? = null
            arrangeOldTargetValue()

            coroutineScope {
                val blocker = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cache(missPolicyCache(11, "blocker")) {
                        holdCompetingLoad()
                    }
                }
                awaitCompetingLoadBlocked()
                val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cache(
                        missPolicyCache(11, "target"),
                        missPolicy = CacheMissPolicy.load(),
                        refreshPolicy = CacheRefreshPolicy.refreshIf { it.value != "fresh" },
                        storeResultIf = { true },
                    ) { previous ->
                        loaderPrevious = previous
                        MissPolicyValue(11, "fresh")
                    }
                }

                awaitInitialTargetRead()
                replaceTargetWithNewerValue()
                releaseCompetingLoad()

                assertEquals(MissPolicyValue(11, "fresh"), refresh.await())
                blocker.await()
            }

            assertEquals(MissPolicyValue(11, "newer"), loaderPrevious)
        }

        test("refreshIf falls back to the latest still-stale value after waiting") {
            arrangeOldTargetValue()

            val result = coroutineScope {
                val blocker = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cache(missPolicyCache(11, "blocker")) {
                        holdCompetingLoad()
                    }
                }
                awaitCompetingLoadBlocked()
                val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                    cache.cache(
                        missPolicyCache(11, "target"),
                        missPolicy = CacheMissPolicy.load(),
                        refreshPolicy = CacheRefreshPolicy.refreshIf { it.value != "fresh" },
                        storeResultIf = { true },
                    ) {
                        error("refresh failed")
                    }
                }

                awaitInitialTargetRead()
                replaceTargetWithNewerValue()
                releaseCompetingLoad()

                refresh.await().also { blocker.await() }
            }

            assertEquals(MissPolicyValue(11, "newer"), result)
        }
    }
}

private class RefreshRaceFixture {
    private val store = RefreshRaceStore()
    private val competingLoadBlocked = CompletableDeferred<Unit>()
    private val releaseCompetingLoad = CompletableDeferred<Unit>()

    val cache = Kacheable(
        store = store,
        configs = mapOf(
            "miss-policy-cache" to CacheConfig(
                name = "miss-policy-cache",
                resilience = CacheResilienceConfig(
                    singleFlight = SingleFlightMode.Local,
                    maxConcurrentLoads = 1,
                ),
            ),
        ),
    )

    fun arrangeOldTargetValue() = store.arrangeOldTargetValue()

    suspend fun holdCompetingLoad(): MissPolicyValue {
        competingLoadBlocked.complete(Unit)
        releaseCompetingLoad.await()
        return MissPolicyValue(11, "blocker")
    }

    suspend fun awaitCompetingLoadBlocked() = competingLoadBlocked.await()

    suspend fun awaitInitialTargetRead() = store.awaitInitialTargetRead()

    fun replaceTargetWithNewerValue() = store.replaceTargetWithNewerValue()

    fun releaseCompetingLoad() {
        releaseCompetingLoad.complete(Unit)
    }
}

private suspend fun eventually(condition: suspend () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(1_000.milliseconds) {
            while (!condition()) {
                delay(10.milliseconds)
            }
        }
    }
}
