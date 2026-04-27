package com.github.dave08

import de.infix.testBalloon.framework.core.testSuite
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

val StoreSemanticOperationsSpec by testSuite {
    testFixture {
        SuspendCacheFixture()
    } asContextForEach {
        test("replaceSetMembership moves a member to the positive membership set and applies expiry") {
            val artistId = 3
            val accountId = 7
            val membersKey = artistFollowersKey(artistId)
            val nonMembersKey = artistFollowerNonMembersKey(artistId)

            store.addSetMember(nonMembersKey, accountId.toString())

            store.replaceSetMembership(
                member = accountId.toString(),
                membersKey = membersKey,
                nonMembersKey = nonMembersKey,
                isMember = true,
                expiry = 5.minutes,
            )

            store.assertSetMember(membersKey, accountId)
            store.assertSetDoesNotContain(nonMembersKey, accountId)
            assertEquals(5.minutes, store.expiries[membersKey])
        }

        test("replaceSetMembership can skip writing a cached false entry") {
            val artistId = 5
            val accountId = 9
            val membersKey = artistFollowersKey(artistId)
            val nonMembersKey = artistFollowerNonMembersKey(artistId)

            store.addSetMember(membersKey, accountId.toString())

            store.replaceSetMembership(
                member = accountId.toString(),
                membersKey = membersKey,
                nonMembersKey = nonMembersKey,
                isMember = false,
                cacheFalse = false,
            )

            store.assertSetDoesNotContain(membersKey, accountId)
            store.assertSetMissing(nonMembersKey)
        }

        test("replaceClassifiedMembership removes stale classifications before writing the new one") {
            val songId = 7
            val accountId = 11
            val candidateKeys = SongLike.entries.map { songLikeKey(songId, it) }

            store.addSetMember(songLikeKey(songId, SongLike.DISLIKE), accountId.toString())
            store.addSetMember(songLikeKey(songId, SongLike.NONE), accountId.toString())

            store.replaceClassifiedMembership(
                member = accountId.toString(),
                targetKey = songLikeKey(songId, SongLike.LIKE),
                candidateKeys = candidateKeys,
                expiry = 3.minutes,
            )

            store.assertSetMember(songLikeKey(songId, SongLike.LIKE), accountId)
            store.assertSetDoesNotContain(songLikeKey(songId, SongLike.DISLIKE), accountId)
            store.assertSetDoesNotContain(songLikeKey(songId, SongLike.NONE), accountId)
            assertEquals(3.minutes, store.expiries[songLikeKey(songId, SongLike.LIKE)])
        }
    }

    testFixture {
        BlockingCacheFixture()
    } asContextForEach {
        test("blocking replaceSetMembership uses the same default mutation semantics") {
            val artistId = 13
            val accountId = 17
            val membersKey = artistFollowersKey(artistId)
            val nonMembersKey = artistFollowerNonMembersKey(artistId)

            store.addSetMember(nonMembersKey, accountId.toString())

            store.replaceSetMembership(
                member = accountId.toString(),
                membersKey = membersKey,
                nonMembersKey = nonMembersKey,
                isMember = true,
                expiry = 2.minutes,
            )

            store.assertSetMember(membersKey, accountId)
            store.assertSetDoesNotContain(nonMembersKey, accountId)
            assertEquals(2.minutes, store.expiries[membersKey])
        }

        test("blocking replaceClassifiedMembership removes old classifications before writing the new one") {
            val songId = 19
            val accountId = 23
            val candidateKeys = SongLike.entries.map { songLikeKey(songId, it) }

            store.addSetMember(songLikeKey(songId, SongLike.LIKE), accountId.toString())

            store.replaceClassifiedMembership(
                member = accountId.toString(),
                targetKey = songLikeKey(songId, SongLike.NONE),
                candidateKeys = candidateKeys,
            )

            store.assertSetDoesNotContain(songLikeKey(songId, SongLike.LIKE), accountId)
            store.assertSetMember(songLikeKey(songId, SongLike.NONE), accountId)
        }
    }
}
