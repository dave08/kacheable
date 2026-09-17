package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.internal.snapshot.CacheSnapshotCoordinator
import com.github.dave08.kacheable.internal.storage.CachedValue
import com.github.dave08.kacheable.internal.storage.SelectedEntryStorage
import com.github.dave08.kacheable.internal.storage.hash.PartitionLoaderAdmission
import com.github.dave08.kacheable.store.KacheableStore
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Groups loader work; per-entry representation and publication live in the storage adapter. */
internal class CacheManyRuntime(
    private val store: KacheableStore,
    private val configs: Map<String, CacheConfig>,
    private val naming: CacheNamingStrategy,
    private val coordinator: CacheLoadCoordinator,
    private val telemetry: CacheTelemetryRuntime,
    private val snapshots: CacheSnapshotCoordinator?,
    private val backgroundScope: () -> CoroutineScope,
    private val loaderAdmission: PartitionLoaderAdmission? = null,
) {
    suspend fun <K, V, P : KeyPart<K>> load(
        ref: CacheManyRef<K, V, P>,
        missPolicy: CacheMissPolicy<V>,
        refreshPolicy: CacheRefreshPolicy<V>,
        storeResultIf: (V) -> Boolean,
        block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
    ): Map<K, V> {
        if (ref.keys.isEmpty()) return emptyMap()
        val first = ref.entry(ref.keys.first()).entryRef
        val config = configs[first.name]
        return telemetry.observe(first.name, first.storage.toTelemetryKind(), first.loadConcurrency) { observation ->
            val storage = SelectedEntryStorage(ref.entry, store, naming, config, observation)
            val representatives = ref.keys.distinct().associateByFirst(storage::identity)
            val keys = representatives.values.toList()
            val initial = storage.read(keys, CacheReadAttempt.Hot).toMutableMap()
            val unrestored = keys.filterNot(initial::containsKey)
            if (snapshots != null && unrestored.isNotEmpty()) {
                unrestored.forEach { snapshots.restoreEntry(first.name, storage.address(it)) }
                initial.putAll(storage.read(unrestored, CacheReadAttempt.AfterSnapshot))
            }
            val missing = keys.filterNot(initial::containsKey)
            val stale = if (refreshPolicy is CacheRefreshPolicy.RefreshIf)
                keys.filter { key -> initial[key]?.let { refreshPolicy.isStale(it.value) } == true }
            else emptyList()
            val result = initial.toMutableMap()

            suspend fun runLoad(
                selected: List<K>,
                trigger: CacheLoadTrigger,
                execution: CacheExecution,
            ): BatchRunResult<K, V> {
                if (selected.isEmpty()) return BatchRunResult(emptyMap(), null)
                val byIdentity = selected.associateBy(storage::identity)
                val latest = initial.toMutableMap()
                val completed = linkedMapOf<K, CachedValue<V>>()
                val entryFailures = linkedMapOf<K, Throwable>()
                var failure: Throwable? = null
                var usedFallback = false
                var usedStale = false
                try {
                    val loaded = coordinator.loadMany(
                        cacheName = first.name,
                        entryKeys = byIdentity.keys.toList(),
                        store = store,
                        config = config,
                        observation = observation,
                        loadConcurrencyGroup = first.loadConcurrency.takeIf { loaderAdmission == null },
                        execution = execution,
                        onEntryResolved = { identity, value -> completed[byIdentity.getValue(identity)] = value },
                        onEntryFailure = { identity, error -> entryFailures[byIdentity.getValue(identity)] = error },
                        readCached = { identities ->
                            val current = storage.read(identities.map(byIdentity::getValue), CacheReadAttempt.SingleFlightRecheck)
                            latest.putAll(current)
                            current.filterValues { cached -> trigger != CacheLoadTrigger.Refresh ||
                                refreshPolicy !is CacheRefreshPolicy.RefreshIf || !refreshPolicy.isStale(cached.value)
                            }.mapKeys { (key, _) -> storage.identity(key) }
                        },
                        loadAndSave = { identities, effectiveExecution ->
                            val requested = identities.map(byIdentity::getValue)
                            val context = SelectedLoadContext(ref.keyPart) { siblingKeys ->
                                storage.read(siblingKeys, CacheReadAttempt.PartitionContext)
                            }
                            val started = observation.startTimer()
                            observation.loaderStarted(trigger, effectiveExecution)
                            val values = try {
                                var returned: Map<K, V>? = null
                                if (loaderAdmission == null) returned = block(requested, context)
                                else loaderAdmission.run(first.name, first.loadConcurrency, observation) {
                                    returned = block(requested, context)
                                }
                                val loadedValues = checkNotNull(returned).toMap()
                                require(loadedValues.keys.all { it in requested }) { "Batch loader returned an unrequested key." }
                                observation.loaderCompleted(trigger, effectiveExecution,
                                    if (requested.all(loadedValues::containsKey)) CacheLoadResult.Success else CacheLoadResult.Failure, started)
                                loadedValues
                            } catch (error: Throwable) {
                                observation.loaderCompleted(trigger, effectiveExecution, when (error) {
                                    is TimeoutCancellationException, is CacheLoadTimeoutException -> CacheLoadResult.Timeout
                                    is CancellationException -> CacheLoadResult.Cancelled
                                    else -> CacheLoadResult.Failure
                                }, started)
                                throw error
                            } finally { context.close() }
                            storage.publish(values, storeResultIf)
                            values.forEach { (key, value) -> completed[key] = CachedValue(value) }
                            values.mapKeys { (key, _) -> storage.identity(key) }.mapValues { CachedValue(it.value) }
                        },
                    )
                    loaded.forEach { (identity, value) -> completed[byIdentity.getValue(identity)] = value }
                } catch (error: TimeoutCancellationException) { failure = error }
                catch (error: CancellationException) { throw error }
                catch (error: Throwable) { failure = error }

                val resilience = coordinator.resilienceFor(config)
                fun failureFor(key: K): Throwable? = entryFailures[key] ?: failure
                fun allowsStale(error: Throwable?): Boolean = when (error) {
                    is TimeoutCancellationException,
                    is CacheLoadTimeoutException,
                    -> resilience.staleOnTimeout

                    null -> false
                    else -> resilience.staleOnFailure
                }
                val recoveryKeys = selected.filterNot(completed::containsKey).filter { key ->
                    failureFor(key)?.let { trigger == CacheLoadTrigger.Refresh || allowsStale(it) } == true
                }
                if (recoveryKeys.isNotEmpty()) {
                    val recovered = storage.read(recoveryKeys, CacheReadAttempt.SingleFlightRecheck)
                    latest.putAll(recovered)
                    val accepted = recovered.filterKeys { allowsStale(failureFor(it)) }
                    completed.putAll(accepted)
                    usedStale = accepted.isNotEmpty()
                }

                for (key in selected.filterNot(completed::containsKey)) {
                    val previous = latest[key]
                    if (trigger == CacheLoadTrigger.Refresh && previous != null) {
                        completed[key] = previous
                        usedStale = true
                    } else if (missPolicy is CacheMissPolicy.Load && missPolicy.fallbackOnFailure != null) {
                        completed[key] = CachedValue(missPolicy.fallbackOnFailure.invoke(failureFor(key) ?: UnresolvedCacheKeyException(key)))
                        usedFallback = true
                    }
                }
                selected.filterNot(completed::containsKey).firstNotNullOfOrNull(::failureFor)?.let { throw it }
                val outcome = when {
                    usedFallback -> CacheOperationResult.FailureFallback
                    usedStale -> CacheOperationResult.Stale
                    trigger == CacheLoadTrigger.Refresh -> CacheOperationResult.Refreshed
                    else -> CacheOperationResult.Loaded
                }
                return BatchRunResult(completed, outcome)
            }

            fun background(selected: List<K>, trigger: CacheLoadTrigger) {
                if (selected.isEmpty()) return
                backgroundScope().launch(ObservationContext(observation)) {
                    try { runLoad(selected, trigger, CacheExecution.Background) }
                    catch (_: TimeoutCancellationException) { }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Throwable) { /* Loader/storage failures were observed; foreground values remain usable. */ }
                }
            }

            var foregroundOutcome: CacheOperationResult? = null
            if (missPolicy is CacheMissPolicy.LoadInBackground) {
                missing.forEach { result[it] = CachedValue(missPolicy.fallback()) }
                background(missing, CacheLoadTrigger.Miss)
            } else {
                val loaded = runLoad(missing, CacheLoadTrigger.Miss, CacheExecution.Foreground)
                result.putAll(loaded.values)
                foregroundOutcome = foregroundOutcome.combine(loaded.outcome)
            }
            if (refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.inBackground)
                background(stale, CacheLoadTrigger.Refresh)
            else {
                val refreshed = runLoad(stale, CacheLoadTrigger.Refresh, CacheExecution.Foreground)
                result.putAll(refreshed.values)
                foregroundOutcome = foregroundOutcome.combine(refreshed.outcome)
            }

            observation.complete(when {
                keys.any { !result.containsKey(it) } -> CacheOperationResult.Failed
                missPolicy is CacheMissPolicy.LoadInBackground && missing.isNotEmpty() -> CacheOperationResult.BackgroundFallback
                refreshPolicy is CacheRefreshPolicy.RefreshIf && refreshPolicy.inBackground && stale.isNotEmpty() ->
                    CacheOperationResult.Stale
                foregroundOutcome != null -> foregroundOutcome
                else -> CacheOperationResult.CachedValue
            })
            buildMap {
                ref.keys.forEach { key -> result[representatives.getValue(storage.identity(key))]?.let { put(key, it.value) } }
            }
        }
    }
}

internal class SelectedLoadContext<K, V, P : KeyPart<K>>(
    override val keyPart: P,
    private val read: suspend (List<K>) -> Map<K, CachedValue<V>>,
) : CacheLoadContext<K, V, P>() {
    private val active = AtomicBoolean(true)
    fun close() { active.set(false) }
    override suspend fun entry(key: K): CacheEntry<V> {
        val found = entries(listOf(key))
        return if (found.containsKey(key)) CacheEntry.Present(found.getValue(key)) else CacheEntry.Missing
    }
    override suspend fun entries(keys: Iterable<K>): Map<K, V> {
        check(active.get()) { "Cache contexts are valid only during their loader attempt." }
        val result = read(keys.toList()).mapValues { it.value.value }
        check(active.get()) { "Cache contexts are valid only during their loader attempt." }
        return result
    }
}

private data class BatchRunResult<K, V>(
    val values: Map<K, CachedValue<V>>,
    val outcome: CacheOperationResult?,
)

private fun CacheOperationResult?.combine(other: CacheOperationResult?): CacheOperationResult? {
    if (this == null) return other
    if (other == null) return this
    fun CacheOperationResult.priority(): Int = when (this) {
        CacheOperationResult.FailureFallback -> 4
        CacheOperationResult.Stale -> 3
        CacheOperationResult.Refreshed -> 2
        CacheOperationResult.Loaded -> 1
        else -> 0
    }
    return if (priority() >= other.priority()) this else other
}

private fun <K, A> Iterable<K>.associateByFirst(address: (K) -> A): Map<A, K> =
    linkedMapOf<A, K>().also { result ->
        forEach { key ->
            val identity = address(key)
            if (!result.containsKey(identity)) result[identity] = key
        }
    }
