# Load selected entries

Use `.many(...)` to request several entries from an existing typed cache key. Every
value uses the same address, serializer, expiry, invalidation, and storage plan as
a single-entry call. The selection itself is never stored as a collection.

```kotlin
val songs = cacheKey(
    "songs",
    returns<Song?>(),
    key = exact(keyPart<Int>("songId")),
)

val byId: Map<Int, Song?> = cache(songs.many(23, 34, 56)) { keys, context ->
    val found = repository.loadSongs(keys).associateBy { it.id }
    // A successful complete database query establishes that absent rows do not exist.
    keys.associateWith { found[it] }
}
```

Both `many(vararg keys)` and `many(Iterable<K>)` are available. For a partitioned
key, bind its outer arguments first: `artistSongs.many(artistId, songIds)`.
Selections support one logical entry key, including a domain object whose
`keyPart` extracts multiple physical segments. Up to five outer partition key
parts are supported, matching the existing contextual partition keys. To batch a
composite exact lookup, model its arguments as one logical key object.

## Partial results

The loader receives distinct unresolved keys. A returned key/value pair is
resolved; a present key with a null value is a resolved nullable result; an omitted
key remains unresolved. Extra, unrequested keys are rejected. Kacheable uses map
membership to distinguish explicit null from omission.

Resolved entries are eligible for publication even when other keys are omitted.
Null results are stored only when the cache's `nullPlaceholder` is configured and
the storage predicate accepts them. Predicate-rejected values are still returned.
A thrown exception supplies no partial map; the runtime cannot recover values
held only inside the loader. Per-item recoverable errors must be represented as
omissions by the loader or its repository adapter. Cancellation is propagated.

The returned map combines cached values, resolved loader values, and any configured
fallbacks. It follows the first occurrence of each requested logical key. Duplicate
requests do not duplicate backend work or map entries. Keep the original ID list
when rebuilding an ordered delivery containing duplicates. Missing map entries
remain distinguishable from confirmed nulls.

An empty selection performs no reads and invokes no loader. A complete cache hit
also skips the loader. A subsequent single-entry call can reuse a value loaded by
`.many`, and `.many` can reuse single-entry publications.

Selected calls work through Kotlin `Kacheable by delegate` and
`BlockingKacheable by delegate` wrappers. Custom runtimes can override the public
selected `invoke` member; its default reports unsupported selected loading.

## Reuse the loading context and policies

Both batch and scalar partition loaders receive `CacheLoadContext`:
`entry(key)` returns `CacheEntry.Present(value)` or `CacheEntry.Missing`; `entries(keys)` returns cached
values only. Reads do not invoke other loaders. Contexts are valid only during the
loader attempt. Ordinary reads provide ordinary cache consistency; guarded
partition contexts additionally track read dependencies and validate versions.

Known-key selections require only `KeyPart<K>`. Enumerable keys are needed only
for supported no-argument partition `keys()` and `entries()` operations, where
storage must reconstruct keys the caller did not supply.

Ordinary suspending selections accept the existing `CacheMissPolicy`,
`CacheRefreshPolicy`, and `storeResultIf` overload. Policies apply to each entry.
Miss fallbacks are never stored; failed refreshes retain the latest cached value.
An omitted key can invoke a configured failure fallback with
`UnresolvedCacheKeyException`, since the map supplies no original failure cause.
Background work uses the cache's existing background scope. Blocking `.many`
shares the foreground algorithm and exposes `cacheIf`; it does not add miss,
refresh, fallback, or background policy overloads to the blocking API.

## Storage and coordination

String, ordinary hash, Boolean membership, and enum classification caches retain
their existing representation. Cached false is distinct from an unknown member.
Hashes and membership sets retain their existing shared expiry behavior; batching
does not introduce per-field TTLs.

Store interfaces provide default scalar implementations of selected reads.
Lettuce uses native multi-key/field/membership reads where supported and the
existing grouped mutation infrastructure for publication. Custom stores need not
implement optimized reads to use ordinary selections.

For string entries with access expiry, Lettuce uses a fixed cached Lua script to
read the selection and refresh hit TTLs together. A warmed script needs one
client `EVALSHA` command; first use loads the script, and eviction uses the
existing reload path. Redis still performs a read and expiry update for each hit
inside the script, so fewer client commands do not imply the same reduction in
server work or latency. All values are read before TTL updates, so a wrong-type
key fails without refreshing earlier keys. Scalar reads retain their existing
`GETEX` behavior.

When single-flight is enabled, single and selected calls use the same entry
identities. A batch publishes/completes entries it owns before waiting for entries
owned by another call. Redis entry coordination requires
`AdmissionAwareDistributedSingleFlightStore` so a batch can claim leases without
blocking while holding other entries. Existing ordinary lease behavior does not
add generation fencing or exactly-once loader execution.

## Guarded partitions

`.many` retains the configured partition policy:

- `OnDemand` batches independent missing entries and uses atomic, version-checked
  publication. Omitted entries do not prevent valid siblings from being published.
- `SequentialFrom` invokes the batch loader with singleton prerequisite lists in
  order. Each prerequisite must be published before later entries can load. An
  unresolved prerequisite stops dependent work; the returned map includes only
  requested values that were resolved.

A selection may therefore invoke its loader more than once because of dependencies,
coordination, or guarded retries. It does not promise a single invocation for every
policy. Generation changes restart guarded selection work rather than combine
values from different partition lifetimes.

Guarded selections retain existing restrictions on snapshots, miss/refresh policies,
expiry, and invalidation. Custom guarded stores must implement atomic
`publishHashes` for multi-entry publication; the default safely supports only an
empty or singleton publication. In-memory and Lettuce stores implement the batch
capability. Ordinary and guarded namespaces remain independent.

Telemetry records batched backend operations and loader attempts. A returned map
with unresolved requested entries is reported as an incomplete/failed operation;
its successfully published values remain reusable.
