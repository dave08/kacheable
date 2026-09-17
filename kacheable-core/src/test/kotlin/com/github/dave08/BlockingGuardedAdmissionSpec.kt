package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

val BlockingGuardedAdmissionSpec by testSuite {
    testFixture { BlockingGuardedAdmissionFixture() } asContextForEach {
        for (many in listOf(false, true)) {
            val operation = if (many) "selection" else "scalar"
            test("blocking guarded $operation rechecks stored values after waiting for admission") {
                try {
                    startBlockingLoader()
                    val follower = executor.submit<Map<Int, String>> {
                        if (many) cache(pages.many("a", listOf(1))) { _, _ -> error("queued loader ran") }
                        else mapOf(1 to cache(pages("a", 1)) { _, _ -> error("queued loader ran") })
                    }
                    awaitAdmissionWait()
                    peer(pages("a", 1)) { _, _ -> "published-during-wait" }
                    releaseBlockingLoader()

                    assertEquals(mapOf(1 to "published-during-wait"), follower.get(10, TimeUnit.SECONDS))
                } finally {
                    close()
                }
            }
        }
    }
}

private class BlockingGuardedAdmissionFixture {
    private val group = loadConcurrencyGroup("database", LoadConcurrencyConfig(maxConcurrentLoads = 1))
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val queued = CountDownLatch(1)
    private val store = BlockingPartitionTestStore()
    private val configs = mapOf("pages" to CacheConfig("pages", partition = CachePartitionPolicy.OnDemand()))
    val executor = Executors.newFixedThreadPool(2)
    private var blocker: Future<String>? = null
    private val telemetry = CacheTelemetry {
        object : CacheObservation {
            override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
                if (reason == CacheWaitReason.ConcurrencyLimit) queued.countDown()
            }
        }
    }
    val cache = BlockingKacheable(store, configs = configs, telemetry = telemetry)
    val peer = BlockingKacheable(store, configs = configs)
    val pages = cacheKey("pages", returns<String>(), key = partitioned(
        partition = keyPart<String>("group"), key = keyPart<Int>("page"),
    ), loadConcurrency = group)
    private val ordinary = cacheKey("ordinary", returns<String>(), exact(keyPart<Int>("id")), loadConcurrency = group)

    fun startBlockingLoader() {
        blocker = executor.submit<String> {
            cache(ordinary(1)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "blocking loader was not released" }
                "blocker"
            }
        }
        check(entered.await(10, TimeUnit.SECONDS)) { "blocking loader did not start" }
    }

    fun awaitAdmissionWait() {
        check(queued.await(10, TimeUnit.SECONDS)) { "guarded loader did not wait for admission" }
    }

    fun releaseBlockingLoader() {
        release.countDown()
        checkNotNull(blocker).get(10, TimeUnit.SECONDS)
    }

    fun close() {
        release.countDown()
        executor.shutdownNow()
        check(executor.awaitTermination(10, TimeUnit.SECONDS)) { "blocking loaders did not finish" }
    }
}
