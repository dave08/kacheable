package  kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.BlockingKacheableImpl
import com.github.dave08.kacheable.blocking.RedisBlockingKacheableStore
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.testcontainers.perTest
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.delay
import org.testcontainers.containers.GenericContainer
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import kotlin.time.Duration.Companion.minutes
import com.github.dave08.kacheable.blocking.invoke

class BlockingCacheableTest : FreeSpec() {
    init {
        lateinit var client: RedisClient
        lateinit var conn: StatefulRedisConnection<String, String>
        val container = GenericContainer<Nothing>("redis:5.0.3-alpine").apply {
            withExposedPorts(6379)
        }

        extensions(container.perTest())

        "Saves the result of a function with no parameters" {
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn)))
            val results = mutableListOf<Bar>()

            results += (1..5).map { testClass.bar() }

            expect {
                that(testClass.timesCalled).isEqualTo(1)

                that(conn.sync().get("BlockingFoo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }

        }

        "Saves the result of a function with multiple parameters" {
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn)))

            testClass.baz(32, "something")

            expectThat(conn.sync().keys("*")).containsExactly("BlockingFoo:32,something")
        }

        "Sets expiry from last write" {
            val config = listOf(CacheConfig("BlockingFoo", ExpiryType.after_write, 30.minutes)).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

            testClass.bar()

            expectThat(conn.sync().ttl("BlockingFoo")).isEqualTo((30.minutes).inWholeSeconds)
        }

        "Saves cache with default configs when not specified" {
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), emptyMap()))

            testClass.bar()

            expectThat(conn.sync().exists("BlockingFoo")).isEqualTo(1)
        }

        "Sets expiry from last access" {
            val config = listOf(CacheConfig("BlockingFoo", ExpiryType.after_access, 30.minutes)).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

            testClass.bar()
            delay(100)
            testClass.bar()

            expectThat(conn.sync().pttl("BlockingFoo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }

        "When function result is null" - {
            "and nullPlaceholder setting is not set, the cache entry isn't saved" {
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = null)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().keys("*")).isEmpty()
            }
            "and nullPlaceholder setting is set, the cache value is the placeholder" {
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()

                expectThat(conn.sync().get("BlockingFoo")).isEqualTo(placeholder)
            }
            "and nullPlaceholder setting is set, null is returned when retrieving the value" {
                val placeholder = "--placeholder--"
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = placeholder)).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.nullBar()
                val result = testClass.nullBar()

                expectThat(result).isNull()
            }
        }

        "Invalidates a cache entry" - {
            "without parameters" {
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.bar()
                testClass.invBar()

                expectThat(conn.sync().exists("BlockingFoo")).isEqualTo(0)
            }

            "with same parameters" {
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.baz(32, "something")
                testClass.invBaz(32, "something")

                expectThat(conn.sync().exists("BlockingFoo:32,something")).isEqualTo(0)
            }
        }

        "Save when condition is fullfilled" {
            val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
            val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

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

        "Cache results that are not serializable" - {
            "int" {
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveInt()

                expect {
                    that(conn.sync().get("BlockingFoo")).isEqualTo("32")
                }
            }

            "null int" {
                val config = listOf(CacheConfig("BlockingFoo", nullPlaceholder = "null")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveNullInt()

                expect {
                    that(conn.sync().get("BlockingFoo")).isEqualTo("null")
                }
            }

            "boolean" {
                val config = listOf(CacheConfig("BlockingFoo")).associateBy { it.name }
                val testClass = BlockingFoo(BlockingKacheableImpl(RedisBlockingKacheableStore(conn), config))

                testClass.primitiveBoolean()

                expect {
                    that(conn.sync().get("BlockingFoo")).isEqualTo("true")
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

class BlockingFoo(val cache: BlockingKacheable) {
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

    fun dontSaveBar(shouldSave: Boolean = false): Bar = cache("BlockingFoo", saveResultIf = { shouldSave }) {
        Bar(32, "something")
    }

    fun baz(id: Int, name: String) = cache("BlockingFoo", id, name) {
        Bar(32, "something")
    }

    fun invBar() = cache.invalidate(
        "BlockingFoo" to emptyList()
    ) {

    }

    fun invBaz(id: Int, name: String) = cache.invalidate(
        "BlockingFoo" to listOf(id, name)
    ) {

    }
}