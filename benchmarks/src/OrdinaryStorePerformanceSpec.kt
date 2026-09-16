package kacheable

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite

val OrdinaryStorePerformanceSpec by testSuite {
    test("bounded ordinary feature-disabled store comparison") {
        val hashKey = cacheKey("perf-hash", returns<String>(), key = partitioned(keyPart<Int>("owner"), keyPart<Int>("item")))
        val stringKey = cacheKey("perf-string", returns<String>(), key = exact(keyPart<Int>("item")))
        val hashRef = hashKey(1, 2)
        val stringRef = stringKey(2)
        val memory = InMemoryKacheableStore()
        Kacheable(memory, emptyMap()).let { cache ->
            cache(hashRef) { "cached-value" }
            cache(stringRef) { "cached-value" }
            measure("memory-hash-cache-hit", 100_000, 200_000) { check(cache(hashRef) { error("miss") } == "cached-value") }
            measure("memory-string-cache-hit", 100_000, 200_000) { check(cache(stringRef) { error("miss") } == "cached-value") }
            measure("memory-hash-call-site", 100_000, 200_000) { check(cache(hashKey(1, 2)) { error("miss") } == "cached-value") }
            measure("memory-string-call-site", 100_000, 200_000) { check(cache(stringKey(2)) { error("miss") } == "cached-value") }
        }
        measure("memory-hash-store-hit", 100_000, 200_000) { check(memory.getHashValue("perf-hash:1", "2") != null) }
        measure("memory-string-store-hit", 100_000, 200_000) { check(memory.get("perf-string:2") != null) }
        repeat(10_000) { memory.map["unrelated-$it"] = "value" }
        memory.hashMap["fields"] = (0 until 10_000).associate { "field-$it" to "value" }.toMutableMap()
        measure("memory-exact-delete-10k", 500, 1000) { memory.delete("absent") }
        measure("memory-exact-field-delete-10k", 500, 1000) { memory.deleteHashValuesMatching("fields", "absent") }

        RedisFixture.start().use { redis ->
            val suspendStore = RedisKacheableStore(redis.connection)
            Kacheable(suspendStore, emptyMap()).let { cache ->
                cache(hashRef) { "cached-value" }
                cache(stringRef) { "cached-value" }
                measure("redis-suspend-hash-cache-hit", 1500, 1500) { check(cache(hashRef) { error("miss") } == "cached-value") }
                measure("redis-suspend-string-cache-hit", 1500, 1500) { check(cache(stringRef) { error("miss") } == "cached-value") }
                redis.commands.configResetstat()
                repeat(100) { cache(hashRef) { error("miss") } }
                println("PERF_COMMANDS hash ${redis.commands.info("commandstats").replace("\r\n", " | ")}")
                redis.commands.configResetstat()
                repeat(100) { cache(stringRef) { error("miss") } }
                println("PERF_COMMANDS string ${redis.commands.info("commandstats").replace("\r\n", " | ")}")
            }
            BlockingKacheable(RedisBlockingKacheableStore(redis.connection), emptyMap()).let { cache ->
                measure("redis-blocking-hash-cache-hit", 1500, 1500) { check(cache(hashRef) { error("miss") } == "cached-value") }
                measure("redis-blocking-string-cache-hit", 1500, 1500) { check(cache(stringRef) { error("miss") } == "cached-value") }
            }
        }
    }
}

private suspend fun measure(name: String, warmup: Int, iterations: Int, operation: suspend () -> Unit) {
    repeat(warmup) { operation() }
    val samples = List(5) {
        val start = System.nanoTime()
        repeat(iterations) { operation() }
        (System.nanoTime() - start).toDouble() / iterations
    }
    println("PERF $name iterations=$iterations ns/op=${samples.joinToString(",") { "%.1f".format(it) }} median=${samples.sorted()[2]}")
}
