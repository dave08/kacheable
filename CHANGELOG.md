# Changelog

## 0.2.0-alpha02

This release makes typed cache-key calls work from Kotlin classes that delegate `Kacheable`.

### Changed

- Typed cache-key runtime operations are now part of the public `Kacheable` and `BlockingKacheable` contracts instead of relying on casts to internal runtime interfaces.
- `cache(...)`, `invoke(...)`, and typed invalidation extensions now dispatch through the public delegated interface, so patterns like `class Repo(kacheable: Kacheable) : Kacheable by kacheable` work with typed keys.

### Verification

- `./gradlew test`

## 0.2.0-alpha01

This release introduces the result-first typed cache-key API as the main direction for Kacheable.

### Added

- `cacheKey("name", returns<T>(), key = exact(...))` for exact typed cache keys.
- `cacheKey("name", returns<T>(), key = partitioned(...))` for partitioned typed cache keys.
- `matchableKeyPart(...)` and `matching(...)` for scoped key-part invalidation inside partitioned caches.
- Typed invalidation refs for exact entries, partitions, matching subsets, whole typed caches, and raw migration targets.
- Automatic storage planning for exact values, indexed values, Boolean membership, and enum membership.
- `cacheIf` for conditional writes after a value is computed.
- Nullable key-part support through the default naming strategy's null placeholder.
- Blocking API parity for the typed cache-key surface.

### Changed

- Public raw-cache write predicates now use `cacheIf` instead of `saveResultIf`/`shouldSaveResult`.
- The typed API no longer requires Kotlin opt-in annotations. Version `0.x` communicates that the API may still receive minor source-level refinements before `1.0`.
- Published artifact metadata now consistently uses `com.github.dave08.kacheable`.

### Migration Notes

- Prefer the new result-first API for new code:

  ```kotlin
  val songCache = cacheKey(
      "song",
      returns<Song>(),
      key = exact(songId),
  )
  ```

- Remove old `ExperimentalKacheableApi` imports and `@OptIn(ExperimentalKacheableApi::class)` markers when upgrading.
- Raw string-cache calls and raw invalidation refs remain available as migration escape hatches.
- Existing Redis data may not be reusable when moving a cache from flat raw keys to typed partitioned or membership storage. Treat storage-shape changes as new cache namespaces or clear the old cache data.

### Verification

- `./gradlew :kacheable-core:test :kacheable-lettuce:test publishToMavenLocal`
