package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import de.infix.testBalloon.framework.core.testSuite
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val BlockingPartitionRoutingSpec by testSuite {
    test("ordinary blocking partition hit preserves an interrupted caller") {
        val cache = BlockingKacheable(BlockingPartitionTestStore())
        val pages = cacheKey(
            "pages", returns<String>(),
            key = partitioned(partition = keyPart<String>("group"), key = keyPart<Int>("page")),
        )
        cache.cache(pages("a", 1), block = { "stored" })
        val outcome = CompletableFuture<Pair<Result<String>, Boolean>>()

        val caller = thread(name = "interrupted-cache-caller") {
            Thread.currentThread().interrupt()
            try {
                val result = runCatching {
                    cache.cache(pages("a", 1), block = { error("A hit must not invoke the loader") })
                }
                outcome.complete(result to Thread.currentThread().isInterrupted)
            } finally {
                Thread.interrupted()
            }
        }
        try {
            val (result, interrupted) = outcome.get(10, TimeUnit.SECONDS)
            assertEquals("stored", result.getOrThrow())
            assertTrue(interrupted, "Ordinary synchronous cache hits must preserve the caller's interrupt flag")
        } finally {
            caller.join(10_000)
        }
    }
}
