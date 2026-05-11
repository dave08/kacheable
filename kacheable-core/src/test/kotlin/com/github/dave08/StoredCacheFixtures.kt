@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.store.InMemoryKacheableStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

@Serializable
data class TestSong(val id: Int, val title: String)

@Serializable
internal data class CachedSong(val id: Int, val title: String)

internal data class ResultPage(val offset: Int, val limit: Int)

internal data class UnserializablePodcast(val id: Int, val title: String)

@Serializable
internal data class CachedImageVariant(val url: String, val width: Int, val height: Int)

internal data class ImageVariantRequest(val format: String, val width: Int)

internal enum class SongLike {
    LIKE,
    DISLIKE,
    NONE,
}

internal fun artistFollowersKey(artistId: Int): String = "artist-followers-cache:$artistId"

internal fun artistFollowerNonMembersKey(artistId: Int): String =
    "${artistFollowersKey(artistId)}:__kacheable_non_members"

internal fun songLikeKey(songId: Int, like: SongLike): String = "song-like-cache:$songId:${like.name}"

internal fun InMemoryKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}

internal fun InMemoryKacheableStore.assertHashMissing(key: String) {
    assertNull(hashMap[key])
}

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

internal suspend fun InMemoryKacheableStore.assertStringValue(
    key: String,
    expectedValue: String,
) {
    assertEquals(expectedValue, get(key))
}

internal suspend fun InMemoryKacheableStore.assertStringValueMissing(key: String) {
    assertNull(get(key))
}

internal fun InMemoryBlockingKacheableStore.assertHashField(
    key: String,
    field: Any,
    expectedValue: String,
) {
    assertEquals(expectedValue, hashMap[key]?.get(field.toString()))
}

internal fun InMemoryBlockingKacheableStore.assertHashMissing(key: String) {
    assertNull(hashMap[key])
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

internal fun InMemoryBlockingKacheableStore.assertStringValueMissing(key: String) {
    assertNull(map[key])
}
