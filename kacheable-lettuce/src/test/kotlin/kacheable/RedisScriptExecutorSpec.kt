package kacheable

import com.github.dave08.kacheable.redis.RedisScriptExecutor
import de.infix.testBalloon.framework.core.testSuite
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.ScriptOutputType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val RedisScriptExecutorSpec by testSuite {
    for (api in ScriptApi.entries) {
        testWithRedis("$api reloads an evicted script without repeating its mutation") {
            val scripts = RedisScriptExecutor(connection)
            assertEquals(1L, api.execute(scripts, INCREMENT))
            assertEquals(listOf(true), commands.scriptExists(commands.digest(INCREMENT)))

            commands.scriptFlush()
            assertEquals(listOf(false), commands.scriptExists(commands.digest(INCREMENT)))

            assertEquals(2L, api.execute(scripts, INCREMENT))
            assertEquals("2", commands.get("counter"))
            assertEquals(listOf(true), commands.scriptExists(commands.digest(INCREMENT)))
        }
        testWithRedis("$api does not replay a script that mutates then fails") {
            val scripts = RedisScriptExecutor(connection)

            assertFailsWith<RedisCommandExecutionException> { api.execute(scripts, INCREMENT_THEN_FAIL) }

            assertEquals("1", commands.get("counter"))
        }
    }
}

private enum class ScriptApi {
    Suspending, Blocking;

    suspend fun execute(scripts: RedisScriptExecutor, script: String): Long? = when (this) {
        Suspending -> scripts.execute(script, ScriptOutputType.INTEGER, arrayOf("counter"))
        Blocking -> scripts.executeBlocking(script, ScriptOutputType.INTEGER, arrayOf("counter"))
    }
}

private const val INCREMENT = "return redis.call('INCR', KEYS[1])"
private const val INCREMENT_THEN_FAIL = """
redis.call('INCR', KEYS[1])
return redis.error_reply('Failure after mutation')
"""
