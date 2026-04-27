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

    test("suspend Redis store can write a value with expiry in one operation") {
        RedisFixture.start().use { fixture ->
            val store = RedisKacheableStore(fixture.connection)

            store.setValueWithExpire("songs:9", """{"id":9}""", 2.minutes)

            expectThat(fixture.commands.get("songs:9")).isEqualTo("""{"id":9}""")
            expectThat(fixture.commands.ttl("songs:9")).isEqualTo(2.minutes.inWholeSeconds)
        }
    }

    test("blocking Redis store can refresh expiry while reading a string value") {
        RedisFixture.start().use { fixture ->
            val store = RedisBlockingKacheableStore(fixture.connection)
            fixture.commands.set("songs:10", """{"id":10}""")
            fixture.commands.pexpire("songs:10", 500)

            val result = store.getValueRefreshingExpire("songs:10", 1.minutes)

            expectThat(result).isEqualTo("""{"id":10}""")
            expectThat(fixture.commands.ttl("songs:10")).isEqualTo(1.minutes.inWholeSeconds)
        }
    }
}
