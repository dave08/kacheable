package com.github.dave08.kacheable.blocking.internal

import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration

/** Adapts the synchronous ordinary store to the shared suspending batch engine. */
internal class BlockingStoreBridge(
    private val store: BlockingKacheableStore,
) : KacheableStore {
    override suspend fun delete(key: String) = store.delete(key)
    override suspend fun deleteHashValue(key: String, field: String) = store.deleteHashValue(key, field)
    override suspend fun deleteHashValuesMatching(key: String, fieldPattern: String) =
        store.deleteHashValuesMatching(key, fieldPattern)
    override suspend fun deleteSetMember(key: String, member: String) = store.deleteSetMember(key, member)
    override suspend fun set(key: String, value: String) = store.set(key, value)
    override suspend fun setHashValue(key: String, field: String, value: String) = store.setHashValue(key, field, value)
    override suspend fun addSetMember(key: String, member: String) = store.addSetMember(key, member)
    override suspend fun get(key: String): String? = store.get(key)
    override suspend fun getValues(keys: List<String>): Map<String, String> = store.getValues(keys)
    override suspend fun getHashValue(key: String, field: String): String? = store.getHashValue(key, field)
    override suspend fun getHashValues(key: String, fields: List<String>): Map<String, String> =
        store.getHashValues(key, fields)
    override suspend fun isSetMember(key: String, member: String): Boolean = store.isSetMember(key, member)
    override suspend fun areSetMembers(key: String, members: List<String>): Set<String> =
        store.areSetMembers(key, members)
    override suspend fun setExpire(key: String, expiry: Duration) = store.setExpire(key, expiry)
    override suspend fun setValueWithExpire(key: String, value: String, expiry: Duration) =
        store.setValueWithExpire(key, value, expiry)
    override suspend fun setHashValueWithExpire(key: String, field: String, value: String, expiry: Duration) =
        store.setHashValueWithExpire(key, field, value, expiry)
    override suspend fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        store.getValueRefreshingExpire(key, expiry)
    override suspend fun getValuesRefreshingExpire(keys: List<String>, expiry: Duration): Map<String, String> =
        store.getValuesRefreshingExpire(keys, expiry)
    override suspend fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration?,
        cacheFalse: Boolean,
    ) = store.replaceSetMembership(member, membersKey, nonMembersKey, isMember, expiry, cacheFalse)

    override suspend fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration?,
    ) = store.replaceClassifiedMembership(member, targetKey, candidateKeys, expiry)

    override suspend fun mutate(block: suspend StoreMutationScope.() -> Unit) {
        store.mutate {
            val blockingScope = this
            runBlocking { SuspendingMutationScope(blockingScope).block() }
        }
    }
}

private class SuspendingMutationScope(
    private val scope: BlockingStoreMutationScope,
) : StoreMutationScope {
    override suspend fun delete(key: String) = scope.delete(key)
    override suspend fun deleteHashValue(key: String, field: String) = scope.deleteHashValue(key, field)
    override suspend fun deleteSetMember(key: String, member: String) = scope.deleteSetMember(key, member)
    override suspend fun set(key: String, value: String) = scope.set(key, value)
    override suspend fun setHashValue(key: String, field: String, value: String) = scope.setHashValue(key, field, value)
    override suspend fun addSetMember(key: String, member: String) = scope.addSetMember(key, member)
    override suspend fun setExpire(key: String, expiry: Duration) = scope.setExpire(key, expiry)
}
