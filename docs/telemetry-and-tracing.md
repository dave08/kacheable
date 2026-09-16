# Cache telemetry and tracing

Kacheable exposes backend-neutral `CacheTelemetry` callbacks. An application adapter decides
whether to turn them into metrics, trace events, or spans. The library does not create an
OpenTelemetry SDK or export traces to Tempo.

## What the hooks explain

| Hook | Diagnostic question |
| --- | --- |
| `begin` / `complete` | Which cache operation ran, how long did it take, and did it hit, load, fail, or cancel? |
| `storageRead` | Was time spent on the initial read, a coordination recheck, sibling values, or key metadata? |
| `loadWaitStarted` / `loadWait` | Was the caller waiting for admission, local/Redis single-flight, or partition coordination? |
| `loaderStarted` / `loaderCompleted` | How long did application loading take, and did it succeed, fail, or time out? |
| `storageWrite` | Was a result stored, skipped (including a competing winner), or did publication fail? |
| `partitionConflict` | Which load attempt lost its generation/revision and will another attempt run? |
| `maintenance` | What happened during invalidation or snapshot work? |

Partition reads distinguish `Conflict` from `Absent` and `Failed`. A conflict callback reports
the one-based attempt number and `willRetry`; it does not expose key values or payloads.
The final operation result reports failure when the retry limit is exhausted.

Use `InMemoryCacheTelemetry` for bounded local diagnostics. Its events and counters include
partition waits, sibling reads, and conflicts. It is not an OTLP exporter.

## Connecting an application to Tempo

An application that already propagates OpenTelemetry context and exports OTLP can implement
`CacheTelemetry` in its existing tracing adapter. Two useful approaches are:

- Add cache events to the active request span. This keeps high-volume cache hits inexpensive;
  aggregate hits when recording an event per entry would overwhelm the trace.
- Create an operation span and attach stage timings as events. The adapter owns sampling,
  span lifecycle, and parent selection.

`CacheOperation` supplies the cache family, storage kind, concurrency group, parent cache
operation ID, and optional correlation ID. A cache operation ID or correlation string is not
an OpenTelemetry `SpanContext`. The application must propagate its actual tracing context
across coroutine and thread boundaries.

Kacheable preserves its own operation-parent relationship across nested suspending and blocking
calls. It does not install an adapter-created OpenTelemetry span as the current span while the
loader runs. Consequently, downstream database/Redis spans do not automatically become children
of such a cache span. They can remain children of the application's request span. Full cache-span
parenting would require a separate scoped tracing integration.

Background work may finish after a request span ends. An adapter should deliberately create or
link a background span, or skip that event; do not attach it to whichever unrelated span happens
to be current later.

## Adapting to this release

Existing adapters continue receiving the callbacks they implement. To expose the new behavior:

1. Handle `PartitionContext` and `PartitionMetadata` read attempts, `Conflict` and `Failed` read
   results, and the `PartitionCoordination` wait reason.
2. Override `partitionConflict(attempt, willRetry)` and forward it through any composite/fan-out
   adapter. Its default implementation does nothing, so an old composite silently drops it.
3. Record storage read/write stages if you need to distinguish cache I/O from repository time.
4. Keep background behavior and nested operation linkage explicit in integration tests.

Use bounded metric dimensions such as cache family, storage, result, stage, and wait reason.
Do not use cache arguments, payloads, trace IDs, or operation IDs as metric labels. Telemetry
callbacks must be thread-safe, fast, and non-blocking. Kacheable isolates callback failures from
cache behavior, but an adapter can still add latency by doing synchronous work.

## Verification boundary

Library tests verify callback delivery, classifications, conflict attempts, partition waits,
and parent relationships. They do not prove a service exports those events, that its sampling
keeps the trace, or that Tempo ingests it. Validate those separately in the consuming service:
exercise a hit, miss, contended partition, and conflict; then inspect the exported trace and
compare event timing with the library's local counters.
