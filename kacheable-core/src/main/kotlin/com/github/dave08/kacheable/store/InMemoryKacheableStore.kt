package com.github.dave08.kacheable.store

import java.util.UUID
import kotlin.time.Duration

class InMemoryKacheableStore(
    val map: MutableMap<String, String> = mutableMapOf(),
    val hashMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf(),
    val sets: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val expiries: MutableMap<String, Duration> = mutableMapOf(),
    val expireCalls: MutableList<Pair<String, Duration>> = mutableListOf(),
    private val nanoTime: () -> Long = System::nanoTime,
) : KacheableStore, VersionedHashOperations {
    private val storageLock = Any()
    private val versionedHashes = mutableMapOf<String, VersionedHashState>()

    override suspend fun delete(key: String) {
        synchronized(storageLock) {
            if (!key.contains('*')) {
                map.remove(key)
                hashMap.remove(key)
                sets.remove(key)
                versionedHashes.remove(key)
                return
            }
            val matches = wildcardRegex(key)::matches
            map.keys.removeAll(matches)
            hashMap.keys.removeAll(matches)
            sets.keys.removeAll(matches)
            versionedHashes.keys.removeAll(matches)
        }
    }

    override suspend fun deleteHashValue(key: String, field: String) = withOrdinaryKey(key) {
        hashMap[key]?.remove(field)
        Unit
    }

    override suspend fun deleteHashValuesMatching(key: String, fieldPattern: String) = withOrdinaryKey(key) {
        hashMap[key]?.let { fields ->
            if (!fieldPattern.contains('*')) fields.remove(fieldPattern)
            else fields.keys.removeAll(wildcardRegex(fieldPattern)::matches)
            if (fields.isEmpty()) hashMap.remove(key)
        }
        Unit
    }

    override suspend fun deleteSetMember(key: String, member: String) = withOrdinaryKey(key) {
        sets[key]?.remove(member)
        Unit
    }

    override suspend fun set(key: String, value: String) = withOrdinaryKey(key) {
        map[key] = value
    }

    override suspend fun setHashValue(key: String, field: String, value: String) = withOrdinaryKey(key) {
        hashMap.getOrPut(key, ::mutableMapOf)[field] = value
    }

    override suspend fun addSetMember(key: String, member: String) = withOrdinaryKey(key) {
        sets.getOrPut(key, ::mutableSetOf) += member
    }

    override suspend fun get(key: String): String? = withOrdinaryKey(key) { map[key] }

    override suspend fun getHashValue(key: String, field: String): String? =
        withOrdinaryKey(key) { hashMap[key]?.get(field) }

    override suspend fun isSetMember(key: String, member: String): Boolean =
        withOrdinaryKey(key) { sets[key]?.contains(member) == true }

    override suspend fun scanHashFields(keyPattern: String): List<HashFieldEntry> = synchronized(storageLock) {
        val regex = wildcardRegex(keyPattern)
        hashMap.entries
            .filter { (key, _) -> regex.matches(key) && liveVersionedHash(key) == null }
            .flatMap { (key, fields) -> fields.map { (field, value) -> HashFieldEntry(key, field, value) } }
    }

    override suspend fun setExpire(key: String, expiry: Duration) = withOrdinaryKey(key) {
        recordExpiry(key, expiry)
    }

    override suspend fun openHash(key: String, expiry: Duration?): HashVersion = synchronized(storageLock) {
        validateHashExpiry(expiry)
        liveVersionedHash(key)?.let { return@synchronized it.version }
        check(key !in map && key !in hashMap && key !in sets) { "Existing key is not a versioned hash." }
        val state = VersionedHashState(HashVersion(UUID.randomUUID().toString(), 0))
        versionedHashes[key] = state
        applyVersionedExpiry(key, state, expiry)
        state.version
    }

    override suspend fun readHash(
        key: String,
        version: HashVersion,
        fields: List<String>?,
    ): Map<String, String>? = synchronized(storageLock) {
        matchingHash(key, version)?.readEntries(fields)
    }

    override suspend fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? =
        synchronized(storageLock) {
            matchingHash(key, version)?.entries?.mapValues { (_, entry) -> entry.metadata }
        }

    override suspend fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult = synchronized(storageLock) {
        validateHashExpiry(expiry)
        val state = matchingHash(key, version) ?: return@synchronized HashPublishResult.Conflict
        if (ifAbsent) {
            state.entries[field]?.let { return@synchronized HashPublishResult.Existing(version, it.value) }
        }
        state.publish(field, value, metadata)
        applyVersionedExpiry(key, state, expiry)
        HashPublishResult.Published(state.version, value)
    }

    /** Raw string keys cannot establish storage shape statically; guard that boundary in one place. */
    private fun <T> withOrdinaryKey(key: String, operation: () -> T): T = synchronized(storageLock) {
        check(liveVersionedHash(key) == null) { "Use versioned hash operations for this key." }
        operation()
    }

    private fun matchingHash(key: String, version: HashVersion): VersionedHashState? =
        liveVersionedHash(key)?.takeIf { it.version == version }

    private fun liveVersionedHash(key: String): VersionedHashState? {
        val state = versionedHashes[key] ?: return null
        if (state.deadlineNanos?.let { nanoTime() - it >= 0 } == true) {
            versionedHashes.remove(key)
            return null
        }
        return state
    }

    private fun validateHashExpiry(expiry: Duration?) {
        require(expiry == null || (expiry.isFinite() && expiry.inWholeMilliseconds > 0)) {
            "Hash expiry must be finite and at least one millisecond."
        }
    }

    private fun applyVersionedExpiry(key: String, state: VersionedHashState, expiry: Duration?) {
        if (expiry != null) {
            recordExpiry(key, expiry)
            state.deadlineNanos = nanoTime() + expiry.inWholeNanoseconds
        }
    }

    private fun recordExpiry(key: String, expiry: Duration) {
        expiries[key] = expiry
        expireCalls += key to expiry
    }

    private fun wildcardRegex(pattern: String): Regex =
        Regex("^${pattern.split("*").joinToString(".*") { Regex.escape(it) }}$")

    private class VersionedHashState(var version: HashVersion) {
        val entries = mutableMapOf<String, VersionedHashEntry>()
        var deadlineNanos: Long? = null

        fun readEntries(fields: List<String>?): Map<String, String> =
            if (fields == null) entries.mapValues { (_, entry) -> entry.value }
            else fields.mapNotNull { field -> entries[field]?.let { field to it.value } }.toMap()

        fun publish(field: String, value: String, metadata: String?) {
            val next = version.copy(revision = Math.addExact(version.revision, 1))
            entries[field] = VersionedHashEntry(value, metadata)
            version = next
        }
    }

    private data class VersionedHashEntry(val value: String, val metadata: String?)
}
