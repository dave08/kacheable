package kacheable

import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.assertions.isEqualTo

val RedisBlockingTypedSetStorageSpec by testSuite {
    testFixture {
        RedisFixture.start().blockingFixture()
    } asContextForEach {
        test("stores typed set membership entries as Redis set members") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = cache(
                redisBlockingArtistFollowerCache(artistId, accountId),
            ) {
                calls++
                true
            }
            val second = cache(
                redisBlockingArtistFollowerCache(artistId, accountId),
            ) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(commands.type(redisBlockingArtistFollowersKey(artistId))).isEqualTo("set")
                that(commands.sismember(redisBlockingArtistFollowersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores false typed set membership entries in the non-member set") {
            val artistId = 13
            val accountId = 7
            var calls = 0

            val first = cache(
                redisBlockingArtistFollowerCache(artistId, accountId),
            ) {
                calls++
                false
            }
            val second = cache(
                redisBlockingArtistFollowerCache(artistId, accountId),
            ) {
                calls++
                true
            }

            expect {
                that(first).isEqualTo(false)
                that(second).isEqualTo(false)
                that(calls).isEqualTo(1)
                that(commands.type(redisBlockingArtistFollowerNonMembersKey(artistId))).isEqualTo("set")
                that(commands.sismember(redisBlockingArtistFollowerNonMembersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores classified typed set membership entries as Redis set members") {
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = cache(
                redisBlockingSongReactionCache(songId, accountId),
            ) {
                calls++
                RedisBlockingSongReaction.NONE
            }
            val second = cache(
                redisBlockingSongReactionCache(songId, accountId),
            ) {
                calls++
                RedisBlockingSongReaction.LIKE
            }

            expect {
                that(first).isEqualTo(RedisBlockingSongReaction.NONE)
                that(second).isEqualTo(RedisBlockingSongReaction.NONE)
                that(calls).isEqualTo(1)
                that(commands.type(redisBlockingSongReactionKey(songId, RedisBlockingSongReaction.NONE)))
                    .isEqualTo("set")
                that(
                    commands.sismember(
                        redisBlockingSongReactionKey(songId, RedisBlockingSongReaction.NONE),
                        accountId.toString(),
                    ),
                ).isEqualTo(true)
            }
        }
    }
}
