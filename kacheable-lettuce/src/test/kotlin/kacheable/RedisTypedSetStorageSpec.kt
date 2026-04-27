@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.assertions.isEqualTo

val RedisTypedSetStorageSpec by testSuite {
    testFixture {
        RedisFixture.start().suspendFixture()
    } asContextForEach {
        test("stores typed set membership entries as Redis set members") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }
            val second = cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }

            expect {
                that(first).isEqualTo(true)
                that(second).isEqualTo(true)
                that(calls).isEqualTo(1)
                that(commands.type(redisArtistFollowersKey(artistId))).isEqualTo("set")
                that(commands.sismember(redisArtistFollowersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores false typed set membership entries in the non-member set") {
            val artistId = 13
            val accountId = 7
            var calls = 0

            val first = cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }
            val second = cache(redisArtistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }

            expect {
                that(first).isEqualTo(false)
                that(second).isEqualTo(false)
                that(calls).isEqualTo(1)
                that(commands.type(redisArtistFollowerNonMembersKey(artistId))).isEqualTo("set")
                that(commands.sismember(redisArtistFollowerNonMembersKey(artistId), accountId.toString()))
                    .isEqualTo(true)
            }
        }

        test("stores classified typed set membership entries as Redis set members") {
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = cache(
                redisSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisSongReaction>(),
            ) {
                calls++
                RedisSongReaction.LIKE
            }
            val second = cache(
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
                that(commands.type(redisSongReactionKey(songId, RedisSongReaction.LIKE))).isEqualTo("set")
                that(commands.sismember(redisSongReactionKey(songId, RedisSongReaction.LIKE), accountId.toString()))
                    .isEqualTo(true)
                that(commands.exists(redisSongReactionKey(songId, RedisSongReaction.DISLIKE))).isEqualTo(0)
                that(commands.exists(redisSongReactionKey(songId, RedisSongReaction.NONE))).isEqualTo(0)
            }
        }

        test("invalidates classified typed set membership entries across all values") {
            val songId = 13
            val accountId = 7
            val otherAccountId = 8

            cache(redisSongReactionCache.key(songId, accountId), returnsAs = enumMember<RedisSongReaction>()) {
                RedisSongReaction.DISLIKE
            }
            cache(redisSongReactionCache.key(songId, otherAccountId), returnsAs = enumMember<RedisSongReaction>()) {
                RedisSongReaction.LIKE
            }

            cache.invalidate(
                redisSongReactionCache.key(songId, accountId),
                returnsAs = enumMember<RedisSongReaction>(),
            )

            expect {
                that(commands.sismember(redisSongReactionKey(songId, RedisSongReaction.DISLIKE), accountId.toString()))
                    .isEqualTo(false)
                that(commands.sismember(redisSongReactionKey(songId, RedisSongReaction.LIKE), otherAccountId.toString()))
                    .isEqualTo(true)
            }
        }
    }
}
