package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.classificationInvalidationPlan
import com.github.dave08.kacheable.internal.storage.invalidationPlan
import com.github.dave08.kacheable.internal.storage.keyForClassificationResult
import com.github.dave08.kacheable.internal.storage.setMembershipEntry
import com.github.dave08.kacheable.internal.storage.shouldWriteSetMembershipResult
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

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
        missPolicy: CacheMissPolicy<Boolean>,
        refreshPolicy: CacheRefreshPolicy<Boolean>,
        storeResultIf: (Boolean) -> Boolean,
        loadCoordinator: CacheLoadCoordinator,
        backgroundScope: () -> CoroutineScope,
        block: suspend (previous: Boolean?) -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        suspend fun readCached(): Boolean? {
            if (store.isSetMember(membershipEntry.membersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
                return true
            }

            if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
                return false
            }

            return null
        }

        readCached()?.let { cached ->
            return applyRefreshPolicy(
                cached = cached,
                cacheName = name,
                entryKey = "${membershipEntry.membersKey}:$member",
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                backgroundScope = backgroundScope,
                refreshPolicy = refreshPolicy,
                readFreshCached = {
                    readCached()?.takeUnless { cachedValue ->
                        refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(cachedValue)
                    }
                },
                loadAndSave = {
                    val blockResult = block(cached)
                    if (shouldWriteSetMembershipResult(blockResult, cacheFalse, storeResultIf)) {
                        store.replaceSetMembership(
                            member = member,
                            membersKey = membershipEntry.membersKey,
                            nonMembersKey = membershipEntry.nonMembersKey,
                            isMember = blockResult,
                            expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                            cacheFalse = cacheFalse,
                        )
                    }
                    blockResult
                },
            )
        }

        val loadAndSave = suspend {
            val blockResult = block(null)
            if (shouldWriteSetMembershipResult(blockResult, cacheFalse, storeResultIf)) {
                store.replaceSetMembership(
                    member = member,
                    membersKey = membershipEntry.membersKey,
                    nonMembersKey = membershipEntry.nonMembersKey,
                    isMember = blockResult,
                    expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                    cacheFalse = cacheFalse,
                )
            }
            blockResult
        }

        return applyMissPolicy(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            backgroundScope = backgroundScope,
            missPolicy = missPolicy,
            readCached = ::readCached,
            loadAndSave = loadAndSave,
        )
    }

    suspend fun <R : Any> invokeClassification(
        store: KacheableStore,
        configs: Map<String, CacheConfig>,
        namingStrategy: CacheNamingStrategy,
        name: String,
        cacheArgs: PrimarySecondaryCacheArgs,
        values: List<R>,
        valueName: (R) -> String,
        missPolicy: CacheMissPolicy<R>,
        refreshPolicy: CacheRefreshPolicy<R>,
        storeResultIf: (R) -> Boolean,
        loadCoordinator: CacheLoadCoordinator,
        backgroundScope: () -> CoroutineScope,
        block: suspend (previous: R?) -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        suspend fun readCached(): R? {
            values.forEach { value ->
                val key = membershipEntry.classifiedKey(valueName(value))
                if (store.isSetMember(key, member)) {
                    if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                    return value
                }
            }

            return null
        }

        readCached()?.let { cached ->
            return applyRefreshPolicy(
                cached = cached,
                cacheName = name,
                entryKey = "${membershipEntry.membersKey}:$member",
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                backgroundScope = backgroundScope,
                refreshPolicy = refreshPolicy,
                readFreshCached = {
                    readCached()?.takeUnless { cachedValue ->
                        refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(cachedValue)
                    }
                },
                loadAndSave = {
                    val blockResult = block(cached)
                    if (storeResultIf(blockResult)) {
                        val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
                        store.replaceClassifiedMembership(
                            member = member,
                            targetKey = keyToWrite,
                            candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                            expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                        )
                    }
                    blockResult
                },
            )
        }

        val loadAndSave = suspend {
            val blockResult = block(null)
            if (storeResultIf(blockResult)) {
                val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
                store.replaceClassifiedMembership(
                    member = member,
                    targetKey = keyToWrite,
                    candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                    expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                )
            }
            blockResult
        }

        return applyMissPolicy(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            backgroundScope = backgroundScope,
            missPolicy = missPolicy,
            readCached = ::readCached,
            loadAndSave = loadAndSave,
        )
    }

    private suspend fun <R> applyMissPolicy(
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        loadCoordinator: CacheLoadCoordinator,
        backgroundScope: () -> CoroutineScope,
        missPolicy: CacheMissPolicy<R>,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R = when (missPolicy) {
        is CacheMissPolicy.Load -> loadWithPolicy(
            cacheName = cacheName,
            entryKey = entryKey,
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            readCached = readCached,
            loadAndSave = loadAndSave,
            onFailure = { error ->
                missPolicy.fallbackOnFailure?.invoke(error) ?: throw error
            },
        )

        is CacheMissPolicy.LoadInBackground -> {
            val fallback = missPolicy.fallback()
            backgroundScope().launch {
                try {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryKey = entryKey,
                        store = store,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        readCached = readCached,
                        loadAndSave = loadAndSave,
                        onFailure = { throw it },
                    )
                } catch (_: TimeoutCancellationException) {
                    // Background load timeouts are failures, not caller-visible cancellation.
                } catch (t: CancellationException) {
                    throw t
                } catch (_: Throwable) {
                    // Background refresh failures are intentionally not surfaced to the caller.
                }
            }
            fallback
        }
    }

    private suspend fun <R> applyRefreshPolicy(
        cached: R,
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        loadCoordinator: CacheLoadCoordinator,
        backgroundScope: () -> CoroutineScope,
        refreshPolicy: CacheRefreshPolicy<R>,
        readFreshCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        return when (refreshPolicy) {
            is CacheRefreshPolicy.NeverRefresh -> cached
            is CacheRefreshPolicy.RefreshIf -> {
                if (!refreshPolicy.isStale(cached)) return cached
                if (refreshPolicy.inBackground) {
                    backgroundScope().launch {
                        try {
                            loadWithPolicy(
                                cacheName = cacheName,
                                entryKey = entryKey,
                                store = store,
                                config = config,
                                loadCoordinator = loadCoordinator,
                                readCached = readFreshCached,
                                loadAndSave = loadAndSave,
                                onFailure = { throw it },
                            )
                        } catch (_: TimeoutCancellationException) {
                            // Background refresh timeouts are failures, not caller-visible cancellation.
                        } catch (t: CancellationException) {
                            throw t
                        } catch (_: Throwable) {
                            // Background refresh failures are intentionally not surfaced to the caller.
                        }
                    }
                    cached
                } else {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryKey = entryKey,
                        store = store,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        readCached = readFreshCached,
                        loadAndSave = loadAndSave,
                        onFailure = { cached },
                    )
                }
            }
        }
    }

    private suspend fun <R> loadWithPolicy(
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        loadCoordinator: CacheLoadCoordinator,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
        onFailure: suspend (Throwable) -> R,
    ): R {
        val resilience = loadCoordinator.resilienceFor(config)
        return try {
            loadCoordinator.load(
                cacheName = cacheName,
                entryKey = entryKey,
                store = store,
                config = config,
                readCached = readCached,
                loadAndSave = loadAndSave,
            )
        } catch (t: TimeoutCancellationException) {
            readCached().takeIf { resilience.staleOnTimeout } ?: onFailure(t)
        } catch (t: CacheLoadTimeoutException) {
            readCached().takeIf { resilience.staleOnTimeout } ?: onFailure(t)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            readCached().takeIf { resilience.staleOnFailure } ?: onFailure(t)
        }
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
