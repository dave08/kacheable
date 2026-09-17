# Package Layering

This is the current layering direction for the typed cache-key API.

## Layers

`com.github.dave08.kacheable`
- Public API surface.
- Owns key shape, result descriptors, storage plans, builders, and user-facing types.
- Should not own storage-specific branching beyond thin delegation.

`com.github.dave08.kacheable.internal.keys`
- Internal key/arg resolution only.
- Owns `PrimarySecondaryCacheArgs` assembly helpers and resolved key shapes.
- Must not depend on store implementations.
- Must not depend on storage operation planning.

`com.github.dave08.kacheable.internal.storage`
- Internal storage-facing adaptation.
- Owns entry-name resolution and storage-specific read/write behavior.
- Prefer subpackages such as `internal.storage.string`, `internal.storage.hash`, and `internal.storage.set`
  when behavior is truly storage-owned.
- May depend on public API contracts and `internal.keys`.
- Should be the main home for `when (storage)` branching.

`com.github.dave08.kacheable.store`
- Store abstractions and codecs.
- Should not depend on typed key DSL internals.

`com.github.dave08.kacheable.blocking`
- Blocking wrappers over the same public semantics.

## Dependency Rules

- Public packages may depend on `internal.keys` and `internal.storage` only through small internal seams.
- `internal.keys` must stay storage-agnostic.
- `internal.storage` may use `CacheStorage` and storage capabilities directly.
- `store` should remain below typed DSL concerns.
- Blocking code should mirror async behavior, not fork semantics.

## Guarded hash loading

- `internal.storage.hash.PartitionCacheRoutes` selects configured behavior at construction.
- `PartitionCacheRuntime` owns prerequisite loading, lazy sibling access, retries, and publication.
  Scalar calls adapt to its selected-entry engine, so both use the same attempt and publication path.
- `CacheLoadCoordinator` owns admission and load coordination; the partition runtime delegates to it.
- The coordinator's final read runs under load ownership. Its version and selected values are
  passed into the loader attempt rather than discarded and fetched again. Rechecks remain after
  waits, and publication still validates generation and revision atomically.
- Stores implement optional `VersionedHashOperations` capabilities. In-memory state and Redis
  scripts enforce atomicity below the typed API. Guarded invalidation uses the same capability.
- Redis ordinary operations use native commands in the ordinary namespace. Versioned operations
  own the reserved physical prefix; core naming remains logical and storage-agnostic.
- `CacheCodec` is shared by values and logical-key metadata. Physical key extraction remains
  in the key layer; enumerable metadata does not invert those extractors.

## Next Cleanup Targets

- Move any remaining storage-aware helpers out of public package files when they are not part of the API story.
- Keep narrowing public files so they read like declarations plus delegation.
- If more internal grouping emerges, prefer subpackages under `internal.keys` or `internal.storage` before adding new public vocabulary.

## Selected-entry loading

- `CacheManyRef` selects existing typed entries without introducing another storage layout.
- `CacheLoadContext` and `CacheEntry` define the common read vocabulary for scalar partition
  loaders and batch loaders. The guarded implementation supplies version checking and
  enumerable-key capabilities. Blocking loaders use the matching `BlockingCacheLoadContext`.
- `CacheLifecycleSupport` shares the scalar miss/refresh/fallback lifecycle across ordinary
  value and membership storage. `CachedValue` distinguishes present nulls from absence.
- `CacheManyRuntime` groups loader work and normalizes partial results; `SelectedEntryStorage`
  owns representation-specific reads and grouped publication. Both use the existing naming,
  write decisions, store capabilities, load coordinator, and telemetry infrastructure.
- Blocking selections adapt synchronous store operations into the same batch runtime and use
  the existing blocking admission coordinator. They do not start an independent loading engine.
- Guarded selections stay in `PartitionCacheRuntime` and use atomic `publishHashes` below the
  typed API. Independent entries can publish together; sequential prerequisites still publish
  before dependent loaders execute.
