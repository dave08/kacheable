# Typed Key Shape Refactor Plan

This is a planning note for a possible cleanup of the typed cache DSL. It is not an implementation commitment.

The current staged work intentionally remains uncommitted so we can compare the concrete storage-specific API against this shape-first direction.

## Problem

The typed API is starting to multiply public types by storage type and arity:

- `StringPrimaryKeyN`
- `HashMapPrimaryKeyN`
- `HashMapStoredCacheN`
- `SetStoredCacheN`
- `StringCacheEntryRef`
- `HashMapCacheEntryRef`
- `SetMembershipCacheEntryRef`

That duplication is understandable, but every new storage form or return view risks creating another family of public types and overloads.

The deeper distinction may be key shape, not storage:

- primary-only keys
- primary/secondary keys

Storage still matters, but it should not necessarily force a distinct public key class family for every storage type.

## Naming Direction

Use existing terminology:

- `TypedPrimaryKey`
- `TypedPrimarySecondaryKey`

Avoid introducing `TypedLayeredKey` as a model name. “Layered” is useful prose for explaining storage behavior, but `PrimarySecondaryCacheArgs` is already the internal boundary language and the public model should align with that.

## Design Hypothesis

A future API could model shape first:

```kotlin
TypedPrimaryKey<P1, ...>
TypedPrimarySecondaryKey<P1, ..., S1, ...>
```

Storage becomes data carried by the key definition:

```kotlin
entryKey("song-cache", songId, storedAs = CacheStorage.String)
entryKey("song-page-cache", songId * (page + locale), storedAs = CacheStorage.HashMap)
entryKey("song-like-cache", songId * accountId, storedAs = CacheStorage.Set)
```

Entry refs could also be shape-first:

```kotlin
TypedPrimaryEntryRef
TypedPrimarySecondaryEntryRef
TypedPartialEntryRef
```

The operation boundary decides whether a return view is supported by the selected storage.

## Type Safety To Preserve

These should stay strongly protected:

- `+` means same key level.
- `*` means primary/secondary boundary.
- `.key(...)` preserves the function-parameter shape from the key definition.
- primary-only keys should not expose secondary partial-invalidation selectors.
- primary/secondary keys should preserve primary and secondary part boundaries in `PrimarySecondaryCacheArgs`.
- duplicate named parts inside one key definition fail fast.
- partial invalidation selectors require named key parts.
- hash partial invalidation currently requires all primary parts to be concrete.

These can be fail-fast at the operation boundary if that keeps the public model simpler:

- whether `returnsAs = map<K, V>()` is supported for a given storage.
- whether a future storage can interpret a return view differently.
- whether a storage supports partial invalidation beyond exact refs.

Reason: storage is representation, while return views are interpretation. A string-backed JSON object could theoretically support a map view later without changing key definitions.

## What Not To Do Yet

Do not introduce a generic type maze such as:

```kotlin
TypedCacheKey<S, Shape, Capabilities, Arity>
```

unless it makes call sites clearer. The public API should not become a puzzle just to reduce implementation duplication.

Do not move wildcard or pattern language into `KeyPart`.

Do not expose primary wildcard / keyspace-scan invalidation as a natural consequence of named selectors. If it is added, it should be explicit, storage-aware, and documented for cost.

Do not broaden a shared suspend/blocking base unless it removes real behavior duplication. The previous plan-helper experiment mostly moved parameters around and was not worth keeping.

## Current Coverage

Already covered by tests:

- raw exact cache reads/writes and invalidation.
- typed string exact reads/writes and invalidation.
- typed hash exact value reads/writes.
- typed same-level key composition.
- typed primary/secondary hash storage.
- grouped hash invalidation by primary.
- selected secondary hash invalidation under a concrete primary.
- named primary/secondary selector invalidation for hash storage.
- anonymous partial selectors are rejected.
- duplicate key-part names fail fast.
- delegated key-part names.
- low-level primary/secondary args and part names.
- set boolean membership.
- set classified enum membership.
- blocking typed string/hash/set behavior.
- Redis hash storage and partial invalidation.
- Redis set membership and classified membership.
- custom naming strategy compatibility.

## Coverage Gaps Before A Shape Refactor

Before refactoring public key classes, add or verify:

- compile-time examples for invalid return views, or a deliberate decision that these are runtime fail-fast checks.
- typed string storage with custom naming strategy.
- typed string storage with same-level multi-part keys in blocking API.
- typed string storage in Redis, if Redis string typed storage should be guaranteed beyond core store behavior.
- exact invalidation for `StringCacheEntryRef`, not only `keyPart(...)`.
- invalidation overload parity for suspend and blocking APIs.
- one contract-style test that runs the same typed exact cases against raw, string, and hash exact-like storage where applicable.
- one contract-style test that runs the same set-membership cases against suspend and blocking APIs.
- negative tests for unsupported operation boundaries:
  - `map<K, V>()` on string storage, if not supported yet.
  - `isMember()` on string/hash storage.
  - `value<T>()` on set storage.

The negative tests may need a compile-testing dependency if we want “does not compile” guarantees. If we prefer runtime fail-fast for storage/return compatibility, then those tests should assert clear exception messages instead.

## Proposed Refactor Sequence

1. Freeze current behavior with tests.
2. Introduce internal shape models behind existing public types.
3. Make existing public storage-specific classes delegate to shape models.
4. Add new shape-first public types only if call sites stay clean.
5. Migrate tests to assert behavior through the public DSL, not concrete class names.
6. Remove or typealias old storage-specific public classes only after compatibility decisions are explicit.

## Compatibility Questions

- Are `StringPrimaryKeyN` and `HashMapPrimaryKeyN` acceptable experimental public types for now, or do we want to avoid shipping them?
- Should storage/return compatibility be compile-time enforced or runtime fail-fast?
- Should `map<K, V>()` mean “hash bucket” specifically, or “map-like view over this storage”?
- Should typed string storage allow primary/secondary shapes later by flattening all parts, or should `CacheStorage.String` stay primary-only / same-level only?
- Do we want a compile-testing dependency to protect invalid DSL combinations?

## Recommendation

Keep the current staged concrete implementation as a comparison point, but do not commit to expanding storage-specific class families further.

Before a larger refactor, add the missing regression coverage listed above. Then prototype a small internal `TypedPrimaryKey` / `TypedPrimarySecondaryKey` model and see whether it reduces implementation duplication without making public signatures harder to read.
