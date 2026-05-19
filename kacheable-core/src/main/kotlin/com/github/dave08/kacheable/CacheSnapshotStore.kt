package com.github.dave08.kacheable

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * Durable snapshot slot names used by snapshot stores.
 */
enum class CacheSnapshotSlot(val storageName: String) {
    Latest("latest"),
    Previous("previous"),
}

/**
 * A concrete file/object inside one cache snapshot slot.
 */
sealed interface CacheSnapshotPart {
    val fileName: String

    data object Manifest : CacheSnapshotPart {
        override val fileName: String = "manifest.json"
    }

    data class Chunk(val id: String) : CacheSnapshotPart {
        override val fileName: String = "$id.jsonl.gz"
    }
}

/**
 * Address of a snapshot object.
 */
data class CacheSnapshotRef(
    val cacheName: String,
    val slot: CacheSnapshotSlot,
    val part: CacheSnapshotPart,
)

/**
 * Storage adapter for durable cache snapshots.
 *
 * Implementations should treat [CacheSnapshotRef] as an object address and store opaque bytes.
 * Snapshot encoding, chunking, retention, and restore semantics are owned by Kacheable.
 *
 * Most applications should use [S3CacheSnapshotStore] or [FileCacheSnapshotStore]. Implement this
 * interface only when adapting another object store.
 */
interface CacheSnapshotStore {
    /**
     * Returns the stored bytes for [ref], or `null` when that object does not exist.
     */
    suspend fun read(ref: CacheSnapshotRef): ByteArray?

    /**
     * Writes [bytes] to [ref], replacing any existing object at the same address.
     */
    suspend fun write(ref: CacheSnapshotRef, bytes: ByteArray)
}

/**
 * Snapshot store that drops writes and always misses.
 */
object NoopCacheSnapshotStore : CacheSnapshotStore {
    override suspend fun read(ref: CacheSnapshotRef): ByteArray? = null

    override suspend fun write(ref: CacheSnapshotRef, bytes: ByteArray) = Unit
}

/**
 * File-system snapshot store, useful for local development and tests.
 *
 * Example:
 *
 * ```kotlin
 * val cache = Kacheable(
 *     store = inMemoryStore,
 *     snapshotStore = FileCacheSnapshotStore(Path("/tmp/my-cache-snapshots")),
 * )
 * ```
 */
class FileCacheSnapshotStore(
    private val root: Path,
) : CacheSnapshotStore {
    override suspend fun read(ref: CacheSnapshotRef): ByteArray? =
        path(ref).takeIf { it.exists() }?.readBytes()

    override suspend fun write(ref: CacheSnapshotRef, bytes: ByteArray) {
        path(ref).also { it.parent.createDirectories() }.writeBytes(bytes)
    }

    private fun path(ref: CacheSnapshotRef): Path =
        root.resolve(ref.cacheName).resolve(ref.slot.storageName).resolve(ref.part.fileName)
}

/**
 * SDK-agnostic S3-style snapshot store.
 *
 * This class deliberately does not depend on an AWS SDK. Apps provide the object read/write
 * functions for their S3, MinIO, LocalStack, or compatible client.
 *
 * Example:
 *
 * ```kotlin
 * val snapshotStore = S3CacheSnapshotStore(
 *     bucket = "app-cache-snapshots",
 *     prefix = "product-cards/v1",
 *     readObject = { bucket, key -> s3.readBytesOrNull(bucket, key) },
 *     writeObject = { bucket, key, bytes -> s3.writeBytes(bucket, key, bytes) },
 * )
 * ```
 */
class S3CacheSnapshotStore(
    private val bucket: String,
    private val prefix: String = "",
    private val readObject: suspend (bucket: String, key: String) -> ByteArray?,
    private val writeObject: suspend (bucket: String, key: String, bytes: ByteArray) -> Unit,
) : CacheSnapshotStore {
    override suspend fun read(ref: CacheSnapshotRef): ByteArray? =
        readObject(bucket, key(ref))

    override suspend fun write(ref: CacheSnapshotRef, bytes: ByteArray) {
        writeObject(bucket, key(ref), bytes)
    }

    private fun key(ref: CacheSnapshotRef): String =
        listOf(
            prefix.trim('/'),
            ref.cacheName,
            ref.slot.storageName,
            ref.part.fileName,
        ).filter(String::isNotEmpty).joinToString("/")
}
