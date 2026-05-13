package com.github.dave08.kacheable.blocking.store

import kotlin.time.Duration

/**
 * Blocking storage adapter used by [com.github.dave08.kacheable.blocking.BlockingKacheable].
 *
 * Store implementations provide string, hash, and optionally set-membership operations. Default
 * methods compose primitive operations for stores that do not need specialized batching.
 */
interface BlockingKacheableStore {
    fun delete(key: String)

    fun deleteHashValue(key: String, field: String)

    fun deleteHashValuesMatching(key: String, fieldPattern: String) {
        if (fieldPattern.contains("*")) {
            throw UnsupportedOperationException("This BlockingKacheableStore does not support partial hash invalidation.")
        }
        deleteHashValue(key, fieldPattern)
    }

    fun deleteSetMember(key: String, member: String) {
        unsupportedSetMembership()
    }

    fun set(key: String, value: String)

    fun setHashValue(key: String, field: String, value: String)

    fun addSetMember(key: String, member: String) {
        unsupportedSetMembership()
    }

    fun get(key: String): String?

    fun getHashValue(key: String, field: String): String?

    fun isSetMember(key: String, member: String): Boolean {
        unsupportedSetMembership()
    }

    fun setExpire(key: String, expiry: Duration)

    fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        mutate {
            set(key, value)
            setExpire(key, expiry)
        }
    }

    fun setHashValueWithExpire(key: String, field: String, value: String, expiry: Duration) {
        mutate {
            setHashValue(key, field, value)
            setExpire(key, expiry)
        }
    }

    fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        get(key)?.also { setExpire(key, expiry) }

    fun replaceSetMembership(
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

    fun replaceClassifiedMembership(
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

    fun mutate(block: BlockingStoreMutationScope.() -> Unit) {
        DefaultBlockingStoreMutationScope(this).block()
    }

    private fun unsupportedSetMembership(): Nothing =
        throw UnsupportedOperationException("This BlockingKacheableStore does not support set membership storage.")
}
