@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal val artistFollowersCache = mainKey<Int>("artist-followers-cache", storedAs = CacheStorage.Set)
internal val followerAccountKey = keyPart<Int>()
internal val artistFollowerCache = artistFollowersCache + followerAccountKey
internal val songLikesCache = mainKey<Int>("song-like-cache", storedAs = CacheStorage.Set)
internal val listenerAccountKey = keyPart<Int>()
internal val songLikeCache = songLikesCache + listenerAccountKey

internal enum class SongLike {
    LIKE,
    DISLIKE,
    NONE,
}

internal fun artistFollowersKey(artistId: Int): String = "artist-followers-cache:$artistId"

internal fun artistFollowerNonMembersKey(artistId: Int): String =
    "${artistFollowersKey(artistId)}:__kacheable_non_members"

internal fun songLikeKey(songId: Int, like: SongLike): String = "song-like-cache:$songId:${like.name}"

internal fun InMemoryKacheableStore.assertSetMember(
    key: String,
    member: Any,
) {
    assertTrue(sets[key]?.contains(member.toString()) == true)
}

internal fun InMemoryKacheableStore.assertSetDoesNotContain(
    key: String,
    member: Any,
) {
    assertFalse(sets[key]?.contains(member.toString()) == true)
}

internal fun InMemoryKacheableStore.assertSetMissing(key: String) {
    assertNull(sets[key])
}

internal fun InMemoryBlockingKacheableStore.assertSetMember(
    key: String,
    member: Any,
) {
    assertTrue(sets[key]?.contains(member.toString()) == true)
}

internal fun InMemoryBlockingKacheableStore.assertSetDoesNotContain(
    key: String,
    member: Any,
) {
    assertFalse(sets[key]?.contains(member.toString()) == true)
}

internal fun InMemoryBlockingKacheableStore.assertSetMissing(key: String) {
    assertNull(sets[key])
}
