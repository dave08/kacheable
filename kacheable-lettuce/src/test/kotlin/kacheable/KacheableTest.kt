package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.RedisKacheableStore
import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.delay
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNull
import kotlin.time.Duration.Companion.minutes

val KacheableTest by testSuite {
    test("saves the result of a function with no parameters") {
        withRedisConnection { conn ->
            val testClass = Foo(Kacheable(RedisKacheableStore(conn)))
            val results = (1..5).map { testClass.bar() }

            expect {
                that(testClass.timesCalled).isEqualTo(1)
                that(conn.sync().get("foo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }
        }
    }

    test("saves the result of a function with multiple parameters") {
        withRedisConnection { conn ->
            val testClass = Foo(Kacheable(RedisKacheableStore(conn)))

            testClass.baz(32, "something")

            expectThat(conn.sync().keys("*")).containsExactly("foo:32,something")
        }
    }

    test("sets expiry from last write") {
        withRedisConnection { conn ->
            val config = listOf(CacheConfig("foo", ExpiryType.after_write, 30.minutes)).associateBy { it.name }
            val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

            testClass.bar()

            expectThat(conn.sync().ttl("foo")).isEqualTo((30.minutes).inWholeSeconds)
        }
    }

    test("saves cache with default configs when not specified") {
        withRedisConnection { conn ->
            val testClass = Foo(Kacheable(RedisKacheableStore(conn), emptyMap()))

            testClass.bar()

            expectThat(conn.sync().exists("foo")).isEqualTo(1)
        }
    }

    test("sets expiry from last access") {
        withRedisConnection { conn ->
            val config = listOf(CacheConfig("foo", ExpiryType.after_access, 30.minutes)).associateBy { it.name }
            val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

            testClass.bar()
            delay(100)
            testClass.bar()

            expectThat(conn.sync().pttl("foo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }
    }

    testSuite("when function result is null") {
        test("it does not save an entry when the null placeholder is not configured") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo", nullPlaceholder = null)).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().keys("*")).isEmpty()
            }
        }

        test("it stores the placeholder when the null placeholder is configured") {
            withRedisConnection { conn ->
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("foo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().get("foo")).isEqualTo(placeholder)
            }
        }

        test("it returns null when the cached value is the placeholder") {
            withRedisConnection { conn ->
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("foo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.nullBar()
                val result = testClass.nullBar()

                expectThat(result).isNull()
            }
        }
    }

    testSuite("invalidation") {
        test("invalidates a cache entry without parameters") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.bar()
                testClass.invBar()

                expectThat(conn.sync().exists("foo")).isEqualTo(0)
            }
        }

        test("invalidates a cache entry with matching parameters") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.baz(32, "something")
                testClass.invBaz(32, "something")

                expectThat(conn.sync().exists("foo:32,something")).isEqualTo(0)
            }
        }
    }

    test("saveResultIf controls whether the result is cached") {
        withRedisConnection { conn ->
            val config = listOf(CacheConfig("foo")).associateBy { it.name }
            val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

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
                that(conn.sync().keys("*")).containsExactly("foo")
            }
        }
    }

    testSuite("cache results that are not serializable objects") {
        test("int") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.primitiveInt()

                expectThat(conn.sync().get("foo")).isEqualTo("32")
            }
        }

        test("null int") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo", nullPlaceholder = "null")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.primitiveNullInt()

                expectThat(conn.sync().get("foo")).isEqualTo("null")
            }
        }

        test("boolean") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.primitiveBoolean()

                expectThat(conn.sync().get("foo")).isEqualTo("true")
            }
        }

        test("set") {
            withRedisConnection { conn ->
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(Kacheable(RedisKacheableStore(conn), config))

                testClass.setOfInts()

                expectThat(conn.sync().get("foo")).isEqualTo("[1,2,3]")
            }
        }
    }
}

class Foo(private val cache: Kacheable) {
    var timesCalled: Int = 0

    suspend fun bar() = cache("foo") {
        timesCalled++
        Bar(32, "something")
    }

    suspend fun nullBar(): Bar? = cache("foo") {
        null
    }

    suspend fun primitiveInt(): Int = cache("foo") {
        32
    }

    suspend fun primitiveNullInt(): Int? = cache("foo") {
        null
    }

    suspend fun primitiveBoolean(): Boolean = cache("foo") {
        true
    }

    suspend fun setOfInts(): Set<Int> = cache("foo") {
        setOf(1, 2, 3)
    }

    suspend fun dontSaveBar(shouldSave: Boolean = false): Bar =
        cache("foo", saveResultIf = { shouldSave }) {
            Bar(32, "something")
        }

    suspend fun baz(id: Int, name: String) = cache("foo", id, name) {
        Bar(32, "something")
    }

    suspend fun invBar() = cache.invalidate(
        "foo" to emptyList()
    ) {}

    suspend fun invBaz(id: Int, name: String) = cache.invalidate(
        "foo" to listOf(id, name)
    ) {}
}
