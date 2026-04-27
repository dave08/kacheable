@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.CacheConfig
import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExpiryType
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.Kacheable
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.value
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

private val artistCache = mainKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
private val songKey = key<Int>()
private val artistSongsCache = artistCache + songKey
private val artistFollowersCache = mainKey<Int>("artist-followers-cache", storedAs = CacheStorage.Set)
private val followerAccountKey = key<Int>()
private val artistFollowerCache = artistFollowersCache + followerAccountKey
private val songReactionsCache = mainKey<Int>("song-reaction-cache", storedAs = CacheStorage.Set)
private val reactingAccountKey = key<Int>()
private val songReactionCache = songReactionsCache + reactingAccountKey

private enum class SongReaction {
    LIKE,
    DISLIKE,
    NONE,
}

private fun artistCacheKey(artistId: Int): String = "artist-cache:$artistId"

private fun artistFollowersKey(artistId: Int): String = "artist-followers-cache:$artistId"

private fun artistFollowerNonMembersKey(artistId: Int): String =
    "${artistFollowersKey(artistId)}:__kacheable_non_members"

private fun songReactionKey(songId: Int, reaction: SongReaction): String =
    "song-reaction-cache:$songId:${reaction.name}"

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

        test("stores typed hash-map cache entries as Redis hash fields") {
            val fixture = suspendSubject()
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = fixture.cache(
                artistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = fixture.cache(
                artistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(fixture.commands.type(artistCacheKey(artistId))).isEqualTo("hash")
                that(fixture.commands.hget(artistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by main key") {
            val fixture = suspendSubject()
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            fixture.cache(artistSongsCache.key(artistId, firstSongId), returnsAs = value<Bar>()) {
                Bar(firstSongId, "Seven")
            }
            fixture.cache(artistSongsCache.key(artistId, secondSongId), returnsAs = value<Bar>()) {
                Bar(secondSongId, "Eight")
            }

            fixture.cache.invalidate(artistSongsCache.keyPart(artistCache.keyPart(artistId)))

            expectThat(fixture.commands.exists(artistCacheKey(artistId))).isEqualTo(0)
        }

        test("stores typed set membership entries as Redis set members") {
            val fixture = suspendSubject()
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }
            val second = fixture.cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(artistFollowersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(artistFollowersKey(artistId), accountId.toString())).isEqualTo(true)
            }
        }

        test("stores false typed set membership entries in the non-member set") {
            val fixture = suspendSubject()
            val artistId = 13
            val accountId = 7
            var calls = 0

            val first = fixture.cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }
            val second = fixture.cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }

            expect {
                that(first).isEqualTo(false)
                that(second).isEqualTo(false)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(artistFollowerNonMembersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(artistFollowerNonMembersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores classified typed set membership entries as Redis set members") {
            val fixture = suspendSubject()
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(songReactionCache.key(songId, accountId), returnsAs = enumMember<SongReaction>()) {
                calls++
                SongReaction.LIKE
            }
            val second = fixture.cache(songReactionCache.key(songId, accountId), returnsAs = enumMember<SongReaction>()) {
                calls++
                SongReaction.DISLIKE
            }

            expect {
                that(first).isEqualTo(SongReaction.LIKE)
                that(second).isEqualTo(SongReaction.LIKE)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(songReactionKey(songId, SongReaction.LIKE))).isEqualTo("set")
                that(fixture.commands.sismember(songReactionKey(songId, SongReaction.LIKE), accountId.toString()))
                    .isEqualTo(true)
                that(fixture.commands.exists(songReactionKey(songId, SongReaction.DISLIKE))).isEqualTo(0)
                that(fixture.commands.exists(songReactionKey(songId, SongReaction.NONE))).isEqualTo(0)
            }
        }

        test("invalidates classified typed set membership entries across all values") {
            val fixture = suspendSubject()
            val songId = 13
            val accountId = 7
            val otherAccountId = 8

            fixture.cache(songReactionCache.key(songId, accountId), returnsAs = enumMember<SongReaction>()) {
                SongReaction.DISLIKE
            }
            fixture.cache(songReactionCache.key(songId, otherAccountId), returnsAs = enumMember<SongReaction>()) {
                SongReaction.LIKE
            }

            fixture.cache.invalidate(songReactionCache.key(songId, accountId), returnsAs = enumMember<SongReaction>())

            expect {
                that(fixture.commands.sismember(songReactionKey(songId, SongReaction.DISLIKE), accountId.toString()))
                    .isEqualTo(false)
                that(fixture.commands.sismember(songReactionKey(songId, SongReaction.LIKE), otherAccountId.toString()))
                    .isEqualTo(true)
            }
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
