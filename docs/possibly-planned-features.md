# Possibly Planned Features

These are design notes for ideas that may be useful later, but are not committed
roadmap items yet.

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
