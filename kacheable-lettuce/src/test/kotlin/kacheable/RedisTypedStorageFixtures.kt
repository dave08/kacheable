@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.CacheStorage
import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.key
import com.github.dave08.kacheable.mainKey
import com.github.dave08.kacheable.plus

val redisArtistCache = mainKey<Int>("artist-cache", storedAs = CacheStorage.HashMap)
val redisSongKey = key<Int>()
val redisArtistSongsCache = redisArtistCache + redisSongKey
val redisArtistFollowersCache = mainKey<Int>("artist-followers-cache", storedAs = CacheStorage.Set)
val redisFollowerAccountKey = key<Int>()
val redisArtistFollowerCache = redisArtistFollowersCache + redisFollowerAccountKey
val redisSongReactionsCache = mainKey<Int>("song-reaction-cache", storedAs = CacheStorage.Set)
val redisReactingAccountKey = key<Int>()
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

val redisBlockingArtistCache = mainKey<Int>("blocking-artist-cache", storedAs = CacheStorage.HashMap)
val redisBlockingSongKey = key<Int>()
val redisBlockingArtistSongsCache = redisBlockingArtistCache + redisBlockingSongKey
val redisBlockingArtistFollowersCache =
    mainKey<Int>("blocking-artist-followers-cache", storedAs = CacheStorage.Set)
val redisBlockingFollowerAccountKey = key<Int>()
val redisBlockingArtistFollowerCache = redisBlockingArtistFollowersCache + redisBlockingFollowerAccountKey
val redisBlockingSongReactionsCache =
    mainKey<Int>("blocking-song-reaction-cache", storedAs = CacheStorage.Set)
val redisBlockingReactingAccountKey = key<Int>()
val redisBlockingSongReactionCache = redisBlockingSongReactionsCache + redisBlockingReactingAccountKey

enum class RedisBlockingSongReaction {
    LIKE,
    DISLIKE,
    NONE,
}

fun redisBlockingArtistCacheKey(artistId: Int): String = "blocking-artist-cache:$artistId"

fun redisBlockingArtistFollowersKey(artistId: Int): String = "blocking-artist-followers-cache:$artistId"

fun redisBlockingSongReactionKey(songId: Int, reaction: RedisBlockingSongReaction): String =
    "blocking-song-reaction-cache:$songId:${reaction.name}"
