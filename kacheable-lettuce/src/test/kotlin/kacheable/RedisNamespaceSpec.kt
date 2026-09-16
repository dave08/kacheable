package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.KacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.*
import kotlin.time.Duration

val RedisNamespaceSpec by testSuite {
    for (api in OrdinaryApi.entries) {
        testWithRedis("$api ordinary hash hits use HGET without script execution") {
            commands.hset("ordinary", "page", "cached")
            val store = api.store(this)
            commands.configResetstat()

            assertEquals("cached", store.getHashValue("ordinary", "page"))

            val statistics = commands.info("commandstats")
            assertTrue("cmdstat_hget:calls=1," in statistics, statistics)
            assertFalse("cmdstat_eval" in statistics, statistics)
            assertFalse("cmdstat_script" in statistics, statistics)
        }
        testWithRedis("$api ordinary string writes use SET without script execution") {
            val store = api.store(this)
            commands.configResetstat()

            store.set("ordinary", "value")

            val statistics = commands.info("commandstats")
            assertTrue("cmdstat_set:calls=1," in statistics, statistics)
            assertFalse("cmdstat_eval" in statistics, statistics)
            assertFalse("cmdstat_script" in statistics, statistics)
        }
        testWithRedis("$api ordinary and guarded hashes with the same logical name remain independent") {
            val guarded = RedisKacheableStore(connection)
            val initial = guarded.openHash("delivery", null)
            val written = assertIs<HashPublishResult.Published>(guarded.publishHash("delivery", initial, "page", "guarded", null, true))
            val ordinary = api.store(this)

            ordinary.setHashValue("delivery", "page", "ordinary")

            assertEquals("ordinary", ordinary.getHashValue("delivery", "page"))
            assertEquals(mapOf("page" to "guarded"), guarded.readHash("delivery", written.version, null))
        }
        testWithRedis("$api raw reserved namespace access fails before Redis writes") {
            val store = api.store(this)

            assertFailsWith<IllegalArgumentException> { store.set(RESERVED_KEY, "unsafe") }

            assertEquals(0L, commands.exists(RESERVED_KEY))
        }
        testWithRedis("$api ordinary wildcard deletion preserves guarded partitions") {
            val guarded = RedisKacheableStore(connection)
            val initial = guarded.openHash("delivery", null)
            commands.set("ordinary", "value")

            api.store(this).delete("*")

            assertNull(commands.get("ordinary"))
            assertEquals(emptyMap(), guarded.readHash("delivery", initial, null))
        }
        testWithRedis("$api reserved mutation targets are rejected before earlier writes execute") {
            assertFailsWith<IllegalArgumentException> {
                when (api) {
                    OrdinaryApi.Suspending -> RedisKacheableStore(connection).mutate {
                        set("ordinary", "must not be written")
                        set(RESERVED_KEY, "unsafe")
                    }
                    OrdinaryApi.Blocking -> RedisBlockingKacheableStore(connection).mutate {
                        set("ordinary", "must not be written")
                        set(RESERVED_KEY, "unsafe")
                    }
                }
            }

            assertEquals(0L, commands.exists("ordinary", RESERVED_KEY))
        }
    }
    testWithRedis("ordinary scans never expose reserved namespace entries even without version markers") {
        commands.hset(RESERVED_KEY, "field", "private")
        commands.hset("ordinary", "field", "public")

        val fields = RedisKacheableStore(connection).scanHashFields("*")

        assertEquals(listOf("ordinary"), fields.map { it.key })
    }
}

private const val RESERVED_KEY = "__kacheable:versioned-hash:v1:delivery"

private enum class OrdinaryApi {
    Suspending, Blocking;

    fun store(redis: RedisFixture): KacheableStore = when (this) {
        Suspending -> RedisKacheableStore(redis.connection)
        Blocking -> object : KacheableStore {
            private val store = RedisBlockingKacheableStore(redis.connection)
            override suspend fun delete(key: String) = store.delete(key)
            override suspend fun deleteHashValue(key: String, field: String) = store.deleteHashValue(key, field)
            override suspend fun set(key: String, value: String) = store.set(key, value)
            override suspend fun setHashValue(key: String, field: String, value: String) = store.setHashValue(key, field, value)
            override suspend fun get(key: String) = store.get(key)
            override suspend fun getHashValue(key: String, field: String) = store.getHashValue(key, field)
            override suspend fun setExpire(key: String, expiry: Duration) = store.setExpire(key, expiry)
        }
    }
}
