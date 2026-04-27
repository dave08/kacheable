@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

val RedisTypedStorageSpec by testSuite {
    testFixture {
        RedisFixture.start()
    } asContextForEach {
        test("stores typed hash-map cache entries as Redis hash fields") {
            val fixture = suspendSubject()
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = fixture.cache(
                redisArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = fixture.cache(
                redisArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisArtistCacheKey(artistId))).isEqualTo("hash")
                that(fixture.commands.hget(redisArtistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by main key") {
            val fixture = suspendSubject()
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            fixture.cache(redisArtistSongsCache.key(artistId, firstSongId), returnsAs = value<Bar>()) {
                Bar(firstSongId, "Seven")
            }
            fixture.cache(redisArtistSongsCache.key(artistId, secondSongId), returnsAs = value<Bar>()) {
                Bar(secondSongId, "Eight")
            }

            fixture.cache.invalidate(redisArtistSongsCache.keyPart(artistId))

            expectThat(fixture.commands.exists(redisArtistCacheKey(artistId))).isEqualTo(0)
        }

        test("stores typed set membership entries as Redis set members") {
            val fixture = suspendSubject()
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }
            val second = fixture.cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisArtistFollowersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(redisArtistFollowersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores false typed set membership entries in the non-member set") {
            val fixture = suspendSubject()
            val artistId = 13
            val accountId = 7
            var calls = 0

            val first = fixture.cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }
            val second = fixture.cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }

            expect {
                that(first).isEqualTo(false)
                that(second).isEqualTo(false)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisArtistFollowerNonMembersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(redisArtistFollowerNonMembersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores classified typed set membership entries as Redis set members") {
            val fixture = suspendSubject()
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(
                redisSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisSongReaction>(),
            ) {
                calls++
                RedisSongReaction.LIKE
            }
            val second = fixture.cache(
                redisSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisSongReaction>(),
            ) {
                calls++
                RedisSongReaction.DISLIKE
            }

            expect {
                that(first).isEqualTo(RedisSongReaction.LIKE)
                that(second).isEqualTo(RedisSongReaction.LIKE)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisSongReactionKey(songId, RedisSongReaction.LIKE))).isEqualTo("set")
                that(fixture.commands.sismember(redisSongReactionKey(songId, RedisSongReaction.LIKE), accountId.toString()))
                    .isEqualTo(true)
                that(fixture.commands.exists(redisSongReactionKey(songId, RedisSongReaction.DISLIKE))).isEqualTo(0)
                that(fixture.commands.exists(redisSongReactionKey(songId, RedisSongReaction.NONE))).isEqualTo(0)
            }
        }

        test("invalidates classified typed set membership entries across all values") {
            val fixture = suspendSubject()
            val songId = 13
            val accountId = 7
            val otherAccountId = 8

            fixture.cache(redisSongReactionCache.key(songId, accountId), returnsAs = enumMember<RedisSongReaction>()) {
                RedisSongReaction.DISLIKE
            }
            fixture.cache(redisSongReactionCache.key(songId, otherAccountId), returnsAs = enumMember<RedisSongReaction>()) {
                RedisSongReaction.LIKE
            }

            fixture.cache.invalidate(
                redisSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisSongReaction>(),
            )

            expect {
                that(fixture.commands.sismember(redisSongReactionKey(songId, RedisSongReaction.DISLIKE), accountId.toString()))
                    .isEqualTo(false)
                that(fixture.commands.sismember(redisSongReactionKey(songId, RedisSongReaction.LIKE), otherAccountId.toString()))
                    .isEqualTo(true)
            }
        }
    }
}
