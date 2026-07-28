[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!IMPORTANT]
> Kacheable is production-usable, but it is still a `0.x` library. The typed cache-key API is the intended direction and should be safe to try in real applications, while minor source-level refinements may still happen before `1.0` as community feedback comes in.

> [!NOTE]
> Cached values currently use Kotlinx Serialization JSON by default, so stored value types should be `@Serializable` unless you provide a custom codec.

# Kacheable

Kacheable is a Kotlin caching library for expensive function and repository results. You keep the
domain call as a lambda, then attach the cache identity, storage shape, miss behavior, refresh rules,
and cold-start recovery around it.

The core idea is:

> Keep the lambda as the source of truth. Make everything around it typed, explicit, and reusable.

That means call sites can stay close to the work they are doing:

```kotlin
cache(productCardCache(productId)) {
    catalogClient.fetchProductCard(productId)
}
```

When that result becomes costly enough to deserve more care, the same lambda can opt into fallback,
background loading, refresh, single-flight, and snapshots without turning the repository method into
cache plumbing.

## Installation

Releases are available from JitPack. Add the repository after Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

For the in-memory store and core API:

```kotlin
dependencies {
    implementation("com.github.dave08.kacheable:kacheable-core:0.3.0-alpha02")
}
```

For Redis, use the Lettuce module instead; it brings in `kacheable-core` transitively:

```kotlin
dependencies {
    implementation("com.github.dave08.kacheable:kacheable-lettuce:0.3.0-alpha02")
}
```

Release tags now match the dependency version exactly and do not use a `v` prefix. Historical
releases whose Git tags start with `v` remain available under their existing coordinates. The
`/v/` segment in the JitPack badge URL at the top of this page names the badge endpoint; it is not
part of the current version.

## Quick Start

Define reusable key parts, then compose them into cache keys:

```kotlin
val songId = keyPart<Int>("songId")
val artistId = keyPart<Int>("artistId")
val accountId = keyPart<Int>("accountId")
val locale = matchableKeyPart<String>("locale")
val page = keyPart<Page>("page", Page::offset, Page::limit)

val songCache = cacheKey(
    "song",
    returns<Song>(),             // 1
    key = exact(songId),         // 2
)

val artistPagesCache = cacheKey(
    "artist-pages",
    returns<List<Song>>(),       // 3
    key = partitioned(
        partition = artistId,    // 4
        key = page + locale,     // 5
    ),
)

suspend fun song(songIdValue: Int): Song =
    cache(songCache(songIdValue)) {
        repository.song(songIdValue)
    }

suspend fun artistPage(artistIdValue: Int, pageValue: Page, localeValue: String): List<Song> =
    cache(artistPagesCache(artistIdValue, pageValue, localeValue)) {
        repository.artistPage(artistIdValue, pageValue, localeValue)
    }

suspend fun invalidateArtistLocale(artistIdValue: Int, localeValue: String) {
    cache.invalidate(artistPagesCache.matching(artistIdValue, locale(localeValue))) // 6
}
```

1. `returns<Song>()` says what one cache lookup returns. The return type belongs to the key definition, not to each call site.
2. `exact(songId)` means one `Song` is identified directly by one `songId`.
3. `returns<List<Song>>()` is still one cached value. Collections are not split into many entries unless you model the cache that way.
4. `partition = artistId` groups related entries so they can be invalidated together.
5. `page + locale` composes two key parts into the entry key inside that artist partition.
6. `matching(...)` can invalidate every entry in one artist partition whose matchable key part has that locale.

Partition related values when you want narrow invalidation:

```kotlin
val artistSongCache = cacheKey(
    "artist-song",
    returns<Song>(),          // 1
    key = partitioned(
        partition = artistId, // 2
        key = songId,         // 3
    ),
)

cache(artistSongCache(artistIdValue, songIdValue)) {
    repository.artistSong(artistIdValue, songIdValue)
}

cache.invalidate(artistSongCache(artistIdValue, songIdValue)) // 4
cache.invalidate(artistSongCache.partition(artistIdValue))    // 5
cache.invalidate(artistSongCache.all())                       // 6
```

1. The cache still returns one `Song` per lookup.
2. The partition is the outer grouping key.
3. The entry key identifies one cached result inside that partition.
4. Exact invalidation removes one cached result.
5. Partition invalidation removes every result under one artist.
6. Whole-cache invalidation removes every `artist-song` result across all artists.

## Features

- Raw cache API for simple exact keys
- Typed `cacheKey(...)` API for result-first cache definitions
- Exact values, indexed values, boolean membership, and enum membership
- Exact, partition, matchable, and whole-cache invalidation refs
- Single-partition caches for top-level paginated result families
- Nullable results and nullable key parts
- Conditional writes with `cacheIf`
- Typed miss policies for fallback and background loading
- Blocking and suspending interfaces
- In-memory, Redis/Lettuce, and no-op stores
- Per-cache expiry configuration
- Opt-in loader resilience for cold-cache pressure
- Durable snapshots for indexed/hash-style cache families
- Dependency-free telemetry with bounded in-memory diagnostics
- Typed load-concurrency groups with foreground-aware admission
- Custom cache naming strategies

## Why It Exists

Most application caches start as a small wrapper around a function:

```kotlin
suspend fun productCard(id: ProductId): ProductCard =
    cache(productCardCache(id)) {
        catalogClient.fetchProductCard(id)
    }
```

The hard part arrives later:

- the key has several parts and should be type-safe;
- some values should not be stored;
- a miss should return a cheap fallback while the real value loads;
- a cached value may be stale but still better than blocking the caller;
- a cold Redis start should not force thousands of expensive computations to rerun at once.

Kacheable keeps those concerns around the lambda instead of replacing the lambda with a cache-specific
repository API.

## The Mental Model

`cacheKey(...)` binds three things together:

1. The cache name.
2. The result type returned by one cache lookup.
3. The key shape that identifies that result.

```kotlin
val artistSongsCache = cacheKey(
    "artist-songs",
    returns<List<Song>>(), // 1
    key = exact(artistId), // 2
)
```

Read this as:

> Cache one `List<Song>` for each `artistId`.

The result type is not a storage instruction. `List<Song>`, `Set<Int>`, and `Map<Int, Song>` are ordinary cached values unless you model the cache as partitioned.

1. One lookup returns the whole list.
2. `artistId` directly identifies that one list.

```kotlin
val artistPageCache = cacheKey(
    "artist-page",
    returns<List<Song>>(),    // 1
    key = partitioned(
        partition = artistId, // 2
        key = page,           // 3
    ),
)
```

Read this as:

> Cache one `List<Song>` for each `page` entry inside one `artistId` partition.

This is the point where Kacheable can store related entries together and invalidate them together.

1. One lookup still returns a whole `List<Song>`.
2. `artistId` is the grouping key.
3. `page` identifies one list inside the artist partition.

## Exact Values

Use `exact(...)` when the key points directly at one cached result.

```kotlin
val appSettingsCache = cacheKey(
    "app-settings",
    returns<AppSettings>(), // 1
    key = exact(),          // 2
)

val artistSongsCache = cacheKey(
    "artist-songs",
    returns<List<Song>>(),  // 3
    key = exact(artistId),  // 4
)
```

Collections are ordinary values. `returns<List<Song>>()`, `returns<Set<Int>>()`, and `returns<Map<Int, Song>>()` each describe one cached result unless you choose a partitioned key.

1. `AppSettings` is one cached result.
2. `exact()` is for no-argument values.
3. The whole song list is one cached result.
4. `artistId` directly identifies that one list.

## Partitioned Values

Use `partitioned(partition = ..., key = ...)` when one domain value owns many cached entries.

```kotlin
val artistPagesCache = cacheKey(
    "artist-pages",
    returns<List<Song>>(),    // 1
    key = partitioned(
        partition = artistId, // 2
        key = page,           // 3
    ),
)
```

Read it as: one `List<Song>` for each `page` key inside one `artistId` partition.

The refs tell you what can be invalidated:

```kotlin
cache.invalidate(artistPagesCache(artistIdValue, pageValue)) // 4
cache.invalidate(artistPagesCache.partition(artistIdValue))  // 5
cache.invalidate(artistPagesCache.all())                     // 6
```

1. Each page lookup returns one list.
2. The artist is the partition.
3. The page is the entry key inside the partition.
4. Removes one artist page.
5. Removes every page for one artist.
6. Removes all pages for every artist.

Use `partitioned(key = ...)` when there is no natural outer partition, but the cache should still be stored as one indexed family:

```kotlin
val newestVideosCache = cacheKey(
    "newest-videos",
    returns<List<VideoId>>(),     // 1
    key = partitioned(key = page), // 2
)

cache.invalidate(newestVideosCache.partition()) // 3
```

That is useful for paginated top-level results: each page is still one logical result, but clearing the whole family does not require a raw key-prefix delete.

1. Each lookup returns one page of ids.
2. There is no outer partition value, but the pages still belong to one cache family.
3. `partition()` clears that whole family.

## Matchable Key Parts

Use `matchableKeyPart(...)` when a part of the inner key should be available for scoped invalidation.

```kotlin
val locale = matchableKeyPart<String>("locale")

val localizedPagesCache = cacheKey(
    "localized-pages",
    returns<PageResult>(),     // 1
    key = partitioned(
        partition = artistId,  // 2
        key = page + locale,   // 3
    ),
)

cache.invalidate(localizedPagesCache.matching(artistIdValue, locale("he"))) // 4
```

Matching is key matching inside the cache structure, not value search. It is scoped to a partition or cache family; Kacheable does not do keyspace-wide wildcard searches for typed matchable invalidation.

1. One lookup returns one page result.
2. Matching is scoped to one artist partition.
3. `page + locale` composes the entry key; only `locale` is matchable because it was defined with `matchableKeyPart`.
4. Removes all entries for `locale = "he"` inside the selected artist partition.

Only `matchableKeyPart(...)` values can be passed to `matching(...)`, so this kind of broad invalidation has to be opted into on the key part itself.

```kotlin
val locale = matchableKeyPart<String>("locale")
val device = matchableKeyPart<String>("device")

val pageCache = cacheKey(
    "artist-pages",
    returns<SongPage>(),              // 1
    key = partitioned(
        partition = artistId,         // 2
        key = page + locale + device, // 3
    ),
)

cache.invalidate(pageCache.matching(artistIdValue, locale("he")))                   // 4
cache.invalidate(pageCache.matching(artistIdValue, locale("he"), device("mobile"))) // 5
```

Because matching needs hash-style field matching, `auto()` uses indexed value storage when a partitioned key has matchable entry parts, even if the result type is `Boolean` or an enum.

1. The entry value is still one `SongPage`.
2. Matching stays inside one artist partition.
3. A composed entry key may contain multiple matchable parts.
4. Removes all pages for one locale in the partition.
5. Removes only pages matching both locale and device.

## Membership Results

With `storage = auto()`, partitioned `Boolean` results use set-backed membership storage:

```kotlin
val artistFollowCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),          // 1
    key = partitioned(
        partition = artistId,    // 2
        key = accountId,         // 3
    ),
)

cache(artistFollowCache(artistIdValue, accountIdValue)) {
    repository.isFollowing(artistIdValue, accountIdValue)
}
```

1. The public result is still a `Boolean`.
2. The artist groups all account follow states.
3. Under `auto()`, Kacheable can store account ids in membership sets instead of serialized Boolean values.

Partitioned enum results use enum membership storage:

```kotlin
enum class Reaction { Like, Dislike, None }

val reactionCache = cacheKey(
    "song-reaction",
    returns<Reaction>(),       // 1
    key = partitioned(
        partition = songId,    // 2
        key = accountId,       // 3
    ),
)
```

The caller still gets a `Boolean` or `Reaction`; the set layout is only the storage plan.

1. The public result is still a `Reaction`.
2. The song groups all account reactions.
3. Under `auto()`, Kacheable can store account ids in enum classification sets.

`cacheIf` still applies to newly computed results:

```kotlin
cache(followCache(artistIdValue, accountIdValue), cacheIf = { it }) { // 1
    repository.isFollowing(artistIdValue, accountIdValue)
}
```

1. The result is returned either way, but only `true` values are written.

For membership caches, prefer `membershipStorage(cacheFalse = false)` when the policy is specifically “do not cache false results”:

```kotlin
val followCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),
    key = partitioned(artistId, accountId),
    storage = membershipStorage(cacheFalse = false), // 1
)
```

1. This expresses the same policy at the storage-plan level, which is clearer for Boolean membership caches.

## Storage Overrides

Storage defaults to `auto()`.

```kotlin
cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId),
    storage = auto(), // 1
)

cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId),
    storage = exactValueStorage(), // 2
)

cacheKey(
    "follow",
    returns<Boolean>(),
    key = partitioned(artistId, accountId),
    storage = indexedValueStorage(), // 3
)

cacheKey(
    "follow",
    returns<Boolean>(),
    key = partitioned(artistId, accountId),
    storage = membershipStorage(cacheFalse = false), // 4
)

cacheKey(
    "reaction",
    returns<Reaction>(),
    key = partitioned(songId, accountId),
    storage = enumMembershipStorage<Reaction>(), // 5
)
```

Use overrides when you need a specific storage behavior. For example, force `indexedValueStorage()` if a partitioned `Boolean` should be serialized as an indexed value rather than stored as membership.

1. `auto()` is the default and usually the right choice.
2. `exactValueStorage()` is only for exact keys.
3. `indexedValueStorage()` forces serialized values inside a partition, even for `Boolean`.
4. `membershipStorage(cacheFalse = false)` stores true membership and skips false results.
5. `enumMembershipStorage<Reaction>()` makes enum classification storage explicit.

`auto()` currently resolves like this:

| Key shape | Result type | Storage |
| --- | --- | --- |
| `exact(...)` | any result | one serialized value |
| `partitioned(...)` | `Boolean`, no matchable entry parts | membership sets |
| `partitioned(...)` | enum, no matchable entry parts | enum classification sets |
| `partitioned(...)` | any other result | indexed/hash values |
| `partitioned(...)` | any result with matchable entry parts | indexed/hash values |

Overrides are intentionally type-limited. For example, `exactValueStorage()` belongs to exact keys, while `membershipStorage()` belongs to partitioned `Boolean` keys.

## Nullable Values

Nullable results are allowed:

```kotlin
val optionalSongCache = cacheKey(
    "optional-song",
    returns<Song?>(), // 1
    key = exact(songId),
)
```

Nullable key parts are positional values, not omitted values:

```kotlin
val filter = keyPart<ArtistFilter?>("filter") // 2
val sort = keyPart<ArtistSort?>("sort")       // 3

val artistsCache = cacheKey(
    "artists",
    returns<List<Artist>>(),          // 4
    key = exact(filter + sort + page), // 5
)
```

1. Nullable results can be cached when the cache config has a null placeholder.
2. `filter = null` can be a real key value, such as “no filter selected”.
3. `sort = null` is still positional; it is not omitted from the generated key.
4. The result is one list of artists.
5. Nullable and non-nullable key parts can be composed together.

The default naming strategy renders null key parts as `<null>`. Customize that with:

```kotlin
val cache = Kacheable(
    store = store,
    namingStrategy = defaultCacheNamingStrategy(nullKeyPart = "__NULL__"), // 6
)
```

Use nullable key parts when `null` is a real part of the repository call identity. For example, `filter = null` can mean “no filter selected”, which is different from omitting the filter from the key.

6. The null key placeholder is configurable in the naming strategy.

## Resilience

Kacheable does not add loader coordination by default. Plain cache misses keep the simple behavior: if ten callers miss the same key at the same time, all ten loaders may run.

For expensive loaders, configure resilience globally or per cache:

```kotlin
val cache = Kacheable(
    store = redisStore,
    defaultResilience = CacheResilienceConfig(
        singleFlight = SingleFlightMode.Local, // 1
        maxConcurrentLoads = 8,                // 2
        loadTimeout = 2.seconds,               // 3
        staleOnTimeout = true,                 // 4
    ),
    configs = mapOf(
        "artist-page" to CacheConfig(
            name = "artist-page",
            expiryType = ExpiryType.after_write,
            expiry = 10.minutes,
            resilience = CacheResilienceConfig(
                singleFlight = SingleFlightMode.Redis,
                maxConcurrentLoads = 3,
            ),
        ),
    ),
)
```

1. `Local` runs one loader per cache key per JVM; concurrent callers await the same result.
2. `maxConcurrentLoads` limits how many different cold keys can load for that cache at once.
3. `loadTimeout` bounds the loader path. It does not change Redis command timeouts.
4. `staleOnTimeout` and `staleOnFailure` may return a previously cached value when one exists.

`SingleFlightMode.Redis` coordinates across processes with Redis lock keys. It requires a store that supports distributed coordination, such as the Lettuce store. Kacheable fails fast during startup if Redis single-flight is configured against a store that cannot provide it.

Use `Redis` single-flight for multi-pod cold-cache stampedes. Use `Local` for a cheaper per-process guard. Keep `None` for cheap loaders or when duplicate work is acceptable.

### Load concurrency groups

When several cache families load from the same constrained dependency, declare a typed group on
their cache keys:

```kotlin
val databaseLoads = loadConcurrencyGroup(
    name = "database",
    defaults = LoadConcurrencyConfig(
        maxConcurrentLoads = 12,
        maxConcurrentBackgroundLoads = 4,
        maxQueuedLoads = 100,
        queueTimeout = 500.milliseconds,
    ),
)

val artistCache = cacheKey(
    "artist",
    returns<Artist>(),
    key = exact(artistId),
    loadConcurrency = databaseLoads,
)

val albumCache = cacheKey(
    "album",
    returns<Album>(),
    key = exact(albumId),
    loadConcurrency = databaseLoads,
)
```

Both cache keys now compete for the same 12 permits. The smaller background limit prevents
background refreshes from occupying every permit, leaving capacity for caller-facing misses.
Suspending loaders invoked from a background loader inherit background execution, so the limit also
applies through orchestration such as an availability probe that calls other cache keys.
When work queues, foreground admission takes priority. Kacheable admits background work after a
bounded foreground burst so sustained request traffic cannot starve maintenance indefinitely.
`maxQueuedLoads` rejects excess work with `CacheLoadRejectedException`; normal miss and stale
policies can handle that loader failure.

With the Lettuce store and Redis single-flight, admission happens before distributed leadership.
The admitted caller rechecks the cache and tries to claim the Redis lease. If another pod already
owns that entry, the caller releases its local permit immediately and waits as a joiner. A
background load therefore cannot own a distributed lease while it is merely queued for local
capacity. Custom stores implementing only `DistributedSingleFlightStore` retain the compatibility
path; stores can opt into the improved ordering with
`AdmissionAwareDistributedSingleFlightStore`.

Redis single-flight makes loader execution unique per cache entry across instances that share the
same Redis namespace. Each instance may still schedule a background attempt, but only the lease
holder runs the loader; the others wait for the stored result. It is not a cluster-wide background
job scheduler, and it does not coordinate instances using different stores or namespaces.

Admission does not preempt a loader that already owns a local or Redis single-flight entry. A
foreground request may legitimately join an actively executing background loader for the same
entry. The priority rule prevents queued background work from claiming leadership ahead of
foreground work; it does not cancel or promote work that is already running.

Do not put an orchestration cache and the nested resource caches it awaits in the same group. The
outer loader retains its permit while awaiting the nested cache; a small or exhausted group can
therefore deadlock. Give orchestration work its own group, such as `home-probes`, and put the
loaders that actually consume database connections in a resource group such as `odoo-db`.

Applications can override a declared group without replacing its typed identity, and can specify
an independent per-cache default for keys that do not opt into a group:

```kotlin
val cache = Kacheable(
    store = redisStore,
    loadConcurrency = LoadConcurrencySettings(
        default = LoadConcurrencyConfig(maxConcurrentLoads = 4),
        overrides = mapOf(
            databaseLoads to LoadConcurrencyConfig(
                maxConcurrentLoads = 20,
                maxConcurrentBackgroundLoads = 5,
            ),
        ),
    ),
)
```

Precedence is: explicit group override, then the group’s declared defaults. Ungrouped cache
families use `LoadConcurrencySettings.default`, with a separate limiter per cache name. The older
`CacheResilienceConfig.maxConcurrentLoads` remains supported for ungrouped suspending caches, but
must not be combined with a load concurrency group.

`BlockingKacheable` applies total concurrency, queue-size, and queue-timeout settings. It has no
background miss or refresh API and no coroutine context, so `maxConcurrentBackgroundLoads` and
background-execution propagation apply only to the suspending runtime.

## Miss Policies

Most cache calls can stay as simple read-through lambdas:

```kotlin
cache(songCache(songIdValue)) {
    repository.song(songIdValue)
}
```

When a real miss should behave differently, use `CacheMissPolicy` at the call site:

```kotlin
val productId = keyPart<String>("productId")

val productCardCache = cacheKey(
    "product-cards",
    returns<ProductCard?>(),
    key = partitioned(key = productId),
)

cache(
    productCardCache(productIdValue),
    missPolicy = CacheMissPolicy.loadInBackground(
        fallback = { ProductCard.placeholder(productIdValue) }, // 1
    ),
    storeResultIf = { it != null },                              // 2
) {
    catalogClient.fetchProductCard(productIdValue)              // 3
}
```

1. `loadInBackground` returns the fallback immediately. The fallback is not stored.
2. `storeResultIf` only decides whether the lambda result is cached. It never changes what the caller receives.
3. The lambda remains the source of truth and runs in the background after the fallback is returned.

Use `load(fallbackOnFailure = ...)` when callers should wait for the real value, but you still have
a safe degraded response for errors or timeouts:

```kotlin
cache(
    priceQuoteCache(productIdValue),
    missPolicy = CacheMissPolicy.load(
        fallbackOnFailure = { error -> PriceQuote.unavailable(productIdValue, error.message) },
    ),
) {
    pricingClient.quote(productIdValue)
}
```

The fallback is returned only when the lambda fails. Successful lambda results are still the only
values considered for storage.

Available miss policies:

| Policy | Behavior |
| --- | --- |
| `CacheMissPolicy.load()` | Normal read-through caching. Run the lambda in the request path and return its result. |
| `CacheMissPolicy.load(fallbackOnFailure = ...)` | Run the lambda in the request path and return a fallback only if it fails or times out. |
| `CacheMissPolicy.loadInBackground(fallback = ...)` | Return fallback immediately, then run the lambda in the background. |

Storage decisions are separate from miss behavior:

```kotlin
cache(
    expensiveCache(id),
    missPolicy = CacheMissPolicy.load(),
    refreshPolicy = CacheRefreshPolicy.refreshIf(inBackground = true) { cached -> cached.isStale },
    storeResultIf = { result -> result.isStable },
) { previous ->
    repository.loadExpensiveValue(id, previous)
}
```

`CacheRefreshPolicy.neverRefresh()` returns present cached values normally. `refreshIf(...)` reruns
the lambda only when a cached value is considered stale.

```kotlin
cache(
    productCardCache(productIdValue),
    missPolicy = CacheMissPolicy.loadInBackground(
        fallback = { ProductCard.placeholder(productIdValue) },
    ),
    refreshPolicy = CacheRefreshPolicy.refreshIf(inBackground = true) { cached ->
        cached.generatedAt < clock.now() - 30.minutes
    },
    storeResultIf = { result -> result.isUsable },
) { previous ->
    catalogClient.fetchProductCard(
        id = productIdValue,
        previousVersion = previous?.version,
    )
}
```

On a true miss, `previous` is `null`. On a refresh, `previous` is the cached value that triggered the
refresh. With `inBackground = true`, the caller receives the cached value immediately while the lambda
refreshes it for later callers. Refresh failures keep the previous cached value.

The existing `cacheIf` overload remains available:

```kotlin
cache(expensiveCache(id), cacheIf = { it.isStable }) {
    repository.loadExpensiveValue(id)
}
```

It maps to normal read-through loading with `storeResultIf = cacheIf`.

The three knobs are intentionally separate:

| Knob | Question it answers |
| --- | --- |
| `CacheMissPolicy` | What happens when no cached value exists? |
| `CacheRefreshPolicy` | What happens when a cached value exists but may be stale? |
| `storeResultIf` / `cacheIf` | Should the lambda result be written to storage? |

## Cache Snapshots

Snapshots are opt-in durable warm-cache snapshots for expensive cache families. They are useful when
a cache is too expensive to recreate after a cold Redis start, but the loader lambda should remain
the source of truth.

```kotlin
val productId = keyPart<String>("productId")

val productCardCache = cacheKey(
    "product-cards",
    returns<ProductCard?>(),
    key = partitioned(key = productId),
    storage = indexedValueStorage(), // 1
)

val cache = Kacheable(
    store = redisStore,
    snapshotStore = S3CacheSnapshotStore(
        bucket = "app-cache-snapshots",
        prefix = "catalog/product-cards/v1",
        readObject = { bucket, key -> s3.getObjectBytes(bucket, key) },
        writeObject = { bucket, key, bytes -> s3.putObjectBytes(bucket, key, bytes) },
    ),
    configs = mapOf(
        "product-cards" to CacheConfig(
            name = "product-cards",
            snapshot = persistentSnapshot(
                restore = SnapshotRestore.BackgroundWithOnDemandChunks, // 2
                flushInterval = 15.minutes,                             // 3
                retention = SnapshotRetention.LatestAndPrevious,         // 4
            ),
        ),
    ),
)
```

1. `indexedValueStorage()` stores related entries as an indexed family, which can be exported and restored as chunks.
2. Background restore starts immediately, and an early request miss can restore just the relevant snapshot chunk before running the miss policy.
3. Periodic flush exports the hot cache to the configured snapshot store.
4. Keeping latest and previous lets restore fall back when the latest snapshot is missing or corrupt.

Snapshots are intentionally a warm-cache mechanism, not a second database. A restored entry behaves
like any other cached value: refresh policies may refresh it, expiry may later remove it, and misses
still go through the configured miss policy.

Restore modes:

| Mode | Behavior |
| --- | --- |
| `SnapshotRestore.Blocking` | Restore before `Kacheable(...)` returns. |
| `SnapshotRestore.Background` | Start restoring immediately in the background. |
| `SnapshotRestore.BackgroundWithOnDemandChunks` | Restore in the background, and on a miss try the relevant chunk before running the miss policy. |

Retention modes:

| Mode | Behavior |
| --- | --- |
| `SnapshotRetention.LatestOnly` | Write and restore only the latest snapshot slot. |
| `SnapshotRetention.LatestAndPrevious` | Rotate latest to previous on flush, and fall back to previous when latest is missing or corrupt. |

## Local cache telemetry

Kacheable can report semantic cache behavior without depending on a metrics backend. The built-in
in-memory implementation is intended for tests and local diagnostics:

```kotlin
val telemetry = InMemoryCacheTelemetry(recentEventCapacity = 1_000)

val cache = Kacheable(
    store = redisStore,
    telemetry = telemetry,
    correlationProvider = { currentTraceId() },
)

val snapshot = telemetry.snapshot()
val events = telemetry.recentEvents()
val slowest = telemetry.summary(sortBy = CacheSummarySort.TotalWait)
```

Snapshots separate physical reads, usable cached values, loader execution, concurrency and
single-flight waits, writes, fallbacks, stale values, and caller-visible outcomes. Diagnostic events
carry generated operation and parent-operation identifiers so nested cache calls can be
reconstructed. Optional external correlation identifiers are diagnostic data and must never be
used as metric tags.

Use `withCacheCorrelation(requestId) { ... }` to correlate sibling and nested cache calls in one
coroutine request scope. A `CacheCorrelationProvider` remains useful when the host already exposes
trace context through another mechanism. Ranked summaries can identify cache families with the
largest cumulative waits or loader times, while activity snapshots expose current and peak
foreground loaders, background loaders, and waiters.
Summary rows report admission, local single-flight, and Redis single-flight wait totals separately,
making capacity pressure distinguishable from same-key coordination.

Event retention is disabled by default. When enabled, it is bounded and never records cache keys,
arguments, payloads, returned values, or failures. Use `telemetry.snapshots(interval)` for a
periodic `Flow`; callers that need a `StateFlow` can apply `stateIn` with an application-owned
scope. `reset(resetIdentifiers = true)` is available for isolated local diagnostic sessions.

The same `CacheTelemetry` contract can back future Micrometer or tracing adapters. With the default
`NoopCacheTelemetry`, Kacheable does not create diagnostic events or read stage timers.

## Raw Escape Hatch

The raw API remains available for low-level or migration cases:

```kotlin
cache("user", userId) { // 1
    repository.user(userId)
}

cache.invalidate(rawCacheEntry("user", userId)) // 2
cache.invalidate(rawCache("legacy-family"))     // 3
```

Prefer typed cache refs for new code because they preserve the cache result type and storage plan through invalidation.

1. Raw cache calls are string-keyed and do not carry a typed cache definition.
2. `rawCacheEntry(...)` targets one known legacy entry.
3. `rawCache(...)` targets a whole legacy cache family.

## Naming Strategy

The default naming strategy receives exact and partitioned keys differently:

```kotlin
val songCache = cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId), // 1
)

val artistPageCache = cacheKey(
    "artist-page",
    returns<Page>(),
    key = partitioned(artistId, page), // 2
)
```

For `songCache(7)`, `songId` is passed as primary params.

For `artistPageCache(3, Page(0, 20))`, `artistId` is passed as primary params and `page` is passed as secondary params. Redis/hash-like stores use that split to keep all pages for one artist under one partition key.

Custom naming strategies can change the generated strings while keeping that exact/partition split.

1. Exact keys pass all key values as primary params.
2. Partitioned keys pass partition values as primary params and entry-key values as secondary params.

## More

See [docs/cache-key.md](docs/cache-key.md) for the full cache-key guide, including blocking APIs, custom naming strategies, matchable invalidation, miss policies, snapshots, and storage planning details.

Contributors should follow [docs/testing-guidelines.md](docs/testing-guidelines.md)
for TestBalloon test structure, deterministic coroutine coverage, fixture
boundaries, and core-versus-Redis integration responsibilities.
