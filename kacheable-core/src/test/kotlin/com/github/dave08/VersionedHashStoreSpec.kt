package com.github.dave08

import com.github.dave08.kacheable.store.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

val VersionedHashOperationsSpec by testSuite {
    test("exact in-memory deletion does not enumerate unrelated storage keys") {
        val values = DirectAccessOnlyMap(mutableMapOf("target" to "value", "unrelated" to "kept"))
        val store = InMemoryKacheableStore(map = values)

        store.delete("target")

        assertNull(values["target"])
        assertEquals("kept", values["unrelated"])
    }
    test("exact in-memory field deletion does not enumerate unrelated hash fields") {
        val fields = DirectAccessOnlyMap(mutableMapOf("target" to "value", "unrelated" to "kept"))
        val store = InMemoryKacheableStore(hashMap = mutableMapOf("hash" to fields))

        store.deleteHashValuesMatching("hash", "target")

        assertNull(fields["target"])
        assertEquals("kept", fields["unrelated"])
    }
    testFixture { VersionedHashFixture() } asContextForEach {
        test("ordinary backing hash mutations cannot erase versioned entries") {
            val initial = store.openHash(key, null)
            val written = assertIs<HashPublishResult.Published>(
                store.publishHash(key, initial, "page", "chunk", null, true, "typed page"),
            )

            store.hashMap.clear()

            assertEquals(mapOf("page" to "chunk"), store.readHash(key, written.version, null))
            assertEquals(mapOf("page" to "typed page"), store.readHashMetadata(key, written.version))
        }
        test("ordinary backing maps do not expose versioned payloads or metadata") {
            val initial = store.openHash(key, null)
            store.publishHash(key, initial, "page", "chunk", null, true, "typed page")

            assertFalse(key in store.hashMap)
            assertTrue(store.scanHashFields("*").isEmpty())
        }
        test("ordinary backing hash collisions cannot replace an active versioned partition") {
            val initial = store.openHash(key, null)
            val written = assertIs<HashPublishResult.Published>(
                store.publishHash(key, initial, "page", "chunk", null, true),
            )

            store.hashMap[key] = mutableMapOf("page" to "ordinary")

            assertEquals(written.version, store.openHash(key, null))
            assertEquals(mapOf("page" to "chunk"), store.readHash(key, written.version, null))
            assertEquals("ordinary", store.getHashValue(key, "page"))
            assertEquals(listOf(HashFieldEntry(key, "page", "ordinary")), store.scanHashFields("*"))
        }
        test("ordinary whole-key deletion preserves versioned homonyms") {
            val initial = store.openHash(key, null)
            store.hashMap[key] = mutableMapOf("page" to "ordinary")

            store.delete(key)

            assertEquals(emptyMap(), store.readHash(key, initial, null))
            assertFalse(key in store.hashMap)
            assertEquals(initial, store.openHash(key, null))
        }
        test("expired versioned keys can be reused by ordinary storage") {
            val initial = store.openHash(key, 10.seconds)
            now = 11.seconds.inWholeNanoseconds

            store.setHashValue(key, "page", "ordinary")

            assertEquals("ordinary", store.getHashValue(key, "page"))
            assertNull(store.readHash(key, initial, null))
        }
        test("wildcard deletion retires matching versioned partitions and preserves other partitions") {
            val matching = store.openHash("delivery:one", null)
            val other = store.openHash("image:one", null)

            store.deleteHashes("delivery:*")

            assertNull(store.readHash("delivery:one", matching, null))
            assertEquals(emptyMap(), store.readHash("image:one", other, null))
        }
        test("metadata reads include stored fields whose key metadata is unavailable") {
            val initial = store.openHash(key, null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash(key, initial, "forward-only", "payload", null, true))
            assertEquals<Map<String, String?>?>(mapOf("forward-only" to null), store.readHashMetadata(key, written.version))
        }
        test("invalid versioned expiry is rejected without recording expiry") {
            assertFailsWith<IllegalArgumentException> { store.openHash(key, kotlin.time.Duration.ZERO) }
            assertTrue(store.expireCalls.isEmpty())
        }
        test("invalid publication expiry preserves versioned hash data and metadata") {
            val initial = store.openHash(key, null)
            val written = assertIs<HashPublishResult.Published>(store.publishHash(key, initial, "field", "winner", null, true, "key"))
            assertFailsWith<IllegalArgumentException> { store.publishHash(key, written.version, "field", "invalid", kotlin.time.Duration.INFINITE, false, "bad key") }
            assertEquals(mapOf("field" to "winner"), store.readHash(key, written.version, null))
            assertEquals(mapOf("field" to "key"), store.readHashMetadata(key, written.version))
        }
        test("versioned hash rejects a stale writer to a distinct field") {
            val initial = store.openHash(key, 10.seconds)
            val written = assertIs<HashPublishResult.Published>(store.publishHash(key, initial, "first", "one", 10.seconds, true))
            assertEquals(HashPublishResult.Conflict, store.publishHash(key, initial, "second", "two", 10.seconds, true))
            assertNull(store.readHash(key, initial, null))
            assertEquals(mapOf("first" to "one"), store.readHash(key, written.version, null))
        }
        test("versioned hash batch publication commits resolved fields as one revision") {
            val initial = store.openHash(key, 10.seconds)

            val published = assertIs<HashPublishManyResult.Success>(store.publishHashes(
                key,
                initial,
                linkedMapOf(
                    "first" to VersionedHashValue("one", "typed one"),
                    "second" to VersionedHashValue("two"),
                ),
                10.seconds,
                ifAbsent = true,
            ))

            assertEquals(initial.revision + 1, published.version.revision)
            assertEquals(mapOf("first" to "one", "second" to "two"), published.values)
            assertEquals(setOf("first", "second"), published.publishedFields)
            assertEquals(published.values, store.readHash(key, published.version, null))
            assertEquals(mapOf("first" to "typed one", "second" to null), store.readHashMetadata(key, published.version))
        }
        test("versioned hash batch publication rejects a stale revision without partial writes") {
            val stale = store.openHash(key, null)
            val current = assertIs<HashPublishResult.Published>(
                store.publishHash(key, stale, "winner", "kept", null, true),
            ).version

            assertEquals(HashPublishManyResult.Conflict, store.publishHashes(
                key,
                stale,
                linkedMapOf("first" to VersionedHashValue("one"), "second" to VersionedHashValue("two")),
                null,
                ifAbsent = false,
            ))
            assertEquals(mapOf("winner" to "kept"), store.readHash(key, current, null))
        }
        test("empty versioned hash batch validates its version without changing revision or expiry") {
            val initial = store.openHash(key, 10.seconds)
            val expiryCalls = store.expireCalls.size

            assertEquals(
                HashPublishManyResult.Success(initial, emptyMap(), emptySet()),
                store.publishHashes(key, initial, emptyMap(), 10.seconds, ifAbsent = true),
            )
            assertEquals(expiryCalls, store.expireCalls.size)
            val current = assertIs<HashPublishResult.Published>(
                store.publishHash(key, initial, "winner", "kept", null, true),
            ).version
            assertEquals(HashPublishManyResult.Conflict, store.publishHashes(
                key, initial, emptyMap(), 10.seconds, ifAbsent = true,
            ))
            assertEquals(mapOf("winner" to "kept"), store.readHash(key, current, null))
        }
        test("versioned hash batch if-absent publication retains winners and publishes misses") {
            val initial = store.openHash(key, null)
            val withWinner = assertIs<HashPublishResult.Published>(
                store.publishHash(key, initial, "first", "winner", null, true, "winner key"),
            ).version

            val batch = assertIs<HashPublishManyResult.Success>(store.publishHashes(
                key,
                withWinner,
                linkedMapOf(
                    "first" to VersionedHashValue("loser", "loser key"),
                    "second" to VersionedHashValue("two", "second key"),
                ),
                null,
                ifAbsent = true,
            ))

            assertEquals(mapOf("first" to "winner", "second" to "two"), batch.values)
            assertEquals(setOf("second"), batch.publishedFields)
            assertEquals(mapOf("first" to "winner key", "second" to "second key"), store.readHashMetadata(key, batch.version))
        }
        test("versioned hash losing insertion preserves expiry and revision") {
            val initial = store.openHash(key, 10.seconds)
            val written = assertIs<HashPublishResult.Published>(store.publishHash(key, initial, "first", "one", 10.seconds, true))
            now = 9.seconds.inWholeNanoseconds
            assertEquals(written.version, store.openHash(key, 10.seconds))
            assertEquals(HashPublishResult.Existing(written.version, "one"), store.publishHash(key, written.version, "first", "two", 10.seconds, true))
            now = 11.seconds.inWholeNanoseconds
            assertNull(store.readHash(key, written.version, null))
        }
        test("versioned hash rejects old generation after deletion and reopening") {
            val old = store.openHash(key, null)
            store.deleteHashes(key)
            val current = store.openHash(key, null)
            assertNotEquals(old.generation, current.generation)
            assertEquals(HashPublishResult.Conflict, store.publishHash(key, old, "field", "stale", null, false))
        }
        test("versioned deletion preserves an ordinary homonym") {
            val version = store.openHash(key, null)
            store.setHashValue(key, "field", "ordinary")

            store.deleteHashes(key)

            assertNull(store.readHash(key, version, null))
            assertEquals("ordinary", store.getHashValue(key, "field"))
        }
        test("versioned hash opens independently of an ordinary homonym") {
            store.setHashValue(key, "field", "ordinary")
            val version = store.openHash(key, null)
            assertEquals(emptyMap(), store.readHash(key, version, null))
            assertEquals("ordinary", store.getHashValue(key, "field"))
        }
        test("ordinary field mutations cannot change a versioned homonym") {
            val version = store.openHash(key, null)
            store.setHashValue(key, "field", "ordinary")
            store.deleteHashValue(key, "field")
            assertEquals(emptyMap(), store.readHash(key, version, null))
        }
    }
}

private class VersionedHashFixture {
    var now = 0L
    val key = "delivery"
    val store = InMemoryKacheableStore(nanoTime = { now })
}

private class DirectAccessOnlyMap<K, V>(private val delegate: MutableMap<K, V>) : MutableMap<K, V> by delegate {
    override val keys: MutableSet<K>
        get() = error("Exact operations must not enumerate keys")
}
