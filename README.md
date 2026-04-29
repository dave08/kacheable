[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!CAUTION]
> Kacheable is still experimental. The typed key API is intentionally evolving toward a smaller, clearer surface.

> [!NOTE]
> Cached values currently use Kotlinx Serialization JSON by default, so stored value types should be `@Serializable` unless you provide a custom codec.

# Kacheable

Kacheable is a Kotlin caching library for wrapping computations behind a small API.

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
- Hash-backed grouped caches
- Set-backed membership and classified membership caches
- Exact invalidation, grouped invalidation, and partial layered hash invalidation
- Blocking and suspending interfaces
- In-memory, Redis/Lettuce, and no-op stores
- Per-cache expiry configuration
- Custom cache naming strategies

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

## Basic usage

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
val artistId by keyPart<Int>()
val songId by keyPart<Int>()
val locale = keyPart<String>("locale")
val page = keyPart<Page>(Page::offset, Page::limit)
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

Use `+` for parts on the same level:

```kotlin
val artistLocale = artistId + locale
val artistLocaleCache = entryKey("artist-locale-cache", artistLocale, storedAs = CacheStorage.HashMap)
```

Use `*` to create a layered key with primary and secondary parts:

```kotlin
val songPageCache = entryKey(
    "song-page-cache",
    artistId * (page + locale),
    storedAs = CacheStorage.HashMap,
)
```

## Working with typed caches

### Exact entries

```kotlin
suspend fun loadSongPage(artistId: Int, page: Page, locale: String): SongPage =
    cache(songPageCache.key(artistId, page, locale), returnsAs = value<SongPage>()) {
        fetchSongPage(artistId, page, locale)
    }
```

### Group invalidation

```kotlin
suspend fun invalidateArtistPages(artistId: Int) {
    cache.invalidate(songPageCache.keyPart(artistId))
}
```

### Partial invalidation

For layered hash storage, you can invalidate a subset of secondary entries by selecting named or reusable secondary parts:

```kotlin
suspend fun invalidateEnglishPages(artistId: Int) {
    cache.invalidate(songPageCache.keyPart(artistId, locale("en")))
}
```

This removes entries matching that secondary part while keeping others under the same primary bucket.

Current scope:
- supported for layered hash storage
- intended for precise invalidation of grouped entries

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

`returnsAs = ...` tells Kacheable how the selected storage entry should be interpreted.

Examples:

```kotlin
value<Song>()
value<List<Song>>()
map<Int, Song>()
isMember()
enumMember<Reaction>()
```

The return view is chosen at the call site instead of being baked into the key definition.

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
