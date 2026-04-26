@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke as typedInvoke
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.blocking.BlockingKacheable
import com.github.dave08.kacheable.blocking.cache
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.value
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

private val blockingArtistCache = mainKey<Int>("blocking-artist-cache", storedAs = CacheStorage.HashMap)
private val blockingSongKey = key<Int>()
private val blockingArtistSongsCache = blockingArtistCache + blockingSongKey
private val blockingArtistFollowersCache =
    mainKey<Int>("blocking-artist-followers-cache", storedAs = CacheStorage.Set)
private val blockingFollowerAccountKey = key<Int>()
private val blockingArtistFollowerCache = blockingArtistFollowersCache + blockingFollowerAccountKey

private fun blockingArtistCacheKey(artistId: Int): String = "blocking-artist-cache:$artistId"

private fun blockingArtistFollowersKey(artistId: Int): String = "blocking-artist-followers-cache:$artistId"

val BlockingCacheableTest by testSuite {
    testFixture {
        RedisFixture.start()
    } asContextForEach {
        test("saves the result of a function with no parameters") {
            val fixture = blockingSubject()
            val results = (1..5).map { fixture.subject.bar() }

            expect {
                that(fixture.subject.timesCalled).isEqualTo(1)
                that(fixture.commands.get("BlockingFoo")).isEqualTo("""{"id":32,"name":"something"}""")
                that(results).all { isEqualTo(Bar(32, "something")) }
            }
        }

        test("saves the result of a function with multiple parameters") {
            val fixture = blockingSubject()

            fixture.subject.baz(32, "something")

            expectThat(fixture.commands.keys("*")).containsExactly("BlockingFoo:32,something")
        }

        test("stores typed hash-map cache entries as Redis hash fields") {
            val fixture = blockingSubject()
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = fixture.cache.typedInvoke(
                blockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = fixture.cache.typedInvoke(
                blockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(fixture.commands.type(blockingArtistCacheKey(artistId))).isEqualTo("hash")
                that(fixture.commands.hget(blockingArtistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by main key") {
            val fixture = blockingSubject()
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            fixture.cache.typedInvoke(
                blockingArtistSongsCache.key(artistId, firstSongId),
                returnsAs = value<Bar>(),
            ) {
                Bar(firstSongId, "Seven")
            }
            fixture.cache.typedInvoke(
                blockingArtistSongsCache.key(artistId, secondSongId),
                returnsAs = value<Bar>(),
            ) {
                Bar(secondSongId, "Eight")
            }

            fixture.cache.invalidate(blockingArtistSongsCache.keyPart(blockingArtistCache.keyPart(artistId)))

            expectThat(fixture.commands.exists(blockingArtistCacheKey(artistId))).isEqualTo(0)
        }

        test("stores typed set membership entries as Redis set members") {
            val fixture = blockingSubject()
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache.typedInvoke(
                blockingArtistFollowerCache.key(artistId, accountId),
                returnsAs = isMember(),
            ) {
                calls++
                true
            }
            val second = fixture.cache.typedInvoke(
                blockingArtistFollowerCache.key(artistId, accountId),
                returnsAs = isMember(),
            ) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(blockingArtistFollowersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(blockingArtistFollowersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("sets expiry from last write") {
            val fixture = blockingSubject(
                CacheConfig("BlockingFoo", ExpiryType.after_write, 30.minutes),
            )

            fixture.subject.bar()

            expectThat(fixture.commands.ttl("BlockingFoo")).isEqualTo((30.minutes).inWholeSeconds)
        }

        test("saves cache with default configs when not specified") {
            val fixture = blockingSubject()

            fixture.subject.bar()

            expectThat(fixture.commands.exists("BlockingFoo")).isEqualTo(1)
        }

        test("sets expiry from last access") {
            val fixture = blockingSubject(
                CacheConfig("BlockingFoo", ExpiryType.after_access, 30.minutes),
            )

            fixture.subject.bar()
            Thread.sleep(100)
            fixture.subject.bar()

            expectThat(fixture.commands.pttl("BlockingFoo")).isGreaterThan((30.minutes).inWholeMilliseconds - 10)
        }

        testSuite("when function result is null") {
            test("it does not save an entry when the null placeholder is not configured") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo", nullPlaceholder = null))

                fixture.subject.nullBar()

                expectThat(fixture.commands.keys("*")).isEmpty()
            }

            test("it stores the placeholder when the null placeholder is configured") {
                val placeholder = "--placeholder--"
                val fixture = blockingSubject(CacheConfig("BlockingFoo", nullPlaceholder = placeholder))

                fixture.subject.nullBar()

                expectThat(fixture.commands.get("BlockingFoo")).isEqualTo(placeholder)
            }

            test("it returns null when the cached value is the placeholder") {
                val placeholder = "--placeholder--"
                val fixture = blockingSubject(CacheConfig("BlockingFoo", nullPlaceholder = placeholder))

                fixture.subject.nullBar()
                val result = fixture.subject.nullBar()

                expectThat(result).isNull()
            }
        }

        testSuite("invalidation") {
            test("invalidates a cache entry without parameters") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo"))

                fixture.subject.bar()
                fixture.subject.invBar()

                expectThat(fixture.commands.exists("BlockingFoo")).isEqualTo(0)
            }

            test("invalidates a cache entry with matching parameters") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo"))

                fixture.subject.baz(32, "something")
                fixture.subject.invBaz(32, "something")

                expectThat(fixture.commands.exists("BlockingFoo:32,something")).isEqualTo(0)
            }
        }

        test("saveResultIf controls whether the result is cached") {
            val fixture = blockingSubject(CacheConfig("BlockingFoo"))

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
                that(fixture.commands.keys("*")).containsExactly("BlockingFoo")
            }
        }

        testSuite("cache results that are not serializable objects") {
            test("int") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo"))

                fixture.subject.primitiveInt()

                expectThat(fixture.commands.get("BlockingFoo")).isEqualTo("32")
            }

            test("null int") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo", nullPlaceholder = "null"))

                fixture.subject.primitiveNullInt()

                expectThat(fixture.commands.get("BlockingFoo")).isEqualTo("null")
            }

            test("boolean") {
                val fixture = blockingSubject(CacheConfig("BlockingFoo"))

                fixture.subject.primitiveBoolean()

                expectThat(fixture.commands.get("BlockingFoo")).isEqualTo("true")
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
