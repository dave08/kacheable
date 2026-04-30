# Package Layering

This is the current direction for the typed cache API after the shape-first refactor work.

## Layers

`com.github.dave08.kacheable`
- Public API surface.
- Owns key shape, return views, storage markers, builders, and user-facing types.
- Should not own storage-specific branching beyond thin delegation.

`com.github.dave08.kacheable.internal.keys`
- Internal key/arg resolution only.
- Owns `PrimarySecondaryCacheArgs` assembly helpers and resolved key shapes.
- Must not depend on store implementations.
- Must not depend on storage operation planning.

`com.github.dave08.kacheable.internal.storage`
- Internal storage-facing planning and adaptation.
- Owns storage-specific ref resolution, entry-name resolution, and set membership planning.
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

## Next Cleanup Targets

- Move any remaining storage-aware helpers out of public package files when they are not part of the API story.
- Keep narrowing public files so they read like declarations plus delegation.
- If more internal grouping emerges, prefer subpackages under `internal.keys` or `internal.storage` before adding new public vocabulary.
