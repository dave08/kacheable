@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.invalidate
import com.github.dave08.kacheable.invoke
import com.github.dave08.kacheable.keyPart
import com.github.dave08.kacheable.value
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

val RedisTypedHashStorageSpec by testSuite {
    testFixture {
        RedisFixture.start().suspendFixture()
    } asContextForEach {
        test("stores typed hash-map cache entries as Redis hash fields") {
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = cache(
                redisArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = cache(
                redisArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(commands.type(redisArtistCacheKey(artistId))).isEqualTo("hash")
                that(commands.hget(redisArtistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by primary key") {
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            cache(redisArtistSongsCache.key(artistId, firstSongId), returnsAs = value<Bar>()) {
                Bar(firstSongId, "Seven")
            }
            cache(redisArtistSongsCache.key(artistId, secondSongId), returnsAs = value<Bar>()) {
                Bar(secondSongId, "Eight")
            }

            cache.invalidate(redisArtistSongsCache.keyPart(artistId))

            expectThat(commands.exists(redisArtistCacheKey(artistId))).isEqualTo(0)
        }

        test("partially invalidates layered hash fields by selected secondary key part") {
            val artistId = 13

            cache(redisArtistSongsByLocaleCache.key(artistId, 1, "en"), returnsAs = value<Bar>()) {
                Bar(artistId, "Page EN 1")
            }
            cache(redisArtistSongsByLocaleCache.key(artistId, 2, "en"), returnsAs = value<Bar>()) {
                Bar(artistId, "Page EN 2")
            }
            cache(redisArtistSongsByLocaleCache.key(artistId, 1, "he"), returnsAs = value<Bar>()) {
                Bar(artistId, "Page HE 1")
            }

            cache.invalidate(redisArtistSongsByLocaleCache.keyPart(redisArtistPagePrimaryKey(artistId), redisLocaleKey("en")))

            expect {
                that(commands.hget("artist-page-cache:$artistId", "1,he")).isEqualTo("""{"id":13,"name":"Page HE 1"}""")
                that(commands.hget("artist-page-cache:$artistId", "1,en")).isEqualTo(null)
                that(commands.hget("artist-page-cache:$artistId", "2,en")).isEqualTo(null)
            }
        }
    }
}
