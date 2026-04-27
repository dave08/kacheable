@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.cache
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.isMember
import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val SetMembershipBooleanSpec by testSuite {
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
    }
}
