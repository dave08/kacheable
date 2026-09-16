# Partition loading

This guide describes the upcoming release. See the [changelog](../CHANGELOG.md#unreleased)
for upgrade requirements; the published version in the README installation examples does not
include these APIs yet.

Use a partition policy when a cache loader needs published sibling entries or must reject a
result computed against an expired or invalidated partition. The feature uses the existing
hash store. Leave `CacheConfig.partition` unset for ordinary caching.

## Load dependent chunks

Define the key and configure how missing entries are loaded:

```kotlin
import com.github.dave08.kacheable.*

val deliveryChunks = cacheKey(
    "delivery-chunks",
    returns<Chunk>(),
    key = partitioned(
        partition = keyPart<String>("delivery"),
        key = keyPart<Int>("page"),
    ),
)
val cache = Kacheable(
    store = redisStore,
    configs = mapOf("delivery-chunks" to CacheConfig(
        name = "delivery-chunks",
        partition = CachePartitionPolicy.SequentialFrom(first = 0),
    )),
)

val chunk = cache(deliveryChunks("D55", page)) { missingPage, partition ->
    val previous = partition.entries(0 until missingPage)
    repository.loadChunk(missingPage, previous)
}
```

`Chunk` is an application value with a kotlinx.serialization serializer; `redisStore` is the
application's `RedisKacheableStore`. The call returns the requested chunk. If page 0 exists and
page 2 is requested, Kacheable loads and publishes page 1, then loads and publishes page 2.
The second loader can read the newly published page 1. A hit returns the existing requested value.

Sequential loading uses integer keys, partition coordination, and `IfAbsent` publication.
These choices are fixed by the policy type. The requested key must be at least `first`.
Each missing prerequisite must be published: returning an uncached null or rejecting a
prerequisite with `cacheIf` fails the request before later chunks are loaded.

## Load independent chunks

Use `OnDemand` when only the requested entry needs to be loaded:

```kotlin
CacheConfig(
    name = "image-chunks",
    partition = CachePartitionPolicy.OnDemand(
        publication = CachePublication.IfAbsent,
        coordination = CacheCoordination.Entry,
    ),
)
```

The loader may ignore sibling access:

```kotlin
val chunk = cache(imageChunks(imageId, chunkId)) { missingId, _ ->
    repository.loadChunk(missingId)
}
```

`OnDemand` also accepts the existing zero-argument lambda. `SequentialFrom` requires the
parameterized loader because it may request keys other than the one at the call site.

| Setting | Behavior |
| --- | --- |
| `Replace` | A successful publication can replace an entry written by a competing loader. |
| `IfAbsent` | A competing publication keeps the stored winner and returns that value. |
| `Entry` | Different entries may load concurrently. Reading siblings makes publication depend on the version that was read. |
| `Partition` | Loaders extending the same partition are serialized within a cache runtime. |

`OnDemand` defaults to `Replace` and `Entry`. Both policies default to three conflict attempts.
For suspending callers sharing Redis across processes, configure
`CacheResilienceConfig(singleFlight = SingleFlightMode.Redis)` to coordinate loads across
processes. Local partition coordination alone is not a distributed lock. Atomic publication
checks still protect the data if a lock lease expires or another writer bypasses coordination.

## Read only what the loader needs

A context reads published entries without invoking another loader:

| Operation | Result | Key declaration |
| --- | --- | --- |
| `partition.entry(key)` | `Present(value)` or `Missing`; `Present(null)` is distinct from absence | Any `KeyPart<K>` |
| `partition.entries(keys)` | Map of the requested entries that exist | Any `KeyPart<K>` |
| `partition.keys()` | All published logical keys, without fetching value payloads | `EnumerableKeyPart<K>` |
| `partition.entries()` | Map of all published keys and values | `EnumerableKeyPart<K>` |

All reads are lazy. `entries()` explicitly materializes the whole partition; use selected reads
for large chunks. Contexts are valid only during their loader attempt. Retaining one and using
it after the lambda completes fails at runtime.

### Typed enumeration and mapped keys

Declare an enumerable inner key to enable the no-argument `keys()` and `entries()` extensions:

```kotlin
val page = enumerableKeyPart<Int>("page")
val chunks = cacheKey(
    "chunks", returns<Chunk>(),
    key = partitioned(keyPart<String>("delivery"), page),
)

cache(chunks("D55", 2)) { missingPage, partition ->
    val publishedPages: Set<Int> = partition.keys()
    repository.loadChunk(missingPage, publishedPages)
}
```

Configure `chunks` with a partition policy as in the earlier examples. Import extensions from
`com.github.dave08.kacheable`, or `com.github.dave08.kacheable.blocking` for blocking calls.

The reified builder obtains the standard serializer for `K`. Custom or generic code can pass
`enumerableKeyPart(name = "page", codec = pageCodec)`. Both keys and values use `CacheCodec<T>`.

A codec preserves the whole logical key as separate metadata. It does not reverse the physical
key's extractors. For example:

```kotlin
@kotlinx.serialization.Serializable
data class Window(val offset: Int, val limit: Int)

val window = enumerableKeyPart<Window>("window", Window::offset, Window::limit)
```

The extractors determine the cache address; the serializer retains `Window` for enumeration.
All properties needed to distinguish logical keys must also participate in their physical
identity. Two different objects that map to the same address still refer to one cache entry.

Forward-only `keyPart<Request>("page", Request::page)` supports known-key reads using a
`Request`. It does not gain an inverse mapping. If enumeration should return `Int`, declare
an enumerable `Int` key and invoke it with `request.page`.

## Expiry, retries, and invalidation

A partition has one generation (its lifetime identity) and an advancing write revision.
Kacheable checks these atomically when publishing. Expiry, invalidation, or a conflicting
write causes the attempt to restart with a new context, up to `maxLoadAttempts`. Exhausting
that limit fails the request. Cancellation is propagated without retry.

Loaders must tolerate repeated execution. Read dependencies through the supplied context on
each attempt; a read from an unrelated connection cannot establish that dependency.

Optional `after_write` expiry applies to the entire partition. Opening a new empty partition
starts its TTL; successful publication resets it. Reads, opening an existing partition, and
losing `IfAbsent` publications do not renew it. A configured write expiry must be at least
one millisecond; infinite expiry means no expiration. `after_access` is unsupported.

Invalidate a whole partition or cache family:

```kotlin
cache.invalidate(deliveryChunks.partition("D55"))
cache.invalidate(deliveryChunks.all())
```

Per-entry and matching invalidation are unsupported for guarded caches. They could remove
prerequisites while leaving dependent chunks behind.

## Configuration files and validation

`CachePartitionPolicy` is a sealed hierarchy with data-class variants. With Hoplite, configure
an explicit discriminator using `withExplicitSealedTypes("type")` (an experimental API that
may require opt-in in your Hoplite version), then use a configuration such as:

```yaml
name: delivery-chunks
partition:
  type: SequentialFrom
  first: 0
  maxLoadAttempts: 3
```

The explicit discriminator prevents ambiguity between variants with defaulted constructor
parameters. This follows Hoplite's [configuration builder API](https://github.com/sksamuel/hoplite/blob/master/hoplite-core/src/main/kotlin/com/sksamuel/hoplite/ConfigLoaderBuilder.kt).
Kacheable has no Hoplite dependency or bundled integration test; applications own config loading.

Invalid expiry, nonpositive attempt limits, explicit stale fallback, and snapshots are rejected
when configuration objects are constructed. Cache construction validates backend capabilities
and effective inherited resilience settings before starting background work. Configuration maps
are copied so caller mutations cannot bypass validation.

Key definitions arrive separately from the name-based configuration map. A non-integer key
with a sequential policy, wrong storage shape, unsupported operation, or an invalid requested
range is therefore still rejected at the call boundary. Those combinations are not yet
prevented by the type system.

## Supported scope

| Capability | Guarded partition support |
| --- | --- |
| Storage | Hash values with one typed inner key; that key may map to multiple physical segments |
| Return value | One requested entry per call |
| Nulls | Stored only when `nullPlaceholder` is configured and `cacheIf` accepts the value |
| Suspend/blocking | Shared loading algorithm; blocking rejects non-default coroutine resilience settings |
| Loader limits and telemetry | Supported |
| Whole-partition/family invalidation | Supported |
| Snapshot restoration, miss/refresh policies, stale fallback | Unsupported |
| Whole-partition return views, bulk loaders, idle loading | Not implemented |

In-memory and Lettuce stores implement `VersionedHashOperations`; custom stores must implement
it (or `BlockingVersionedHashOperations`) to use this feature. Raw entry reads/writes cannot
operate on guarded keys. The in-memory store keeps guarded state privately; its public mutable
maps expose ordinary data only.

Lettuce checks and publishes atomically with Lua. Fixed scripts use `SCRIPT LOAD`/`EVALSHA`
with recovery for `NOSCRIPT`; generated mutation scripts and queued blocking `MULTI` operations
use `EVAL`. Script failures after execution begins are not replayed. Redis transactions do not
provide rollback after individual command failures. The raw hash field
`__kacheable_versioned_hash_generation_v1` is reserved for guarded-hash identification.

## Upgrading existing caches

Recompile clients for the new public signatures. `CacheValueCodec<T>` remains a source alias
for `CacheCodec<T>`, and the old value-codec factory names remain available.

Before enabling a partition policy on an ordinary hash, invalidate its existing data or choose
a new cache name. Ordinary hashes do not contain the version metadata required by guarded
loading. Coordinate deployment so older instances no longer write ordinary data to that name.

Also invalidate a guarded partition before changing a forward-only inner key to an enumerable
one. Entries without logical-key metadata cannot be enumerated; Kacheable rejects incomplete
enumeration rather than silently omitting them. Changing the codec or key identity requires the
same consideration.
