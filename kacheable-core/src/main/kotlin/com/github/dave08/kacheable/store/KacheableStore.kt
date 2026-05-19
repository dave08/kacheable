package com.github.dave08.kacheable.store

import kotlin.time.Duration

/**
 * One field/value entry inside a hash-like storage key.
 */
data class HashFieldEntry(
    val key: String,
    val field: String,
    val value: String,
)

/**
 * Suspended storage adapter used by Kacheable.
 *
 * Store implementations provide string, hash, and optionally set-membership operations. Default
 * methods compose primitive operations for stores that do not need specialized batching.
 */
interface KacheableStore {
    suspend fun delete(key: String)

    suspend fun deleteHashValue(key: String, field: String)

    suspend fun deleteHashValuesMatching(key: String, fieldPattern: String) {
        if (fieldPattern.contains("*")) {
            throw UnsupportedOperationException("This KacheableStore does not support partial hash invalidation.")
        }
        deleteHashValue(key, fieldPattern)
    }

    suspend fun deleteSetMember(key: String, member: String) {
        unsupportedSetMembership()
    }

    suspend fun set(key: String, value: String)

    suspend fun setHashValue(key: String, field: String, value: String)

    suspend fun addSetMember(key: String, member: String) {
        unsupportedSetMembership()
    }

    suspend fun get(key: String): String?

    suspend fun getHashValue(key: String, field: String): String?

    suspend fun isSetMember(key: String, member: String): Boolean {
        unsupportedSetMembership()
    }

    /**
     * Returns hash fields under keys matching [keyPattern].
     *
     * This is a low-level store capability used by features such as snapshot export. Stores that
     * cannot scan hash fields can leave the default unsupported implementation.
     */
    suspend fun scanHashFields(keyPattern: String): List<HashFieldEntry> {
        unsupportedHashScanning()
    }

    /**
     * Writes hash fields, optionally applying [expiry] to each touched hash key.
     */
    suspend fun writeHashFields(
        entries: Iterable<HashFieldEntry>,
        expiry: Duration? = null,
    ) {
        val touchedKeys = mutableSetOf<String>()
        entries.forEach { entry ->
            setHashValue(entry.key, entry.field, entry.value)
            touchedKeys += entry.key
        }
        expiry?.let { duration ->
            touchedKeys.forEach { key -> setExpire(key, duration) }
        }
    }

    suspend fun setExpire(key: String, expiry: Duration)

    suspend fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        mutate {
            set(key, value)
            setExpire(key, expiry)
        }
    }

    suspend fun setHashValueWithExpire(key: String, field: String, value: String, expiry: Duration) {
        mutate {
            setHashValue(key, field, value)
            setExpire(key, expiry)
        }
    }

    suspend fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        get(key)?.also { setExpire(key, expiry) }

    suspend fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration? = null,
        cacheFalse: Boolean = true,
    ) {
        mutate {
            deleteSetMember(membersKey, member)
            if (cacheFalse) {
                deleteSetMember(nonMembersKey, member)
            }

            val targetKey = when {
                isMember -> membersKey
                cacheFalse -> nonMembersKey
                else -> null
            }

            if (targetKey != null) {
                addSetMember(targetKey, member)
                expiry?.let { setExpire(targetKey, it) }
            }
        }
    }

    suspend fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration? = null,
    ) {
        mutate {
            candidateKeys.forEach { deleteSetMember(it, member) }
            addSetMember(targetKey, member)
            expiry?.let { setExpire(targetKey, it) }
        }
    }

    suspend fun mutate(block: suspend StoreMutationScope.() -> Unit) {
        DefaultStoreMutationScope(this).block()
    }

    private fun unsupportedSetMembership(): Nothing =
        throw UnsupportedOperationException("This KacheableStore does not support set membership storage.")

    private fun unsupportedHashScanning(): Nothing =
        throw UnsupportedOperationException("This KacheableStore does not support scanning hash fields.")
}
