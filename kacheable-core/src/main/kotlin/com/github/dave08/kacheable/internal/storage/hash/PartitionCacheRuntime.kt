package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.OperationObservation
import com.github.dave08.kacheable.internal.storage.CachedValue
import com.github.dave08.kacheable.internal.storage.CacheEntryNamer
import com.github.dave08.kacheable.internal.storage.StoreEntryName
import com.github.dave08.kacheable.store.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean

/** Generation and revision checks belong to the hash path, below the typed public API. */
internal class PartitionCacheRuntime(
    private val store: KacheableStore,
    private val config: CacheConfig,
    private val backend: VersionedHashOperations,
    namingStrategy: CacheNamingStrategy,
    private val coordinator: CacheLoadCoordinator,
    private val telemetry: CacheTelemetryRuntime,
    private val loaderAdmission: PartitionLoaderAdmission? = null,
) {
    private val namer = CacheEntryNamer(namingStrategy)
    private val expiry = config.expiry.takeIf { config.expiryType != ExpiryType.none && it.isFinite() }

    suspend fun invalidate(partRef: CacheEntryPartRef) {
        require(partRef.storage == CacheStorage.HashMap && partRef.secondaryPatternPartArgs == null &&
            partRef.cacheArgs.secondary == null) {
            "Generation-checked caches only support whole-partition or whole-cache hash invalidation."
        }
        backend.deleteHashes(namer.nameEntry(partRef.name, partRef.cacheArgs).primaryKey)
    }

    suspend fun invalidate(allRef: StoredCacheAllRef<*>) {
        require(allRef.storage == CacheStorage.HashMap) { "Partition policies require hash storage." }
        val entryName = if (allRef is SinglePartitionAllRef<*>) {
            namer.nameEntry(allRef.name, emptyArray())
        } else {
            namer.nameAllEntries(allRef.name)
        }
        backend.deleteHashes(entryName.primaryKey)
    }

    suspend fun <K, V, P : KeyPart<K>> load(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend (K, CacheLoadContext<K, V, P>) -> V,
    ): V {
        require(ref.entryRef.storage == CacheStorage.HashMap) { "Partition contexts require indexed/hash value storage." }
        return telemetry.observe(ref.entryRef.name, CacheStorageKind.HashMap, ref.entryRef.loadConcurrency) { observation ->
            val selected = CacheManyRef(listOf(ref.key), ref.keyPart, ref.sibling)
            val result = ManyLoadOperation(
                selected, selected.keys, cacheIf,
                { keys, context -> keys.associateWith { key -> block(key, context) } },
                observation, scalarResult = true,
            ).load()
            if (!result.containsKey(ref.key)) throw UnresolvedCacheKeyException(ref.key)
            result.getValue(ref.key)
        }
    }

    suspend fun <K, V, P : KeyPart<K>> load(
        ref: PartitionCacheEntryRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend () -> V,
    ): V {
        require(config.partition !is CachePartitionPolicy.SequentialFrom) {
            "Sequential loading requires a parameterized contextual loader."
        }
        return load(ref, cacheIf) { _, _ -> block() }
    }

    suspend fun <K, V, P : KeyPart<K>> loadMany(
        ref: CacheManyRef<K, V, P>,
        cacheIf: (V) -> Boolean,
        block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
    ): Map<K, V> {
        val keys = ref.keys.distinct()
        if (keys.isEmpty()) return emptyMap()
        val first = ref.entry(keys.first())
        require(first.entryRef.storage == CacheStorage.HashMap) {
            "Partition contexts require indexed/hash value storage."
        }
        return telemetry.observe(first.entryRef.name, CacheStorageKind.HashMap, first.entryRef.loadConcurrency) { observation ->
            ManyLoadOperation(ref, keys, cacheIf, block, observation).load()
        }
    }

    private inner class ManyLoadOperation<K, V, P : KeyPart<K>>(
        private val ref: CacheManyRef<K, V, P>,
        private val requestedKeys: List<K>,
        private val cacheIf: (V) -> Boolean,
        private val block: suspend (List<K>, CacheLoadContext<K, V, P>) -> Map<K, V>,
        private val observation: OperationObservation,
        private val scalarResult: Boolean = false,
    ) {
        private val refs = requestedKeys.associateWith(ref.entry)
        private val addresses = refs.mapValues { (_, entry) -> address(entry) }
        private val firstRef = refs.getValue(requestedKeys.first())
        private val hash = addresses.getValue(requestedKeys.first()).key
        private val cacheName = firstRef.entryRef.name
        private val returnCodec = firstRef.returnView.codec
        private val representativesByFlight = linkedMapOf<String, K>().also { representatives ->
            requestedKeys.forEach { key ->
                val identity = fieldIdentity(key)
                if (!representatives.containsKey(identity)) representatives[identity] = key
            }
        }
        private val selectionKeys = representativesByFlight.values.toList()
        private val sequentialRange = (config.partition as? CachePartitionPolicy.SequentialFrom)?.let { policy ->
            val ints = selectionKeys.map {
                it as? Int ?: throw IllegalArgumentException("SequentialFrom requires Int entry keys.")
            }
            require(ints.all { it >= policy.first }) { "Requested key precedes the sequential loading origin." }
            policy.first..ints.max()
        }
        @Suppress("UNCHECKED_CAST")
        private val keyCodec = (ref.keyPart as? EnumerableKeyPart<K>)?.codec

        init {
            require(refs.values.all { it.entryRef.name == cacheName }) {
                "Selected entries must belong to one cache."
            }
            require(addresses.values.all { it.key == hash }) {
                "Selected guarded entries must belong to one partition."
            }
        }

        suspend fun load(): Map<K, V> {
            var loadedDuringOperation = false
            repeat(config.maxLoadAttempts) { attempt ->
                try {
                    val selected = readSnapshot(selectionKeys, CacheReadAttempt.Hot)
                    val opened = selected.version
                    val cached = selected.values
                    val missing = selectionKeys.filterNot(cached::containsKey)
                    if (missing.isEmpty()) {
                        observation.complete(
                            if (loadedDuringOperation) CacheOperationResult.Loaded else CacheOperationResult.CachedValue,
                        )
                        return orderedValues(cached)
                    }
                    loadedDuringOperation = true
                    val loaded = when (config.partition) {
                        is CachePartitionPolicy.OnDemand -> loadOnDemand(missing, opened.generation)
                        is CachePartitionPolicy.SequentialFrom -> loadSequential(selected)
                        null -> error("Selected guarded loading requires a partition policy.")
                    }
                    if (loaded.values.any { it.generation != opened.generation }) {
                        throw PartitionSelectionConflict()
                    }
                    val finalStored = readSnapshot(selectionKeys, CacheReadAttempt.SingleFlightRecheck)
                    if (finalStored.version.generation != opened.generation) throw PartitionSelectionConflict()
                    // Scalar loading returns its resolved value, including a nonstored null or cacheIf=false.
                    // Selected loading returns the final stored selection wherever it is available.
                    val resolved = if (scalarResult) cached + finalStored.values + loaded
                    else cached + loaded + finalStored.values
                    val result = orderedValues(resolved)
                    observation.complete(
                        if (result.size == requestedKeys.size) CacheOperationResult.Loaded else CacheOperationResult.Failed,
                    )
                    return result
                } catch (_: PartitionRetry) {
                    currentCoroutineContext().ensureActive()
                    observation.partitionConflict(attempt + 1, attempt + 1 < config.maxLoadAttempts)
                }
            }
            error("Partition '$cacheName' changed during all ${config.maxLoadAttempts} selected load attempts.")
        }

        private suspend fun loadOnDemand(keys: List<K>, generation: String): Map<K, CachedValue<V>> {
            val keysByFlight = keys.associateBy { key -> flightKey(key, generation) }
            var coordinatedSnapshot: SelectionSnapshot<K, V>? = null
            val admission: (suspend (suspend () -> Map<String, CachedValue<V>>) -> Map<String, CachedValue<V>>)? =
                loaderAdmission?.let { gate ->
                    { load ->
                        var admitted: Map<String, CachedValue<V>>? = null
                        gate.run(cacheName, firstRef.entryRef.loadConcurrency, observation) { admitted = load() }
                        checkNotNull(admitted)
                    }
                }
            val loaded = coordinator.loadMany(
                cacheName = cacheName,
                entryKeys = keysByFlight.keys.toList(),
                store = store,
                config = config,
                observation = observation,
                loadConcurrencyGroup = firstRef.entryRef.loadConcurrency.takeIf { loaderAdmission == null },
                execution = CacheExecution.Foreground,
                readCached = { flights ->
                    val selected = flights.map(keysByFlight::getValue)
                    val snapshot = readSnapshot(selected, CacheReadAttempt.SingleFlightRecheck)
                    if (snapshot.version.generation != generation) throw PartitionSelectionConflict()
                    coordinatedSnapshot = snapshot
                    snapshot.values.mapKeys { (key, _) -> flightKey(key, generation) }
                },
                loadAndSave = { flights, execution ->
                    val selected = flights.map(keysByFlight::getValue)
                    // The coordinator reads after admission, the lease, and the partition mutex.
                    // Carry that exact snapshot into the loader instead of opening and reading again.
                    loadOnDemandAttempt(selected, generation, execution, checkNotNull(coordinatedSnapshot))
                        .mapKeys { (key, _) -> flightKey(key, generation) }
                },
                coordinationKey = hash.takeIf { config.coordination == CacheCoordination.Partition },
                initialMiss = true,
                loadAdmission = admission,
            )
            return loaded.mapKeys { (flight, _) -> keysByFlight.getValue(flight) }
        }

        private suspend fun loadOnDemandAttempt(
            keys: List<K>,
            generation: String,
            execution: CacheExecution,
            initialSnapshot: SelectionSnapshot<K, V>,
        ): Map<K, CachedValue<V>> {
            repeat(config.maxLoadAttempts) { attempt ->
                try {
                    val snapshot = if (attempt == 0) initialSnapshot
                    else readSnapshot(keys, CacheReadAttempt.SingleFlightRecheck)
                    if (snapshot.version.generation != generation) throw PartitionSelectionConflict()
                    return loadAndPublish(keys, snapshot, execution)
                } catch (_: PartitionConflict) {
                    currentCoroutineContext().ensureActive()
                    observation.partitionConflict(attempt + 1, attempt + 1 < config.maxLoadAttempts)
                }
            }
            error("Partition '$cacheName' changed during all ${config.maxLoadAttempts} load attempts.")
        }

        private suspend fun loadAndPublish(
            keys: List<K>,
            snapshot: SelectionSnapshot<K, V>,
            execution: CacheExecution,
        ): Map<K, CachedValue<V>> {
            val version = snapshot.version
            val existing = snapshot.values.filterKeys { it in keys }
            val missing = keys.filterNot(existing::containsKey)
            if (missing.isEmpty()) return existing

            val context = context(version)
            val resolved = try {
                runBatchLoader(missing, context, execution).also { returned ->
                    require(returned.keys.all { it in missing }) {
                        "Selected-entry loader returned a key that was not requested."
                    }
                }
            } finally {
                context.close()
            }
            currentCoroutineContext().ensureActive()
            if (resolved.isEmpty()) {
                verifyVersion(version, context.used)
                return existing
            }
            val published = publishResolved(resolved, version, context.used)
            return existing + published
        }

        private suspend fun loadSequential(initialSnapshot: SelectionSnapshot<K, V>): Map<K, CachedValue<V>> {
            val generation = initialSnapshot.version.generation
            val requested = selectionKeys.toSet()
            val result = linkedMapOf<K, CachedValue<V>>()
            val required = checkNotNull(sequentialRange)
            for ((index, number) in required.withIndex()) {
                @Suppress("UNCHECKED_CAST")
                val key = number as K
                currentCoroutineContext().ensureActive()
                val snapshot = if (index == 0 && key in requested) initialSnapshot
                else readSnapshot(listOf(key), CacheReadAttempt.SingleFlightRecheck)
                if (snapshot.version.generation != generation) throw PartitionSelectionConflict()
                val value = snapshot.values[key] ?: loadOnDemand(listOf(key), generation)[key] ?: break
                if (value.generation != generation) throw PartitionSelectionConflict()
                if (key in requested) result[key] = value
                if (number != required.last) {
                    val stored = readSnapshot(listOf(key), CacheReadAttempt.SingleFlightRecheck)
                    if (stored.version.generation != generation) throw PartitionSelectionConflict()
                    if (!stored.values.containsKey(key)) {
                        require(!scalarResult) {
                            "Sequential prerequisite '$key' was not published. Prerequisites require a stored value."
                        }
                        break
                    }
                }
            }
            return result
        }

        private suspend fun runBatchLoader(
            keys: List<K>,
            context: AttemptContext<K, V, P>,
            execution: CacheExecution,
        ): Map<K, V> {
            val started = observation.startTimer()
            observation.loaderStarted(CacheLoadTrigger.Miss, execution)
            try {
                val result = block(keys, context)
                context.close()
                observation.loaderCompleted(
                    CacheLoadTrigger.Miss,
                    execution,
                    if (keys.all(result::containsKey)) CacheLoadResult.Success else CacheLoadResult.Failure,
                    started,
                )
                return result
            } catch (failure: Throwable) {
                val result = when (failure) {
                    is TimeoutCancellationException -> CacheLoadResult.Timeout
                    is CancellationException -> CacheLoadResult.Cancelled
                    else -> CacheLoadResult.Failure
                }
                observation.loaderCompleted(CacheLoadTrigger.Miss, execution, result, started)
                throw failure
            }
        }

        private suspend fun publishResolved(
            resolved: Map<K, V>,
            version: HashVersion,
            readSiblings: Boolean,
        ): Map<K, CachedValue<V>> {
            val publishVersion = publicationVersion(version, readSiblings)
            val candidates = buildMap {
                resolved.forEach { (key, value) ->
                    if (shouldCache(value)) {
                        val encodedKey = keyCodec?.encode(key)
                        put(field(key), VersionedHashValue(encodeValue(returnCodec, value, encodedKey), encodedKey))
                    }
                }
            }
            if (candidates.isEmpty()) {
                if (backend.readHash(hash, publishVersion, emptyList()) == null) throw PartitionConflict()
                observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
                return resolved.mapValuesTo(linkedMapOf()) { (_, value) -> CachedValue(value, publishVersion.generation) }
            }
            val started = observation.startTimer()
            val result = try {
                if (candidates.size == 1) {
                    val (field, candidate) = candidates.entries.single()
                    when (val published = backend.publishHash(
                        hash, publishVersion, field, candidate.value, expiry,
                        config.publication == CachePublication.IfAbsent, candidate.metadata,
                    )) {
                        HashPublishResult.Conflict -> HashPublishManyResult.Conflict
                        is HashPublishResult.Existing -> HashPublishManyResult.Success(
                            published.version, mapOf(field to published.value), emptySet(),
                        )
                        is HashPublishResult.Published -> HashPublishManyResult.Success(
                            published.version, mapOf(field to published.value), setOf(field),
                        )
                    }
                } else {
                    backend.publishHashes(
                        hash, publishVersion, candidates, expiry,
                        config.publication == CachePublication.IfAbsent,
                    )
                }
            } catch (failure: Throwable) {
                observation.storageWrite(CacheWriteResult.Failed, started)
                throw failure
            }
            if (result === HashPublishManyResult.Conflict) {
                observation.storageWrite(CacheWriteResult.Skipped, started)
                throw PartitionConflict()
            }
            result as HashPublishManyResult.Success
            observation.storageWrite(
                if (result.publishedFields.isEmpty()) CacheWriteResult.Skipped else CacheWriteResult.Stored,
                started,
            )
            return resolved.mapValuesTo(linkedMapOf()) { (key, value) ->
                val stored = result.values[field(key)]
                if (stored == null) CachedValue(value, result.version.generation)
                else CachedValue(decodeValue(returnCodec, stored), result.version.generation)
            }
        }

        private fun shouldCache(value: V): Boolean =
            cacheIf(value) && (value != null || config.nullPlaceholder != null)

        private suspend fun publicationVersion(version: HashVersion, readSiblings: Boolean): HashVersion {
            if (readSiblings || config.coordination != CacheCoordination.Entry) return version
            return backend.openHash(hash, expiry).also {
                if (it.generation != version.generation) throw PartitionConflict()
            }
        }

        private suspend fun verifyVersion(version: HashVersion, readSiblings: Boolean) {
            val current = publicationVersion(version, readSiblings)
            if (backend.readHash(hash, current, emptyList()) == null) throw PartitionConflict()
            observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
        }

        private suspend fun readSnapshot(
            keys: List<K>,
            attempt: CacheReadAttempt,
        ): SelectionSnapshot<K, V> {
            val fields = keys.associateBy { field(it) }
            var snapshot: HashReadSnapshot? = null
            val raw = readObserved(observation, attempt) {
                backend.readHashSnapshot(hash, expiry, fields.keys.toList()).also { snapshot = it }?.values
            } ?: throw PartitionConflict()
            val version = checkNotNull(snapshot).version
            val values = raw.map { (field, value) ->
                fields.getValue(field) to CachedValue(decodeValue(returnCodec, value), version.generation)
            }.toMap(linkedMapOf())
            return SelectionSnapshot(version, values)
        }

        private fun context(version: HashVersion) = AttemptContext(
            backend, hash, version, ref.keyPart, ref.entry, returnCodec, ::address, observation,
        )

        private fun field(key: K): String = addresses[key]?.entry ?: address(ref.entry(key)).entry
        private fun fieldIdentity(key: K): String = "$hash:${field(key)}"
        private fun flightKey(key: K, generation: String): String = "$hash:$generation:${field(key)}"

        private fun orderedValues(values: Map<K, CachedValue<V>>): Map<K, V> = buildMap {
            requestedKeys.forEach { key ->
                val representative = representativesByFlight.getValue(fieldIdentity(key))
                values[representative]?.let { put(key, it.value) }
            }
        }
    }

    private fun address(ref: CacheEntryRef<*>): StoreEntryName.Layered =
        HashMapStorageStrategy.storeEntryName(namer.nameEntry(ref.entryRef.name, ref.entryRef.cacheArgs)) as? StoreEntryName.Layered
            ?: throw IllegalArgumentException("Partition contexts require an entry within a hash.")
}

private data class SelectionSnapshot<K, V>(
    val version: HashVersion,
    val values: Map<K, CachedValue<V>>,
)

private sealed class PartitionRetry : RuntimeException()
private class PartitionConflict : PartitionRetry()
private class PartitionSelectionConflict : PartitionRetry()

private fun <V> decodeValue(codec: CacheCodec<V>, raw: String): V =
    codec.decode(Json.parseToJsonElement(raw).jsonObject.getValue("value").jsonPrimitive.content)

private class AttemptContext<K, V, P : KeyPart<K>>(
    private val backend: VersionedHashOperations,
    private val hash: String,
    private val version: HashVersion,
    override val keyPart: P,
    private val entryRefFor: (K) -> CacheEntryRef<V>,
    private val returnCodec: CacheCodec<V>,
    private val address: (CacheEntryRef<*>) -> StoreEntryName.Layered,
    private val observation: OperationObservation,
) : CacheLoadContext<K, V, P>() {
    private val active = AtomicBoolean(true)
    var used = false

    fun close() { active.set(false) }

    suspend fun read(fields: List<String>?, attempt: CacheReadAttempt = CacheReadAttempt.PartitionContext): Map<String, String> {
        check(active.get()) { "Partition context is no longer active; contexts belong to one loader attempt." }
        currentCoroutineContext().ensureActive()
        used = true
        val values = readObserved(observation, attempt) { backend.readHash(hash, version, fields) }
            ?: throw PartitionConflict()
        check(active.get()) { "Partition context is no longer active." }
        return values
    }

    override suspend fun entry(key: K): CacheEntry<V> {
        val field = address(entryRefFor(key)).entry
        val raw = read(listOf(field))[field] ?: return CacheEntry.Missing
        return CacheEntry.Present(decodeValue(returnCodec, raw))
    }

    override suspend fun entries(keys: Iterable<K>): Map<K, V> {
        val logicalKeys = keys.distinct()
        val fields = logicalKeys.associateWith { address(entryRefFor(it)).entry }
        val raw = read(fields.values.distinct())
        return buildMap {
            logicalKeys.forEach { key ->
                raw[fields.getValue(key)]?.let { put(key, decodeValue(returnCodec, it)) }
            }
        }
    }

    override suspend fun enumerateEntries(codec: CacheCodec<K>): Map<K, V> = read(null).values.associate { raw ->
        val envelope = Json.parseToJsonElement(raw).jsonObject
        codec.decode(requireKeyMetadata(envelope["key"]?.jsonPrimitive?.content)) to
            returnCodec.decode(envelope.getValue("value").jsonPrimitive.content)
    }

    override suspend fun enumerateKeys(codec: CacheCodec<K>): Set<K> {
        check(active.get()) { "Partition context is no longer active." }
        currentCoroutineContext().ensureActive()
        used = true
        val metadata = readObserved(observation, CacheReadAttempt.PartitionMetadata) {
            backend.readHashMetadata(hash, version)
        } ?: throw PartitionConflict()
        check(active.get()) { "Partition context is no longer active." }
        return metadata.values.mapTo(linkedSetOf()) { codec.decode(requireKeyMetadata(it)) }
    }
}

private fun requireKeyMetadata(value: String?): String = checkNotNull(value) {
    "Partition entries lack enumerable key metadata. Invalidate the partition before enabling enumeration."
}

private fun <V> encodeValue(codec: CacheCodec<V>, value: V, encodedKey: String?): String = buildJsonObject {
    if (encodedKey != null) put("key", encodedKey)
    put("value", codec.encode(value))
}.toString()

/** Records physical read outcomes before value decoding, including rejected versions. */
private suspend fun <V> readObserved(
    observation: OperationObservation,
    attempt: CacheReadAttempt,
    read: suspend () -> Map<String, V>?,
): Map<String, V>? {
    val started = observation.startTimer()
    val values = try {
        read()
    } catch (failure: Throwable) {
        observation.storageRead(attempt, CacheReadResult.Failed, started)
        throw failure
    }
    val result = when {
        values == null -> CacheReadResult.Conflict
        values.isEmpty() -> CacheReadResult.Absent
        else -> CacheReadResult.Present
    }
    observation.storageRead(attempt, result, started)
    return values
}
