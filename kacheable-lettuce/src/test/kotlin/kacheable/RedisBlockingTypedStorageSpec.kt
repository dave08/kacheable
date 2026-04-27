@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.value
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

val RedisBlockingTypedStorageSpec by testSuite {
    testFixture {
        RedisFixture.start()
    } asContextForEach {
        test("stores typed hash-map cache entries as Redis hash fields") {
            val fixture = blockingSubject()
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = fixture.cache(
                redisBlockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = fixture.cache(
                redisBlockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisBlockingArtistCacheKey(artistId))).isEqualTo("hash")
                that(fixture.commands.hget(redisBlockingArtistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by main key") {
            val fixture = blockingSubject()
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            fixture.cache(redisBlockingArtistSongsCache.key(artistId, firstSongId), returnsAs = value<Bar>()) {
                Bar(firstSongId, "Seven")
            }
            fixture.cache(redisBlockingArtistSongsCache.key(artistId, secondSongId), returnsAs = value<Bar>()) {
                Bar(secondSongId, "Eight")
            }

            fixture.cache.invalidate(redisBlockingArtistSongsCache.keyPart(artistId))

            expectThat(fixture.commands.exists(redisBlockingArtistCacheKey(artistId))).isEqualTo(0)
        }

        test("stores typed set membership entries as Redis set members") {
            val fixture = blockingSubject()
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(
                redisBlockingArtistFollowerCache.key(artistId, accountId),
                returnsAs = isMember(),
            ) {
                calls++
                true
            }
            val second = fixture.cache(
                redisBlockingArtistFollowerCache.key(artistId, accountId),
                returnsAs = isMember(),
            ) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisBlockingArtistFollowersKey(artistId))).isEqualTo("set")
                that(fixture.commands.sismember(redisBlockingArtistFollowersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores classified typed set membership entries as Redis set members") {
            val fixture = blockingSubject()
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = fixture.cache(
                redisBlockingSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisBlockingSongReaction>(),
            ) {
                calls++
                RedisBlockingSongReaction.NONE
            }
            val second = fixture.cache(
                redisBlockingSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisBlockingSongReaction>(),
            ) {
                calls++
                RedisBlockingSongReaction.LIKE
            }

            expect {
                that(first).isEqualTo(RedisBlockingSongReaction.NONE)
                that(second).isEqualTo(RedisBlockingSongReaction.NONE)
                that(calls).isEqualTo(1)
                that(fixture.commands.type(redisBlockingSongReactionKey(songId, RedisBlockingSongReaction.NONE)))
                    .isEqualTo("set")
                that(
                    fixture.commands.sismember(
                        redisBlockingSongReactionKey(songId, RedisBlockingSongReaction.NONE),
                        accountId.toString(),
                    ),
                ).isEqualTo(true)
            }
        }
    }
}
