package kacheable

import com.github.dave08.kacheable.blocking.redis.RedisBlockingKacheableStore
import com.github.dave08.kacheable.redis.RedisKacheableStore
import de.infix.testBalloon.framework.core.testSuite
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo
import kotlin.time.Duration.Companion.minutes

val RedisStoreMutationSpec by testSuite {
    test("suspend Redis store mutates grouped writes atomically through one batch") {
        RedisFixture.start().use { fixture ->
            val store = RedisKacheableStore(fixture.connection)

            store.mutate {
                addSetMember("song-reactions:7:LIKE", "15")
                deleteSetMember("song-reactions:7:DISLIKE", "15")
                setExpire("song-reactions:7:LIKE", 5.minutes)
            }

            expectThat(fixture.commands.smembers("song-reactions:7:LIKE"))
                .containsExactlyInAnyOrder("15")
            expectThat(fixture.commands.smembers("song-reactions:7:DISLIKE"))
                .isEqualTo(emptySet<String>())
            expectThat(fixture.commands.ttl("song-reactions:7:LIKE"))
                .isEqualTo(5.minutes.inWholeSeconds)
        }
    }

    test("blocking Redis store mutates grouped writes atomically through one batch") {
        RedisFixture.start().use { fixture ->
            val store = RedisBlockingKacheableStore(fixture.connection)

            store.mutate {
                set("songs:7", """{"id":7}""")
                setExpire("songs:7", 3.minutes)
                delete("songs:old")
            }

            expectThat(fixture.commands.get("songs:7")).isEqualTo("""{"id":7}""")
            expectThat(fixture.commands.ttl("songs:7")).isEqualTo(3.minutes.inWholeSeconds)
            expectThat(fixture.commands.exists("songs:old")).isEqualTo(0)
        }
    }
}
