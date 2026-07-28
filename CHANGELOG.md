# Changelog

## 0.3.0-alpha02

This release adds dependency-free cache telemetry and resource-aware load admission. It is informed
by a real service workload using Redis single-flight in a multi-pod deployment, where speculative
background cache loads could claim distributed leadership before obtaining local database capacity
and cause foreground requests to inherit background queue delays.

### Added

- `CacheTelemetry`, a backend-neutral semantic observation contract with no-op defaults for
  forwards-compatible adapters.
- `InMemoryCacheTelemetry` for bounded local diagnostics, recent operation timelines, activity
  snapshots, ranked summaries, resettable counters, and periodic `Flow` snapshots.
- Generated operation and parent-operation identifiers for reconstructing nested cache work, plus
  optional external correlation that is deliberately excluded from metric tags.
- Typed `LoadConcurrencyGroup` declarations on cache keys.
- `LoadConcurrencySettings` with:
  - a default applied independently to each ungrouped cache family;
  - typed group overrides;
  - total and background concurrency limits;
  - bounded queues and queue timeouts.
- `AdmissionAwareDistributedSingleFlightStore`, implemented by the Lettuce store, for non-blocking
  distributed lease attempts after local admission.
- Separate summary totals for admission, local single-flight, and Redis single-flight waits.
- Project testing guidelines covering TestBalloon fixtures, narrow fakes, deterministic coroutine
  barriers, core-versus-adapter coverage, and incremental adoption.

### Changed

- Suspending background execution now propagates through nested cache calls.
- Queued suspending loads prioritize foreground work while periodically admitting background work
  to prevent starvation.
- Local and admission-aware Redis single-flight recheck the cache after admission.
- Lettuce Redis single-flight acquires local admission before distributed leadership. Callers that
  find another lease owner release local capacity immediately while joining.
- Blocking caches support total concurrency, queue size, and queue timeout, but intentionally do
  not expose background classification or coroutine-context propagation.
- Release tags now match the Gradle and dependency version exactly, without a `v` prefix. Existing
  prefixed tags remain unchanged.

### Fixed

- Prevented queued background work from owning a Redis lease while waiting for local resource
  capacity.
- Prevented local and Redis single-flight joiners from retaining load permits while waiting.
- Made queue cancellation, permit release, local in-flight cleanup, and Redis lease cleanup safe
  against coroutine cancellation.
- Kept foreground priority from starving background maintenance under sustained request traffic.

### Current Limits

- The built-in telemetry implementation is intended for tests and local diagnostics. Metrics and
  tracing backends should implement `CacheTelemetry`; no Micrometer adapter is bundled yet.
- `snapshots(interval)` returns a cold periodic `Flow`. Applications that require a `StateFlow`
  should apply `stateIn` with an application-owned scope and lifecycle.
- Redis single-flight coordinates loader execution per cache entry; it is not a cluster-wide
  background scheduler.
- Priority admission does not preempt or promote a background loader that already owns a
  single-flight entry.
- Custom stores implementing only `DistributedSingleFlightStore` retain the previous compatibility
  path. They must implement `AdmissionAwareDistributedSingleFlightStore` to use
  admission-before-leadership.

### Verification

- Full `kacheable-core` and `kacheable-lettuce` suites: 153 tests, 0 failures.
- Deterministic coverage for foreground priority, bounded background starvation, queue
  cancellation, background propagation, and separated wait telemetry.
- Redis integration coverage proves queued background work does not claim leadership before
  admission and joiners release local capacity.
- A real Hadran consumer compiled and exercised the release locally. In the controlled contention
  scenario, foreground Redis single-flight wait fell to zero and request time improved from 9.97s
  to 4.77s; remaining latency was attributable to foreground admission and loader work.

## 0.3.0-alpha01

This release broadens Kacheable from typed cache identity into typed cache miss handling and cold-start recovery. The main direction is still simple: wrap a lambda, keep the call site type-safe, and configure the loading/caching behavior around that lambda instead of moving cache logic into every repository.

### Added

- `CacheMissPolicy` for typed miss behavior:
  - `load(...)` for normal read-through caching.
  - `load(fallbackOnFailure = ...)` to run the lambda in the request path and return a fallback if loading fails or times out.
  - `loadInBackground(...)` to return a fallback immediately and warm the cache in the background.
- `CacheRefreshPolicy` for typed stale-value behavior:
  - `neverRefresh()` to return present cached values normally.
  - `refreshIf(inBackground = ...)` to rerun the lambda when a cached value is considered stale.
- Typed `cache(..., missPolicy = ..., refreshPolicy = ..., storeResultIf = ...)` overloads. Existing `cacheIf` overloads remain and map to normal read-through loading with `storeResultIf = cacheIf`.
- Cache snapshots for expensive indexed/hash-style cache families:
  - `CacheConfig(snapshot = persistentSnapshot(...))`
  - `SnapshotRestore.Blocking`
  - `SnapshotRestore.Background`
  - `SnapshotRestore.BackgroundWithOnDemandChunks`
  - `SnapshotRetention.LatestOnly`
  - `SnapshotRetention.LatestAndPrevious`
- `CacheSnapshotStore` with `FileCacheSnapshotStore`, `S3CacheSnapshotStore`, and `NoopCacheSnapshotStore`.
- Low-level store hash scan/write primitives used by snapshot export/import.
- Redis and in-memory snapshot export/import for indexed/hash-style caches.
- Stress coverage for restoring a 1,000-entry indexed Redis cache from snapshots after a cold Redis reset.

### Changed

- `Kacheable(...)` accepts an optional `backgroundScope`. Kacheable only creates an internal scope when background work is actually needed, such as snapshots or `loadInBackground`.
- Snapshot timestamps use Kotlin time internally, without exposing a public clock parameter.
- Store APIs speak storage primitives (`scanHashFields`, `writeHashFields`) instead of snapshot-specific concepts.

### Current Limits

- V1 snapshots cover indexed/hash-style caches. Exact string values and set-membership storage are not snapshotted yet.
- `S3CacheSnapshotStore` is an SDK-agnostic adapter that delegates object reads/writes to user-provided functions. A dedicated S3/LocalStack integration module can still be added later if provider-specific testing earns its keep.
- Fallback values are never stored. Only lambda results are stored, and only when `storeResultIf` allows.

### Verification

- `./gradlew :kacheable-core:test :kacheable-lettuce:test`

## 0.2.0-alpha03

This release adds opt-in resilience controls for cold-cache pressure and hardens the Redis/Lettuce store after production rollout testing.

### Added

- `CacheResilienceConfig` with global and per-cache settings for:
  - `singleFlight = None | Local | Redis`
  - `loadTimeout`
  - `maxConcurrentLoads`
  - `staleOnFailure`
  - `staleOnTimeout`
- Local single-flight, so concurrent callers in one JVM can share one loader for the same cold key.
- Redis single-flight for cross-process coordination when the store implements distributed coordination.
- Store-level compound operations for write-with-expiry and hash-write-with-expiry, so normal cache writes avoid generic transaction machinery.
- Pressure tests for local single-flight, Redis single-flight across two connections, loader failure/timeout cleanup, and concurrent mutation failure behavior.

### Changed

- Redis/Lettuce suspend operations now use Lettuce coroutine commands directly instead of wrapping coroutine-friendly calls in `Dispatchers.IO`.
- Redis/Lettuce `mutate { ... }` records operations first and executes them atomically with Lua after the block completes successfully.
- Redis/Lettuce known compound operations use direct Redis commands or Lua scripts instead of opening `MULTI` for ordinary cache writes.
- `CacheConfig` keeps `nullPlaceholder` in its existing positional slot; the new `resilience` property is appended to reduce source compatibility surprises.

### Fixed

- Avoids `DISCARD without MULTI` when a mutation block fails before all operations are known.
- Redis single-flight fails fast at startup if configured against a store that does not support distributed coordination.
- Loader failure or timeout clears local in-flight state so later calls are not poisoned.

### Verification

- `./gradlew :kacheable-core:test :kacheable-lettuce:test`
- Real API consumer compiled and tested against a local composite build of this version.

## 0.2.0-alpha02

This release makes typed cache-key calls work from Kotlin classes that delegate `Kacheable`.

### Changed

- Typed cache-key runtime operations are now part of the public `Kacheable` and `BlockingKacheable` contracts instead of relying on casts to internal runtime interfaces.
- `cache(...)`, `invoke(...)`, and typed invalidation extensions now dispatch through the public delegated interface, so patterns like `class Repo(kacheable: Kacheable) : Kacheable by kacheable` work with typed keys.

### Verification

- `./gradlew test`

## 0.2.0-alpha01

This release introduces the result-first typed cache-key API as the main direction for Kacheable.

### Added

- `cacheKey("name", returns<T>(), key = exact(...))` for exact typed cache keys.
- `cacheKey("name", returns<T>(), key = partitioned(...))` for partitioned typed cache keys.
- `matchableKeyPart(...)` and `matching(...)` for scoped key-part invalidation inside partitioned caches.
- Typed invalidation refs for exact entries, partitions, matching subsets, whole typed caches, and raw migration targets.
- Automatic storage planning for exact values, indexed values, Boolean membership, and enum membership.
- `cacheIf` for conditional writes after a value is computed.
- Nullable key-part support through the default naming strategy's null placeholder.
- Blocking API parity for the typed cache-key surface.

### Changed

- Public raw-cache write predicates now use `cacheIf` instead of `saveResultIf`/`shouldSaveResult`.
- The typed API no longer requires Kotlin opt-in annotations. Version `0.x` communicates that the API may still receive minor source-level refinements before `1.0`.
- Published artifact metadata now consistently uses `com.github.dave08.kacheable`.

### Migration Notes

- Prefer the new result-first API for new code:

  ```kotlin
  val songCache = cacheKey(
      "song",
      returns<Song>(),
      key = exact(songId),
  )
  ```

- Remove old `ExperimentalKacheableApi` imports and `@OptIn(ExperimentalKacheableApi::class)` markers when upgrading.
- Raw string-cache calls and raw invalidation refs remain available as migration escape hatches.
- Existing Redis data may not be reusable when moving a cache from flat raw keys to typed partitioned or membership storage. Treat storage-shape changes as new cache namespaces or clear the old cache data.

### Verification

- `./gradlew :kacheable-core:test :kacheable-lettuce:test publishToMavenLocal`
