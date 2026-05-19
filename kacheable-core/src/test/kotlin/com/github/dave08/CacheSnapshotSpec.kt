@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheSnapshotPart
import com.github.dave08.kacheable.CacheSnapshotRef
import com.github.dave08.kacheable.CacheSnapshotSlot
import com.github.dave08.kacheable.FileCacheSnapshotStore
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.S3CacheSnapshotStore
import com.github.dave08.kacheable.SnapshotRestore
import com.github.dave08.kacheable.SnapshotRetention
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.defaultCacheNamingStrategy
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCodec
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.persistentSnapshot
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Serializable
private data class SnapshotValue(val id: Int, val value: String)

private val snapshotOwnerKey = keyPart<Int>("owner")
private val snapshotFormatKey = keyPart<String>("format")
private val snapshotCache = cacheKey(
    "snapshot-cache",
    returns<SnapshotValue>(),
    key = partitioned(partition = snapshotOwnerKey, key = snapshotFormatKey),
)

val CacheSnapshotSpec by testSuite {
    test("empty flush is skipped") {
        val snapshotStore = tempSnapshotStore()
        val coordinator = snapshotCoordinator(
            store = InMemoryKacheableStore(),
            snapshotStore = snapshotStore,
        )

        assertFalse(coordinator.flush("snapshot-cache"))
        assertNull(snapshotStore.read(CacheSnapshotRef("snapshot-cache", CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest)))
    }

    test("flush writes a latest manifest and chunked indexed payloads") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore().apply {
            putSnapshotValue(1, "webp", SnapshotValue(1, "one"))
            putSnapshotValue(1, "avif", SnapshotValue(2, "two"))
        }
        val coordinator = snapshotCoordinator(sourceStore, snapshotStore)

        assertTrue(coordinator.flush("snapshot-cache"))

        val manifest = assertNotNull(readManifest(snapshotStore, CacheSnapshotSlot.Latest))
        val records = readRecords(snapshotStore, CacheSnapshotSlot.Latest)

        assertEquals("snapshot-cache", manifest.cacheName)
        assertEquals(2, manifest.entryCount)
        assertEquals(2, records.size)
        assertEquals(
            setOf("""{"id":1,"value":"one"}""", """{"id":2,"value":"two"}"""),
            records.map { it.value }.toSet(),
        )
    }

    test("flush includes unpartitioned indexed cache hash entries") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore().apply {
            hashMap.getOrPut("snapshot-cache", ::mutableMapOf)["https://cdn.example/image.png"] =
                """{"id":10,"value":"unpartitioned"}"""
        }
        val coordinator = snapshotCoordinator(sourceStore, snapshotStore)

        assertTrue(coordinator.flush("snapshot-cache"))

        val records = readRecords(snapshotStore, CacheSnapshotSlot.Latest)
        assertEquals(
            listOf("""{"id":10,"value":"unpartitioned"}"""),
            records.map { it.value },
        )
    }

    test("latest snapshot rotates to previous on the next flush") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore()
        val coordinator = snapshotCoordinator(sourceStore, snapshotStore)

        sourceStore.putSnapshotValue(2, "webp", SnapshotValue(2, "previous"))
        assertTrue(coordinator.flush("snapshot-cache"))

        sourceStore.hashMap.clear()
        sourceStore.putSnapshotValue(2, "webp", SnapshotValue(2, "latest"))
        assertTrue(coordinator.flush("snapshot-cache"))

        assertEquals(
            listOf("""{"id":2,"value":"latest"}"""),
            readRecords(snapshotStore, CacheSnapshotSlot.Latest).map { it.value },
        )
        assertEquals(
            listOf("""{"id":2,"value":"previous"}"""),
            readRecords(snapshotStore, CacheSnapshotSlot.Previous).map { it.value },
        )
    }

    test("restore falls back to previous when latest is corrupt") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore()
        val sourceCoordinator = snapshotCoordinator(sourceStore, snapshotStore)

        sourceStore.putSnapshotValue(3, "webp", SnapshotValue(3, "previous"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))

        sourceStore.hashMap.clear()
        sourceStore.putSnapshotValue(3, "webp", SnapshotValue(3, "latest"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))
        snapshotStore.write(
            CacheSnapshotRef("snapshot-cache", CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest),
            "not-json".encodeToByteArray(),
        )

        val targetStore = InMemoryKacheableStore()
        val targetCoordinator = snapshotCoordinator(targetStore, snapshotStore)

        assertTrue(targetCoordinator.restoreCache("snapshot-cache"))
        assertEquals("""{"id":3,"value":"previous"}""", targetStore.hashMap["snapshot-cache:3"]?.get("webp"))
    }

    test("latest-only restore does not fall back to a previous slot") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore()
        val sourceCoordinator = snapshotCoordinator(sourceStore, snapshotStore)

        sourceStore.putSnapshotValue(300, "webp", SnapshotValue(300, "previous"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))

        sourceStore.hashMap.clear()
        sourceStore.putSnapshotValue(300, "webp", SnapshotValue(300, "latest"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))
        snapshotStore.write(
            CacheSnapshotRef("snapshot-cache", CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest),
            "not-json".encodeToByteArray(),
        )

        val targetStore = InMemoryKacheableStore()
        val targetCoordinator = snapshotCoordinator(
            targetStore,
            snapshotStore,
            config = snapshotConfig(retention = SnapshotRetention.LatestOnly),
        )

        assertFalse(targetCoordinator.restoreCache("snapshot-cache"))
        assertNull(targetStore.hashMap["snapshot-cache:300"]?.get("webp"))
    }

    test("restore falls back to previous without keeping partial latest chunks") {
        val snapshotStore = tempSnapshotStore()
        val sourceStore = InMemoryKacheableStore()
        val sourceCoordinator = snapshotCoordinator(sourceStore, snapshotStore)

        sourceStore.putSnapshotValue(30, "webp", SnapshotValue(30, "previous-webp"))
        sourceStore.putSnapshotValue(30, "avif", SnapshotValue(31, "previous-avif"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))

        sourceStore.hashMap.clear()
        sourceStore.putSnapshotValue(30, "webp", SnapshotValue(30, "latest-webp"))
        sourceStore.putSnapshotValue(30, "avif", SnapshotValue(31, "latest-avif"))
        assertTrue(sourceCoordinator.flush("snapshot-cache"))

        val chunk = requireNotNull(readManifest(snapshotStore, CacheSnapshotSlot.Latest)).chunks.first()
        snapshotStore.write(
            CacheSnapshotRef("snapshot-cache", CacheSnapshotSlot.Latest, CacheSnapshotPart.Chunk(chunk.id)),
            "not-gzip".encodeToByteArray(),
        )

        val targetStore = InMemoryKacheableStore()
        val targetCoordinator = snapshotCoordinator(targetStore, snapshotStore)

        assertTrue(targetCoordinator.restoreCache("snapshot-cache"))
        assertEquals(
            setOf("""{"id":30,"value":"previous-webp"}""", """{"id":31,"value":"previous-avif"}"""),
            targetStore.hashMap["snapshot-cache:30"]?.values?.toSet(),
        )
    }

    test("blocking restore makes snapshots available before the first typed read") {
        val snapshotStore = tempSnapshotStore()
        seedSnapshot(snapshotStore, 4, "webp", SnapshotValue(4, "restored"))

        val targetStore = InMemoryKacheableStore()
        val cache = Kacheable(
            targetStore,
            configs = mapOf(snapshotConfig(SnapshotRestore.Blocking).let { it.name to it }),
            snapshotStore = snapshotStore,
        )

        val restored: SnapshotValue = cache.cache(
            snapshotCache(4, "webp"),
            missPolicy = CacheMissPolicy.load(),
        ) {
            error("loader should not run after blocking restore")
        }

        assertEquals(SnapshotValue(4, "restored"), restored)
    }

    test("background restore warms configured snapshots after startup") {
        val snapshotStore = tempSnapshotStore()
        seedSnapshot(snapshotStore, 5, "webp", SnapshotValue(5, "background"))

        val targetStore = InMemoryKacheableStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            Kacheable(
                targetStore,
                configs = mapOf(snapshotConfig(SnapshotRestore.Background).let { it.name to it }),
                snapshotStore = snapshotStore,
                backgroundScope = scope,
            )

            eventually {
                targetStore.hashMap["snapshot-cache:5"]?.get("webp") == """{"id":5,"value":"background"}"""
            }
        } finally {
            scope.cancel()
        }
    }

    test("on-demand restore loads the requested chunk before miss policy runs") {
        val snapshotStore = tempSnapshotStore()
        seedSnapshot(
            snapshotStore,
            6 to ("webp" to SnapshotValue(6, "requested")),
            7 to ("avif" to SnapshotValue(7, "other")),
        )

        val targetStore = InMemoryKacheableStore()
        val coordinator = snapshotCoordinator(
            store = targetStore,
            snapshotStore = snapshotStore,
            config = snapshotConfig(SnapshotRestore.BackgroundWithOnDemandChunks),
        )

        coordinator.restoreEntry("snapshot-cache", StoreEntryName.Layered("snapshot-cache:6", "webp"))

        assertEquals("""{"id":6,"value":"requested"}""", targetStore.hashMap["snapshot-cache:6"]?.get("webp"))
        assertNull(targetStore.hashMap["snapshot-cache:7"]?.get("avif"))
    }

    test("S3 snapshot store maps refs to stable prefixed object keys") {
        val objects = mutableMapOf<Pair<String, String>, ByteArray>()
        val store = S3CacheSnapshotStore(
            bucket = "snapshot-bucket",
            prefix = "/catalog/product-cards/v1/",
            readObject = { bucket, key -> objects[bucket to key] },
            writeObject = { bucket, key, bytes -> objects[bucket to key] = bytes },
        )
        val ref = CacheSnapshotRef(
            cacheName = "product-cards",
            slot = CacheSnapshotSlot.Latest,
            part = CacheSnapshotPart.Chunk("abc123"),
        )

        store.write(ref, "payload".encodeToByteArray())

        assertEquals(
            "payload",
            store.read(ref)?.decodeToString(),
        )
        assertEquals(
            setOf("snapshot-bucket" to "catalog/product-cards/v1/product-cards/latest/abc123.jsonl.gz"),
            objects.keys,
        )
    }
}

private fun snapshotConfig(
    restore: SnapshotRestore = SnapshotRestore.Blocking,
    retention: SnapshotRetention = SnapshotRetention.LatestAndPrevious,
): CacheConfig = CacheConfig(
    name = "snapshot-cache",
    snapshot = persistentSnapshot(
        restore = restore,
        flushInterval = Duration.ZERO,
        retention = retention,
        chunkHashLength = 64,
    ),
)

private fun snapshotCoordinator(
    store: InMemoryKacheableStore,
    snapshotStore: FileCacheSnapshotStore,
    config: CacheConfig = snapshotConfig(),
): CacheSnapshotCoordinator = CacheSnapshotCoordinator(
    store = store,
    snapshotStore = snapshotStore,
    configs = mapOf(config.name to config),
    namingStrategy = defaultCacheNamingStrategy(),
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    clock = FixedSnapshotClock,
)

private object FixedSnapshotClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

private suspend fun seedSnapshot(
    snapshotStore: FileCacheSnapshotStore,
    owner: Int,
    field: String,
    value: SnapshotValue,
) {
    seedSnapshot(snapshotStore, owner to (field to value))
}

private suspend fun seedSnapshot(
    snapshotStore: FileCacheSnapshotStore,
    vararg values: Pair<Int, Pair<String, SnapshotValue>>,
) {
    val sourceStore = InMemoryKacheableStore()
    values.forEach { (owner, fieldValue) ->
        sourceStore.putSnapshotValue(owner, fieldValue.first, fieldValue.second)
    }
    assertTrue(snapshotCoordinator(sourceStore, snapshotStore).flush("snapshot-cache"))
}

private fun InMemoryKacheableStore.putSnapshotValue(owner: Int, field: String, value: SnapshotValue) {
    hashMap.getOrPut("snapshot-cache:$owner", ::mutableMapOf)[field] =
        """{"id":${value.id},"value":"${value.value}"}"""
}

private suspend fun readManifest(
    snapshotStore: FileCacheSnapshotStore,
    slot: CacheSnapshotSlot,
) = snapshotStore.read(CacheSnapshotRef("snapshot-cache", slot, CacheSnapshotPart.Manifest))
    ?.let(CacheSnapshotCodec::decodeManifest)

private suspend fun readRecords(
    snapshotStore: FileCacheSnapshotStore,
    slot: CacheSnapshotSlot,
) = readManifest(snapshotStore, slot)
    ?.chunks
    .orEmpty()
    .flatMap { chunk ->
        CacheSnapshotCodec.decodeRecords(
            requireNotNull(
                snapshotStore.read(CacheSnapshotRef("snapshot-cache", slot, CacheSnapshotPart.Chunk(chunk.id))),
            ),
        )
    }

private fun tempSnapshotStore(): FileCacheSnapshotStore =
    FileCacheSnapshotStore(Files.createTempDirectory(Path.of("/private/tmp"), "kacheable-snapshot-test-"))

private suspend fun eventually(condition: suspend () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(1_000) {
            while (!condition()) {
                delay(10)
            }
        }
    }
}
