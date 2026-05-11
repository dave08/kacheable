[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!CAUTION]
> Kacheable is still experimental. The typed cache-key API is the intended public direction, but names may still tighten before a stable release.

> [!NOTE]
> Cached values currently use Kotlinx Serialization JSON by default, so stored value types should be `@Serializable` unless you provide a custom codec.

# Kacheable

Kacheable is a Kotlin caching library for wrapping computations behind typed cache keys.

The core idea is:

> One cache key describes one logical cached result. Storage is an optimization plan.

That means the call site talks about what the repository returns, while Kacheable can choose an exact value, hash/indexed value, boolean membership set, or enum classification set behind the scenes.

## Quick Start

Define reusable key parts:

```kotlin
val songId = keyPart<Int>("songId")
val artistId = keyPart<Int>("artistId")
val accountId = keyPart<Int>("accountId")
```

Cache one exact value per key:

```kotlin
val songCache = cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId),
)

suspend fun song(songIdValue: Int): Song =
    cache(songCache(songIdValue)) {
        repository.song(songIdValue)
    }

suspend fun invalidateSong(songIdValue: Int) {
    cache.invalidate(songCache(songIdValue))
}
```

Partition related values when you want narrow invalidation:

```kotlin
val artistSongCache = cacheKey(
    "artist-song",
    returns<Song>(),
    key = partitioned(
        partition = artistId,
        key = songId,
    ),
)

cache(artistSongCache(artistIdValue, songIdValue)) {
    repository.artistSong(artistIdValue, songIdValue)
}

cache.invalidate(artistSongCache(artistIdValue, songIdValue))
cache.invalidate(artistSongCache.partition(artistIdValue))
cache.invalidate(artistSongCache.all())
```

## Features

- Raw cache API for simple exact keys
- Typed `cacheKey(...)` API for result-first cache definitions
- Exact values, indexed values, boolean membership, and enum membership
- Exact, partition, matchable, and whole-cache invalidation refs
- Single-partition caches for top-level paginated result families
- Nullable results and nullable key parts
- Blocking and suspending interfaces
- In-memory, Redis/Lettuce, and no-op stores
- Per-cache expiry configuration
- Custom cache naming strategies

## Exact Values

Use `exact(...)` when the key points directly at one cached result.

```kotlin
val appSettingsCache = cacheKey(
    "app-settings",
    returns<AppSettings>(),
    key = exact(),
)

val artistSongsCache = cacheKey(
    "artist-songs",
    returns<List<Song>>(),
    key = exact(artistId),
)
```

Collections are ordinary values. `returns<List<Song>>()`, `returns<Set<Int>>()`, and `returns<Map<Int, Song>>()` each describe one cached result unless you choose a partitioned key.

## Partitioned Values

Use `partitioned(partition = ..., key = ...)` when one outer domain value owns many cached entries.

```kotlin
val artistPagesCache = cacheKey(
    "artist-pages",
    returns<List<Song>>(),
    key = partitioned(
        partition = artistId,
        key = page,
    ),
)
```

Read it as: one `List<Song>` for each `page` key inside one `artistId` partition.

Use `partitioned(key = ...)` when there is no natural outer partition, but the cache should still be stored as one indexed family:

```kotlin
val newestVideosCache = cacheKey(
    "newest-videos",
    returns<List<VideoId>>(),
    key = partitioned(key = page),
)

cache.invalidate(newestVideosCache.partition()) // all pages in this cache family
```

## Matchable Key Parts

Use `matchableKeyPart(...)` when a part of the inner key should be available for scoped invalidation.

```kotlin
val locale = matchableKeyPart<String>("locale")

val localizedPagesCache = cacheKey(
    "localized-pages",
    returns<PageResult>(),
    key = partitioned(
        partition = artistId,
        key = page + locale,
    ),
)

cache.invalidate(localizedPagesCache.matching(artistIdValue, locale("he")))
```

Matching is key matching inside the cache structure, not value search. It is scoped to a partition or cache family; Kacheable does not do keyspace-wide wildcard searches for typed matchable invalidation.

## Membership Results

With `storage = auto()`, partitioned `Boolean` results use set-backed membership storage:

```kotlin
val artistFollowCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),
    key = partitioned(
        partition = artistId,
        key = accountId,
    ),
)

cache(artistFollowCache(artistIdValue, accountIdValue)) {
    repository.isFollowing(artistIdValue, accountIdValue)
}
```

Partitioned enum results use enum membership storage:

```kotlin
enum class Reaction { Like, Dislike, None }

val reactionCache = cacheKey(
    "song-reaction",
    returns<Reaction>(),
    key = partitioned(
        partition = songId,
        key = accountId,
    ),
)
```

The caller still gets a `Boolean` or `Reaction`; the set layout is only the storage plan.

## Storage Overrides

Storage defaults to `auto()`.

```kotlin
storage = auto()
storage = exactValueStorage()
storage = indexedValueStorage()
storage = membershipStorage(cacheFalse = false)
storage = enumMembershipStorage<Reaction>()
```

Use overrides when you need a specific storage behavior. For example, force `indexedValueStorage()` if a partitioned `Boolean` should be serialized as an indexed value rather than stored as membership.

## Nullable Values

Nullable results are allowed:

```kotlin
val optionalSongCache = cacheKey(
    "optional-song",
    returns<Song?>(),
    key = exact(songId),
)
```

Nullable key parts are positional values, not omitted values:

```kotlin
val filter = keyPart<ArtistFilter?>("filter")
val sort = keyPart<ArtistSort?>("sort")

val artistsCache = cacheKey(
    "artists",
    returns<List<Artist>>(),
    key = exact(filter + sort + page),
)
```

The default naming strategy renders null key parts as `<null>`. Customize that with:

```kotlin
val cache = Kacheable(
    store = store,
    namingStrategy = defaultCacheNamingStrategy(nullKeyPart = "__NULL__"),
)
```

## Raw Escape Hatch

The raw API remains available for low-level or migration cases:

```kotlin
cache("user", userId) {
    repository.user(userId)
}

cache.invalidate(rawCacheEntry("user", userId))
cache.invalidate(rawCache("legacy-family"))
```

Prefer typed cache refs for new code because they preserve the cache result type and storage plan through invalidation.

## More

See [docs/cache-key.md](docs/cache-key.md) for the full cache-key guide, including blocking APIs, custom naming strategies, matchable invalidation, and storage planning details.
