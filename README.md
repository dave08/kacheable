[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!CAUTION]
> Kacheable is still experimental. The typed key API is intentionally evolving toward a smaller, clearer surface.

> [!NOTE]
> Cached values currently use Kotlinx Serialization JSON by default, so stored value types should be `@Serializable` unless you provide a custom codec.

# Kacheable

Kacheable is a Kotlin caching library for wrapping computations behind a small API.

## Why use it

Kacheable lets you start with exact caching:

```kotlin
suspend fun loadUser(userId: Int): User =
    cache("user-cache", userId) {
        fetchUserFromDatabase(userId)
    }
```

Then move to structured storage when the cache should express a real relationship instead of flattening everything into one string key:

```kotlin
// Delegated key parts are named from the property: "artistId".
val artistId by keyPart<Int>()

// This part is anonymous. It is fine for exact reads/writes, but not for
// named partial invalidation selectors.
val page = keyPart<Page>(Page::offset, Page::limit)

// Explicit names are useful when there is no delegated property.
val locale = keyPart<String>("locale")

val songPageCache = entryKey(
    "song-page-cache",
    // `*` means primary * secondary:
    // one artistId bucket containing many page + locale entries.
    artistId * (page + locale),
    storedAs = CacheStorage.HashMap,
)

suspend fun loadSongPage(artistIdValue: Int, page: Page, locale: String): SongPage =
    cache(songPageCache.key(artistIdValue, page, locale), returnsAs = value<SongPage>()) {
        fetchSongPage(artistIdValue, page, locale)
    }

suspend fun invalidateArtistPages(artistIdValue: Int) {
    // Partial invalidation selectors use named key parts.
    cache.invalidate(songPageCache.keyPart(artistId(artistIdValue)))
}

suspend fun invalidateEnglishPages(artistIdValue: Int) {
    // This targets all secondary fields under artistIdValue where locale == "en".
    cache.invalidate(songPageCache.keyPart(artistId(artistIdValue), locale("en")))
}
```

And use set-backed relationships when the cache is really answering a membership question:

```kotlin
val accountId = keyPart<Int>("accountId")

val artistFollowCache = entryKey(
    "artist-follow-cache",
    // For set storage, the primary side names the set and the secondary side
    // is the member being checked/classified.
    artistId * accountId,
    storedAs = CacheStorage.Set,
)

suspend fun isFollowing(artistId: Int, accountId: Int): Boolean =
    cache(artistFollowCache.key(artistId, accountId), returnsAs = isMember()) {
        repository.isFollowing(artistId, accountId)
    }
```

So the library’s main value is not just “cache this result”, but “make the cache structure explicit enough that reads, invalidation, and storage shape stay aligned”.

Its goals are:
- make exact cache reads/writes easy
- support grouped storage layouts like hash fields and membership sets
- keep cache structure explicit in code
- let blocking and suspending apps use the same core ideas

It is **not** trying to be:
- a distributed lock or single-flight system
- a write-behind data sync layer
- a general Redis abstraction
- a query planner for arbitrary wildcard invalidation
- a replacement for your source of truth

## Features

- Raw cache API for simple exact keys
- Typed `entryKey(...)` API for structured cache definitions
- Experimental logical `cacheKey(...)` prototype for result-first cache definitions
- Hash-backed grouped caches
- Set-backed membership and classified membership caches
- Exact invalidation, grouped invalidation, and partial layered hash invalidation
- Blocking and suspending interfaces
- In-memory, Redis/Lettuce, and no-op stores
- Per-cache expiry configuration
- Custom cache naming strategies

## Experimental logical cache keys

The `cacheKey` prototype lets you define the logical cached result first and leave storage as an optimization plan:

```kotlin
val songCache = cacheKey(
    "song",
    returns<Song>(),
    key = exact(songId),
)

val artistSongCache = cacheKey(
    "artist-song",
    returns<Song>(),
    key = partitioned(
        partition = artistId,
        key = songId,
    ),
)
```

Calls do not need `returnsAs`:

```kotlin
cache(songCache(songIdValue)) { loadSong(songIdValue) }
cache(artistSongCache(artistIdValue, songIdValue)) { loadArtistSong() }
```

See [docs/logical-cache-key-prototype.md](docs/logical-cache-key-prototype.md) for the prototype terminology, storage inference rules, collection examples, and power-user storage overrides.

## Storage layouts in practice

`storedAs = ...` is there so the cache definition can describe how entries relate to each other.

### Flat string-style storage

Good for typed exact cache entries that should be stored as one flat key:

```kotlin
val userCache = entryKey<Int>("user-cache", storedAs = CacheStorage.String)

suspend fun loadUser(userId: Int): User =
    cache(userCache.key(userId), returnsAs = value<User>()) {
        loadUserFromDatabase(userId)
    }
```

This uses `CacheStorage.String`: all key parts are flattened into the final cache key, such as `user-cache:42`. It is the simplest typed storage shape when invalidation is mostly exact:

```kotlin
suspend fun invalidateUser(userId: Int) {
    cache.invalidate(userCache.keyPart(userId))
}
```

The raw `cache("user-cache", userId) { ... }` form remains available as the low-level escape hatch when you do not need a typed `entryKey`.

### Layered hash storage

Good when many entries share one primary bucket:

```kotlin
val songPageCache = entryKey(
    "song-page-cache",
    artistId * (page + locale),
    storedAs = CacheStorage.HashMap,
)
```

Practical advantages:
- avoid duplicating the primary part into every Redis key
- invalidate one whole group by primary key
- invalidate selected secondary slices without dropping the whole group
- keep related entries together

### Set storage

Good when the cache is fundamentally about presence or classification:

```kotlin
val artistFollowCache = entryKey(
    "artist-follow-cache",
    artistId * accountId,
    storedAs = CacheStorage.Set,
)
```

This fits:
- follow / liked / saved relationships
- boolean membership checks
- enum-like classified membership such as `LIKE`, `DISLIKE`, `NONE`

## Installation

Snapshots are published through JitPack.

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.dave08:kacheable:<version>")
    implementation("com.github.dave08:kacheable-core:<version>")
    implementation("com.github.dave08:kacheable-lettuce:<version>")
}
```

## Raw exact-key usage

```kotlin
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.redis.RedisKacheableStore
import io.lettuce.core.RedisClient

val client = RedisClient.create("redis://localhost:6379/0")
val connection = client.connect()
val cache = Kacheable(RedisKacheableStore(connection))

suspend fun loadUser(userId: Int): User =
    cache("user-cache", userId) {
        fetchUserFromDatabase(userId)
    }
```

This raw API is the low-friction escape hatch. If exact string-style keys are enough, you can stop there.

## Cache configuration

```kotlin
val configs = listOf(
    CacheConfig("user-cache", expiryType = ExpiryType.after_write, expiry = 30.minutes),
).associateBy(CacheConfig::name)

val cache = Kacheable(RedisKacheableStore(connection), configs)
```

```kotlin
enum class ExpiryType {
    none, after_write, after_access
}

data class CacheConfig(
    val name: String,
    val expiryType: ExpiryType = ExpiryType.none,
    val expiry: Duration = Duration.INFINITE,
    val nullPlaceholder: String? = null,
)
```

If `nullPlaceholder` is `null`, null results are not stored. If it is set, that placeholder is stored and later decoded back to `null`.

## Typed cache definitions

The typed API separates:
- user-facing key structure
- storage layout
- cached return shape

### Key parts

`keyPart()` defines one reusable part of a cache key.

```kotlin
val artistId by keyPart<Int>()                   // name inferred as "artistId"
val songId by keyPart<Int>()                     // name inferred as "songId"
val locale = keyPart<String>("locale")           // explicit name
val page = keyPart<Page>(Page::offset, Page::limit) // anonymous multi-segment part
```

You can:
- let delegated properties infer a part name
- provide an explicit name with `keyPart("locale")`
- map one argument into multiple encoded segments with extractors

### entryKey

`entryKey(...)` defines the cache structure and storage layout.

```kotlin
val songCache = entryKey("song-cache", songId, storedAs = CacheStorage.HashMap)
```

Use `+` for parts on the same level. These parts are encoded together into one key level:

```kotlin
// `+` keeps both parts on the same key level.
val artistLocale = artistId + locale
val artistLocaleCache = entryKey("artist-locale-cache", artistLocale, storedAs = CacheStorage.HashMap)
```

Use `*` to split a cache into primary and secondary levels:

```kotlin
val songPageCache = entryKey(
    "song-page-cache",
    // `artistId` is the primary bucket.
    // `page + locale` is the secondary hash field shape.
    artistId * (page + locale),
    storedAs = CacheStorage.HashMap,
)
```

Read `artistId * (page + locale)` as: store many `page + locale` entries under one `artistId` bucket. With `CacheStorage.HashMap`, `artistId` becomes the hash key and `page + locale` becomes the hash field. This is what makes whole-bucket invalidation and selected secondary invalidation possible.

## API cheat sheet

```kotlin
val songId by keyPart<Int>()                      // named from property: "songId"
val locale = keyPart<String>("locale")            // explicitly named
val page = keyPart<Page>(Page::offset, Page::limit) // anonymous, encodes two segments

val exact = entryKey("song-cache", songId, storedAs = CacheStorage.String)

// `*` splits primary and secondary storage levels.
// `+` combines page and locale at the secondary level.
val grouped = entryKey("song-page-cache", songId * (page + locale), storedAs = CacheStorage.HashMap)

// In set storage, the secondary side is the set member.
val membership = entryKey("song-like-cache", songId * keyPart<Int>("accountId"), storedAs = CacheStorage.Set)

cache(exact.key(7), returnsAs = value<Song>()) { loadSong(7) }
cache(grouped.key(7, Page(0, 25), "en"), returnsAs = value<List<Song>>()) { loadPage() }
cache(membership.key(7, 42), returnsAs = isMember()) { isLiked(songId = 7, accountId = 42) }

cache.invalidate(exact.keyPart(7))

// Named selectors support precise partial invalidation.
cache.invalidate(grouped.keyPart(songId(7)))
cache.invalidate(grouped.keyPart(songId(7), locale("en")))
```

## Working with typed caches

### Exact entries

```kotlin
suspend fun loadSongPage(artistIdValue: Int, page: Page, locale: String): SongPage =
    cache(songPageCache.key(artistIdValue, page, locale), returnsAs = value<SongPage>()) {
        fetchSongPage(artistIdValue, page, locale)
    }
```

### Group invalidation

```kotlin
suspend fun invalidateArtistPages(artistIdValue: Int) {
    // Select only the named primary part to invalidate the whole hash bucket.
    cache.invalidate(songPageCache.keyPart(artistId(artistIdValue)))
}
```

### Partial invalidation

For layered hash storage, you can invalidate a subset of secondary entries by selecting named key parts:

```kotlin
suspend fun invalidateEnglishPages(artistIdValue: Int) {
    // Select the named primary part plus one named secondary part.
    // Omitted secondary parts are wildcards within the known hash bucket.
    cache.invalidate(songPageCache.keyPart(artistId(artistIdValue), locale("en")))
}
```

This removes entries matching that secondary part while keeping others under the same primary bucket. If you want to invalidate the entire primary bucket, select only the primary key part:

```kotlin
suspend fun invalidateArtistPages(artistIdValue: Int) {
    cache.invalidate(songPageCache.keyPart(artistId(artistIdValue)))
}
```

There is also a shorthand for the common one-primary hash case:

```kotlin
suspend fun invalidateEnglishPages(artistIdValue: Int) {
    // Shorthand for one-primary hash caches. Secondary selectors still need names.
    cache.invalidate(songPageCache.keyPart(artistIdValue, locale("en")))
}

suspend fun invalidateArtistPages(artistIdValue: Int) {
    // Shorthand for selecting the concrete primary bucket.
    cache.invalidate(songPageCache.keyPart(artistIdValue))
}
```

Current scope:
- supported for layered hash storage
- primary key parts must be selected concretely; primary wildcard scans are not part of the typed DSL
- selected parts must be named with delegated `val part by keyPart<T>()` or explicit `keyPart<T>("part")`
- intended for precise invalidation of grouped entries

Redis note: partial hash invalidation scans fields inside the known hash key and deletes matching fields. That keeps the operation scoped to one primary bucket, but very large hashes can still make partial invalidation more expensive than exact field deletes or whole-bucket invalidation.

Not currently intended as a generic wildcard language for every storage type.

## Set membership views

Set-backed caches can answer membership-style questions without storing full JSON values.

```kotlin
val artistFollowCache = entryKey(
    "artist-follow-cache",
    artistId * keyPart<Int>("accountId"),
    storedAs = CacheStorage.Set,
)

suspend fun isFollowing(artistId: Int, accountId: Int): Boolean =
    cache(artistFollowCache.key(artistId, accountId), returnsAs = isMember()) {
        repository.isFollowing(artistId, accountId)
    }
```

Classified membership works the same way for enum-like results:

```kotlin
enum class Reaction { LIKE, DISLIKE, NONE }

val reactionCache = entryKey(
    "song-reaction-cache",
    songId * keyPart<Int>("accountId"),
    storedAs = CacheStorage.Set,
)

suspend fun reaction(songId: Int, accountId: Int): Reaction =
    cache(reactionCache.key(songId, accountId), returnsAs = enumMember<Reaction>()) {
        repository.reaction(songId, accountId)
    }
```

## Return views

`returnsAs = ...` tells Kacheable how the selected storage entry should be interpreted. Choose it based on the storage layout and the kind of answer you want back.

For raw exact string-style calls, you do not pass `returnsAs`; the serializer is inferred from the function result:

```kotlin
cache("user-cache", userId) { loadUser(userId) }
```

For hash-backed value entries, use `value<T>()`. This stores one encoded value at the selected typed entry. In a layered hash cache, that means one hash field:

```kotlin
cache(songPageCache.key(artistIdValue, page, "en"), returnsAs = value<SongPage>()) {
    loadSongPage(artistIdValue, page, "en")
}
```

For a whole hash bucket, use `map<K, V>()`. This treats the primary key as a map-like view over the stored hash fields:

```kotlin
cache(artistSongsCache.key(artistIdValue), returnsAs = map<Int, Song>()) {
    loadSongsById(artistIdValue)
}
```

For set-backed boolean membership, use `isMember()`. This stores positive members in one set and, by default, false results in an internal non-member set:

```kotlin
cache(artistFollowCache.key(artistIdValue, accountIdValue), returnsAs = isMember()) {
    repository.isFollowing(artistIdValue, accountIdValue)
}
```

For set-backed classified membership, use `enumMember<E>()`. This stores the member in one set per enum value, such as `LIKE`, `DISLIKE`, or `NONE`:

```kotlin
cache(reactionCache.key(songIdValue, accountIdValue), returnsAs = enumMember<Reaction>()) {
    repository.reaction(songIdValue, accountIdValue)
}
```

In short:
- `value<T>()` is for one stored value in typed `String` or `HashMap` caches
- raw exact calls infer the result serializer directly
- `map<K, V>()` is for reading/writing a whole hash bucket
- `isMember()` is for boolean set membership
- `enumMember<E>()` is for enum-like set classification

## Custom naming strategies

You can replace the default naming strategy when cache keys need a different format.

```kotlin
val namingStrategy = defaultCacheNamingStrategy(
    secondaryEntryCombiner = { params -> params.joinToString("|") },
)

val cache = Kacheable(
    store = RedisKacheableStore(connection),
    namingStrategy = namingStrategy,
)
```

The naming strategy resolves:
- flat entries for string-like storage
- layered entries for hash/set-style storage

## Blocking usage

The blocking API mirrors the suspending one:

```kotlin
val blockingCache = BlockingKacheable(RedisBlockingKacheableStore(connection))

fun loadUser(userId: Int): User =
    blockingCache("user-cache", userId) {
        fetchUser(userId)
    }
```

Typed `entryKey(...)`, `.key(...)`, `.keyPart(...)`, and `returnsAs = ...` work the same way.

## Purpose of the low-level model

Kacheable intentionally keeps a boundary between:
- the public typed DSL (`keyPart`, `entryKey`, `.key`, `.keyPart`)
- the low-level resolved cache arguments used internally by naming, invalidation, and stores

That boundary exists so the public API can stay expressive without forcing storage engines to understand the whole typed DSL.

## Current non-goals

Kacheable does not currently try to provide:
- per-member TTLs inside Redis sets
- transactional read/compute/write deduplication across nodes
- typed partial invalidation for every storage layout
- a schema migration framework for changing live cache layouts

Those may be built around Kacheable, but they are not its primary responsibility.
