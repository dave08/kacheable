@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.cacheKey
import com.github.dave08.kacheable.matchableKeyPart
import com.github.dave08.kacheable.partitioned
import com.github.dave08.kacheable.returns
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.plus

val redisArtistKey = keyPart<Int>("artist")
val redisSongKey = keyPart<Int>("song")
val redisArtistSongsCache = cacheKey(
    "artist-cache",
    returns<Bar>(),
    key = partitioned(partition = redisArtistKey, key = redisSongKey),
)
val redisPagingKey = keyPart<Int>("page")
val redisLocaleKey = matchableKeyPart<String>("locale")
val redisArtistPagePrimaryKey = keyPart<Int>("artist")
val redisArtistSongsByLocaleCache = cacheKey(
    "artist-page-cache",
    returns<Bar>(),
    key = partitioned(partition = redisArtistPagePrimaryKey, key = redisPagingKey + redisLocaleKey),
)
val redisFollowerArtistKey = keyPart<Int>("artist")
val redisFollowerAccountKey = keyPart<Int>("account")
val redisArtistFollowerCache = cacheKey(
    "artist-followers-cache",
    returns<Boolean>(),
    key = partitioned(partition = redisFollowerArtistKey, key = redisFollowerAccountKey),
)
val redisSongReactionSongKey = keyPart<Int>("song")
val redisReactingAccountKey = keyPart<Int>("account")
val redisSongReactionCache = cacheKey(
    "song-reaction-cache",
    returns<RedisSongReaction>(),
    key = partitioned(partition = redisSongReactionSongKey, key = redisReactingAccountKey),
)

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

val redisBlockingArtistKey = keyPart<Int>("artist")
val redisBlockingSongKey = keyPart<Int>("song")
val redisBlockingArtistSongsCache = cacheKey(
    "blocking-artist-cache",
    returns<Bar>(),
    key = partitioned(partition = redisBlockingArtistKey, key = redisBlockingSongKey),
)
val redisBlockingPagingKey = keyPart<Int>("page")
val redisBlockingLocaleKey = matchableKeyPart<String>("locale")
val redisBlockingArtistPagePrimaryKey = keyPart<Int>("artist")
val redisBlockingArtistSongsByLocaleCache = cacheKey(
    "blocking-artist-page-cache",
    returns<Bar>(),
    key = partitioned(partition = redisBlockingArtistPagePrimaryKey, key = redisBlockingPagingKey + redisBlockingLocaleKey),
)
val redisBlockingFollowerArtistKey = keyPart<Int>("artist")
val redisBlockingFollowerAccountKey = keyPart<Int>("account")
val redisBlockingArtistFollowerCache = cacheKey(
    "blocking-artist-followers-cache",
    returns<Boolean>(),
    key = partitioned(partition = redisBlockingFollowerArtistKey, key = redisBlockingFollowerAccountKey),
)
val redisBlockingSongReactionSongKey = keyPart<Int>("song")
val redisBlockingReactingAccountKey = keyPart<Int>("account")
val redisBlockingSongReactionCache = cacheKey(
    "blocking-song-reaction-cache",
    returns<RedisBlockingSongReaction>(),
    key = partitioned(partition = redisBlockingSongReactionSongKey, key = redisBlockingReactingAccountKey),
)

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
