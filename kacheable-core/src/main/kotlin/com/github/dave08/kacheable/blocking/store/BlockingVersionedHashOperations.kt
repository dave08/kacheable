package com.github.dave08.kacheable.blocking.store

import com.github.dave08.kacheable.store.VersionedHashOperations
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashPublishManyResult
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.VersionedHashValue
import kotlin.time.Duration

/** Blocking counterpart of [VersionedHashOperations]. */
interface BlockingVersionedHashOperations {
    /** Deletes matching whole hashes in the versioned namespace only. */
    fun deleteHashes(keyPattern: String)

    fun openHash(key: String, expiry: Duration?): HashVersion
    /**
     * Opens or creates the hash and reads [fields] at one version without renewing an existing TTL.
     * Null [fields] selects all user fields; an empty list reads only the version.
     * A null result denotes a concurrent version conflict in the default two-operation fallback.
     */
    fun readHashSnapshot(
        key: String,
        expiry: Duration?,
        fields: List<String>?,
    ): HashReadSnapshot? {
        val version = openHash(key, expiry)
        val values = readHash(key, version, fields) ?: return null
        return HashReadSnapshot(version, values)
    }

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


    /** Atomically checks one version and publishes every supplied field as one revision. */
    fun publishHashes(
        key: String,
        version: HashVersion,
        values: Map<String, VersionedHashValue>,
        expiry: Duration?,
        ifAbsent: Boolean,
    ): HashPublishManyResult {
        if (values.isEmpty()) return if (readHash(key, version, emptyList()) == null) {
            HashPublishManyResult.Conflict
        } else {
            HashPublishManyResult.Success(version, emptyMap(), emptySet())
        }
        require(values.size == 1) {
            "This BlockingVersionedHashOperations backend does not support atomic multi-field publication."
        }
        val (field, candidate) = values.entries.single()
        return when (val result = publishHash(
            key, version, field, candidate.value, expiry, ifAbsent, candidate.metadata,
        )) {
            HashPublishResult.Conflict -> HashPublishManyResult.Conflict
            is HashPublishResult.Existing -> HashPublishManyResult.Success(
                result.version, mapOf(field to result.value), emptySet(),
            )
            is HashPublishResult.Published -> HashPublishManyResult.Success(
                result.version, mapOf(field to result.value), setOf(field),
            )
        }
    }
}
