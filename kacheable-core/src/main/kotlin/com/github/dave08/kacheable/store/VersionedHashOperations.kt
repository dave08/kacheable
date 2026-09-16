package com.github.dave08.kacheable.store

import kotlin.time.Duration

/** Opaque lifetime identity and monotonically increasing write revision of one hash. */
data class HashVersion(val generation: String, val revision: Long)

sealed interface HashPublishResult {
    data class Published(val version: HashVersion, val value: String) : HashPublishResult
    data class Existing(val version: HashVersion, val value: String) : HashPublishResult
    data object Conflict : HashPublishResult
}

/** Optional atomic hash capability. Metadata and data share one key and lifetime. */
interface VersionedHashOperations {
    /** Opens or creates a hash. Existing ordinary storage is rejected; opening never renews its TTL.
     * Non-null expiry must be finite and at least one millisecond.
     */
    suspend fun openHash(key: String, expiry: Duration?): HashVersion
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
}
