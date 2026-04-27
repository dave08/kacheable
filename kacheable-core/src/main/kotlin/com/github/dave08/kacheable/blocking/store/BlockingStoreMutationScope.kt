package com.github.dave08.kacheable.blocking.store

import kotlin.time.Duration

interface BlockingStoreMutationScope {
    fun delete(key: String)

    fun deleteHashValue(key: String, field: String)

    fun deleteSetMember(key: String, member: String)

    fun set(key: String, value: String)

    fun setHashValue(key: String, field: String, value: String)

    fun addSetMember(key: String, member: String)

    fun setExpire(key: String, expiry: Duration)
}

internal class DefaultBlockingStoreMutationScope(
    private val store: BlockingKacheableStore,
) : BlockingStoreMutationScope {
    override fun delete(key: String) = store.delete(key)

    override fun deleteHashValue(key: String, field: String) = store.deleteHashValue(key, field)

    override fun deleteSetMember(key: String, member: String) = store.deleteSetMember(key, member)

    override fun set(key: String, value: String) = store.set(key, value)

    override fun setHashValue(key: String, field: String, value: String) = store.setHashValue(key, field, value)

    override fun addSetMember(key: String, member: String) = store.addSetMember(key, member)

    override fun setExpire(key: String, expiry: Duration) = store.setExpire(key, expiry)
}
