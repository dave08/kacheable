package com.github.dave08.kacheable

import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thread-safe, bounded telemetry implementation intended for tests and local diagnostics.
 *
 * Aggregate series are bounded by [maxSeriesPerStorage] for each storage kind. Diagnostic events are retained only when
 * [recentEventCapacity] is greater than zero, and the oldest event is dropped when the bound is
 * reached. Payloads, cache keys, arguments, and failures are never retained.
 */
class InMemoryCacheTelemetry(
    private val maxSeriesPerStorage: Int = 1_000,
    private val recentEventCapacity: Int = 0,
) : CacheTelemetry {
    private val lock = Any()
    private val nextOperationId = AtomicLong()
    private val nextSequence = AtomicLong()
    private val mutableSeries = linkedMapOf<CacheMetricSeries, MutableCacheSeries>()
    private val recentEvents = ArrayDeque<CacheDiagnosticEvent>()

    init {
        require(maxSeriesPerStorage > 0) { "maxSeriesPerStorage must be greater than zero." }
        require(recentEventCapacity >= 0) { "recentEventCapacity must not be negative." }
    }

    override fun begin(operation: CacheOperation): CacheObservation {
        val operationId = CacheOperationId(nextOperationId.incrementAndGet())
        val context = CacheDiagnosticContext(
            operationId = operationId,
            parentOperationId = operation.parentOperationId,
            correlationId = operation.correlationId,
            cacheName = operation.cacheName,
            storage = operation.storage,
            loadConcurrencyGroup = operation.loadConcurrencyGroup,
        )
        append(context, CacheDiagnosticStage.Started)
        return InMemoryCacheObservation(context)
    }

    override fun maintenance(event: CacheMaintenanceEvent) {
        val context = CacheDiagnosticContext(
            operationId = CacheOperationId(nextOperationId.incrementAndGet()),
            parentOperationId = null,
            correlationId = null,
            cacheName = event.cacheName,
            storage = event.storage,
            loadConcurrencyGroup = null,
        )
        update(context) {
            val metric = CacheMaintenanceMetric(event.operation, event.result)
            maintenance[metric] = maintenance.getOrDefault(metric, 0) + 1
            maintenanceDuration.record(event.durationNanos)
        }
        append(context, CacheDiagnosticStage.Maintenance(event))
    }

    fun snapshot(): CacheTelemetrySnapshot = synchronized(lock) {
        CacheTelemetrySnapshot(
            series = mutableSeries.mapValues { (_, value) -> value.snapshot() },
            capturedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun recentEvents(): List<CacheDiagnosticEvent> = synchronized(lock) {
        recentEvents.toList()
    }

    fun recentEvents(correlationId: String): List<CacheDiagnosticEvent> =
        recentEvents().filter { it.context.correlationId == correlationId }

    fun diagnosticReport(correlationId: String? = null): CacheDiagnosticReport {
        val events = recentEvents().let { retained ->
            if (correlationId == null) retained else retained.filter { it.context.correlationId == correlationId }
        }
        if (events.isEmpty()) {
            return CacheDiagnosticReport(correlationId, 0, 0, 0, emptyList())
        }

        var activeOperations = 0
        var peakConcurrentOperations = 0
        var cumulativeOperationDurationNanos = 0L
        events.forEach { event ->
            when (val stage = event.stage) {
                CacheDiagnosticStage.Started -> {
                    activeOperations++
                    peakConcurrentOperations = max(peakConcurrentOperations, activeOperations)
                }

                is CacheDiagnosticStage.Completed -> {
                    activeOperations = (activeOperations - 1).coerceAtLeast(0)
                    cumulativeOperationDurationNanos += stage.durationNanos
                }

                else -> Unit
            }
        }

        return CacheDiagnosticReport(
            correlationId = correlationId,
            wallDurationNanos = events.last().timestampNanos - events.first().timestampNanos,
            cumulativeOperationDurationNanos = cumulativeOperationDurationNanos,
            peakConcurrentOperations = peakConcurrentOperations,
            events = events,
        )
    }

    fun render(correlationId: String? = null): String = diagnosticReport(correlationId).render()

    /**
     * Ranks retained diagnostic activity without introducing high-cardinality metric series.
     *
     * The summary is derived from [recentEventCapacity], so an instance configured with no retained
     * events returns an empty summary.
     */
    fun summary(
        correlationId: String? = null,
        limit: Int = 20,
        sortBy: CacheSummarySort = CacheSummarySort.TotalWait,
    ): CacheDiagnosticSummary {
        require(limit > 0) { "Summary limit must be greater than zero." }
        val events = recentEvents().let { retained ->
            if (correlationId == null) retained else retained.filter { it.context.correlationId == correlationId }
        }
        val rows = linkedMapOf<CacheDiagnosticSummaryKey, MutableCacheDiagnosticSummaryRow>()
        events.forEach { event ->
            val key = CacheDiagnosticSummaryKey(
                event.context.cacheName,
                event.context.storage,
                event.context.loadConcurrencyGroup,
            )
            val row = rows.getOrPut(key, ::MutableCacheDiagnosticSummaryRow)
            when (val stage = event.stage) {
                is CacheDiagnosticStage.Completed -> {
                    row.operationCount++
                    row.cumulativeOperationDurationNanos += stage.durationNanos
                    when (stage.result) {
                        CacheOperationResult.CachedValue -> row.cachedValueCount++
                        CacheOperationResult.Loaded,
                        CacheOperationResult.Refreshed,
                        -> row.loadCount++

                        CacheOperationResult.Failed,
                        CacheOperationResult.Cancelled,
                        -> row.failureCount++

                        else -> Unit
                    }
                }

                is CacheDiagnosticStage.LoadWait -> {
                    row.totalWaitDurationNanos += stage.durationNanos
                    row.maxWaitDurationNanos = max(row.maxWaitDurationNanos, stage.durationNanos)
                    when (stage.reason) {
                        CacheWaitReason.ConcurrencyLimit ->
                            row.admissionWaitDurationNanos += stage.durationNanos

                        CacheWaitReason.LocalSingleFlight ->
                            row.localSingleFlightWaitDurationNanos += stage.durationNanos

                        CacheWaitReason.RedisSingleFlight ->
                            row.redisSingleFlightWaitDurationNanos += stage.durationNanos
                    }
                }

                is CacheDiagnosticStage.LoaderCompleted ->
                    row.maxLoaderDurationNanos = max(row.maxLoaderDurationNanos, stage.durationNanos)

                else -> Unit
            }
        }
        val ranked = rows.map { (key, row) -> row.snapshot(key) }
            .sortedWith(
                compareByDescending<CacheDiagnosticSummaryRow> { sortBy.value(it) }
                    .thenBy { it.cacheName }
                    .thenBy { it.storage.name },
            )
            .take(limit)
        return CacheDiagnosticSummary(correlationId, sortBy, ranked)
    }

    fun renderSummary(
        correlationId: String? = null,
        limit: Int = 20,
        sortBy: CacheSummarySort = CacheSummarySort.TotalWait,
    ): String = summary(correlationId, limit, sortBy).render()

    fun reset(resetIdentifiers: Boolean = false) {
        synchronized(lock) {
            mutableSeries.clear()
            recentEvents.clear()
            if (resetIdentifiers) {
                nextOperationId.set(0)
                nextSequence.set(0)
            }
        }
    }

    private fun append(
        context: CacheDiagnosticContext,
        stage: CacheDiagnosticStage,
    ) {
        if (recentEventCapacity == 0) return
        val event = CacheDiagnosticEvent(
            sequence = CacheEventSequence(nextSequence.incrementAndGet()),
            timestampNanos = System.nanoTime(),
            context = context,
            stage = stage,
        )
        synchronized(lock) {
            while (recentEvents.size >= recentEventCapacity) {
                recentEvents.removeFirst()
            }
            recentEvents.addLast(event)
        }
    }

    private fun update(
        context: CacheDiagnosticContext,
        update: MutableCacheSeries.() -> Unit,
    ) {
        synchronized(lock) {
            val requested = CacheMetricSeries(context.cacheName, context.storage)
            val seriesForStorage = mutableSeries.keys.count { it.storage == context.storage }
            val key = when {
                mutableSeries.containsKey(requested) -> requested
                seriesForStorage < maxSeriesPerStorage - 1 -> requested
                else -> CacheMetricSeries(OverflowCacheName, context.storage)
            }
            mutableSeries.getOrPut(key, ::MutableCacheSeries).update()
        }
    }

    private inner class InMemoryCacheObservation(
        private val context: CacheDiagnosticContext,
    ) : CacheObservation {
        override val operationId: CacheOperationId = context.operationId
        override val correlationId: String? = context.correlationId

        override fun storageRead(attempt: CacheReadAttempt, result: CacheReadResult, durationNanos: Long) {
            update(context) {
                storageReads[result] = storageReads.getValue(result) + 1
                storageReadDuration.record(durationNanos)
            }
            append(context, CacheDiagnosticStage.StorageRead(attempt, result, durationNanos))
        }

        override fun loadWaitStarted(reason: CacheWaitReason, role: CacheLoadRole) {
            update(context) { waiters.started() }
            append(context, CacheDiagnosticStage.LoadWaitStarted(reason, role))
        }

        override fun loadWait(reason: CacheWaitReason, role: CacheLoadRole, durationNanos: Long) {
            update(context) {
                waiters.completed()
                val metric = CacheWaitMetric(reason, role)
                loadWaits[metric] = loadWaits.getOrDefault(metric, 0) + 1
                loadWaitDuration.record(durationNanos)
            }
            append(context, CacheDiagnosticStage.LoadWait(reason, role, durationNanos))
        }

        override fun loaderStarted(trigger: CacheLoadTrigger, execution: CacheExecution) {
            update(context) {
                when (execution) {
                    CacheExecution.Foreground -> foregroundLoaders.started()
                    CacheExecution.Background -> backgroundLoaders.started()
                }
            }
            append(context, CacheDiagnosticStage.LoaderStarted(trigger, execution))
        }

        override fun loaderCompleted(
            trigger: CacheLoadTrigger,
            execution: CacheExecution,
            result: CacheLoadResult,
            durationNanos: Long,
        ) {
            update(context) {
                when (execution) {
                    CacheExecution.Foreground -> foregroundLoaders.completed()
                    CacheExecution.Background -> backgroundLoaders.completed()
                }
                loaders[CacheLoaderMetric(trigger, execution, result)] =
                    loaders.getOrDefault(CacheLoaderMetric(trigger, execution, result), 0) + 1
                loaderDuration.record(durationNanos)
            }
            append(context, CacheDiagnosticStage.LoaderCompleted(trigger, execution, result, durationNanos))
        }

        override fun storageWrite(result: CacheWriteResult, durationNanos: Long) {
            update(context) {
                storageWrites[result] = storageWrites.getValue(result) + 1
                storageWriteDuration.record(durationNanos)
            }
            append(context, CacheDiagnosticStage.StorageWrite(result, durationNanos))
        }

        override fun complete(result: CacheOperationResult, durationNanos: Long) {
            update(context) {
                operations[result] = operations.getValue(result) + 1
                operationDuration.record(durationNanos)
            }
            append(context, CacheDiagnosticStage.Completed(result, durationNanos))
        }
    }

    private companion object {
        const val OverflowCacheName = "<overflow>"
    }
}

data class CacheMetricSeries(
    val cacheName: String,
    val storage: CacheStorageKind,
)

data class CacheLoaderMetric(
    val trigger: CacheLoadTrigger,
    val execution: CacheExecution,
    val result: CacheLoadResult,
)

data class CacheWaitMetric(
    val reason: CacheWaitReason,
    val role: CacheLoadRole,
)

data class CacheMaintenanceMetric(
    val operation: CacheMaintenanceOperation,
    val result: CacheMaintenanceResult,
)

data class CacheTelemetrySnapshot(
    val series: Map<CacheMetricSeries, CacheSeriesSnapshot>,
    val capturedAtEpochMillis: Long,
) {
    operator fun get(cacheName: String): List<CacheSeriesSnapshot> =
        series.filterKeys { it.cacheName == cacheName }.values.toList()
}

data class CacheSeriesSnapshot(
    val operations: Map<CacheOperationResult, Long>,
    val storageReads: Map<CacheReadResult, Long>,
    val loadWaits: Map<CacheWaitMetric, Long>,
    val loaders: Map<CacheLoaderMetric, Long>,
    val storageWrites: Map<CacheWriteResult, Long>,
    val maintenance: Map<CacheMaintenanceMetric, Long>,
    val operationDuration: CacheDurationSnapshot,
    val storageReadDuration: CacheDurationSnapshot,
    val loadWaitDuration: CacheDurationSnapshot,
    val loaderDuration: CacheDurationSnapshot,
    val storageWriteDuration: CacheDurationSnapshot,
    val maintenanceDuration: CacheDurationSnapshot,
    val foregroundLoaders: CacheActivitySnapshot,
    val backgroundLoaders: CacheActivitySnapshot,
    val waiters: CacheActivitySnapshot,
)

data class CacheActivitySnapshot(
    val current: Int,
    val peak: Int,
)

data class CacheDurationSnapshot(
    val count: Long,
    val totalNanos: Long,
    val maxNanos: Long,
) {
    val averageNanos: Double
        get() = if (count == 0L) 0.0 else totalNanos.toDouble() / count
}

data class CacheDiagnosticContext(
    val operationId: CacheOperationId,
    val parentOperationId: CacheOperationId?,
    val correlationId: String?,
    val cacheName: String,
    val storage: CacheStorageKind,
    val loadConcurrencyGroup: String?,
)

data class CacheDiagnosticEvent(
    val sequence: CacheEventSequence,
    val timestampNanos: Long,
    val context: CacheDiagnosticContext,
    val stage: CacheDiagnosticStage,
)

data class CacheDiagnosticReport(
    val correlationId: String?,
    val wallDurationNanos: Long,
    val cumulativeOperationDurationNanos: Long,
    val peakConcurrentOperations: Int,
    val events: List<CacheDiagnosticEvent>,
) {
    fun render(): String = buildString {
        append("cache telemetry")
        correlationId?.let { append(" correlation=").append(it) }
        append(", wall=").append(formatNanos(wallDurationNanos))
        append(", cumulative operations=").append(formatNanos(cumulativeOperationDurationNanos))
        append(", peak operations=").append(peakConcurrentOperations)
        events.forEach { event ->
            append('\n')
            append('#').append(event.sequence.value)
            append(" op=").append(event.context.operationId.value)
            event.context.parentOperationId?.let { append(" parent=").append(it.value) }
            append(' ').append(event.context.cacheName)
            event.context.loadConcurrencyGroup?.let { append(" group=").append(it) }
            append(' ').append(event.stage.render())
        }
    }
}

enum class CacheSummarySort {
    TotalWait,
    AdmissionWait,
    LocalSingleFlightWait,
    RedisSingleFlightWait,
    MaxWait,
    MaxLoader,
    CumulativeOperationTime,
    Loads,
    Failures,
    ;

    internal fun value(row: CacheDiagnosticSummaryRow): Long = when (this) {
        TotalWait -> row.totalWaitDurationNanos
        AdmissionWait -> row.admissionWaitDurationNanos
        LocalSingleFlightWait -> row.localSingleFlightWaitDurationNanos
        RedisSingleFlightWait -> row.redisSingleFlightWaitDurationNanos
        MaxWait -> row.maxWaitDurationNanos
        MaxLoader -> row.maxLoaderDurationNanos
        CumulativeOperationTime -> row.cumulativeOperationDurationNanos
        Loads -> row.loadCount
        Failures -> row.failureCount
    }
}

data class CacheDiagnosticSummaryRow(
    val cacheName: String,
    val storage: CacheStorageKind,
    val loadConcurrencyGroup: String?,
    val operationCount: Long,
    val cachedValueCount: Long,
    val loadCount: Long,
    val failureCount: Long,
    val cumulativeOperationDurationNanos: Long,
    val totalWaitDurationNanos: Long,
    val admissionWaitDurationNanos: Long,
    val localSingleFlightWaitDurationNanos: Long,
    val redisSingleFlightWaitDurationNanos: Long,
    val maxWaitDurationNanos: Long,
    val maxLoaderDurationNanos: Long,
)

data class CacheDiagnosticSummary(
    val correlationId: String?,
    val sortBy: CacheSummarySort,
    val rows: List<CacheDiagnosticSummaryRow>,
) {
    fun render(): String = buildString {
        append("cache telemetry summary")
        correlationId?.let { append(" correlation=").append(it) }
        append(" sort=").append(sortBy)
        rows.forEachIndexed { index, row ->
            append('\n').append(index + 1).append(". ").append(row.cacheName)
            append(" storage=").append(row.storage)
            row.loadConcurrencyGroup?.let { append(" group=").append(it) }
            append(" operations=").append(row.operationCount)
            append(" cached=").append(row.cachedValueCount)
            append(" loads=").append(row.loadCount)
            append(" failures=").append(row.failureCount)
            append(" cumulative=").append(formatNanos(row.cumulativeOperationDurationNanos))
            append(" wait=").append(formatNanos(row.totalWaitDurationNanos))
            append(" admission-wait=").append(formatNanos(row.admissionWaitDurationNanos))
            append(" local-single-flight-wait=").append(formatNanos(row.localSingleFlightWaitDurationNanos))
            append(" redis-single-flight-wait=").append(formatNanos(row.redisSingleFlightWaitDurationNanos))
            append(" max-wait=").append(formatNanos(row.maxWaitDurationNanos))
            append(" max-loader=").append(formatNanos(row.maxLoaderDurationNanos))
        }
    }
}

interface CacheDiagnosticStage {
    data object Started : CacheDiagnosticStage

    data class LoadWaitStarted(
        val reason: CacheWaitReason,
        val role: CacheLoadRole,
    ) : CacheDiagnosticStage

    data class LoaderStarted(
        val trigger: CacheLoadTrigger,
        val execution: CacheExecution,
    ) : CacheDiagnosticStage

    data class StorageRead(
        val attempt: CacheReadAttempt,
        val result: CacheReadResult,
        val durationNanos: Long,
    ) : CacheDiagnosticStage

    data class LoadWait(
        val reason: CacheWaitReason,
        val role: CacheLoadRole,
        val durationNanos: Long,
    ) : CacheDiagnosticStage

    data class LoaderCompleted(
        val trigger: CacheLoadTrigger,
        val execution: CacheExecution,
        val result: CacheLoadResult,
        val durationNanos: Long,
    ) : CacheDiagnosticStage

    data class StorageWrite(
        val result: CacheWriteResult,
        val durationNanos: Long,
    ) : CacheDiagnosticStage

    data class Completed(
        val result: CacheOperationResult,
        val durationNanos: Long,
    ) : CacheDiagnosticStage

    data class Maintenance(
        val event: CacheMaintenanceEvent,
    ) : CacheDiagnosticStage
}

/**
 * Emits immutable snapshots at a bounded rate. Callers can use `stateIn` when a live StateFlow is
 * needed and retain ownership of that flow's coroutine lifecycle.
 */
fun InMemoryCacheTelemetry.snapshots(
    interval: Duration = 250.milliseconds,
): Flow<CacheTelemetrySnapshot> = flow {
    require(interval.isPositive()) { "interval must be greater than zero." }
    emit(snapshot())
    while (currentCoroutineContext().isActive) {
        delay(interval)
        emit(snapshot())
    }
}

private class MutableCacheSeries {
    val operations = CacheOperationResult.entries.associateWithTo(linkedMapOf()) { 0L }
    val storageReads = CacheReadResult.entries.associateWithTo(linkedMapOf()) { 0L }
    val loadWaits = linkedMapOf<CacheWaitMetric, Long>()
    val loaders = linkedMapOf<CacheLoaderMetric, Long>()
    val storageWrites = CacheWriteResult.entries.associateWithTo(linkedMapOf()) { 0L }
    val maintenance = linkedMapOf<CacheMaintenanceMetric, Long>()
    val operationDuration = MutableDurationSummary()
    val storageReadDuration = MutableDurationSummary()
    val loadWaitDuration = MutableDurationSummary()
    val loaderDuration = MutableDurationSummary()
    val storageWriteDuration = MutableDurationSummary()
    val maintenanceDuration = MutableDurationSummary()
    val foregroundLoaders = MutableActivity()
    val backgroundLoaders = MutableActivity()
    val waiters = MutableActivity()

    fun snapshot(): CacheSeriesSnapshot = CacheSeriesSnapshot(
        operations = operations.toMap(),
        storageReads = storageReads.toMap(),
        loadWaits = loadWaits.toMap(),
        loaders = loaders.toMap(),
        storageWrites = storageWrites.toMap(),
        maintenance = maintenance.toMap(),
        operationDuration = operationDuration.snapshot(),
        storageReadDuration = storageReadDuration.snapshot(),
        loadWaitDuration = loadWaitDuration.snapshot(),
        loaderDuration = loaderDuration.snapshot(),
        storageWriteDuration = storageWriteDuration.snapshot(),
        maintenanceDuration = maintenanceDuration.snapshot(),
        foregroundLoaders = foregroundLoaders.snapshot(),
        backgroundLoaders = backgroundLoaders.snapshot(),
        waiters = waiters.snapshot(),
    )
}

private data class CacheDiagnosticSummaryKey(
    val cacheName: String,
    val storage: CacheStorageKind,
    val loadConcurrencyGroup: String?,
)

private class MutableCacheDiagnosticSummaryRow {
    var operationCount = 0L
    var cachedValueCount = 0L
    var loadCount = 0L
    var failureCount = 0L
    var cumulativeOperationDurationNanos = 0L
    var totalWaitDurationNanos = 0L
    var admissionWaitDurationNanos = 0L
    var localSingleFlightWaitDurationNanos = 0L
    var redisSingleFlightWaitDurationNanos = 0L
    var maxWaitDurationNanos = 0L
    var maxLoaderDurationNanos = 0L

    fun snapshot(key: CacheDiagnosticSummaryKey): CacheDiagnosticSummaryRow =
        CacheDiagnosticSummaryRow(
            cacheName = key.cacheName,
            storage = key.storage,
            loadConcurrencyGroup = key.loadConcurrencyGroup,
            operationCount = operationCount,
            cachedValueCount = cachedValueCount,
            loadCount = loadCount,
            failureCount = failureCount,
            cumulativeOperationDurationNanos = cumulativeOperationDurationNanos,
            totalWaitDurationNanos = totalWaitDurationNanos,
            admissionWaitDurationNanos = admissionWaitDurationNanos,
            localSingleFlightWaitDurationNanos = localSingleFlightWaitDurationNanos,
            redisSingleFlightWaitDurationNanos = redisSingleFlightWaitDurationNanos,
            maxWaitDurationNanos = maxWaitDurationNanos,
            maxLoaderDurationNanos = maxLoaderDurationNanos,
        )
}

private class MutableActivity {
    private var current = 0
    private var peak = 0

    fun started() {
        current++
        peak = max(peak, current)
    }

    fun completed() {
        current = (current - 1).coerceAtLeast(0)
    }

    fun snapshot(): CacheActivitySnapshot = CacheActivitySnapshot(current, peak)
}

private class MutableDurationSummary {
    private var count: Long = 0
    private var totalNanos: Long = 0
    private var maxNanos: Long = 0

    fun record(durationNanos: Long) {
        val safeDuration = durationNanos.coerceAtLeast(0)
        count++
        totalNanos += safeDuration
        maxNanos = max(maxNanos, safeDuration)
    }

    fun snapshot(): CacheDurationSnapshot = CacheDurationSnapshot(count, totalNanos, maxNanos)
}

private fun CacheDiagnosticStage.render(): String = when (this) {
    CacheDiagnosticStage.Started -> "started"
    is CacheDiagnosticStage.StorageRead ->
        "read attempt=$attempt result=$result duration=${formatNanos(durationNanos)}"

    is CacheDiagnosticStage.LoadWait ->
        "wait reason=$reason role=$role duration=${formatNanos(durationNanos)}"

    is CacheDiagnosticStage.LoadWaitStarted ->
        "wait-started reason=$reason role=$role"

    is CacheDiagnosticStage.LoaderStarted ->
        "loader-started trigger=$trigger execution=$execution"

    is CacheDiagnosticStage.LoaderCompleted ->
        "loader trigger=$trigger execution=$execution result=$result duration=${formatNanos(durationNanos)}"

    is CacheDiagnosticStage.StorageWrite ->
        "write result=$result duration=${formatNanos(durationNanos)}"

    is CacheDiagnosticStage.Completed ->
        "completed result=$result duration=${formatNanos(durationNanos)}"

    is CacheDiagnosticStage.Maintenance ->
        "maintenance operation=${event.operation} result=${event.result} duration=${formatNanos(event.durationNanos)}"

    else -> toString()
}

private fun formatNanos(nanos: Long): String = when {
    nanos >= 1_000_000_000 -> String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0)
    nanos >= 1_000_000 -> String.format(Locale.ROOT, "%.3fms", nanos / 1_000_000.0)
    nanos >= 1_000 -> String.format(Locale.ROOT, "%.3fµs", nanos / 1_000.0)
    else -> "${nanos}ns"
}
