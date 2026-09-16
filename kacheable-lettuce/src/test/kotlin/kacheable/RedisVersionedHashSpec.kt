package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.versionedRedisKey
import com.github.dave08.kacheable.redis.RedisKacheableStore
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.VersionedHashOperations
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

val RedisVersionedHashSpec by testSuite {
    for (api in HashBackend.entries) {
        testWithRedis("$api guarded wildcard deletion preserves ordinary names and unrelated partitions") {
            val store = api.store(this)
            val deleted = store.openHash("delivery:one", null)
            val preserved = store.openHash("image:one", null)
            commands.hset("delivery:one", "page", "ordinary")

            store.deleteHashes("delivery:*")

            assertNull(store.readHash("delivery:one", deleted, null))
            assertEquals(emptyMap(), store.readHash("image:one", preserved, null))
            assertEquals("ordinary", commands.hget("delivery:one", "page"))
        }
        testWithRedis("$api metadata reads include every stored field even when key metadata is unavailable") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val withoutMetadata = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "forward-only", "large payload", null, true))
            val withMetadata = assertIs<HashPublishResult.Published>(store.publishHash("delivery", withoutMetadata.version, "typed", "other payload", null, true, "typed key"))
            val withEmptyMetadata = assertIs<HashPublishResult.Published>(store.publishHash("delivery", withMetadata.version, "empty", "another payload", null, true, ""))
            assertEquals<Map<String, String?>?>(mapOf("forward-only" to null, "typed" to "typed key", "empty" to ""), store.readHashMetadata("delivery", withEmptyMetadata.version))
        }
        testWithRedis("$api ordinary string write leaves the same-name guarded hash unchanged") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).set("delivery", "unsafe")
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).set("delivery", "unsafe")
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api ordinary expiring string write leaves the same-name guarded hash unchanged") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).setValueWithExpire("delivery", "unsafe", 5.minutes)
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).setValueWithExpire("delivery", "unsafe", 5.minutes)
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api ordinary expiry update cannot renew a versioned hash") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).setExpire("delivery", 5.minutes)
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).setExpire("delivery", 5.minutes)
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api ordinary mutation expiry update cannot renew a versioned hash") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).mutate { setExpire("delivery", 5.minutes) }
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).mutate { setExpire("delivery", 5.minutes) }
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api ordinary mutation string write leaves the same-name guarded hash unchanged") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).mutate { set("delivery", "unsafe") }
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).mutate { set("delivery", "unsafe") }
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api ordinary field writes cannot alter versioned data") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).setHashValue("delivery", "d:field", "unsafe")
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).setHashValue("delivery", "d:field", "unsafe")
            }
            assertEquals(emptyMap(), store.readHash("delivery", initial, null))
        }
        testWithRedis("$api ordinary field deletion cannot alter versioned data") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "field", "winner", null, true))
            when (api) {
                HashBackend.Suspending -> RedisKacheableStore(connection).deleteHashValue("delivery", "d:field")
                HashBackend.Blocking -> RedisBlockingKacheableStore(connection).deleteHashValue("delivery", "d:field")
            }
            assertEquals(mapOf("field" to "winner"), store.readHash("delivery", written.version, null))
        }
        testWithRedis("$api invalid hash expiry leaves storage absent") {
            assertFailsWith<IllegalArgumentException> { api.store(this).openHash("delivery", Duration.ZERO) }
            assertEquals(0L, commands.exists(versionedRedisKey("delivery")))
        }
        testWithRedis("$api invalid publication expiry preserves payload and metadata") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "field", "winner", null, true, "key"))
            assertFailsWith<IllegalArgumentException> { store.publishHash("delivery", written.version, "field", "invalid", Duration.INFINITE, false, "bad key") }
            assertEquals(mapOf("field" to "winner"), store.readHash("delivery", written.version, null))
            assertEquals(mapOf("field" to "key"), store.readHashMetadata("delivery", written.version))
        }
        testWithRedis("$api versioned hash metadata is published atomically with payload and excluded from data reads") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "first", "large payload", null, true, "typed key"))
            assertNull(store.readHashMetadata("delivery", initial))
            assertEquals(mapOf("first" to "typed key"), store.readHashMetadata("delivery", written.version))
            assertEquals(mapOf("first" to "large payload"), store.readHash("delivery", written.version, null))
        }
        testWithRedis("$api losing insertion preserves the winner's metadata") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "first", "winner", null, true, "winner key"))
            store.publishHash("delivery", written.version, "first", "loser", null, true, "loser key")
            assertEquals(mapOf("first" to "winner key"), store.readHashMetadata("delivery", written.version))
        }
        testWithRedis("$api versioned hash rejects distinct-field publication at stale revision") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            val winner = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "first", "one", 5.minutes, true))
            assertEquals(HashPublishResult.Conflict, store.publishHash("delivery", initial, "second", "two", 5.minutes, true))
            assertNull(store.readHash("delivery", initial, null))
            assertEquals(mapOf("first" to "one"), store.readHash("delivery", winner.version, null))
        }
        testWithRedis("$api versioned hash validates revision before returning an existing value") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            store.publishHash("delivery", initial, "field", "winner", null, true)
            assertEquals(HashPublishResult.Conflict, store.publishHash("delivery", initial, "field", "loser", null, true))
        }
        testWithRedis("$api versioned hash rejects a deleted generation without recreating it") {
            val store = api.store(this)
            val old = store.openHash("delivery", null)
            commands.del(versionedRedisKey("delivery"))
            assertEquals(HashPublishResult.Conflict, store.publishHash("delivery", old, "field", "stale", null, false))
            assertEquals(0L, commands.exists(versionedRedisKey("delivery")))
        }
        testWithRedis("$api versioned hash rejects an old generation after the key is reopened") {
            val store = api.store(this)
            val old = store.openHash("delivery", null)
            commands.del(versionedRedisKey("delivery"))
            val current = store.openHash("delivery", null)
            assertNotEquals(old.generation, current.generation)
            assertNull(store.readHash("delivery", old, null))
            assertEquals(HashPublishResult.Conflict, store.publishHash("delivery", old, "field", "stale", null, false))
        }
        testWithRedis("$api opening versioned storage preserves a same-name ordinary hash") {
            commands.hset("delivery", "field", "ordinary")
            api.store(this).openHash("delivery", null)
            assertEquals(mapOf("field" to "ordinary"), commands.hgetall("delivery"))
        }
        testWithRedis("$api versioned reads hide metadata and preserve arbitrary user field names") {
            val store = api.store(this)
            val initial = store.openHash("delivery", null)
            val field = "__kacheable_versioned_hash_generation_v1"
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, field, "user value", null, false))
            assertEquals(mapOf(field to "user value"), store.readHash("delivery", written.version, null))
            assertEquals(mapOf(field to "user value"), store.readHash("delivery", written.version, listOf(field, "absent")))
        }
        testWithRedis("$api opening reading and losing versioned insertion leave TTL unchanged") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            val written = assertIs<HashPublishResult.Published>(store.publishHash("delivery", initial, "field", "winner", 5.minutes, true))
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            store.openHash("delivery", 5.minutes)
            store.readHash("delivery", written.version, null)
            assertEquals(HashPublishResult.Existing(written.version, "winner"), store.publishHash("delivery", written.version, "field", "loser", 5.minutes, true))
            assertTrue(commands.pttl(versionedRedisKey("delivery")) in 1..60_000)
        }
        testWithRedis("$api successful versioned publication renews whole hash TTL") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 60_000)
            store.publishHash("delivery", initial, "field", "value", 5.minutes, true)
            assertTrue(commands.pttl(versionedRedisKey("delivery")) > 60_000)
        }
        testWithRedis("$api expired versioned hash conflicts without recreating storage") {
            val store = api.store(this)
            val initial = store.openHash("delivery", 5.minutes)
            commands.pexpire(versionedRedisKey("delivery"), 0)
            assertNull(store.readHash("delivery", initial, null))
            assertEquals(HashPublishResult.Conflict, store.publishHash("delivery", initial, "field", "stale", 5.minutes, true))
            assertEquals(0L, commands.exists(versionedRedisKey("delivery")))
        }
    }
}

private enum class HashBackend {
    Suspending, Blocking;

    fun store(redis: RedisFixture): VersionedHashOperations = when (this) {
        Suspending -> RedisKacheableStore(redis.connection)
        Blocking -> object : VersionedHashOperations {
            val store = RedisBlockingKacheableStore(redis.connection)
            override suspend fun deleteHashes(keyPattern: String) = store.deleteHashes(keyPattern)
            override suspend fun openHash(key: String, expiry: Duration?) = store.openHash(key, expiry)
            override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?) = store.readHash(key, version, fields)
            override suspend fun readHashMetadata(key: String, version: HashVersion) = store.readHashMetadata(key, version)
            override suspend fun publishHash(key: String, version: HashVersion, field: String, value: String, expiry: Duration?, ifAbsent: Boolean, metadata: String?) =
                store.publishHash(key, version, field, value, expiry, ifAbsent, metadata)
        }
    }
}
