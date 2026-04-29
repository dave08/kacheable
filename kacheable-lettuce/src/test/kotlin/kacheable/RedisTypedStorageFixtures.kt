@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.entryKey
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus
import com.github.dave08.kacheable.times

val redisArtistCache = entryKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
val redisSongKey = keyPart<Int>()
val redisArtistSongsCache = redisArtistCache + redisSongKey
val redisPagingKey = keyPart<Int>("page")
val redisLocaleKey = keyPart<String>("locale")
val redisArtistSongsByLocaleCache = entryKey(
    "artist-page-cache",
    keyPart<Int>() * (redisPagingKey + redisLocaleKey),
    storedAs = CacheStorage.HashMap,
)
val redisArtistFollowersCache = entryKey<Int>("artist-followers-cache", storedAs = CacheStorage.Set)
val redisFollowerAccountKey = keyPart<Int>()
val redisArtistFollowerCache = redisArtistFollowersCache + redisFollowerAccountKey
val redisSongReactionsCache = entryKey<Int>("song-reaction-cache", storedAs = CacheStorage.Set)
val redisReactingAccountKey = keyPart<Int>()
val redisSongReactionCache = redisSongReactionsCache + redisReactingAccountKey

enum class RedisSongReaction {
    LIKE,
    DISLIKE,
    NONE,
}

fun redisArtistCacheKey(artistId: Int): String = "artist-cache:$artistId"

fun redisArtistFollowersKey(artistId: Int): String = "artist-followers-cache:$artistId"

fun redisArtistFollowerNonMembersKey(artistId: Int): String =
    "${redisArtistFollowersKey(artistId)}:__kacheable_non_members"

fun redisSongReactionKey(songId: Int, reaction: RedisSongReaction): String =
    "song-reaction-cache:$songId:${reaction.name}"

val redisBlockingArtistCache = entryKey<Int>("blocking-artist-cache", storedAs = CacheStorage.HashMap)
val redisBlockingSongKey = keyPart<Int>()
val redisBlockingArtistSongsCache = redisBlockingArtistCache + redisBlockingSongKey
val redisBlockingPagingKey = keyPart<Int>("page")
val redisBlockingLocaleKey = keyPart<String>("locale")
val redisBlockingArtistSongsByLocaleCache = entryKey(
    "blocking-artist-page-cache",
    keyPart<Int>() * (redisBlockingPagingKey + redisBlockingLocaleKey),
    storedAs = CacheStorage.HashMap,
)
val redisBlockingArtistFollowersCache =
    entryKey<Int>("blocking-artist-followers-cache", storedAs = CacheStorage.Set)
val redisBlockingFollowerAccountKey = keyPart<Int>()
val redisBlockingArtistFollowerCache = redisBlockingArtistFollowersCache + redisBlockingFollowerAccountKey
val redisBlockingSongReactionsCache =
    entryKey<Int>("blocking-song-reaction-cache", storedAs = CacheStorage.Set)
val redisBlockingReactingAccountKey = keyPart<Int>()
val redisBlockingSongReactionCache = redisBlockingSongReactionsCache + redisBlockingReactingAccountKey

enum class RedisBlockingSongReaction {
    LIKE,
    DISLIKE,
    NONE,
}

fun redisBlockingArtistCacheKey(artistId: Int): String = "blocking-artist-cache:$artistId"

fun redisBlockingArtistFollowersKey(artistId: Int): String = "blocking-artist-followers-cache:$artistId"

fun redisBlockingArtistFollowerNonMembersKey(artistId: Int): String =
    "${redisBlockingArtistFollowersKey(artistId)}:__kacheable_non_members"

fun redisBlockingSongReactionKey(songId: Int, reaction: RedisBlockingSongReaction): String =
    "blocking-song-reaction-cache:$songId:${reaction.name}"
