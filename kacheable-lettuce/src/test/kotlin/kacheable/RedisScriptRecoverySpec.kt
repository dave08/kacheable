package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.store.HashPublishResult
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

val RedisScriptRecoverySpec by testSuite {
    testWithRedis("suspending generated mutation carries its script after Redis script eviction") {
        val store = RedisKacheableStore(connection)
        store.mutate { setHashValue("images", "first", "thumbnail") }
        commands.scriptFlush()

        store.mutate {
            setHashValue("images", "second", "full size")
            deleteHashValue("images", "first")
        }

        assertEquals(mapOf("second" to "full size"), commands.hgetall("images"))
    }
    testWithRedis("suspending hash writes with expiry recover after Redis script eviction") {
        val store = RedisKacheableStore(connection)
        store.setHashValueWithExpire("images", "first", "thumbnail", 1.minutes)

        commands.scriptFlush()
        store.setHashValueWithExpire("images", "second", "full size", 1.minutes)

        assertEquals(mapOf("first" to "thumbnail", "second" to "full size"), commands.hgetall("images"))
    }
    testWithRedis("blocking hash writes with expiry recover after Redis script eviction") {
        val store = RedisBlockingKacheableStore(connection)
        store.setHashValueWithExpire("images", "first", "thumbnail", 1.minutes)

        commands.scriptFlush()
        store.setHashValueWithExpire("images", "second", "full size", 1.minutes)

        assertEquals(mapOf("first" to "thumbnail", "second" to "full size"), commands.hgetall("images"))
    }
    testWithRedis("suspending versioned publication advances once after Redis script eviction") {
        val store = RedisKacheableStore(connection)
        val initial = store.openHash("delivery", null)
        val first = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "first", "one", null, true))

        commands.scriptFlush()
        val second = assertIs<HashPublishResult.Published>(store.publishHash("delivery", first.version, "second", "two", null, true))

        assertEquals(initial.copy(revision = 2), second.version)
        assertEquals(mapOf("first" to "one", "second" to "two"), store.readHash("delivery", second.version, null))
    }
    testWithRedis("blocking versioned publication advances once after Redis script eviction") {
        val store = RedisBlockingKacheableStore(connection)
        val initial = store.openHash("delivery", null)
        val first = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "first", "one", null, true))

        commands.scriptFlush()
        val second = assertIs<HashPublishResult.Published>(store.publishHash("delivery", first.version, "second", "two", null, true))

        assertEquals(initial.copy(revision = 2), second.version)
        assertEquals(mapOf("first" to "one", "second" to "two"), store.readHash("delivery", second.version, null))
    }
    testWithRedis("blocking native mutation remains independent of the Redis script cache") {
        val store = RedisBlockingKacheableStore(connection)
        store.setHashValue("images", "first", "thumbnail")
        commands.scriptFlush()

        store.mutate {
            setHashValue("images", "second", "full size")
            deleteHashValue("images", "first")
        }

        assertEquals(mapOf("second" to "full size"), commands.hgetall("images"))
    }
}
