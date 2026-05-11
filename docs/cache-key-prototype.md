# Cache Key Prototype

This prototype explores a different public model for typed caches:

> One cache key describes one cached result. Storage is an optimization plan.

The existing `entryKey(...)` / `returnsAs = ...` API still works. The cache-key API sits next to it while we test whether this terminology is clearer.

## Defining A Cache

Use `cacheKey("name", returns<Result>(), key = ...)` to bind the cache name, result type, and key shape in one definition.

```kotlin
val songId = keyPart<Int>("songId")

val songCache = cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId),
)
```

Read this as:

> Cache one `Song` for each exact `songId`.

Usage does not need `returnsAs`:

```kotlin
cache(songCache(songIdValue)) {
    repository.song(songIdValue)
}

cache.invalidate(songCache(songIdValue))
```

The `returns<Result>()` token fixes the cache result type without forcing users to spell out key-part type parameters. `exact(...)` and `partitioned(...)` then infer key-part types normally, which keeps invocation type-safe.

For one named value with no parameters, use `exact()`:

```kotlin
val appSettingsCache = cacheKey(
    "app-settings",
    returns<AppSettings>(),
    key = exact(),
)

cache(appSettingsCache()) {
    repository.appSettings()
}

cache.invalidate(appSettingsCache.all())
```

## Nullable Key Parts

Nullable repository parameters can be modeled directly when `null` is part of the call identity.

```kotlin
val filter = keyPart<ArtistFilter?>("filter")
val sort = keyPart<ArtistSort?>("sort")
val page = keyPart<Page>(Page::offset, Page::limit)

val artistsCache = cacheKey(
    "artists",
    returns<List<Artist>>(),
    key = exact(filter + sort + page),
)

cache(artistsCache(filterValue, sortValue, pageValue)) {
    repository.artists(filterValue, sortValue, pageValue)
}
```

Null key-part values are positional values, not omitted parameters. The default naming strategy renders them as `<null>`.

```kotlin
val cache = Kacheable(
    store = store,
    namingStrategy = defaultCacheNamingStrategy(
        nullKeyPart = "__NULL_KEY__",
    ),
)
```

## Collections Are Values

The result type is what one cache lookup returns. A `List`, `Set`, or `Map` is still one cached value unless you opt into partitioning.

```kotlin
val artistSongsCache = cacheKey(
    "artist-songs",
    returns<List<Song>>(),
    key = exact(artistId),
)
```

Read this as:

> Cache one `List<Song>` for each `artistId`.

It is not treated as many song entries.

## Partitioned Values

Use `partitioned(...)` when related values should be stored and invalidated together.

```kotlin
val artistSongCache = cacheKey(
    "artist-song",
    returns<Song>(),
    key = partitioned(
        partition = artistId,
        key = songId,
    ),
)
```

Read this as:

> Cache one `Song` for each `songId` key inside one `artistId` partition.

Usage still asks for one cache result:

```kotlin
cache(artistSongCache(artistIdValue, songIdValue)) {
    repository.artistSong(artistIdValue, songIdValue)
}
```

Invalidation can target one value or the whole partition:

```kotlin
cache.invalidate(artistSongCache(artistIdValue, songIdValue))
cache.invalidate(artistSongCache.partition(artistIdValue))
```

Under `storage = auto()`, partitioned non-Boolean and non-enum results use indexed value storage, currently backed by hash-map style storage.

## Single-Partition Values

Use `partitioned(key = ...)` when related entries belong to one cache family but there is no natural outer partition value.

This is a good fit for top-level paginated queries:

```kotlin
val page = keyPart<Page>("page", Page::offset, Page::limit)

val newVideosCache = cacheKey(
    "new-videos",
    returns<List<VideoId>>(),
    key = partitioned(key = page),
)
```

Read this as:

> Cache one `List<VideoId>` for each `page` entry inside the `new-videos` cache family.

Usage still targets one logical result:

```kotlin
cache(newVideosCache(Page(0, 20))) {
    repository.newVideos(Page(0, 20))
}
```

Invalidation can target one entry or the whole cache family:

```kotlin
cache.invalidate(newVideosCache(Page(0, 20)))
cache.invalidate(newVideosCache.partition())
```

This stores pages as hash entries under the cache name instead of flattening each page into a separate exact key. That makes “clear all pages” a direct typed invalidation instead of a key-prefix scan.

Single-partition keys can also use matchable entry parts:

```kotlin
val locale = matchableKeyPart<String>("locale")

val homePagesCache = cacheKey(
    "home-pages",
    returns<HomePage>(),
    key = partitioned(key = page + locale),
)

cache.invalidate(homePagesCache.matching(locale("he")))
```

Use this only when matching inside one cache family is the intended invalidation scope. It is still key matching, not value search.

## Whole-Cache Invalidation

Use `.all()` when a cache result depends on inputs that are not fully represented by the cache key, or when a broad domain change makes every cached result in that cache definition suspect.

```kotlin
val newestAlbumsCache = cacheKey(
    "newest-albums",
    returns<List<AlbumId>>(),
    key = partitioned(
        partition = albumType,
        key = page,
    ),
)

cache.invalidate(newestAlbumsCache.all())
```

Read this as:

> Delete every cached `newest-albums` result, across every key.

`.all()` is intentionally explicit because it can be expensive for storage backends that implement it with pattern scanning. Prefer exact refs, `partition(...)`, or `matching(...)` when the changed domain value maps to a narrower cache target.

## Matchable Key Parts

Sometimes a part of the inner key should be usable for scoped invalidation inside a concrete partition. Define that part with `matchableKeyPart(...)`.

```kotlin
val locale = matchableKeyPart<String>("locale")

val artistPageCache = cacheKey(
    "artist-pages",
    returns<SongPage>(),
    key = partitioned(
        partition = artistId,
        key = page + locale,
    ),
)
```

Read this as:

> Cache one `SongPage` for each `page + locale` key inside one `artistId` partition. `locale` may be used for scoped invalidation inside one artist partition.

```kotlin
cache.invalidate(artistPageCache.matching(artistIdValue, locale("en")))
```

`matchableKeyPart(...)` is not value search and not keyspace-wide wildcard search. It marks key parts whose values may be passed to `matching(...)` during invalidation, and matching still requires a concrete partition value.

Multiple inner-key parts can be matchable:

```kotlin
val pageCache = cacheKey(
    "artist-pages",
    returns<SongPage>(),
    key = partitioned(
        partition = artistId + collection,
        key = page + locale + device,
    ),
)

cache.invalidate(pageCache.matching(artistIdValue, "top", locale("en")))
cache.invalidate(pageCache.matching(artistIdValue, "top", locale("en"), device("mobile")))
```

Because matching behavior needs hash-style field matching, matchable inner-key parts force indexed value storage under `auto()`, even for Boolean or enum results.

## Legacy Raw Invalidation

Prefer typed cache refs for new code. During migrations, raw refs can be mixed with typed refs in one invalidation call:

```kotlin
cache.invalidate(
    rawCacheEntry("old-song-cache", songId),
    artistSongCache.partition(artistId),
)
```

Use `rawCacheEntry(...)` for one known flat legacy key. Use `rawCache(...)` only when intentionally deleting all flat keys that belong to a legacy cache family. No-argument typed cache keys should normally be invalidated with the entry ref itself:

```kotlin
cache.invalidate(appSettingsCache())
```

## Membership Optimization

Boolean partitioned caches default to membership storage when the inner key has no matchable parts.

```kotlin
val followCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),
    key = partitioned(
        partition = artistId + locale,
        key = accountId,
    ),
)
```

Read this as:

> Cache one `Boolean` follow state for each `accountId` key inside one `artistId + locale` partition.

Kacheable can store that more efficiently than a separate serialized Boolean per account.

Power users can control false caching:

```kotlin
val followCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),
    key = partitioned(
        partition = artistId,
        key = accountId,
    ),
    storage = membershipStorage(cacheFalse = false),
)
```

## Enum Membership Optimization

Enum partitioned caches default to classified membership storage when the inner key has no matchable parts.

```kotlin
enum class Reaction { LIKE, DISLIKE, NONE }

val reactionCache = cacheKey(
    "song-reaction",
    returns<Reaction>(),
    key = partitioned(
        partition = songId,
        key = accountId,
    ),
)
```

`returns<Reaction>()` is enough for correctness and auto-planning. If you want to make the enum classification explicit and avoid enum discovery from the result type, use `returnsEnum<Reaction>()`:

```kotlin
val reactionCache = cacheKey(
    "song-reaction",
    returnsEnum<Reaction>(),
    key = partitioned(
        partition = songId,
        key = accountId,
    ),
)
```

Read this as:

> Cache one `Reaction` for each `accountId` key inside one `songId` partition.

The public model is still a `Reaction` lookup. The classified set layout is an optimization.

Power users can choose the enum universe or storage names:

```kotlin
val reactionCache = cacheKey(
    "song-reaction",
    returns<Reaction>(),
    key = partitioned(
        partition = songId,
        key = accountId,
    ),
    storage = enumMembershipStorage(
        values = listOf(Reaction.LIKE, Reaction.DISLIKE),
        valueName = { it.name.lowercase() },
    ),
)
```

## Power User Storage Overrides

`storage = auto()` is the default. Explicit plans are available where the type and key shape make sense:

```kotlin
cacheKey("song", returns<Song>(), key = exact(songId), storage = exactValueStorage())
cacheKey("follow", returns<Boolean>(), key = partitioned(artistId, accountId), storage = indexedValueStorage())
cacheKey("follow", returns<Boolean>(), key = partitioned(artistId, accountId), storage = membershipStorage(cacheFalse = true))
cacheKey("reaction", returns<Reaction>(), key = partitioned(songId, accountId), storage = enumMembershipStorage())
```

Use overrides when the automatic plan is not what you want. For example, this stores serialized booleans as indexed values instead of membership sets:

```kotlin
val followCache = cacheKey(
    "artist-follow",
    returns<Boolean>(),
    key = partitioned(
        partition = artistId,
        key = accountId,
    ),
    storage = indexedValueStorage(),
)
```

## Hidden Dependencies

A cache key should normally describe the inputs that decide one cached result. Some results also depend on hidden inputs: database views, ranking formulas, visibility rules, background counters, or other data read inside a query but not present at the call site.

When a hidden input changes, broad invalidation can be the correct and honest choice:

```kotlin
cache.invalidate(homePageCache.all())
```

Longer term, Kacheable could grow dependency-aware caches so callers can say that one source change invalidates a set of derived caches. That should stay separate from the basic cache-key model unless it can be expressed without making ordinary exact and partitioned caches harder to understand.

## Future: Cache Families

Some key-only caches may naturally belong to one domain partition even when each lookup returns a different value. For example, several small ad authorization values might all belong to one `adId`, or several setting lookups might belong to one setting scope.

Do not group caches just because they share an id. Grouping is only useful when the caller can honestly say: "these are several named facts about the same thing, and they should be warmed, retrieved, or invalidated together."

A possible future model is a typed cache family: multiple cache keys could share an invalidation partition without forcing unrelated result types into one awkward union value. This would keep the retrieval API type-safe while giving callers a single partition target when that is the real domain boundary.

## Prototype Limits

- Exact cache keys cover no-argument values plus arity 1 through 6.
- Partitioned cache keys cover the shapes exercised by the prototype tests, including single-partition caches, one-part and multi-part partitions, and matchable inner-key parts.
- `matching(...)` accepts only `MatchableKeyPartValue`, so non-matchable key parts cannot be passed to it by accident. A compile-fail harness can make that guarantee explicit in tests later if this terminology survives.
- The old typed API remains available while this model is evaluated.
