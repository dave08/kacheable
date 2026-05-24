# Possibly Planned Features

These are design notes for ideas that may be useful later, but are not committed
roadmap items yet.

## Large Source Loading And Hydration

Keep Kacheable centered on caching lambda results. The core story is typed cache
keys, a lambda that computes a value, policies for miss/refresh/fallback
behavior, and optional snapshots for durable warm-cache recovery.

Kacheable should avoid becoming a Redis/S3 client or a background job framework.
Large-source loading should first be modeled as ordinary cached lambda results:
cache a page, chunk, or inventory result with a short TTL, then let app code use
that result to answer a specific request.

Do not add `.view(...)`, fanout writes, `getOrNull`, `putIfAbsent`, startup
backfill config, or cursor/lease orchestration to public Kacheable unless a real
app-level implementation proves the need and clarifies the API.

If a future feature is promoted, prefer a small, understandable concept around
cached pages/chunks or an advanced, explicitly separate hydration/backfill API.
It must preserve the mental model that the lambda returns what Kacheable stores,
and orchestration remains visible instead of hidden in normal `cache(...)` calls.

## Cache Schema Versioning

Kacheable may eventually make cache schema versioning a first-class
configuration concept.

The version should probably live in `CacheConfig`, not at the call site. Cache
call sites should stay focused on the typed lambda result:

```kotlin
cache(imageFormatResolutionCache(sourceUrl)) {
    resolveImageFormats(sourceUrl)
}
```

The config would own physical compatibility details:

```kotlin
CacheConfig(
    name = "image-format-resolution",
    schemaVersion = 2,
)
```

`schemaVersion` is broader than serialized value format. It should represent the
physical cache schema, including:

- Redis key namespace.
- Storage shape, such as string vs hash/indexed/set.
- Key and partitioning shape.
- Serialized return type shape.
- Snapshot namespace/layout for that cache.
- Naming-strategy-sensitive details, if those affect physical keys.

Kacheable could derive a physical namespace from the logical cache name and
schema version:

```text
logical cache name: image-format-resolution
schema version: 2
physical namespace: image-format-resolution:v2
snapshot namespace: image-format-resolution/v2/...
```

The default behavior for old schema versions should be conservative: keep and
ignore them. That supports rolling deploys and rollback. Optional cleanup could
be added later as an explicit config or maintenance operation, for example:

```kotlin
DeprecatedCacheSchemas(
    versions = setOf(1),
    eviction = DeprecatedSchemaEviction.Keep,
)
```

Potential eviction modes:

- `Keep`: default; do not remove old physical cache data.
- `EvictOnStartup`: remove configured deprecated schema namespaces during app
  startup.
- `EvictAfter(...)`: remove deprecated schemas after a configured age or grace
  period.

Decode-failure handling would still be useful as a safety net, but schema
versioning should be the primary migration path for intentional physical cache
format changes.
