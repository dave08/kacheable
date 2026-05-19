# Changelog

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
