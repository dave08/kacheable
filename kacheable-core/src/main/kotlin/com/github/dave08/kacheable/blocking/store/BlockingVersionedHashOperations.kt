package com.github.dave08.kacheable.blocking.store

import com.github.dave08.kacheable.store.VersionedHashOperations
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashVersion
import kotlin.time.Duration

/** Blocking counterpart of [VersionedHashOperations]. */
interface BlockingVersionedHashOperations {
    /** Deletes matching whole hashes in the versioned namespace only. */
    fun deleteHashes(keyPattern: String)

    fun openHash(key: String, expiry: Duration?): HashVersion
    fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>?
    /** Reads every stored field without fetching payloads. A null map denotes conflict; a null value denotes absent metadata. */
    fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>?
    fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String? = null,
    ): HashPublishResult
}
