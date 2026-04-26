package com.github.dave08.kacheable

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

    private fun unsupportedSetMembership(): Nothing =
        throw UnsupportedOperationException("This KacheableStore does not support set membership storage.")
}
