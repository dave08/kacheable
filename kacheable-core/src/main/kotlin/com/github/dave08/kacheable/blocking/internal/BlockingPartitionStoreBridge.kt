package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.VersionedHashValue
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.VersionedHashOperations
import kotlin.time.Duration

/** Runs synchronous backend operations on the caller of the blocking cache boundary. */
internal class BlockingPartitionStoreBridge(
    private val store: BlockingKacheableStore,
) : KacheableStore, VersionedHashOperations {
    private val versioned = requireNotNull(store as? BlockingVersionedHashOperations) {
        "Partition contexts require BlockingVersionedHashOperations."
    }
    override suspend fun deleteHashes(keyPattern: String) = versioned.deleteHashes(keyPattern)
    override suspend fun delete(key: String) = store.delete(key)
    override suspend fun deleteHashValue(key: String, field: String) = store.deleteHashValue(key, field)
    override suspend fun set(key: String, value: String) = store.set(key, value)
    override suspend fun setHashValue(key: String, field: String, value: String) = store.setHashValue(key, field, value)
    override suspend fun get(key: String) = store.get(key)
    override suspend fun getHashValue(key: String, field: String) = store.getHashValue(key, field)
    override suspend fun setExpire(key: String, expiry: Duration) = store.setExpire(key, expiry)
    override suspend fun openHash(key: String, expiry: Duration?) = versioned.openHash(key, expiry)
    override suspend fun readHashSnapshot(key: String, expiry: Duration?, fields: List<String>?): HashReadSnapshot? =
        versioned.readHashSnapshot(key, expiry, fields)
    override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?) = versioned.readHash(key, version, fields)
    override suspend fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? = versioned.readHashMetadata(key, version)
    override suspend fun publishHash(key: String, version: HashVersion, field: String, value: String, expiry: Duration?, ifAbsent: Boolean, metadata: String?) =
        versioned.publishHash(key, version, field, value, expiry, ifAbsent, metadata)
    override suspend fun publishHashes(key: String, version: HashVersion, values: Map<String, VersionedHashValue>, expiry: Duration?, ifAbsent: Boolean) =
        versioned.publishHashes(key, version, values, expiry, ifAbsent)
}
