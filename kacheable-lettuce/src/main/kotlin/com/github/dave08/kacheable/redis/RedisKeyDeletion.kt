package com.github.dave08.kacheable.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines

/** Shared deletion mechanics; each storage namespace selects which scanned keys it owns. */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class RedisKeyDeletion(
    private val connection: StatefulRedisConnection<String, String>,
    private val chunkSize: Int,
    private val scanCount: Long,
    private val mode: RedisDeleteMode,
) {
    suspend fun delete(pattern: String, includes: (String) -> Boolean) {
        if (!pattern.contains('*')) {
            if (includes(pattern)) deleteKeys(arrayOf(pattern))
            return
        }
        var cursor = ScanCursor.INITIAL
        do {
            val page = checkNotNull(connection.coroutines().scan(cursor, ScanArgs().match(pattern).limit(scanCount)))
            page.keys.filter(includes).chunked(chunkSize).forEach { deleteKeys(it.toTypedArray()) }
            cursor = page
        } while (!cursor.isFinished)
    }

    fun deleteBlocking(pattern: String, includes: (String) -> Boolean) {
        if (!pattern.contains('*')) {
            if (includes(pattern)) deleteKeysBlocking(arrayOf(pattern))
            return
        }
        var cursor = ScanCursor.INITIAL
        do {
            val page = connection.sync().scan(cursor, ScanArgs().match(pattern).limit(scanCount))
            page.keys.filter(includes).chunked(chunkSize).forEach { deleteKeysBlocking(it.toTypedArray()) }
            cursor = page
        } while (!cursor.isFinished)
    }

    private suspend fun deleteKeys(keys: Array<String>) {
        when (mode) {
            RedisDeleteMode.Del -> connection.coroutines().del(*keys)
            RedisDeleteMode.Unlink -> connection.coroutines().unlink(*keys)
        }
    }

    private fun deleteKeysBlocking(keys: Array<String>) {
        when (mode) {
            RedisDeleteMode.Del -> connection.sync().del(*keys)
            RedisDeleteMode.Unlink -> connection.sync().unlink(*keys)
        }
    }
}
