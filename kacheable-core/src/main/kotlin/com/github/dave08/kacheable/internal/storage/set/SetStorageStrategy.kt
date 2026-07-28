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
import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.internal.BlockingLoadConcurrencyCoordinator
import com.github.dave08.kacheable.internal.ObservationContext
import com.github.dave08.kacheable.internal.OperationObservation
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
        loadConcurrency: LoadConcurrencyGroup?,
        backgroundScope: () -> CoroutineScope,
        observation: OperationObservation,
        block: suspend (previous: Boolean?) -> Boolean,
    ): Boolean {
        val membershipEntry = setMembershipEntry(name, cacheArgs, namingStrategy)
        val member = membershipEntry.requiredMember
        val config = configs[name]

        suspend fun readCached(attempt: CacheReadAttempt): Boolean? {
            val started = observation.startTimer()
            if (store.isSetMember(membershipEntry.membersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.membersKey, config.expiry)
                observation.storageRead(attempt, CacheReadResult.Present, started)
                return true
            }

            if (cacheFalse && store.isSetMember(membershipEntry.nonMembersKey, member)) {
                if (config?.expiryType == ExpiryType.after_access) store.setExpire(membershipEntry.nonMembersKey, config.expiry)
                observation.storageRead(attempt, CacheReadResult.Present, started)
                return false
            }

            observation.storageRead(attempt, CacheReadResult.Absent, started)
            return null
        }

        readCached(CacheReadAttempt.Hot)?.let { cached ->
            val observed = applyRefreshPolicy(
                cached = cached,
                cacheName = name,
                entryKey = "${membershipEntry.membersKey}:$member",
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                loadConcurrency = loadConcurrency,
                backgroundScope = backgroundScope,
                observation = observation,
                refreshPolicy = refreshPolicy,
                readFreshCached = {
                    readCached(CacheReadAttempt.SingleFlightRecheck)?.takeUnless { cachedValue ->
                        refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(cachedValue)
                    }
                },
                loadAndSave = { trigger, execution ->
                    loadAndSaveObserved(observation, trigger, execution, { block(cached) }) { blockResult ->
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
                    }
                },
            )
            observation.complete(observed.result)
            return observed.value
        }

        val loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> Boolean = { trigger, execution ->
            loadAndSaveObserved(observation, trigger, execution, { block(null) }) { blockResult ->
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
            }
        }

        val observed = applyMissPolicy(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            observation = observation,
            missPolicy = missPolicy,
            readCached = { readCached(CacheReadAttempt.SingleFlightRecheck) },
            loadAndSave = loadAndSave,
        )
        observation.complete(observed.result)
        return observed.value
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

        suspend fun readCached(attempt: CacheReadAttempt): R? {
            val started = observation.startTimer()
            values.forEach { value ->
                val key = membershipEntry.classifiedKey(valueName(value))
                if (store.isSetMember(key, member)) {
                    if (config?.expiryType == ExpiryType.after_access) store.setExpire(key, config.expiry)
                    observation.storageRead(attempt, CacheReadResult.Present, started)
                    return value
                }
            }

            observation.storageRead(attempt, CacheReadResult.Absent, started)
            return null
        }

        readCached(CacheReadAttempt.Hot)?.let { cached ->
            val observed = applyRefreshPolicy(
                cached = cached,
                cacheName = name,
                entryKey = "${membershipEntry.membersKey}:$member",
                store = store,
                config = config,
                loadCoordinator = loadCoordinator,
                loadConcurrency = loadConcurrency,
                backgroundScope = backgroundScope,
                observation = observation,
                refreshPolicy = refreshPolicy,
                readFreshCached = {
                    readCached(CacheReadAttempt.SingleFlightRecheck)?.takeUnless { cachedValue ->
                        refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.isStale(cachedValue)
                    }
                },
                loadAndSave = { trigger, execution ->
                    loadAndSaveObserved(observation, trigger, execution, { block(cached) }) { blockResult ->
                        recordWrite(observation, storeResultIf(blockResult)) {
                            val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
                            store.replaceClassifiedMembership(
                                member = member,
                                targetKey = keyToWrite,
                                candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                                expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                            )
                        }
                    }
                },
            )
            observation.complete(observed.result)
            return observed.value
        }

        val loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R = { trigger, execution ->
            loadAndSaveObserved(observation, trigger, execution, { block(null) }) { blockResult ->
                recordWrite(observation, storeResultIf(blockResult)) {
                    val keyToWrite = membershipEntry.keyForClassificationResult(blockResult, values, valueName)
                    store.replaceClassifiedMembership(
                        member = member,
                        targetKey = keyToWrite,
                        candidateKeys = values.map { value -> membershipEntry.classifiedKey(valueName(value)) },
                        expiry = config?.takeIf { it.expiryType != ExpiryType.none }?.expiry,
                    )
                }
            }
        }

        val observed = applyMissPolicy(
            cacheName = name,
            entryKey = "${membershipEntry.membersKey}:$member",
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            backgroundScope = backgroundScope,
            observation = observation,
            missPolicy = missPolicy,
            readCached = { readCached(CacheReadAttempt.SingleFlightRecheck) },
            loadAndSave = loadAndSave,
        )
        observation.complete(observed.result)
        return observed.value
    }

    private suspend fun <R> applyMissPolicy(
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        loadCoordinator: CacheLoadCoordinator,
        loadConcurrency: LoadConcurrencyGroup?,
        backgroundScope: () -> CoroutineScope,
        observation: OperationObservation,
        missPolicy: CacheMissPolicy<R>,
        readCached: suspend () -> R?,
        loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    ): ObservedSetValue<R> = when (missPolicy) {
        is CacheMissPolicy.Load -> loadWithPolicy(
            cacheName = cacheName,
            entryKey = entryKey,
            store = store,
            config = config,
            loadCoordinator = loadCoordinator,
            loadConcurrency = loadConcurrency,
            observation = observation,
            trigger = CacheLoadTrigger.Miss,
            execution = CacheExecution.Foreground,
            readCached = readCached,
            loadAndSave = loadAndSave,
            onFailure = { error ->
                missPolicy.fallbackOnFailure?.invoke(error)
                    ?.let { ObservedSetValue(it, CacheOperationResult.FailureFallback) }
                    ?: throw error
            },
        )

        is CacheMissPolicy.LoadInBackground -> {
            val fallback = missPolicy.fallback()
            backgroundScope().launch(ObservationContext(observation)) {
                try {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryKey = entryKey,
                        store = store,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        loadConcurrency = loadConcurrency,
                        observation = observation,
                        trigger = CacheLoadTrigger.Miss,
                        execution = CacheExecution.Background,
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
            ObservedSetValue(fallback, CacheOperationResult.BackgroundFallback)
        }
    }

    private suspend fun <R> applyRefreshPolicy(
        cached: R,
        cacheName: String,
        entryKey: String,
        store: KacheableStore,
        config: CacheConfig?,
        loadCoordinator: CacheLoadCoordinator,
        loadConcurrency: LoadConcurrencyGroup?,
        backgroundScope: () -> CoroutineScope,
        observation: OperationObservation,
        refreshPolicy: CacheRefreshPolicy<R>,
        readFreshCached: suspend () -> R?,
        loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
    ): ObservedSetValue<R> {
        return when (refreshPolicy) {
            is CacheRefreshPolicy.NeverRefresh -> ObservedSetValue(cached, CacheOperationResult.CachedValue)
            is CacheRefreshPolicy.RefreshIf -> {
                if (!refreshPolicy.isStale(cached)) {
                    return ObservedSetValue(cached, CacheOperationResult.CachedValue)
                }
                if (refreshPolicy.inBackground) {
                    backgroundScope().launch(ObservationContext(observation)) {
                        try {
                            loadWithPolicy(
                                cacheName = cacheName,
                                entryKey = entryKey,
                                store = store,
                                config = config,
                                loadCoordinator = loadCoordinator,
                                loadConcurrency = loadConcurrency,
                                observation = observation,
                                trigger = CacheLoadTrigger.Refresh,
                                execution = CacheExecution.Background,
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
                    ObservedSetValue(cached, CacheOperationResult.Stale)
                } else {
                    loadWithPolicy(
                        cacheName = cacheName,
                        entryKey = entryKey,
                        store = store,
                        config = config,
                        loadCoordinator = loadCoordinator,
                        loadConcurrency = loadConcurrency,
                        observation = observation,
                        trigger = CacheLoadTrigger.Refresh,
                        execution = CacheExecution.Foreground,
                        readCached = readFreshCached,
                        loadAndSave = loadAndSave,
                        onFailure = { ObservedSetValue(cached, CacheOperationResult.Stale) },
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
        loadConcurrency: LoadConcurrencyGroup?,
        observation: OperationObservation,
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
        readCached: suspend () -> R?,
        loadAndSave: suspend (CacheLoadTrigger, CacheExecution) -> R,
        onFailure: suspend (Throwable) -> ObservedSetValue<R>,
    ): ObservedSetValue<R> {
        val resilience = loadCoordinator.resilienceFor(config)
        return try {
            loadCoordinator.load(
                cacheName = cacheName,
                entryKey = entryKey,
                store = store,
                config = config,
                observation = observation,
                loadConcurrencyGroup = loadConcurrency,
                execution = execution,
                readCached = readCached,
                loadAndSave = { effectiveExecution -> loadAndSave(trigger, effectiveExecution) },
            ).let {
                ObservedSetValue(
                    it,
                    if (trigger == CacheLoadTrigger.Refresh) {
                        CacheOperationResult.Refreshed
                    } else {
                        CacheOperationResult.Loaded
                    },
                )
            }
        } catch (t: TimeoutCancellationException) {
            readCached().takeIf { resilience.staleOnTimeout }
                ?.let { ObservedSetValue(it, CacheOperationResult.Stale) }
                ?: onFailure(t)
        } catch (t: CacheLoadTimeoutException) {
            readCached().takeIf { resilience.staleOnTimeout }
                ?.let { ObservedSetValue(it, CacheOperationResult.Stale) }
                ?: onFailure(t)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            readCached().takeIf { resilience.staleOnFailure }
                ?.let { ObservedSetValue(it, CacheOperationResult.Stale) }
                ?: onFailure(t)
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

    private data class ObservedSetValue<R>(
        val value: R,
        val result: CacheOperationResult,
    )

    private suspend fun <R> loadAndSaveObserved(
        observation: OperationObservation,
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
        load: suspend () -> R,
        save: suspend (R) -> Unit,
    ): R {
        observation.loaderStarted(trigger, execution)
        val started = observation.startTimer()
        val result = try {
            load().also {
                observation.loaderCompleted(trigger, execution, CacheLoadResult.Success, started)
            }
        } catch (t: Throwable) {
            val loadResult = when (t) {
                is TimeoutCancellationException,
                is CacheLoadTimeoutException,
                -> CacheLoadResult.Timeout

                is CancellationException -> CacheLoadResult.Cancelled
                else -> CacheLoadResult.Failure
            }
            observation.loaderCompleted(trigger, execution, loadResult, started)
            throw t
        }
        save(result)
        return result
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
