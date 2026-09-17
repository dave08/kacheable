package com.github.dave08.kacheable.store

import kotlin.time.Duration

/** Opaque lifetime identity and monotonically increasing write revision of one hash. */
data class HashVersion(val generation: String, val revision: Long)

/** User values and the hash version at which they were read. */
data class HashReadSnapshot(
    val version: HashVersion,
    val values: Map<String, String>,
)

data class VersionedHashValue(
    val value: String,
    val metadata: String? = null,
)

sealed interface HashPublishResult {
    data class Published(val version: HashVersion, val value: String) : HashPublishResult
    data class Existing(val version: HashVersion, val value: String) : HashPublishResult
    data object Conflict : HashPublishResult
}

sealed interface HashPublishManyResult {
    data object Conflict : HashPublishManyResult
    data class Success(
        val version: HashVersion,
        val values: Map<String, String>,
        val publishedFields: Set<String>,
    ) : HashPublishManyResult
}

/**
 * Optional atomic hash capability. Keys are logical names in an independent namespace from
 * ordinary storage. Metadata and data share one key and lifetime within that namespace.
 */
interface VersionedHashOperations {
    /** Deletes matching whole hashes in this namespace only; accepts the store's key patterns. */
    suspend fun deleteHashes(keyPattern: String)

    /** Opens or creates a hash. Ordinary storage is independent; opening never renews its TTL.
     * Non-null expiry must be finite and at least one millisecond.
     */
    suspend fun openHash(key: String, expiry: Duration?): HashVersion
    /**
     * Opens or creates the hash and reads [fields] at one version without renewing an existing TTL.
     * Null [fields] selects all user fields; an empty list reads only the version.
     * A null result denotes a concurrent version conflict in the default two-operation fallback.
     * Backends may override this with one atomic operation.
     */
    suspend fun readHashSnapshot(
        key: String,
        expiry: Duration?,
        fields: List<String>?,
    ): HashReadSnapshot? {
        val version = openHash(key, expiry)
        val values = readHash(key, version, fields) ?: return null
        return HashReadSnapshot(version, values)
    }

    /** Reads atomically at [version]; null denotes conflict. Null [fields] selects all user fields. */
    suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>?
    /** Reads every stored field without fetching payloads. A null map denotes conflict; a null value denotes absent metadata. */
    suspend fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>?
    /** Checks version before presence. A write advances revision and atomically replaces value and optional metadata. */
    suspend fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String? = null,
    ): HashPublishResult

    /** Atomically checks one version and publishes every supplied field as one revision. */
    suspend fun publishHashes(
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
            "This VersionedHashOperations backend does not support atomic multi-field publication."
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
