package com.github.dave08.kacheable.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisNoScriptException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import java.util.concurrent.ConcurrentHashMap

/** Executes standalone scripts; queued MULTI operations must use EVAL instead. */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class RedisScriptExecutor(private val connection: StatefulRedisConnection<String, String>) {
    private val digests = ConcurrentHashMap<String, String>()

    /** Generated mutation shapes are unbounded; do not retain their sources or pin them with SCRIPT LOAD. */
    suspend fun <T> executeUncached(
        script: String,
        output: ScriptOutputType,
        keys: Array<String>,
        vararg args: String,
    ): T? = connection.coroutines().eval(script, output, keys, *args)

    suspend fun <T> execute(
        script: String,
        output: ScriptOutputType,
        keys: Array<String>,
        vararg args: String,
    ): T? {
        val digest = digests[script] ?: load(script)
        return try {
            connection.coroutines().evalsha(digest, output, keys, *args)
        } catch (_: RedisNoScriptException) {
            // NOSCRIPT guarantees the command did not execute; other failures must not be replayed.
            connection.coroutines().evalsha(load(script), output, keys, *args)
        }
    }

    fun <T> executeBlocking(
        script: String,
        output: ScriptOutputType,
        keys: Array<String>,
        vararg args: String,
    ): T? {
        val digest = digests[script] ?: loadBlocking(script)
        return try {
            connection.sync().evalsha(digest, output, keys, *args)
        } catch (_: RedisNoScriptException) {
            connection.sync().evalsha(loadBlocking(script), output, keys, *args)
        }
    }

    private suspend fun load(script: String): String =
        checkNotNull(connection.coroutines().scriptLoad(script)).also { digests[script] = it }

    private fun loadBlocking(script: String): String =
        connection.sync().scriptLoad(script).also { digests[script] = it }
}
