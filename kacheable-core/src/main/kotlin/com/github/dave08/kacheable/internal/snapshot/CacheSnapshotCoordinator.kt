@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.github.dave08.kacheable.internal.snapshot

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheSnapshotPart
import com.github.dave08.kacheable.CacheSnapshotRef
import com.github.dave08.kacheable.CacheSnapshotSlot
import com.github.dave08.kacheable.CacheSnapshotStore
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.SnapshotRestore
import com.github.dave08.kacheable.SnapshotRetention
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.store.HashFieldEntry
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Clock

internal class CacheSnapshotCoordinator(
    private val store: KacheableStore,
    private val snapshotStore: CacheSnapshotStore,
    private val configs: Map<String, CacheConfig>,
    namingStrategy: CacheNamingStrategy,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val telemetryRuntime: CacheTelemetryRuntime = CacheTelemetryRuntime(NoopCacheTelemetry, null),
) {
    private val entryNamer = CacheEntryNamer(namingStrategy)
    private val restoredChunks = ConcurrentHashMap<String, MutableSet<String>>()
    private val chunkLocks = ConcurrentHashMap<String, Mutex>()

    private val snapshotConfigs: Map<String, CacheConfig> =
        configs.filterValues { it.snapshot != null }

    fun start() {
        snapshotConfigs.values.forEach { config ->
            when (config.snapshot?.restore) {
                SnapshotRestore.Blocking -> runBlocking { restoreCache(config.name) }
                SnapshotRestore.Background,
                SnapshotRestore.BackgroundWithOnDemandChunks,
                -> scope.launchSafely { restoreCache(config.name) }

                null -> Unit
            }
        }

        snapshotConfigs.values.forEach { config ->
            val interval = config.snapshot?.flushInterval ?: return@forEach
            if (interval <= kotlin.time.Duration.ZERO) return@forEach
            scope.launchSafely {
                while (true) {
                    delay(interval)
                    flush(config.name)
                }
            }
        }
    }

    suspend fun restoreEntry(cacheName: String, entryName: StoreEntryName) {
        val config = configs[cacheName] ?: return
        val snapshot = config.snapshot ?: return
        if (snapshot.restore != SnapshotRestore.BackgroundWithOnDemandChunks) return
        if (entryName !is StoreEntryName.Layered) return

        val chunkId = chunkId(entryName.key, entryName.entry, snapshot.chunkHashLength)
        val started = if (telemetryRuntime.enabled) System.nanoTime() else 0L
        try {
            val restored = restoreChunk(cacheName, chunkId)
            telemetryRuntime.maintenanceResult(
                cacheName,
                CacheStorageKind.HashMap,
                CacheMaintenanceOperation.SnapshotRestore,
                if (restored) CacheMaintenanceResult.Success else CacheMaintenanceResult.Skipped,
                started,
            )
        } catch (t: Throwable) {
            telemetryRuntime.maintenanceResult(
                cacheName,
                CacheStorageKind.HashMap,
                CacheMaintenanceOperation.SnapshotRestore,
                CacheMaintenanceResult.Failed,
                started,
            )
            throw t
        }
    }

    suspend fun restoreCache(cacheName: String): Boolean {
        val config = configs[cacheName] ?: return false
        if (config.snapshot == null) return false

        val started = if (telemetryRuntime.enabled) System.nanoTime() else 0L
        return try {
            restoreFromConfiguredSlots(config) { slot -> restoreCache(cacheName, slot) }.also { restored ->
                telemetryRuntime.maintenanceResult(
                    cacheName,
                    CacheStorageKind.HashMap,
                    CacheMaintenanceOperation.SnapshotRestore,
                    if (restored) CacheMaintenanceResult.Success else CacheMaintenanceResult.Skipped,
                    started,
                )
            }
        } catch (t: Throwable) {
            telemetryRuntime.maintenanceResult(
                cacheName,
                CacheStorageKind.HashMap,
                CacheMaintenanceOperation.SnapshotRestore,
                CacheMaintenanceResult.Failed,
                started,
            )
            throw t
        }
    }

    suspend fun flush(cacheName: String): Boolean {
        val config = configs[cacheName] ?: return false
        val snapshot = config.snapshot ?: return false
        val started = if (telemetryRuntime.enabled) System.nanoTime() else 0L
        return try {
            flush(cacheName, config, snapshot).also { flushed ->
                telemetryRuntime.maintenanceResult(
                    cacheName,
                    CacheStorageKind.HashMap,
                    CacheMaintenanceOperation.SnapshotFlush,
                    if (flushed) CacheMaintenanceResult.Success else CacheMaintenanceResult.Skipped,
                    started,
                )
            }
        } catch (t: Throwable) {
            telemetryRuntime.maintenanceResult(
                cacheName,
                CacheStorageKind.HashMap,
                CacheMaintenanceOperation.SnapshotFlush,
                CacheMaintenanceResult.Failed,
                started,
            )
            throw t
        }
    }

    private suspend fun flush(
        cacheName: String,
        config: CacheConfig,
        snapshot: com.github.dave08.kacheable.CacheSnapshotConfig,
    ): Boolean {
        val entries = scanIndexedEntries(cacheName)
        if (entries.isEmpty()) return false
        if (snapshot.retention == SnapshotRetention.LatestAndPrevious) {
            rotateLatestToPrevious(cacheName)
        }

        val chunks = entries.groupBy { chunkId(it.key, it.field, snapshot.chunkHashLength) }
        chunks.forEach { (chunkId, records) ->
            snapshotStore.write(
                CacheSnapshotRef(cacheName, CacheSnapshotSlot.Latest, CacheSnapshotPart.Chunk(chunkId)),
                CacheSnapshotCodec.encodeRecords(records),
            )
        }

        snapshotStore.write(
            CacheSnapshotRef(cacheName, CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest),
            CacheSnapshotCodec.encodeManifest(
                CacheSnapshotManifest(
                    cacheName = cacheName,
                    createdAtEpochMillis = clock.now().toEpochMilliseconds(),
                    entryCount = entries.size.toLong(),
                    chunks = chunks.map { (chunkId, records) ->
                        CacheSnapshotChunkManifest(chunkId, records.size.toLong())
                    }.sortedBy { it.id },
                ),
            ),
        )

        restoredChunks.remove(cacheName)
        return true
    }

    private suspend fun restoreCache(cacheName: String, slot: CacheSnapshotSlot): Boolean {
        val manifest = readManifest(cacheName, slot) ?: return false
        if (manifest.chunks.isEmpty()) return false

        val records = manifest.chunks.flatMap { chunk ->
            snapshotStore.read(CacheSnapshotRef(cacheName, slot, CacheSnapshotPart.Chunk(chunk.id)))
                ?.let(CacheSnapshotCodec::decodeRecords)
                ?: return false
        }
        store.writeHashFields(records, configs[cacheName]?.snapshotExpiry())
        return true
    }

    private suspend fun restoreChunk(cacheName: String, chunkId: String): Boolean {
        val config = configs[cacheName] ?: return false
        if (isRestored(cacheName, chunkId)) return false

        val lock = chunkLocks.getOrPut("$cacheName:$chunkId") { Mutex() }
        return lock.withLock {
            if (isRestored(cacheName, chunkId)) return@withLock false

            val restored = restoreFromConfiguredSlots(config) { slot -> restoreChunk(cacheName, slot, chunkId) }
            if (restored) {
                markRestored(cacheName, chunkId)
            }
            restored
        }
    }

    private suspend fun restoreFromConfiguredSlots(
        config: CacheConfig,
        restore: suspend (CacheSnapshotSlot) -> Boolean,
    ): Boolean {
        val latest = runCatching { restore(CacheSnapshotSlot.Latest) }
        latest.getOrNull()?.let { restored ->
            if (restored) return true
        }

        if (config.snapshot?.retention != SnapshotRetention.LatestAndPrevious) {
            latest.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            return false
        }

        val previous = runCatching { restore(CacheSnapshotSlot.Previous) }
        return previous.getOrElse { previousFailure ->
            val latestFailure = latest.exceptionOrNull()
            if (previousFailure is CancellationException) throw previousFailure
            if (latestFailure is CancellationException) throw latestFailure
            false
        }
    }

    private suspend fun restoreChunk(cacheName: String, slot: CacheSnapshotSlot, chunkId: String): Boolean {
        val manifest = readManifest(cacheName, slot) ?: return false
        if (manifest.chunks.none { it.id == chunkId }) return false

        val records = snapshotStore.read(CacheSnapshotRef(cacheName, slot, CacheSnapshotPart.Chunk(chunkId)))
            ?.let(CacheSnapshotCodec::decodeRecords)
            ?: return false

        store.writeHashFields(records, configs[cacheName]?.snapshotExpiry())
        return true
    }

    private suspend fun rotateLatestToPrevious(cacheName: String) {
        val manifest = readManifest(cacheName, CacheSnapshotSlot.Latest) ?: return
        manifest.chunks.forEach { chunk ->
            val bytes = snapshotStore.read(CacheSnapshotRef(cacheName, CacheSnapshotSlot.Latest, CacheSnapshotPart.Chunk(chunk.id)))
                ?: return@forEach
            snapshotStore.write(CacheSnapshotRef(cacheName, CacheSnapshotSlot.Previous, CacheSnapshotPart.Chunk(chunk.id)), bytes)
        }
        snapshotStore.read(CacheSnapshotRef(cacheName, CacheSnapshotSlot.Latest, CacheSnapshotPart.Manifest))
            ?.let { bytes ->
                snapshotStore.write(CacheSnapshotRef(cacheName, CacheSnapshotSlot.Previous, CacheSnapshotPart.Manifest), bytes)
            }
    }

    private suspend fun readManifest(cacheName: String, slot: CacheSnapshotSlot): CacheSnapshotManifest? =
        snapshotStore.read(CacheSnapshotRef(cacheName, slot, CacheSnapshotPart.Manifest))
            ?.let(CacheSnapshotCodec::decodeManifest)

    private fun indexedKeyPattern(cacheName: String): String =
        entryNamer.nameAllEntries(cacheName).primaryKey

    private suspend fun scanIndexedEntries(cacheName: String): List<HashFieldEntry> {
        val namedPattern = indexedKeyPattern(cacheName)
        val patterns = if (namedPattern == cacheName) {
            listOf(cacheName)
        } else {
            listOf(namedPattern, cacheName)
        }

        return patterns
            .flatMap { store.scanHashFields(it) }
            .distinctBy { "${it.key}\u0000${it.field}" }
    }

    private fun CacheConfig.snapshotExpiry(): kotlin.time.Duration? =
        takeIf { it.expiryType != com.github.dave08.kacheable.ExpiryType.none }?.expiry

    private fun isRestored(cacheName: String, chunkId: String): Boolean =
        restoredChunks[cacheName]?.contains(chunkId) == true

    private fun markRestored(cacheName: String, chunkId: String) {
        restoredChunks.getOrPut(cacheName) { ConcurrentHashMap.newKeySet() } += chunkId
    }

    private fun CoroutineScope.launchSafely(block: suspend () -> Unit) {
        launch {
            try {
                block()
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // Snapshot restore/flush should not crash the cache runtime.
            }
        }
    }
}

@Serializable
internal data class CacheSnapshotManifest(
    val cacheName: String,
    val version: Int = 1,
    val storage: String = "indexed",
    val createdAtEpochMillis: Long,
    val entryCount: Long,
    val codecName: String = "jsonl-gzip",
    val codecVersion: Int = 1,
    val chunks: List<CacheSnapshotChunkManifest>,
)

@Serializable
internal data class CacheSnapshotChunkManifest(
    val id: String,
    val entryCount: Long,
)

@Serializable
private data class CacheSnapshotRecord(
    val key: String,
    val field: String,
    val value: String,
)

internal object CacheSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeManifest(manifest: CacheSnapshotManifest): ByteArray =
        json.encodeToString(CacheSnapshotManifest.serializer(), manifest).encodeToByteArray()

    fun decodeManifest(bytes: ByteArray): CacheSnapshotManifest =
        json.decodeFromString(CacheSnapshotManifest.serializer(), bytes.decodeToString())

    fun encodeRecords(records: List<HashFieldEntry>): ByteArray {
        val raw = records.joinToString(separator = "\n") { entry ->
            json.encodeToString(
                CacheSnapshotRecord.serializer(),
                CacheSnapshotRecord(entry.key, entry.field, entry.value),
            )
        }.encodeToByteArray()

        return ByteArrayOutputStream().use { byteOutput ->
            GZIPOutputStream(byteOutput).use { gzip -> gzip.write(raw) }
            byteOutput.toByteArray()
        }
    }

    fun decodeRecords(bytes: ByteArray): List<HashFieldEntry> {
        val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            .decodeToString()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)

        return raw.map { line ->
            val record = json.decodeFromString(CacheSnapshotRecord.serializer(), line)
            HashFieldEntry(record.key, record.field, record.value)
        }.toList()
    }
}

private fun chunkId(key: String, field: String, hashLength: Int): String {
    val length = hashLength.coerceAtLeast(1)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$key\u0000$field".encodeToByteArray())
        .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    return digest.take(length)
}
