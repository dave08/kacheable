package com.github.dave08.kacheable.internal

import com.github.dave08.kacheable.CacheCorrelationProvider
import com.github.dave08.kacheable.CacheCorrelationContext
import com.github.dave08.kacheable.CacheExecution
import com.github.dave08.kacheable.CacheLoadResult
import com.github.dave08.kacheable.CacheLoadRole
import com.github.dave08.kacheable.CacheLoadTrigger
import com.github.dave08.kacheable.CacheMaintenanceEvent
import com.github.dave08.kacheable.CacheMaintenanceOperation
import com.github.dave08.kacheable.CacheMaintenanceResult
import com.github.dave08.kacheable.CacheObservation
import com.github.dave08.kacheable.CacheOperation
import com.github.dave08.kacheable.CacheOperationResult
import com.github.dave08.kacheable.CacheReadAttempt
import com.github.dave08.kacheable.CacheReadResult
import com.github.dave08.kacheable.CacheStorageKind
import com.github.dave08.kacheable.CacheTelemetry
import com.github.dave08.kacheable.CacheWaitReason
import com.github.dave08.kacheable.CacheWriteResult
import com.github.dave08.kacheable.NoopCacheObservation
import com.github.dave08.kacheable.NoopCacheTelemetry
import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.SafeCacheTelemetry
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal class CacheTelemetryRuntime(
    telemetry: CacheTelemetry,
    private val correlationProvider: CacheCorrelationProvider?,
) {
    private val telemetry = if (telemetry === NoopCacheTelemetry) telemetry else SafeCacheTelemetry(telemetry)
    private val blockingParent = ThreadLocal<OperationObservation?>()

    val enabled: Boolean
        get() = telemetry !== NoopCacheTelemetry

    suspend fun <R> observe(
        cacheName: String,
        storage: CacheStorageKind,
        loadConcurrency: LoadConcurrencyGroup? = null,
        block: suspend (OperationObservation) -> R,
    ): R {
        if (!enabled) return block(OperationObservation.noop())

        val context = currentCoroutineContext()
        val parent = context[ObservationContext]?.observation
        val correlationId = context[CacheCorrelationContext]?.correlationId
            ?: parent?.correlationId
            ?: safelyGetCorrelationId()
        val observation = begin(cacheName, storage, loadConcurrency, parent, correlationId)
        return withContext(ObservationContext(observation)) {
            try {
                block(observation)
            } catch (t: Throwable) {
                observation.complete(if (t is kotlinx.coroutines.CancellationException) CacheOperationResult.Cancelled else CacheOperationResult.Failed)
                throw t
            }
        }
    }

    fun <R> observeBlocking(
        cacheName: String,
        storage: CacheStorageKind,
        loadConcurrency: LoadConcurrencyGroup? = null,
        block: (OperationObservation) -> R,
    ): R {
        if (!enabled) return block(OperationObservation.noop())

        val previous = blockingParent.get()
        val observation = begin(
            cacheName,
            storage,
            loadConcurrency,
            previous,
            previous?.correlationId ?: safelyGetCorrelationId(),
        )
        blockingParent.set(observation)
        return try {
            block(observation)
        } catch (t: Throwable) {
            observation.complete(CacheOperationResult.Failed)
            throw t
        } finally {
            blockingParent.set(previous)
        }
    }

    suspend fun <R> maintenance(
        cacheName: String,
        storage: CacheStorageKind,
        operation: CacheMaintenanceOperation,
        block: suspend () -> R,
    ): R {
        if (!enabled) return block()
        val started = System.nanoTime()
        return try {
            block().also {
                telemetry.maintenance(
                    CacheMaintenanceEvent(
                        cacheName,
                        storage,
                        operation,
                        CacheMaintenanceResult.Success,
                        elapsedSince(started),
                    ),
                )
            }
        } catch (t: Throwable) {
            telemetry.maintenance(
                CacheMaintenanceEvent(
                    cacheName,
                    storage,
                    operation,
                    CacheMaintenanceResult.Failed,
                    elapsedSince(started),
                ),
            )
            throw t
        }
    }

    fun <R> maintenanceBlocking(
        cacheName: String,
        storage: CacheStorageKind,
        operation: CacheMaintenanceOperation,
        block: () -> R,
    ): R {
        if (!enabled) return block()
        val started = System.nanoTime()
        return try {
            block().also {
                telemetry.maintenance(
                    CacheMaintenanceEvent(
                        cacheName,
                        storage,
                        operation,
                        CacheMaintenanceResult.Success,
                        elapsedSince(started),
                    ),
                )
            }
        } catch (t: Throwable) {
            telemetry.maintenance(
                CacheMaintenanceEvent(
                    cacheName,
                    storage,
                    operation,
                    CacheMaintenanceResult.Failed,
                    elapsedSince(started),
                ),
            )
            throw t
        }
    }

    fun maintenanceResult(
        cacheName: String,
        storage: CacheStorageKind,
        operation: CacheMaintenanceOperation,
        result: CacheMaintenanceResult,
        startedAtNanos: Long,
        affectedEntries: Long? = null,
    ) {
        if (!enabled) return
        telemetry.maintenance(
            CacheMaintenanceEvent(
                cacheName,
                storage,
                operation,
                result,
                elapsedSince(startedAtNanos),
                affectedEntries,
            ),
        )
    }

    private fun begin(
        cacheName: String,
        storage: CacheStorageKind,
        loadConcurrency: LoadConcurrencyGroup?,
        parent: OperationObservation?,
        correlationId: String?,
    ): OperationObservation {
        val delegate = telemetry.begin(
            CacheOperation(
                cacheName = cacheName,
                storage = storage,
                loadConcurrencyGroup = loadConcurrency?.name,
                parentOperationId = parent?.operationId,
                correlationId = correlationId,
            ),
        )
        return OperationObservation(delegate, System.nanoTime())
    }

    private fun safelyGetCorrelationId(): String? =
        try {
            correlationProvider?.currentCorrelationId()
        } catch (_: Throwable) {
            null
        }
}

internal class OperationObservation internal constructor(
    private val delegate: CacheObservation,
    private val startedAtNanos: Long,
) {
    private val completed = AtomicBoolean()
    val isEnabled: Boolean = delegate !== NoopCacheObservation

    val operationId
        get() = delegate.operationId

    val correlationId
        get() = delegate.correlationId

    fun startTimer(): Long = if (isEnabled) System.nanoTime() else 0L

    fun storageRead(attempt: CacheReadAttempt, result: CacheReadResult, startedAtNanos: Long) {
        if (!isEnabled) return
        delegate.storageRead(attempt, result, elapsedSince(startedAtNanos))
    }

    fun loadWait(reason: CacheWaitReason, role: CacheLoadRole, startedAtNanos: Long) {
        if (!isEnabled) return
        delegate.loadWait(reason, role, elapsedSince(startedAtNanos))
    }

    fun loadWaitDuration(reason: CacheWaitReason, role: CacheLoadRole, durationNanos: Long) {
        if (!isEnabled) return
        delegate.loadWait(reason, role, durationNanos.coerceAtLeast(0))
    }

    fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
        if (!isEnabled) return
        delegate.loadWaitStarted(reason, role)
    }

    fun loaderStarted(trigger: CacheLoadTrigger, execution: CacheExecution) {
        if (!isEnabled) return
        delegate.loaderStarted(trigger, execution)
    }

    fun loaderCompleted(
        trigger: CacheLoadTrigger,
        execution: CacheExecution,
        result: CacheLoadResult,
        startedAtNanos: Long,
    ) {
        if (!isEnabled) return
        delegate.loaderCompleted(trigger, execution, result, elapsedSince(startedAtNanos))
    }

    fun storageWrite(result: CacheWriteResult, startedAtNanos: Long) {
        if (!isEnabled) return
        delegate.storageWrite(result, elapsedSince(startedAtNanos))
    }

    fun complete(result: CacheOperationResult) {
        if (!isEnabled) return
        if (completed.compareAndSet(false, true)) {
            delegate.complete(result, elapsedSince(startedAtNanos))
        }
    }

    companion object {
        fun noop(): OperationObservation = OperationObservation(NoopCacheObservation, 0)
    }
}

internal class ObservationContext(
    val observation: OperationObservation,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ObservationContext>
}

internal fun elapsedSince(startedAtNanos: Long): Long =
    (System.nanoTime() - startedAtNanos).coerceAtLeast(0)
