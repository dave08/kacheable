# Logical Cache Key Prototype

This prototype explores a different public model for typed caches:

> One cache key describes one logical cached result. Storage is an optimization plan.

The existing `entryKey(...)` / `returnsAs = ...` API still works. The logical API sits next to it while we test whether this terminology is clearer.

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

The `returns<Result>()` token fixes the logical result type without forcing users to spell out key-part type parameters. `exact(...)` and `partitioned(...)` then infer key-part types normally, which keeps invocation type-safe.

## Nullable Key Parts

Nullable repository parameters can be modeled directly when `null` is part of the logical call identity.

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

Usage still asks for one logical result:

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

Read this as:

> Cache one `Reaction` for each `accountId` key inside one `songId` partition.

The public model is still a logical `Reaction` lookup. The classified set layout is an optimization.

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

## Prototype Limits

- Exact logical keys cover arity 1 through 6.
- Partitioned logical keys cover the shapes exercised by the prototype tests, including one-part and multi-part partitions plus matchable inner-key parts.
- `matching(...)` accepts only `MatchableKeyPartValue`, so non-matchable key parts cannot be passed to it by accident. A compile-fail harness can make that guarantee explicit in tests later if this terminology survives.
- The old typed API remains available while this model is evaluated.
