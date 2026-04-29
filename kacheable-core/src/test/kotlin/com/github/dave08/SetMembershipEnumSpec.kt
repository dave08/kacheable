@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val SetMembershipEnumSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("classified set membership caches enum values") {
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) {
                calls++
                SongLike.LIKE
            }
            val second = cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) {
                calls++
                SongLike.DISLIKE
            }

            assertEquals(SongLike.LIKE, first)
            assertEquals(SongLike.LIKE, second)
            assertEquals(1, calls)
            store.assertSetMember(songLikeKey(songId, SongLike.LIKE), accountId)
            store.assertSetMissing(songLikeKey(songId, SongLike.DISLIKE))
            store.assertSetMissing(songLikeKey(songId, SongLike.NONE))
        }

        test("classified set membership can use custom enum value names") {
            val songId = 4
            val accountId = 9
            val resultView = enumMember<SongLike> { like -> like.name.lowercase() }

            val result = cache(songLikeCache.key(songId, accountId), returnsAs = resultView) {
                SongLike.DISLIKE
            }

            assertEquals(SongLike.DISLIKE, result)
            store.assertSetMember("song-like-cache:$songId:dislike", accountId)
            store.assertSetMissing(songLikeKey(songId, SongLike.DISLIKE))
        }

        test("classified set membership only writes configured enum values") {
            val songId = 4
            val accountId = 11
            val cacheableValues = enumMember(values = listOf(SongLike.LIKE, SongLike.DISLIKE))

            val failure = kotlin.runCatching {
                cache(songLikeCache.key(songId, accountId), returnsAs = cacheableValues) {
                    SongLike.NONE
                }
            }

            assertTrue(failure.isFailure)
            store.assertSetMissing(songLikeKey(songId, SongLike.NONE))
        }

        test("classified set membership invalidation removes a member from all enum value sets") {
            val songId = 3
            val accountId = 7
            val otherAccountId = 8

            cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) { SongLike.DISLIKE }
            cache(songLikeCache.key(songId, otherAccountId), returnsAs = enumMember<SongLike>()) { SongLike.LIKE }

            cache.invalidate(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>())

            store.assertSetDoesNotContain(songLikeKey(songId, SongLike.DISLIKE), accountId)
            store.assertSetMember(songLikeKey(songId, SongLike.LIKE), otherAccountId)
        }

        test("classified set membership grouped invalidation removes all enum value sets") {
            val songId = 3
            val likeAccountId = 7
            val noneAccountId = 8

            cache(songLikeCache.key(songId, likeAccountId), returnsAs = enumMember<SongLike>()) { SongLike.LIKE }
            cache(songLikeCache.key(songId, noneAccountId), returnsAs = enumMember<SongLike>()) { SongLike.NONE }

            cache.invalidate(songLikeCache.keyPart(songId), returnsAs = enumMember<SongLike>())

            store.assertSetMissing(songLikeKey(songId, SongLike.LIKE))
            store.assertSetMissing(songLikeKey(songId, SongLike.DISLIKE))
            store.assertSetMissing(songLikeKey(songId, SongLike.NONE))
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking cache supports classified set membership return views") {
            val songId = 3
            val accountId = 7
            var calls = 0

            val first = cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) {
                calls++
                SongLike.DISLIKE
            }
            val second = cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) {
                calls++
                SongLike.LIKE
            }

            assertEquals(SongLike.DISLIKE, first)
            assertEquals(SongLike.DISLIKE, second)
            assertEquals(1, calls)
            store.assertSetMember(songLikeKey(songId, SongLike.DISLIKE), accountId)
        }

        test("blocking classified set invalidation removes a cached member from all enum sets") {
            val songId = 3
            val accountId = 7
            val otherAccountId = 8

            cache(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>()) { SongLike.DISLIKE }
            cache(songLikeCache.key(songId, otherAccountId), returnsAs = enumMember<SongLike>()) { SongLike.LIKE }

            cache.invalidate(songLikeCache.key(songId, accountId), returnsAs = enumMember<SongLike>())

            store.assertSetDoesNotContain(songLikeKey(songId, SongLike.DISLIKE), accountId)
            store.assertSetMember(songLikeKey(songId, SongLike.LIKE), otherAccountId)
        }
    }
}
