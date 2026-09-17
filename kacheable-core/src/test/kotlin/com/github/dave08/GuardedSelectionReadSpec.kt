package com.github.dave08

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.store.*
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.time.Duration

/** The snapshot is the storage boundary: coordination must not repeat its final read in the loader. */
val GuardedSelectionReadSpec by testSuite {
    testFixture { GuardedSelectionReadFixture() } asContextForEach {
        test("a hot guarded scalar uses one atomic selection snapshot") {
            cache(pages("a", 1)) { _, _ -> "one" }
            store.resetReads()

            assertEquals("one", cache(pages("a", 1)) { _, _ -> error("unexpected loader") })

            store.assertReads(snapshots = 1)
        }
        test("a hot guarded selection uses one atomic selection snapshot") {
            cache(pages.many("a", listOf(1, 2))) { keys, _ -> keys.associateWith { "page-$it" } }
            store.resetReads()

            assertEquals(mapOf(1 to "page-1", 2 to "page-2"), cache(pages.many("a", listOf(1, 2))) { _, _ ->
                error("unexpected loader")
            })

            store.assertReads(snapshots = 1)
        }
        test("a cold guarded scalar reuses the final coordination snapshot in its loader") {
            assertEquals("one", cache(pages("a", 1)) { _, _ -> "one" })

            store.assertReads(snapshots = 3)
        }
        test("a cold guarded selection reuses the final coordination snapshot in its loader") {
            assertEquals(mapOf(1 to "one"), cache(pages.many("a", listOf(1))) { _, _ -> mapOf(1 to "one") })

            store.assertReads(snapshots = 3)
        }
    }
}

private class GuardedSelectionReadFixture {
    val store = SelectionSnapshotStore()
    val cache = Kacheable(store, configs = mapOf("pages" to CacheConfig(
        "pages",
        partition = CachePartitionPolicy.OnDemand(
            publication = CachePublication.IfAbsent,
            coordination = CacheCoordination.Partition,
        ),
    )))
    val pages = cacheKey("pages", returns<String>(), key = partitioned(
        partition = keyPart<String>("group"), key = enumerableKeyPart<Int>("page"),
    ))
}

private class SelectionSnapshotStore(
    private val backing: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backing, VersionedHashOperations by backing {
    private var snapshots = 0
    private var opens = 0
    private var versionedReads = 0

    override suspend fun readHashSnapshot(key: String, expiry: Duration?, fields: List<String>?): HashReadSnapshot? {
        snapshots++
        val version = backing.openHash(key, expiry)
        return backing.readHash(key, version, fields)?.let { HashReadSnapshot(version, it) }
    }

    override suspend fun openHash(key: String, expiry: Duration?): HashVersion {
        opens++
        return backing.openHash(key, expiry)
    }

    override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
        versionedReads++
        return backing.readHash(key, version, fields)
    }

    fun resetReads() {
        snapshots = 0
        opens = 0
        versionedReads = 0
    }

    fun assertReads(snapshots: Int) {
        assertEquals(snapshots, this.snapshots, "atomic selection snapshots")
        assertEquals(0, opens, "redundant generation opens")
        assertEquals(0, versionedReads, "redundant versioned selection reads")
    }
}
