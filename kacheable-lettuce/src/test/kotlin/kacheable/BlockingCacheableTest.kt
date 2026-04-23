package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.RedisBlockingKacheableStore
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull
import kotlin.time.Duration.Companion.minutes

val BlockingCacheableTest by testSuite {
    test("saves the result of a function with no parameters") {
        withRedisConnectionBlocking { conn ->
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn)))
            val results = (1..5).map { testClass.bar() }

            expect {
                that(testClass.timesCalled).isEqualTo(1)
                that(conn.sync().get("BlockingFoo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }
        }
    }

    test("saves the result of a function with multiple parameters") {
        withRedisConnectionBlocking { conn ->
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn)))

            testClass.baz(32, "something")

            expectThat(conn.sync().keys("*")).containsExactly("BlockingFoo:32,something")
        }
    }

    test("sets expiry from last write") {
        withRedisConnectionBlocking { conn ->
            val config = listOf(CacheConfig("BlockingFoo", ExpiryType.after_write, 30.minutes)).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

            testClass.bar()

            expectThat(conn.sync().ttl("BlockingFoo")).isEqualTo((30.minutes).inWholeSeconds)
        }
    }

    test("saves cache with default configs when not specified") {
        withRedisConnectionBlocking { conn ->
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), emptyMap()))

            testClass.bar()

            expectThat(conn.sync().exists("BlockingFoo")).isEqualTo(1)
        }
    }

    test("sets expiry from last access") {
        withRedisConnectionBlocking { conn ->
            val config = listOf(CacheConfig("BlockingFoo", ExpiryType.after_access, 30.minutes)).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

            testClass.bar()
            Thread.sleep(100)
            testClass.bar()

            expectThat(conn.sync().pttl("BlockingFoo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }
    }

    testSuite("when function result is null") {
        test("it does not save an entry when the null placeholder is not configured") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = null)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().keys("*")).isEmpty()
            }
        }

        test("it stores the placeholder when the null placeholder is configured") {
            withRedisConnectionBlocking { conn ->
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().get("BlockingFoo")).isEqualTo(placeholder)
            }
        }

        test("it returns null when the cached value is the placeholder") {
            withRedisConnectionBlocking { conn ->
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()
                val result = testClass.nullBar()

                expectThat(result).isNull()
            }
        }
    }

    testSuite("invalidation") {
        test("invalidates a cache entry without parameters") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.bar()
                testClass.invBar()

                expectThat(conn.sync().exists("BlockingFoo")).isEqualTo(0)
            }
        }

        test("invalidates a cache entry with matching parameters") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.baz(32, "something")
                testClass.invBaz(32, "something")

                expectThat(conn.sync().exists("BlockingFoo:32,something")).isEqualTo(0)
            }
        }
    }

    test("saveResultIf controls whether the result is cached") {
        withRedisConnectionBlocking { conn ->
            val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

            testClass.dontSaveBar()
            val result = testClass.dontSaveBar()

            expect {
                that(result).isEqualTo(Bar(32, "something"))
                that(conn.sync().keys("*")).isEmpty()
            }

            testClass.dontSaveBar(true)
            val result2 = testClass.dontSaveBar(true)

            expect {
                that(result2).isEqualTo(Bar(32, "something"))
                that(conn.sync().keys("*")).containsExactly("BlockingFoo")
            }
        }
    }

    testSuite("cache results that are not serializable objects") {
        test("int") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveInt()

                expectThat(conn.sync().get("BlockingFoo")).isEqualTo("32")
            }
        }

        test("null int") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = "null")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveNullInt()

                expectThat(conn.sync().get("BlockingFoo")).isEqualTo("null")
            }
        }

        test("boolean") {
            withRedisConnectionBlocking { conn ->
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheable(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveBoolean()

                expectThat(conn.sync().get("BlockingFoo")).isEqualTo("true")
            }
        }
    }
}

class BlockingFoo(private val cache: BlockingKacheable) {
    var timesCalled: Int = 0

    fun bar() = cache("BlockingFoo") {
        timesCalled++
        Bar(32, "something")
    }

    fun nullBar(): Bar? = cache("BlockingFoo") {
        null
    }

    fun primitiveInt(): Int = cache("BlockingFoo") {
        32
    }

    fun primitiveNullInt(): Int? = cache("BlockingFoo") {
        null
    }

    fun primitiveBoolean(): Boolean = cache("BlockingFoo") {
        true
    }

    fun dontSaveBar(shouldSave: Boolean = false): Bar =
        cache("BlockingFoo", saveResultIf = { shouldSave }) {
            Bar(32, "something")
        }

    fun baz(id: Int, name: String) = cache("BlockingFoo", id, name) {
        Bar(32, "something")
    }

    fun invBar() = cache.invalidate(
        "BlockingFoo" to emptyList()
    ) {}

    fun invBaz(id: Int, name: String) = cache.invalidate(
        "BlockingFoo" to listOf(id, name)
    ) {}
}
