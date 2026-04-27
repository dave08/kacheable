@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.enumMember
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val artistFollowersCache = mainKey<Int>("artist-followers-cache", storedAs = CacheStorage.Set)
private val followerAccountKey = key<Int>()
private val artistFollowerCache = artistFollowersCache + followerAccountKey
private val songLikesCache = mainKey<Int>("song-like-cache", storedAs = CacheStorage.Set)
private val listenerAccountKey = key<Int>()
private val songLikeCache = songLikesCache + listenerAccountKey

private enum class SongLike {
    LIKE,
    DISLIKE,
    NONE,
}

private fun artistFollowersKey(artistId: Int): String = "artist-followers-cache:$artistId"

private fun artistFollowerNonMembersKey(artistId: Int): String =
    "${artistFollowersKey(artistId)}:__kacheable_non_members"

private fun songLikeKey(songId: Int, like: SongLike): String = "song-like-cache:$songId:${like.name}"

val SetMembershipReturnViewSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("set membership caches true results as Redis-style set members") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }
            val second = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }

            assertTrue(first)
            assertTrue(second)
            assertEquals(1, calls)
            store.assertSetMember(artistFollowersKey(artistId), accountId)
            store.assertSetMissing(artistFollowerNonMembersKey(artistId))
        }

        test("set membership caches false results in the internal non-member set") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }
            val second = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }

            assertFalse(first)
            assertFalse(second)
            assertEquals(1, calls)
            store.assertSetMissing(artistFollowersKey(artistId))
            store.assertSetMember(artistFollowerNonMembersKey(artistId), accountId)
        }

        test("set membership can skip caching false results") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember(cacheFalse = false)) {
                calls++
                false
            }
            cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember(cacheFalse = false)) {
                calls++
                false
            }

            assertEquals(2, calls)
            store.assertSetMissing(artistFollowerNonMembersKey(artistId))
        }

        test("set membership invalidation removes a single member from both member sets") {
            val artistId = 3
            val accountId = 7
            val otherAccountId = 8

            cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) { true }
            cache(artistFollowerCache.key(artistId, otherAccountId), returnsAs = isMember()) { true }

            cache.invalidate(artistFollowerCache.key(artistId, accountId))

            store.assertSetDoesNotContain(artistFollowersKey(artistId), accountId)
            store.assertSetMember(artistFollowersKey(artistId), otherAccountId)
        }

        test("set membership grouped invalidation removes all membership state for the main key") {
            val artistId = 3
            val memberAccountId = 7
            val nonMemberAccountId = 8

            cache(artistFollowerCache.key(artistId, memberAccountId), returnsAs = isMember()) { true }
            cache(artistFollowerCache.key(artistId, nonMemberAccountId), returnsAs = isMember()) { false }

            cache.invalidate(artistFollowerCache.keyPart(artistId))

            store.assertSetMissing(artistFollowersKey(artistId))
            store.assertSetMissing(artistFollowerNonMembersKey(artistId))
        }

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
        test("blocking cache supports set membership return views") {
            val artistId = 3
            val accountId = 7
            var calls = 0

            val first = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                true
            }
            val second = cache(artistFollowerCache.key(artistId, accountId), returnsAs = isMember()) {
                calls++
                false
            }

            assertTrue(first)
            assertTrue(second)
            assertEquals(1, calls)
            store.assertSetMember(artistFollowersKey(artistId), accountId)
        }

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
    }
}

private fun InMemoryKacheableStore.assertSetMember(
    key: String,
    member: Any,
) {
    assertTrue(sets[key]?.contains(member.toString()) == true)
}

private fun InMemoryKacheableStore.assertSetDoesNotContain(
    key: String,
    member: Any,
) {
    assertFalse(sets[key]?.contains(member.toString()) == true)
}

private fun InMemoryKacheableStore.assertSetMissing(key: String) {
    assertNull(sets[key])
}

private fun InMemoryBlockingKacheableStore.assertSetMember(
    key: String,
    member: Any,
) {
    assertTrue(sets[key]?.contains(member.toString()) == true)
}
