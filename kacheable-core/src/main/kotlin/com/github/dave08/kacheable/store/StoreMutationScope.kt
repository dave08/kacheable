package com.github.dave08.kacheable.store

import kotlin.time.Duration

interface StoreMutationScope {
    suspend fun delete(key: String)

    suspend fun deleteHashValue(key: String, field: String)

    suspend fun deleteSetMember(key: String, member: String)

    suspend fun set(key: String, value: String)

    suspend fun setHashValue(key: String, field: String, value: String)

    suspend fun addSetMember(key: String, member: String)

    suspend fun setExpire(key: String, expiry: Duration)
}

internal class DefaultStoreMutationScope(
    private val store: KacheableStore,
) : StoreMutationScope {
    override suspend fun delete(key: String) = store.delete(key)

    override suspend fun deleteHashValue(key: String, field: String) = store.deleteHashValue(key, field)

    override suspend fun deleteSetMember(key: String, member: String) = store.deleteSetMember(key, member)

    override suspend fun set(key: String, value: String) = store.set(key, value)

    override suspend fun setHashValue(key: String, field: String, value: String) = store.setHashValue(key, field, value)

    override suspend fun addSetMember(key: String, member: String) = store.addSetMember(key, member)

    override suspend fun setExpire(key: String, expiry: Duration) = store.setExpire(key, expiry)
}
