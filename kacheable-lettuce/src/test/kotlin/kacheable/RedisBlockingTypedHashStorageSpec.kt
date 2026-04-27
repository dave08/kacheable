@file:OptIn(ExperimentalKacheableApi::class)

package kacheable

import com.github.dave08.kacheable.ExperimentalKacheableApi
import com.github.dave08.kacheable.blocking.invalidate
import com.github.dave08.kacheable.blocking.invoke
import com.github.dave08.kacheable.value
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
                redisBlockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
            ) {
                calls++
                Bar(songId, "Seven")
            }
            val second = cache(
                redisBlockingArtistSongsCache.key(artistId, songId),
                returnsAs = value<Bar>(),
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

        test("invalidates typed hash-map cache entries by main key") {
            val artistId = 13
            val firstSongId = 7
            val secondSongId = 8

            cache(redisBlockingArtistSongsCache.key(artistId, firstSongId), returnsAs = value<Bar>()) {
                Bar(firstSongId, "Seven")
            }
            cache(redisBlockingArtistSongsCache.key(artistId, secondSongId), returnsAs = value<Bar>()) {
                Bar(secondSongId, "Eight")
            }

            cache.invalidate(redisBlockingArtistSongsCache.keyPart(artistId))

            expectThat(commands.exists(redisBlockingArtistCacheKey(artistId))).isEqualTo(0)
        }
    }
}
