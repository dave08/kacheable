package com.github.dave08.kacheable.blocking.store

import kotlin.time.Duration

interface BlockingKacheableStore {
    fun delete(key: String)

    fun deleteHashValue(key: String, field: String)

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

    fun mutate(block: BlockingStoreMutationScope.() -> Unit) {
        DefaultBlockingStoreMutationScope(this).block()
    }

    private fun unsupportedSetMembership(): Nothing =
        throw UnsupportedOperationException("This BlockingKacheableStore does not support set membership storage.")
}
