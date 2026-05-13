[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!IMPORTANT]
> Kacheable is production-usable, but it is still a `0.x` library. The typed cache-key API is the intended direction and should be safe to try in real applications, while minor source-level refinements may still happen before `1.0` as community feedback comes in.

> [!NOTE]
> Cached values currently use Kotlinx Serialization JSON by default, so stored value types should be `@Serializable` unless you provide a custom codec.

# Kacheable

Kacheable is a Kotlin caching library for wrapping computations behind typed cache keys.

The core idea is:

> One cache key describes one logical cached result. Storage is an optimization plan.

That means the call site talks about what the repository returns, while Kacheable can choose an exact value, hash/indexed value, boolean membership set, or enum classification set behind the scenes.

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
- Blocking and suspending interfaces
- In-memory, Redis/Lettuce, and no-op stores
- Per-cache expiry configuration
- Opt-in loader resilience for cold-cache pressure
- Custom cache naming strategies

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

See [docs/cache-key.md](docs/cache-key.md) for the full cache-key guide, including blocking APIs, custom naming strategies, matchable invalidation, and storage planning details.
