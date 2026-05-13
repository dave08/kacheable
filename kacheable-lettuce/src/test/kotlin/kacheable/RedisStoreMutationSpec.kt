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

    test("suspend Redis store can write a hash value with expiry in one semantic operation") {
        RedisFixture.start().use { fixture ->
            val store = RedisKacheableStore(fixture.connection)

            store.setHashValueWithExpire("songs", "9", """{"id":9}""", 2.minutes)

            expectThat(fixture.commands.hget("songs", "9")).isEqualTo("""{"id":9}""")
            expectThat(fixture.commands.ttl("songs")).isEqualTo(2.minutes.inWholeSeconds)
        }
    }

    test("suspend Redis store can replace boolean membership state semantically") {
        RedisFixture.start().use { fixture ->
            val store = RedisKacheableStore(fixture.connection)
            fixture.commands.sadd("artist-followers:3:__kacheable_non_members", "7")

            store.replaceSetMembership(
                member = "7",
                membersKey = "artist-followers:3",
                nonMembersKey = "artist-followers:3:__kacheable_non_members",
                isMember = true,
                expiry = 4.minutes,
            )

            expectThat(fixture.commands.sismember("artist-followers:3", "7")).isEqualTo(true)
            expectThat(fixture.commands.sismember("artist-followers:3:__kacheable_non_members", "7")).isEqualTo(false)
            expectThat(fixture.commands.ttl("artist-followers:3")).isEqualTo(4.minutes.inWholeSeconds)
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

    test("blocking Redis store can replace classified membership state semantically") {
        RedisFixture.start().use { fixture ->
            val store = RedisBlockingKacheableStore(fixture.connection)
            fixture.commands.sadd("song-like-cache:7:DISLIKE", "11")
            fixture.commands.sadd("song-like-cache:7:NONE", "11")

            store.replaceClassifiedMembership(
                member = "11",
                targetKey = "song-like-cache:7:LIKE",
                candidateKeys = listOf(
                    "song-like-cache:7:LIKE",
                    "song-like-cache:7:DISLIKE",
                    "song-like-cache:7:NONE",
                ),
                expiry = 6.minutes,
            )

            expectThat(fixture.commands.sismember("song-like-cache:7:LIKE", "11")).isEqualTo(true)
            expectThat(fixture.commands.sismember("song-like-cache:7:DISLIKE", "11")).isEqualTo(false)
            expectThat(fixture.commands.sismember("song-like-cache:7:NONE", "11")).isEqualTo(false)
            expectThat(fixture.commands.ttl("song-like-cache:7:LIKE")).isEqualTo(6.minutes.inWholeSeconds)
        }
    }
}
