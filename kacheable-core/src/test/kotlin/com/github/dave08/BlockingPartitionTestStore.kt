package com.github.dave08

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

/** Blocking boundary over the real in-memory atomic hash implementation. */
internal class BlockingPartitionTestStore : BlockingKacheableStore, BlockingVersionedHashOperations {
    private val backing = InMemoryKacheableStore()
    var fullReads = 0
        private set
    override fun delete(key: String) = runBlocking { backing.delete(key) }
    override fun deleteHashValue(key: String, field: String) = runBlocking { backing.deleteHashValue(key, field) }
    override fun set(key: String, value: String) = runBlocking { backing.set(key, value) }
    override fun setHashValue(key: String, field: String, value: String) = runBlocking { backing.setHashValue(key, field, value) }
    override fun get(key: String) = runBlocking { backing.get(key) }
    override fun getHashValue(key: String, field: String) = runBlocking { backing.getHashValue(key, field) }
    override fun setExpire(key: String, expiry: Duration) = runBlocking { backing.setExpire(key, expiry) }
    override fun openHash(key: String, expiry: Duration?) = runBlocking { backing.openHash(key, expiry) }
    override fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? {
        if (fields == null) fullReads++
        return runBlocking { backing.readHash(key, version, fields) }
    }
    override fun readHashMetadata(key: String, version: HashVersion) = runBlocking { backing.readHashMetadata(key, version) }
    override fun publishHash(key: String, version: HashVersion, field: String, value: String, expiry: Duration?, ifAbsent: Boolean, metadata: String?) =
        runBlocking { backing.publishHash(key, version, field, value, expiry, ifAbsent, metadata) }
}
