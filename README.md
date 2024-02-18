[![](https://jitpack.io/v/dave08/kacheable.svg)](https://jitpack.io/#dave08/kacheable)

> [!CAUTION]
> This library is still experimental and actively being developed, it may change until it is released. For now, it's just released as snapshots on Jitpack.io.

> [!NOTE]
> For now, this library depends on KotlinX Serialization to serialize computation results to Json for storage in the cache.

# Kacheable

Kacheable is a Kotlin library simplifies working with caching/retrieving results of the computation inside it's lambda function. It mimics the Micronaut caching feature, expanding a bit on it. There are separate implementations for both blocking and suspending. For now the only storage implementation available is using the Lettuce library with Redis caching, a very simple in memory implementation, and a NoOp implementation for testing or disabling caching.

## Basic Usage

```kotlin
import com.github.dave08.kacheable.invoke

class Foo() {
    // Instatiate the Lettuce client
    val host = container.host
    val port = container.getMappedPort(6379)

    val client = RedisClient.create("redis://$host:$port/0")
    val conn = client.connect()

    // Use the redis store with Kacheable
    val cache = KacheableImpl(RedisKacheableStore(conn))
    
    suspend fun getUser(userId: Int) = cache("user-cache", userId) {
        // some db query to retrieve user... this will be cached the first
        // time for any unique userId and will be retrieved from the cache
        // if without re-running the query if it's still there.
    }
}
```

## Configuring caches

Any cache without a configuration uses defaults, but can be configured per cache like this:

```kotlin
val configs = listOf(CacheConfig("foo", ExpiryType.after_write, 30.minutes)).associateBy { it.name }

// Then initialize the cache with them:
val cache = KacheableImpl(RedisKacheableStore(conn), configs)
```

The options are:
```kotlin
enum class ExpiryType {
    none, after_write, after_access
}

data class CacheConfig(
    val name: String,
    val expiryType: ExpiryType = ExpiryType.none,
    val expiry: Duration = Duration.INFINITE,
    /**
    If this is a real null, the cache entry will not be saved at all.
    This should ONLY be set if the function's return type is nullable!
     */
    val nullPlaceholder: String? = null,
)
```

If the null placeholder setting is null (the default), and the function returns null, the result won't be saved in the cache at all, otherwise the value in `nullPlaceholder` will be saved in the cache, and if found, will be returned as `null` from the cache lambda.

## Extra options

You can provide a condition lambda to determine whether the cache will save the result (if in certain cases the result shouldn't be saved and the calculation should be run). The `saveResultIf` lambda receives the current value in the cache:

```kotlin
suspend fun dontSaveBar(shouldSave: Boolean = false): Bar = cache("foo", saveResultIf = { shouldSave }) {
        Bar(32, "something")
    }
```
