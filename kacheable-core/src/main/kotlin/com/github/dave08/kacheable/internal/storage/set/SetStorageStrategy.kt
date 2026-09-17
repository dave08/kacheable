package com.github.dave08.kacheable.internal.storage.set

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheLoadTrigger
import com.github.dave08.kacheable.CacheLoadResult
import com.github.dave08.kacheable.CacheMissPolicy
import com.github.dave08.kacheable.CacheNamingStrategy
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheReadAttempt
import com.github.dave08.kacheable.CacheReadResult
import com.github.dave08.kacheable.CacheRefreshPolicy
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.CacheWriteResult
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.PrimarySecondaryCacheArgs
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.CachedValue
import com.github.dave08.kacheable.internal.storage.classificationInvalidationPlan
import com.github.dave08.kacheable.internal.storage.invalidationPlan
import com.github.dave08.kacheable.internal.storage.invokeCacheLifecycle
import com.github.dave08.kacheable.internal.storage.keyForClassificationResult
import com.github.dave08.kacheable.internal.storage.setMembershipEntry
import com.github.dave08.kacheable.internal.storage.shouldWriteSetMembershipResult
import com.github.dave08.kacheable.primaryKey
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.CoroutineScope

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
        loadConcurrency: LoadConcurrencyGroup?,
        backgroundScope: () -> CoroutineScope,
        observation: OperationObservation,
        block: suspend (previous: Boolean?) -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        suspend fun readCached(attempt: CacheReadAttempt): CachedValue<Boolean>? {
            val started = observation.startTimer()
            if (store.isSetMember(membershipEntry.membersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
                observation.storageRead(attempt, CacheReadResult.Present, started)
                return CachedValue(true)
            }

            if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
                observation.storageRead(attempt, CacheReadResult.Present, started)
                return CachedValue(false)
            }

            observation.storageRead(attempt, CacheReadResult.Absent, started)
            return null
        }

        return invokeCacheLifecycle(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            missPolicy = missPolicy,
            refreshPolicy = refreshPolicy,
            observation = observation,
            restoreCached = null,
            readCached = ::readCached,
            save = { blockResult ->
                recordWrite(
                    observation,
                    shouldWriteSetMembershipResult(blockResult, cacheFalse, storeResultIf),
                ) {
                    store.replaceSetMembership(
                        member = member,
                        membersKey = membershipEntry.membersKey,
                        nonMembersKey = membershipEntry.nonMembersKey,
                        isMember = blockResult,
                        expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                        cacheFalse = cacheFalse,
                    )
                }
            },
            load = block,
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
        loadConcurrency: LoadConcurrencyGroup?,
        backgroundScope: () -> CoroutineScope,
        observation: OperationObservation,
        block: suspend (previous: R?) -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        suspend fun readCached(attempt: CacheReadAttempt): CachedValue<R>? {
            val started = observation.startTimer()
            values.forEach { value ->
                val key = membershipEntry.classifiedKey(valueName(value))
                if (store.isSetMember(key, member)) {
                    if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                    observation.storageRead(attempt, CacheReadResult.Present, started)
                    return CachedValue(value)
                }
            }

            observation.storageRead(attempt, CacheReadResult.Absent, started)
            return null
        }

        return invokeCacheLifecycle(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            missPolicy = missPolicy,
            refreshPolicy = refreshPolicy,
            observation = observation,
            restoreCached = null,
            readCached = ::readCached,
            save = { blockResult ->
                recordWrite(observation, storeResultIf(blockResult)) {
                    val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
                    store.replaceClassifiedMembership(
                        member = member,
                        targetKey = keyToWrite,
                        candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                        expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                    )
                }
            },
            load = block,
        )
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
        loadCoordinator: BlockingLoadConcurrencyCoordinator,
        loadConcurrency: LoadConcurrencyGroup?,
        observation: OperationObservation,
        block: () -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        val readStarted = observation.startTimer()
        if (store.isSetMember(membershipEntry.membersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
            observation.storageRead(CacheReadAttempt.Hot, CacheReadResult.Present, readStarted)
            observation.complete(CacheOperationResult.CachedValue)
            return true
        }

        if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
            if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
            observation.storageRead(CacheReadAttempt.Hot, CacheReadResult.Present, readStarted)
            observation.complete(CacheOperationResult.CachedValue)
            return false
        }

        observation.storageRead(CacheReadAttempt.Hot, CacheReadResult.Absent, readStarted)
        val blockResult = loadCoordinator.withPermit(name, loadConcurrency, observation) {
            observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)
            val loadStarted = observation.startTimer()
            try {
                block().also {
                    observation.loaderCompleted(
                        CacheLoadTrigger.Miss,
                        CacheExecution.Foreground,
                        CacheLoadResult.Success,
                        loadStarted,
                    )
                }
            } catch (t: Throwable) {
                observation.loaderCompleted(
                    CacheLoadTrigger.Miss,
                    CacheExecution.Foreground,
                    CacheLoadResult.Failure,
                    loadStarted,
                )
                throw t
            }
        }
        if (shouldWriteSetMembershipResult(blockResult, cacheFalse, saveResultIf)) {
            val writeStarted = observation.startTimer()
            store.replaceSetMembership(
                member = member,
                membersKey = membershipEntry.membersKey,
                nonMembersKey = membershipEntry.nonMembersKey,
                isMember = blockResult,
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                cacheFalse = cacheFalse,
            )
            observation.storageWrite(CacheWriteResult.Stored, writeStarted)
        } else {
            observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
        }

        observation.complete(CacheOperationResult.Loaded)
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
        loadCoordinator: BlockingLoadConcurrencyCoordinator,
        loadConcurrency: LoadConcurrencyGroup?,
        observation: OperationObservation,
        block: () -> R,
    ): R {
        require(values.isNotEmpty()) { "Set classification caches require at least one possible value." }
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        val readStarted = observation.startTimer()
        values.forEach { value ->
            val key = membershipEntry.classifiedKey(valueName(value))
            if (store.isSetMember(key, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                observation.storageRead(CacheReadAttempt.Hot, CacheReadResult.Present, readStarted)
                observation.complete(CacheOperationResult.CachedValue)
                return value
            }
        }

        observation.storageRead(CacheReadAttempt.Hot, CacheReadResult.Absent, readStarted)
        val blockResult = loadCoordinator.withPermit(name, loadConcurrency, observation) {
            observation.loaderStarted(CacheLoadTrigger.Miss, CacheExecution.Foreground)
            val loadStarted = observation.startTimer()
            try {
                block().also {
                    observation.loaderCompleted(
                        CacheLoadTrigger.Miss,
                        CacheExecution.Foreground,
                        CacheLoadResult.Success,
                        loadStarted,
                    )
                }
            } catch (t: Throwable) {
                observation.loaderCompleted(
                    CacheLoadTrigger.Miss,
                    CacheExecution.Foreground,
                    CacheLoadResult.Failure,
                    loadStarted,
                )
                throw t
            }
        }
        if (saveResultIf(blockResult)) {
            val writeStarted = observation.startTimer()
            val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
            store.replaceClassifiedMembership(
                member = member,
                targetKey = keyToWrite,
                candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
            )
            observation.storageWrite(CacheWriteResult.Stored, writeStarted)
        } else {
            observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
        }

        observation.complete(CacheOperationResult.Loaded)
        return blockResult
    }

    private suspend inline fun recordWrite(
        observation: OperationObservation,
        shouldWrite: Boolean,
        write: suspend () -> Unit,
    ) {
        if (!shouldWrite) {
            observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
            return
        }

        val started = observation.startTimer()
        try {
            write()
            observation.storageWrite(CacheWriteResult.Stored, started)
        } catch (t: Throwable) {
            observation.storageWrite(CacheWriteResult.Failed, started)
            throw t
        }
    }
}
