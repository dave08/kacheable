package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val RedisHashScanSpec by testSuite {
    testWithRedis("ordinary hash scan excludes a partition converted to versioned storage before its page read") {
        commands.hset("shared", "ordinary", "old value")
        newConnection().use { writer ->
            val conversion = RedisHashConversionBeforePageRead(connection) {
                writer.sync().del("shared")
                val versioned = RedisBlockingKacheableStore(writer)
                val initial = versioned.openHash("shared", null)
                versioned.publishHash("shared", initial, "page", "guarded value", null, true, "typed page")
            }
            val store = RedisKacheableStore(conversion.connection)

            val fields = store.scanHashFields("shared")

            assertTrue(conversion.converted, "The partition must change immediately before its page read")
            assertEquals(emptyList(), fields)
        }
    }
}
