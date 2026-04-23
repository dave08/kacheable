package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.Kacheable
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
    testFixture {
        RedisFixture.start()
    } asContextForEach {
        test("saves the result of a function with no parameters") {
            val fixture = suspendSubject()
            val results = mutableListOf<Bar>()
            repeat(5) {
                results += fixture.subject.bar()
            }

            expect {
                that(fixture.subject.timesCalled).isEqualTo(1)
                that(fixture.commands.get("foo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }
        }

        test("saves the result of a function with multiple parameters") {
            val fixture = suspendSubject()

            fixture.subject.baz(32, "something")

            expectThat(fixture.commands.keys("*")).containsExactly("foo:32,something")
        }

        test("sets expiry from last write") {
            val fixture = suspendSubject(
                CacheConfig("foo", ExpiryType.after_write, 30.minutes),
            )

            fixture.subject.bar()

            expectThat(fixture.commands.ttl("foo")).isEqualTo((30.minutes).inWholeSeconds)
        }

        test("saves cache with default configs when not specified") {
            val fixture = suspendSubject()

            fixture.subject.bar()

            expectThat(fixture.commands.exists("foo")).isEqualTo(1)
        }

        test("sets expiry from last access") {
            val fixture = suspendSubject(
                CacheConfig("foo", ExpiryType.after_access, 30.minutes),
            )

            fixture.subject.bar()
            delay(100)
            fixture.subject.bar()

            expectThat(fixture.commands.pttl("foo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }

        testSuite("when function result is null") {
            test("it does not save an entry when the null placeholder is not configured") {
                val fixture = suspendSubject(CacheConfig("foo", nullPlaceholder = null))

                fixture.subject.nullBar()

                expectThat(fixture.commands.keys("*")).isEmpty()
            }

            test("it stores the placeholder when the null placeholder is configured") {
                val placeholder = "--placeholder--"
                val fixture = suspendSubject(CacheConfig("foo", nullPlaceholder = placeholder))

                fixture.subject.nullBar()

                expectThat(fixture.commands.get("foo")).isEqualTo(placeholder)
            }

            test("it returns null when the cached value is the placeholder") {
                val placeholder = "--placeholder--"
                val fixture = suspendSubject(CacheConfig("foo", nullPlaceholder = placeholder))

                fixture.subject.nullBar()
                val result = fixture.subject.nullBar()

                expectThat(result).isNull()
            }
        }

        testSuite("invalidation") {
            test("invalidates a cache entry without parameters") {
                val fixture = suspendSubject(CacheConfig("foo"))

                fixture.subject.bar()
                fixture.subject.invBar()

                expectThat(fixture.commands.exists("foo")).isEqualTo(0)
            }

            test("invalidates a cache entry with matching parameters") {
                val fixture = suspendSubject(CacheConfig("foo"))

                fixture.subject.baz(32, "something")
                fixture.subject.invBaz(32, "something")

                expectThat(fixture.commands.exists("foo:32,something")).isEqualTo(0)
            }
        }

        test("saveResultIf controls whether the result is cached") {
            val fixture = suspendSubject(CacheConfig("foo"))

            fixture.subject.dontSaveBar()
            val result = fixture.subject.dontSaveBar()

            expect {
                that(result).isEqualTo(Bar(32, "something"))
                that(fixture.commands.keys("*")).isEmpty()
            }

            fixture.subject.dontSaveBar(true)
            val result2 = fixture.subject.dontSaveBar(true)

            expect {
                that(result2).isEqualTo(Bar(32, "something"))
                that(fixture.commands.keys("*")).containsExactly("foo")
            }
        }

        testSuite("cache results that are not serializable objects") {
            test("int") {
                val fixture = suspendSubject(CacheConfig("foo"))

                fixture.subject.primitiveInt()

                expectThat(fixture.commands.get("foo")).isEqualTo("32")
            }

            test("null int") {
                val fixture = suspendSubject(CacheConfig("foo", nullPlaceholder = "null"))

                fixture.subject.primitiveNullInt()

                expectThat(fixture.commands.get("foo")).isEqualTo("null")
            }

            test("boolean") {
                val fixture = suspendSubject(CacheConfig("foo"))

                fixture.subject.primitiveBoolean()

                expectThat(fixture.commands.get("foo")).isEqualTo("true")
            }

            test("set") {
                val fixture = suspendSubject(CacheConfig("foo"))

                fixture.subject.setOfInts()

                expectThat(fixture.commands.get("foo")).isEqualTo("[1,2,3]")
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
