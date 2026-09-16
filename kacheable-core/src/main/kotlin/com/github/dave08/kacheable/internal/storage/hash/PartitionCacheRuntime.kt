package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.*
import com.github.dave08.kacheable.internal.CacheLoadCoordinator
import com.github.dave08.kacheable.internal.CacheTelemetryRuntime
import com.github.dave08.kacheable.internal.OperationObservation
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
        block: suspend (K, CachePartitionContext<K, V, P>) -> V,
    ): V {
        require(ref.entryRef.storage == CacheStorage.HashMap) { "Partition contexts require indexed/hash value storage." }
        return telemetry.observe(ref.entryRef.name, CacheStorageKind.HashMap, ref.entryRef.loadConcurrency) { observation ->
            LoadOperation(ref, cacheIf, block, observation).load()
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

    private inner class LoadOperation<K, V, P : KeyPart<K>>(
        private val ref: PartitionCacheEntryRef<K, V, P>,
        private val cacheIf: (V) -> Boolean,
        private val block: suspend (K, CachePartitionContext<K, V, P>) -> V,
        private val observation: OperationObservation,
    ) {
        private val requested = address(ref)
        // P is already KeyPart<K>; enumeration adds a capability without changing its key type.
        @Suppress("UNCHECKED_CAST")
        private val keyCodec = (ref.keyPart as? EnumerableKeyPart<K>)?.codec
        private val orderedKeys = requiredKeys(ref.key)

        suspend fun load(): V {
            readRequestedEntry()?.let {
                observation.complete(CacheOperationResult.CachedValue)
                return it.value
            }
            val result = coordinator.load(
                cacheName = ref.entryRef.name,
                entryKey = requested.key + ":" + requested.entry,
                store = store,
                config = config,
                observation = observation,
                loadConcurrencyGroup = ref.entryRef.loadConcurrency.takeIf { loaderAdmission == null },
                execution = CacheExecution.Foreground,
                readCached = { readRequestedEntry(CacheReadAttempt.SingleFlightRecheck) },
                coordinationKey = requested.key.takeIf { config.coordination == CacheCoordination.Partition },
                loadAndSave = ::loadRequiredEntries,
            )
            observation.complete(CacheOperationResult.Loaded)
            return result.value
        }

        private suspend fun readRequestedEntry(attempt: CacheReadAttempt = CacheReadAttempt.Hot): Box<V>? {
            val raw = readObserved(observation, attempt) {
                val version = backend.openHash(requested.key, expiry)
                backend.readHash(requested.key, version, listOf(requested.entry))
            } ?: return null
            return raw[requested.entry]?.let { Box(decodeValue(ref.returnView.codec, it)) }
        }

        private suspend fun loadRequiredEntries(execution: CacheExecution): Box<V> {
            repeat(config.maxLoadAttempts) { attempt ->
                val generation = backend.openHash(requested.key, expiry).generation
                try {
                    return loadSequence(generation, execution)
                } catch (_: PartitionConflict) {
                    currentCoroutineContext().ensureActive()
                    observation.partitionConflict(attempt + 1, attempt + 1 < config.maxLoadAttempts)
                }
            }
            error("Partition '${ref.entryRef.name}' changed during all ${config.maxLoadAttempts} load attempts.")
        }

        private suspend fun loadSequence(generation: String, execution: CacheExecution): Box<V> {
            var result: Box<V>? = null
            for (key in orderedKeys) {
                currentCoroutineContext().ensureActive()
                val version = backend.openHash(requested.key, expiry)
                if (version.generation != generation) throw PartitionConflict()
                result = loadEntry(key, version, execution)
            }
            return checkNotNull(result)
        }

        private suspend fun loadEntry(key: K, version: HashVersion, execution: CacheExecution): Box<V> {
            val field = address(ref.sibling(key)).entry
            val context = AttemptContext(backend, requested.key, version, ref, ::address, observation)
            try {
                context.read(listOf(field), CacheReadAttempt.SingleFlightRecheck)[field]?.let { return Box(decodeValue(ref.returnView.codec, it)) }
                // Looking up the requested entry does not create a sibling dependency.
                context.used = false
                val encodedKey = keyCodec?.encode(key)
                val value = runLoader(key, context, execution)
                currentCoroutineContext().ensureActive()
                val publishVersion = publicationVersion(version, context.used)
                return publishResult(key, field, encodedKey, value, publishVersion)
            } finally {
                context.close()
            }
        }

        private suspend fun runLoader(key: K, context: AttemptContext<K, V, P>, execution: CacheExecution): V {
            val started = observation.startTimer()
            observation.loaderStarted(CacheLoadTrigger.Miss, execution)
            try {
                val value = admitLoader { block(key, context) }
                context.close()
                observation.loaderCompleted(CacheLoadTrigger.Miss, execution, CacheLoadResult.Success, started)
                return value
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

        private suspend fun admitLoader(load: suspend () -> V): V {
            val admission = loaderAdmission ?: return load()
            var result: Box<V>? = null
            admission.run(ref.entryRef.name, ref.entryRef.loadConcurrency, observation) { result = Box(load()) }
            return checkNotNull(result).value
        }

        private suspend fun publicationVersion(version: HashVersion, readSiblings: Boolean): HashVersion {
            if (readSiblings || config.coordination != CacheCoordination.Entry) return version
            return backend.openHash(requested.key, expiry).also {
                if (it.generation != version.generation) throw PartitionConflict()
            }
        }

        private suspend fun publishResult(key: K, field: String, encodedKey: String?, value: V, version: HashVersion): Box<V> {
            if (!cacheIf(value) || (value == null && config.nullPlaceholder == null)) {
                return skipPublication(key, value, version)
            }
            val started = observation.startTimer()
            val published = try {
                val envelope = encodeValue(ref.returnView.codec, value, encodedKey)
                backend.publishHash(
                    requested.key, version, field, envelope, expiry,
                    config.publication == CachePublication.IfAbsent, metadata = encodedKey,
                )
            } catch (failure: Throwable) {
                observation.storageWrite(CacheWriteResult.Failed, started)
                throw failure
            }
            val result = when (published) {
                HashPublishResult.Conflict -> {
                    observation.storageWrite(CacheWriteResult.Skipped, started)
                    throw PartitionConflict()
                }
                is HashPublishResult.Existing -> {
                    observation.storageWrite(CacheWriteResult.Skipped, started)
                    Box(decodeValue(ref.returnView.codec, published.value))
                }
                is HashPublishResult.Published -> {
                    observation.storageWrite(CacheWriteResult.Stored, started)
                    Box(value)
                }
            }
            return result
        }

        private suspend fun skipPublication(key: K, value: V, version: HashVersion): Box<V> {
            if (backend.readHash(requested.key, version, emptyList()) == null) throw PartitionConflict()
            observation.storageWrite(CacheWriteResult.Skipped, observation.startTimer())
            require(key == ref.key) { "Sequential prerequisite '$key' was not published. Prerequisites require a stored value." }
            return Box(value)
        }
    }

    private fun <K> requiredKeys(requested: K): Sequence<K> = when (val policy = checkNotNull(config.partition)) {
        is CachePartitionPolicy.OnDemand -> sequenceOf(requested)
        is CachePartitionPolicy.SequentialFrom -> {
            val last = requested as? Int ?: throw IllegalArgumentException("SequentialFrom requires Int entry keys.")
            require(last >= policy.first) { "Requested key precedes the sequential loading origin." }
            @Suppress("UNCHECKED_CAST")
            (policy.first..last).asSequence() as Sequence<K>
        }
    }

    private fun address(ref: CacheEntryRef<*>): StoreEntryName.Layered =
        HashMapStorageStrategy.storeEntryName(namer.nameEntry(ref.entryRef.name, ref.entryRef.cacheArgs)) as? StoreEntryName.Layered
            ?: throw IllegalArgumentException("Partition contexts require an entry within a hash.")
}

private class Box<V>(val value: V)
private class PartitionConflict : RuntimeException()

private fun <V> decodeValue(codec: CacheCodec<V>, raw: String): V =
    codec.decode(Json.parseToJsonElement(raw).jsonObject.getValue("value").jsonPrimitive.content)

private class AttemptContext<K, V, P : KeyPart<K>>(
    private val backend: VersionedHashOperations,
    private val hash: String,
    private val version: HashVersion,
    private val ref: PartitionCacheEntryRef<K, V, P>,
    private val address: (CacheEntryRef<*>) -> StoreEntryName.Layered,
    private val observation: OperationObservation,
) : CachePartitionContext<K, V, P>() {
    override val keyPart: P get() = ref.keyPart
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

    override suspend fun entry(key: K): CachePartitionEntry<V> {
        val field = address(ref.sibling(key)).entry
        val raw = read(listOf(field))[field] ?: return CachePartitionEntry.Missing
        return CachePartitionEntry.Present(decodeValue(ref.returnView.codec, raw))
    }

    override suspend fun entries(keys: Iterable<K>): Map<K, V> {
        val fields = keys.associateBy { address(ref.sibling(it)).entry }
        return read(fields.keys.toList()).map { (field, raw) -> fields.getValue(field) to decodeValue(ref.returnView.codec, raw) }.toMap()
    }

    override suspend fun enumerateEntries(codec: CacheCodec<K>): Map<K, V> = read(null).values.associate { raw ->
        val envelope = Json.parseToJsonElement(raw).jsonObject
        codec.decode(requireKeyMetadata(envelope["key"]?.jsonPrimitive?.content)) to
            ref.returnView.codec.decode(envelope.getValue("value").jsonPrimitive.content)
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
