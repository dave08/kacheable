package kacheable

import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

val RedisBlockingTypedHashStorageSpec by testSuite {
    testFixture {
        RedisFixture.start().blockingFixture()
    } asContextForEach {
        test("stores typed hash-map cache entries as Redis hash fields") {
            val artistId = 3
            val songId = 7
            var calls = 0

            val first = cache(
                redisBlockingArtistSongsCache(artistId, songId),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = cache(
                redisBlockingArtistSongsCache(artistId, songId),
            ) {
                calls++
                Bar(songId, "Changed")
            }

            expect {
                that(first).isEqualTo(Bar(songId, "Seven"))
                that(second).isEqualTo(Bar(songId, "Seven"))
                that(calls).isEqualTo(1)
                that(commands.type(redisBlockingArtistCacheKey(artistId))).isEqualTo("hash")
                that(commands.hget(redisBlockingArtistCacheKey(artistId), songId.toString()))
                    .isEqualTo("""{"id":7,"name":"Seven"}""")
            }
        }

        test("invalidates typed hash-map cache entries by primary key") {
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            cache(redisBlockingArtistSongsCache(artistId, firstSongId)) {
                Bar(firstSongId, "Seven")
            }
            cache(redisBlockingArtistSongsCache(artistId, secondSongId)) {
                Bar(secondSongId, "Eight")
            }

            cache.invalidate(redisBlockingArtistSongsCache.partition(artistId))

            expectThat(commands.exists(redisBlockingArtistCacheKey(artistId))).isEqualTo(0)
        }

        test("partially invalidates layered hash fields by selected secondary key part") {
            val artistId = 13

            cache(redisBlockingArtistSongsByLocaleCache(artistId, 1, "en")) {
                Bar(artistId, "Page EN 1")
            }
            cache(redisBlockingArtistSongsByLocaleCache(artistId, 2, "en")) {
                Bar(artistId, "Page EN 2")
            }
            cache(redisBlockingArtistSongsByLocaleCache(artistId, 1, "he")) {
                Bar(artistId, "Page HE 1")
            }

            cache.invalidate(
                redisBlockingArtistSongsByLocaleCache.matching(artistId, redisBlockingLocaleKey("en")),
            )

            expect {
                that(commands.hget("blocking-artist-page-cache:$artistId", "1,he"))
                    .isEqualTo("""{"id":13,"name":"Page HE 1"}""")
                that(commands.hget("blocking-artist-page-cache:$artistId", "1,en")).isEqualTo(null)
                that(commands.hget("blocking-artist-page-cache:$artistId", "2,en")).isEqualTo(null)
            }
        }
    }
}
