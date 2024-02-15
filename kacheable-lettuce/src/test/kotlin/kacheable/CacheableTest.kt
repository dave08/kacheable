package kacheable

import com.github.dave08.kacheable.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perTest
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.testcontainers.containers.GenericContainer
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import kotlin.time.Duration.Companion.minutes

class CacheableTest : FreeSpec() {
    init {
        lateinit var client: RedisClient
        lateinit var conn: StatefulRedisConnection<String, String>
        val container = GenericContainer<Nothing>("redis:5.0.3-alpine").apply {
            withExposedPorts(6379)
        }

        extensions(container.perTest())

        "Saves the result of a function with no parameters" {
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn)))
            val results = mutableListOf<Bar>()

            results += (1..5).map { testClass.bar() }

            expect {
                that(testClass.timesCalled).isEqualTo(1)

                that(conn.sync().get("foo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }

        }

        "Saves the result of a function with multiple parameters" {
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn)))

            testClass.baz(32, "something")

            expectThat(conn.sync().keys("*")).containsExactly("foo:32,something")
        }

        "Sets expiry from last write" {
            val config = listOf(CacheConfig("foo", ExpiryType.after_write, 30.minutes)).associateBy { it.name }
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

            testClass.bar()

            expectThat(conn.sync().ttl("foo")).isEqualTo((30.minutes).inWholeSeconds)
        }

        "Saves cache with default configs when not specified" {
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), emptyMap()))

            testClass.bar()

            expectThat(conn.sync().exists("foo")).isEqualTo(1)
        }

        "Sets expiry from last access" {
            val config = listOf(CacheConfig("foo", ExpiryType.after_access, 30.minutes)).associateBy { it.name }
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

            testClass.bar()
            delay(100)
            testClass.bar()

            expectThat(conn.sync().pttl("foo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }

        "When function result is null" - {
            "and nullPlaceholder setting is not set, the cache entry isn't saved" {
                val config = listOf(CacheConfig("foo", nullPlaceholder = null)).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().keys("*")).isEmpty()
            }
            "and nullPlaceholder setting is set, the cache value is the placeholder" {
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("foo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().get("foo")).isEqualTo(placeholder)
            }
            "and nullPlaceholder setting is set, null is returned when retrieving the value" {
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("foo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.nullBar()
                val result = testClass.nullBar()

                expectThat(result).isNull()
            }
        }

        "Invalidates a cache entry" - {
            "without parameters" {
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.bar()
                testClass.invBar()

                expectThat(conn.sync().exists("foo")).isEqualTo(0)
            }

            "with same parameters" {
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.baz(32, "something")
                testClass.invBaz(32, "something")

                expectThat(conn.sync().exists("foo:32,something")).isEqualTo(0)
            }
        }

        "Save when condition is fullfilled" {
            val config = listOf(CacheConfig("foo")).associateBy { it.name }
            val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

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

        "Cache results that are not serializable" - {
            "int" {
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.primitiveInt()

                expect {
                    that(conn.sync().get("foo")).isEqualTo("32")
                }
            }

            "null int" {
                val config = listOf(CacheConfig("foo", nullPlaceholder = "null")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.primitiveNullInt()

                expect {
                    that(conn.sync().get("foo")).isEqualTo("null")
                }
            }

            "boolean" {
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.primitiveBoolean()

                expect {
                    that(conn.sync().get("foo")).isEqualTo("true")
                }
            }

            "set" {
                val config = listOf(CacheConfig("foo")).associateBy { it.name }
                val testClass = Foo(KacheableImpl(RedisKacheableStore(conn), config))

                testClass.setOfInts()

                expect {
                    that(conn.sync().get("foo")).isEqualTo("[1,2,3]")
                }
            }
        }

        beforeTest {
            val host = container.host
            val port = container.getMappedPort(6379)

            client = RedisClient.create("redis://$host:$port/0")
            conn = client.connect()
        }

        afterTest {
            conn.close()
            client.shutdown()
        }
    }
}

@Serializable
data class Bar(val id: Int, val name: String)

class Foo(val cache: Kacheable) {
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
        setOf(1,2,3)
    }

    suspend fun dontSaveBar(shouldSave: Boolean = false): Bar = cache("foo", saveResultIf = { shouldSave }) {
        Bar(32, "something")
    }

    suspend fun baz(id: Int, name: String) = cache("foo", id, name) {
        Bar(32, "something")
    }

    suspend fun invBar() = cache.invalidate(
        "foo" to emptyList()
    ) {

    }

    suspend fun invBaz(id: Int, name: String) = cache.invalidate(
        "foo" to listOf(id, name)
    ) {

    }
}