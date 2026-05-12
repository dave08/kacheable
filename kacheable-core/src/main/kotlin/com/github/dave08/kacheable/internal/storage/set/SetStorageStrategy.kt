package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.classificationInvalidationPlan
import com.github.dave08.kacheable.internal.storage.invalidationPlan
import com.github.dave08.kacheable.internal.storage.keyForClassificationResult
import com.github.dave08.kacheable.internal.storage.setMembershipEntry
import com.github.dave08.kacheable.internal.storage.shouldWriteSetMembershipResult
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.store.KacheableStore

internal object SetStorageStrategy {
    val storage: CacheStorage.Set = CacheStorage.Set

    suspend fun invalidateAll(
        store: KacheableStore,
        entryNamer: CacheEntryNamer,
        allRef: com.github.dave08.kacheable.StoredCacheAllRef<CacheStorage.Set>,
    ) {
        val entryName = entryNamer.nameAllEntries(allRef.name)
        store.delete(entryName.primaryKey)
    }

    fun invalidateAll(
        store: BlockingKacheableStore,
        entryNamer: CacheEntryNamer,
        allRef: com.github.dave08.kacheable.StoredCacheAllRef<CacheStorage.Set>,
    ) {
        val entryName = entryNamer.nameAllEntries(allRef.name)
        store.delete(entryName.primaryKey)
    }

    suspend fun <R> invalidateMembership(
        store: KacheableStore,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: suspend () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.invalidationPlan()
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) -> deleteSetMember(key, member) }
        }
        return block()
    }

    suspend fun <R> invalidateClassification(
        store: KacheableStore,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
        block: suspend () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.classificationInvalidationPlan(valueNames)
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) -> deleteSetMember(key, member) }
        }
        return block()
    }

    suspend fun invokeMembership(
        store: KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: suspend () -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        if (store.isSetMember(membershipEntry.membersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
            return true
        }

        if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
            return false
        }

        val blockResult = block()
        if (shouldWriteSetMembershipResult(blockResult, cacheFalse, saveResultIf)) {
            store.replaceSetMembership(
                member = member,
                membersKey = membershipEntry.membersKey,
                nonMembersKey = membershipEntry.nonMembersKey,
                isMember = blockResult,
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                cacheFalse = cacheFalse,
            )
        }

        return blockResult
    }

    suspend fun <R : Any> invokeClassification(
        store: KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: suspend () -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        values.forEach { value ->
            val key = membershipEntry.classifiedKey(valueName(value))
            if (store.isSetMember(key, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                return value
            }
        }

        val blockResult = block()
        if (saveResultIf(blockResult)) {
            val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
            store.replaceClassifiedMembership(
                member = member,
                targetKey = keyToWrite,
                candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
            )
        }

        return blockResult
    }

    fun <R> invalidateMembership(
        store: BlockingKacheableStore,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        block: () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.invalidationPlan()
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) -> deleteSetMember(key, member) }
        }
        return block()
    }

    fun <R> invalidateClassification(
        store: BlockingKacheableStore,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        valueNames: List<String>,
        block: () -> R,
    ): R {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val plan = membershipEntry.classificationInvalidationPlan(valueNames)
        store.mutate {
            plan.keys.forEach { delete(it) }
            plan.members.forEach { (key, member) -> deleteSetMember(key, member) }
        }
        return block()
    }

    fun invokeMembership(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        cacheFalse: Boolean,
        saveResultIf: (Boolean) -> Boolean,
        block: () -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        if (store.isSetMember(membershipEntry.membersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
            return true
        }

        if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
            return false
        }

        val blockResult = block()
        if (shouldWriteSetMembershipResult(blockResult, cacheFalse, saveResultIf)) {
            store.replaceSetMembership(
                member = member,
                membersKey = membershipEntry.membersKey,
                nonMembersKey = membershipEntry.nonMembersKey,
                isMember = blockResult,
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                cacheFalse = cacheFalse,
            )
        }

        return blockResult
    }

    fun <R : Any> invokeClassification(
        store: BlockingKacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        saveResultIf: (R) -> Boolean,
        block: () -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        values.forEach { value ->
            val key = membershipEntry.classifiedKey(valueName(value))
            if (store.isSetMember(key, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                return value
            }
        }

        val blockResult = block()
        if (saveResultIf(blockResult)) {
            val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
            store.replaceClassifiedMembership(
                member = member,
                targetKey = keyToWrite,
                candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
            )
        }

        return blockResult
    }
}
