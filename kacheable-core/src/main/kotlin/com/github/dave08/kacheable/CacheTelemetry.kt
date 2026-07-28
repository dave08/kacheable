package com.github.dave08.kacheable

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * Receives semantic cache observations without tying Kacheable to a metrics or tracing backend.
 *
 * Implementations must be thread-safe. Kacheable protects cache behavior from exceptions thrown by
 * telemetry implementations, but implementations should still keep callbacks fast and non-blocking.
 */
fun interface CacheTelemetry {
    fun begin(operation: CacheOperation): CacheObservation

    fun maintenance(event: CacheMaintenanceEvent) = Unit
}

/**
 * Supplies an optional request or trace identifier for diagnostic correlation.
 *
 * Correlation identifiers are diagnostic data. Metrics adapters must not use them as metric tags.
 */
fun interface CacheCorrelationProvider {
    fun currentCorrelationId(): String?
}

@JvmInline
value class CacheOperationId(val value: Long)

@JvmInline
value class CacheEventSequence(val value: Long)

data class CacheOperation(
    val cacheName: String,
    val storage: CacheStorageKind,
    val loadConcurrencyGroup: String? = null,
    val parentOperationId: CacheOperationId? = null,
    val correlationId: String? = null,
)

class CacheCorrelationContext internal constructor(
    val correlationId: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CacheCorrelationContext>
}

suspend fun <R> withCacheCorrelation(
    correlationId: String,
    block: suspend () -> R,
): R {
    require(correlationId.isNotBlank()) { "Cache correlation identifier must not be blank." }
    return withContext(CacheCorrelationContext(correlationId)) { block() }
}

enum class CacheStorageKind {
    String,
    HashMap,
    Set,
}

enum class CacheReadAttempt {
    Hot,
    AfterSnapshot,
    SingleFlightRecheck,
}

enum class CacheReadResult {
    Present,
    Absent,
}

enum class CacheWaitReason {
    ConcurrencyLimit,
    LocalSingleFlight,
    RedisSingleFlight,
}

enum class CacheLoadRole {
    Leader,
    Joiner,
}

enum class CacheLoadTrigger {
    Miss,
    Refresh,
}

enum class CacheExecution {
    Foreground,
    Background,
}

enum class CacheLoadResult {
    Success,
    Timeout,
    Failure,
    Cancelled,
}

enum class CacheWriteResult {
    Stored,
    Skipped,
    Failed,
}

enum class CacheMaintenanceOperation {
    InvalidateEntry,
    InvalidatePart,
    InvalidateAll,
    SnapshotRestore,
    SnapshotFlush,
}

enum class CacheMaintenanceResult {
    Success,
    Skipped,
    Failed,
}

data class CacheMaintenanceEvent(
    val cacheName: String,
    val storage: CacheStorageKind,
    val operation: CacheMaintenanceOperation,
    val result: CacheMaintenanceResult,
    val durationNanos: Long,
    val affectedEntries: Long? = null,
)

/**
 * Final caller-visible result of one cache invocation.
 *
 * [CachedValue] is recorded only after a present physical value has produced a usable returned
 * value. Decoding or another future transformation may therefore turn a physical read hit into
 * [Loaded] or [Failed] without changing the meaning of these outcomes.
 */
enum class CacheOperationResult {
    CachedValue,
    Loaded,
    Refreshed,
    BackgroundFallback,
    FailureFallback,
    Stale,
    Failed,
    Cancelled,
}

/**
 * One active cache observation.
 *
 * Default no-op methods keep the contract extensible when new semantic stages are introduced.
 */
interface CacheObservation {
    val operationId: CacheOperationId?
        get() = null

    val correlationId: String?
        get() = null

    fun storageRead(
        attempt: CacheReadAttempt,
        result: CacheReadResult,
        durationNanos: Long,
    ) = Unit

    fun loadWaitStarted(
        reason: CacheWaitReason,
        role: CacheLoadRole,
    ) = Unit

    fun loadWait(
        reason: CacheWaitReason,
        role: CacheLoadRole,
        durationNanos: Long,
    ) = Unit

    fun loaderStarted(
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
    ) = Unit

    fun loaderCompleted(
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
        result: CacheLoadResult,
        durationNanos: Long,
    ) = Unit

    fun storageWrite(
        result: CacheWriteResult,
        durationNanos: Long,
    ) = Unit

    fun complete(
        result: CacheOperationResult,
        durationNanos: Long,
    ) = Unit
}

object NoopCacheTelemetry : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation = NoopCacheObservation
}

object NoopCacheObservation : CacheObservation

/**
 * Prevents failures in [delegate] from changing cache behavior.
 */
class SafeCacheTelemetry(
    private val delegate: CacheTelemetry,
) : CacheTelemetry {
    override fun begin(operation: CacheOperation): CacheObservation =
        try {
            SafeCacheObservation(delegate.begin(operation))
        } catch (_: Throwable) {
            NoopCacheObservation
        }

    override fun maintenance(event: CacheMaintenanceEvent) {
        try {
            delegate.maintenance(event)
        } catch (_: Throwable) {
            // Telemetry is never allowed to change cache behavior.
        }
    }
}

private class SafeCacheObservation(
    private val delegate: CacheObservation,
) : CacheObservation {
    override val operationId: CacheOperationId?
        get() = try {
            delegate.operationId
        } catch (_: Throwable) {
            null
        }

    override val correlationId: String?
        get() = try {
            delegate.correlationId
        } catch (_: Throwable) {
            null
        }

    override fun storageRead(attempt: CacheReadAttempt, result: CacheReadResult, durationNanos: Long) {
        safely { delegate.storageRead(attempt, result, durationNanos) }
    }

    override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
        safely { delegate.loadWaitStarted(reason, role) }
    }

    override fun loadWait(reason: CacheWaitReason, role: CacheLoadRole, durationNanos: Long) {
        safely { delegate.loadWait(reason, role, durationNanos) }
    }

    override fun loaderStarted(trigger: CacheLoadTrigger, execution: CacheExecution) {
        safely { delegate.loaderStarted(trigger, execution) }
    }

    override fun loaderCompleted(
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
        result: CacheLoadResult,
        durationNanos: Long,
    ) {
        safely { delegate.loaderCompleted(trigger, execution, result, durationNanos) }
    }

    override fun storageWrite(result: CacheWriteResult, durationNanos: Long) {
        safely { delegate.storageWrite(result, durationNanos) }
    }

    override fun complete(result: CacheOperationResult, durationNanos: Long) {
        safely { delegate.complete(result, durationNanos) }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Telemetry is never allowed to change cache behavior.
        }
    }
}

internal fun CacheStorage.toTelemetryKind(): CacheStorageKind = when (this) {
    CacheStorage.String -> CacheStorageKind.String
    CacheStorage.HashMap -> CacheStorageKind.HashMap
    CacheStorage.Set -> CacheStorageKind.Set
}
