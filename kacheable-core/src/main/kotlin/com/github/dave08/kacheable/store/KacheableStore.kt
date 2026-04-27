package com.github.dave08.kacheable.store

import kotlin.time.Duration

interface KacheableStore {
    suspend fun delete(key: String)

    suspend fun deleteHashValue(key: String, field: String)

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

    suspend fun setExpire(key: String, expiry: Duration)

    suspend fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        mutate {
            set(key, value)
            setExpire(key, expiry)
        }
    }

    suspend fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        get(key)?.also { setExpire(key, expiry) }

    suspend fun mutate(block: suspend StoreMutationScope.() -> Unit) {
        DefaultStoreMutationScope(this).block()
    }

    private fun unsupportedSetMembership(): Nothing =
        throw UnsupportedOperationException("This KacheableStore does not support set membership storage.")
}
