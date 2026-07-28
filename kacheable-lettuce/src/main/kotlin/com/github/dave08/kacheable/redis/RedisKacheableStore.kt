package com.github.dave08.kacheable.redis

import com.github.dave08.kacheable.internal.CacheLoadTimeoutException
import com.github.dave08.kacheable.store.AdmissionAwareDistributedSingleFlightStore
import com.github.dave08.kacheable.store.DistributedLoadLease
import com.github.dave08.kacheable.store.HashFieldEntry
import com.github.dave08.kacheable.store.KacheableStore
import com.github.dave08.kacheable.store.StoreMutationScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.GetExArgs
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
    private val deleteScanCount: Long = 1000,
    private val deleteMode: RedisDeleteMode = RedisDeleteMode.Unlink,
) : KacheableStore, AdmissionAwareDistributedSingleFlightStore {
    override suspend fun delete(key: String) {
        if (!key.contains("*"))
            deleteKeys(key)
        else {
            val commands = conn.coroutines()
            var cursor: ScanCursor = ScanCursor.INITIAL
            do {
                val result = checkNotNull(commands.scan(cursor, ScanArgs().match(key).limit(deleteScanCount)))
                result.keys.chunked(deleteFromPatternInChunksOf).forEach { keys ->
                    if (keys.isNotEmpty()) deleteKeys(*keys.toTypedArray())
                }
                cursor = result
            } while (!cursor.isFinished)
        }
    }

    private suspend fun deleteKeys(vararg keys: String) {
        when (deleteMode) {
            RedisDeleteMode.Del -> conn.coroutines().del(*keys)
            RedisDeleteMode.Unlink -> conn.coroutines().unlink(*keys)
        }
    }

    override suspend fun set(key: String, value: String) {
        conn.coroutines().set(key, value)
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        conn.coroutines().hset(key, field, value)
    }

    override suspend fun get(key: String): String? = conn.coroutines().get(key)

    override suspend fun getHashValue(key: String, field: String): String? = conn.coroutines().hget(key, field)

    override suspend fun deleteHashValue(key: String, field: String) {
        conn.coroutines().hdel(key, field)
    }

    override suspend fun scanHashFields(keyPattern: String): List<HashFieldEntry> {
        val commands = conn.coroutines()
        val entries = mutableListOf<HashFieldEntry>()
        var keyCursor: ScanCursor = ScanCursor.INITIAL
        do {
            val keyScan = checkNotNull(commands.scan(keyCursor, ScanArgs().match(keyPattern).limit(deleteScanCount)))
            keyScan.keys.forEach { key ->
                if (commands.type(key) != "hash") return@forEach
                var hashCursor: ScanCursor = ScanCursor.INITIAL
                do {
                    val hashScan = checkNotNull(commands.hscan(key, hashCursor, ScanArgs().limit(deleteScanCount)))
                    entries += hashScan.map.map { (field, value) ->
                        HashFieldEntry(key, field, value)
                    }
                    hashCursor = hashScan
                } while (!hashCursor.isFinished)
            }
            keyCursor = keyScan
        } while (!keyCursor.isFinished)
        return entries
    }

    override suspend fun writeHashFields(
        entries: Iterable<HashFieldEntry>,
        expiry: Duration?,
    ) {
        val commands = conn.coroutines()
        val touchedKeys = mutableSetOf<String>()
        entries.chunked(500).forEach { chunk ->
            chunk.forEach { entry ->
                commands.hset(entry.key, entry.field, entry.value)
                touchedKeys += entry.key
            }
        }
        expiry?.let { duration ->
            touchedKeys.forEach { key -> commands.pexpire(key, duration.inWholeMilliseconds) }
        }
    }

    override suspend fun deleteHashValuesMatching(key: String, fieldPattern: String) {
        val commands = conn.coroutines()
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result = checkNotNull(commands.hscan(key, cursor, ScanArgs().match(fieldPattern).limit(deleteScanCount)))
            val fields = result.map.keys.toList()
            if (fields.isNotEmpty()) {
                commands.hdel(key, *fields.toTypedArray())
            }
            cursor = result
        } while (!cursor.isFinished)
    }

    override suspend fun deleteSetMember(key: String, member: String) {
        conn.coroutines().srem(key, member)
    }

    override suspend fun addSetMember(key: String, member: String) {
        conn.coroutines().sadd(key, member)
    }

    override suspend fun isSetMember(key: String, member: String): Boolean =
        conn.coroutines().sismember(key, member) == true

    override suspend fun setExpire(key: String, expiry: Duration) {
        conn.coroutines().pexpire(key, expiry.inWholeMilliseconds)
    }

    override suspend fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        conn.coroutines().psetex(key, expiry.inWholeMilliseconds, value)
    }

    override suspend fun setHashValueWithExpire(key: String, field: String, value: String, expiry: Duration) {
        conn.coroutines().eval<Long>(
            SET_HASH_VALUE_WITH_EXPIRE_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            field,
            value,
            expiry.inWholeMilliseconds.toString(),
        )
    }

    override suspend fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        try {
            conn.coroutines().getex(key, GetExArgs.Builder.px(expiry.inWholeMilliseconds))
        } catch (_: RedisCommandExecutionException) {
            conn.coroutines().get(key)?.also {
                conn.coroutines().pexpire(key, expiry.inWholeMilliseconds)
            }
        }

    override suspend fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration?,
        cacheFalse: Boolean,
    ) {
        val targetIndex =
            when {
                isMember -> "1"
                cacheFalse -> "2"
                else -> "0"
            }
        conn.coroutines().eval<Long>(
            REPLACE_SET_MEMBERSHIP_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(membersKey, nonMembersKey),
            member,
            targetIndex,
            expiry?.inWholeMilliseconds?.toString().orEmpty(),
        )
    }

    override suspend fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration?,
    ) {
        val keys = listOf(targetKey) + candidateKeys.filterNot { it == targetKey }
        conn.coroutines().eval<Long>(
            REPLACE_CLASSIFIED_MEMBERSHIP_SCRIPT,
            ScriptOutputType.INTEGER,
            keys.toTypedArray(),
            member,
            expiry?.inWholeMilliseconds?.toString().orEmpty(),
        )
    }

    override suspend fun mutate(block: suspend StoreMutationScope.() -> Unit) {
        val recording = RedisStoreMutationRecording(deleteMode)
        recording.block()
        val operations = recording.operations
        if (operations.isEmpty()) return

        val script = operations.toLuaScript()
        conn.coroutines().eval<Long>(
            script.source,
            ScriptOutputType.INTEGER,
            script.keys.toTypedArray(),
            *script.args.toTypedArray(),
        )
    }

    override suspend fun <R> runWithDistributedSingleFlight(
        key: String,
        lockLease: Duration,
        waitTimeout: Duration,
        pollInterval: Duration,
        readCached: suspend () -> R?,
        loadAndSave: suspend () -> R,
    ): R {
        val deadline = TimeSource.Monotonic.markNow() + waitTimeout

        while (deadline.hasNotPassedNow()) {
            readCached()?.let { return it }

            val lease = tryAcquireDistributedLoadLease(key, lockLease)
            if (lease != null) {
                return try {
                    readCached() ?: loadAndSave()
                } finally {
                    lease.release()
                }
            }

            delay(pollInterval)
        }

        readCached()?.let { return it }
        throw CacheLoadTimeoutException("Timed out waiting for distributed single-flight lock for '$key'.")
    }

    override suspend fun tryAcquireDistributedLoadLease(
        key: String,
        lockLease: Duration,
    ): DistributedLoadLease? {
        val lockKey = "__kacheable:singleflight:$key"
        val ownerToken = java.util.UUID.randomUUID().toString()
        if (!tryAcquireLock(lockKey, ownerToken, lockLease)) return null
        return RedisDistributedLoadLease(lockKey, ownerToken)
    }

    private suspend fun tryAcquireLock(lockKey: String, ownerToken: String, lockLease: Duration): Boolean =
        conn.coroutines().set(lockKey, ownerToken, SetArgs.Builder.nx().px(lockLease.inWholeMilliseconds)) == "OK"

    private suspend fun releaseLock(lockKey: String, ownerToken: String) {
        conn.coroutines().eval<Long>(
            RELEASE_SINGLE_FLIGHT_LOCK_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(lockKey),
            ownerToken,
        )
    }

    private inner class RedisDistributedLoadLease(
        private val lockKey: String,
        private val ownerToken: String,
    ) : DistributedLoadLease {
        private val released = java.util.concurrent.atomic.AtomicBoolean()

        override suspend fun release() {
            if (released.compareAndSet(false, true)) {
                releaseLock(lockKey, ownerToken)
            }
        }
    }
}

private const val SET_HASH_VALUE_WITH_EXPIRE_SCRIPT = """
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
redis.call('PEXPIRE', KEYS[1], ARGV[3])
return 1
"""

private const val REPLACE_SET_MEMBERSHIP_SCRIPT = """
redis.call('SREM', KEYS[1], ARGV[1])
redis.call('SREM', KEYS[2], ARGV[1])
local targetIndex = tonumber(ARGV[2])
if targetIndex ~= nil and targetIndex > 0 then
  redis.call('SADD', KEYS[targetIndex], ARGV[1])
  if ARGV[3] ~= '' then
    redis.call('PEXPIRE', KEYS[targetIndex], ARGV[3])
  end
end
return 1
"""

private const val REPLACE_CLASSIFIED_MEMBERSHIP_SCRIPT = """
for i = 1, #KEYS do
  redis.call('SREM', KEYS[i], ARGV[1])
end
redis.call('SADD', KEYS[1], ARGV[1])
if ARGV[2] ~= '' then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
return 1
"""

private const val RELEASE_SINGLE_FLIGHT_LOCK_SCRIPT = """
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
"""

private class RedisStoreMutationRecording(
    private val deleteMode: RedisDeleteMode,
) : StoreMutationScope {
    val operations = mutableListOf<RedisMutationOperation>()

    override suspend fun delete(key: String) {
        require(!key.contains("*")) { "Pattern deletes are not supported inside atomic Redis mutations." }
        operations += RedisMutationOperation.Delete(key, deleteMode)
    }

    override suspend fun deleteHashValue(key: String, field: String) {
        operations += RedisMutationOperation.DeleteHashValue(key, field)
    }

    override suspend fun deleteSetMember(key: String, member: String) {
        operations += RedisMutationOperation.DeleteSetMember(key, member)
    }

    override suspend fun set(key: String, value: String) {
        operations += RedisMutationOperation.Set(key, value)
    }

    override suspend fun setHashValue(key: String, field: String, value: String) {
        operations += RedisMutationOperation.SetHashValue(key, field, value)
    }

    override suspend fun addSetMember(key: String, member: String) {
        operations += RedisMutationOperation.AddSetMember(key, member)
    }

    override suspend fun setExpire(key: String, expiry: Duration) {
        operations += RedisMutationOperation.SetExpire(key, expiry)
    }
}

private sealed interface RedisMutationOperation {
    fun append(script: RedisMutationScriptBuilder)

    data class Delete(val key: String, val deleteMode: RedisDeleteMode) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('${deleteMode.command}', ${script.key(key)})")
        }
    }

    data class DeleteHashValue(val key: String, val field: String) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('HDEL', ${script.key(key)}, ${script.arg(field)})")
        }
    }

    data class DeleteSetMember(val key: String, val member: String) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('SREM', ${script.key(key)}, ${script.arg(member)})")
        }
    }

    data class Set(val key: String, val value: String) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('SET', ${script.key(key)}, ${script.arg(value)})")
        }
    }

    data class SetHashValue(val key: String, val field: String, val value: String) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('HSET', ${script.key(key)}, ${script.arg(field)}, ${script.arg(value)})")
        }
    }

    data class AddSetMember(val key: String, val member: String) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('SADD', ${script.key(key)}, ${script.arg(member)})")
        }
    }

    data class SetExpire(val key: String, val expiry: Duration) : RedisMutationOperation {
        override fun append(script: RedisMutationScriptBuilder) {
            script.line("redis.call('PEXPIRE', ${script.key(key)}, ${script.arg(expiry.inWholeMilliseconds.toString())})")
        }
    }
}

private val RedisDeleteMode.command: String
    get() = when (this) {
        RedisDeleteMode.Del -> "DEL"
        RedisDeleteMode.Unlink -> "UNLINK"
    }

private data class RedisMutationScript(
    val source: String,
    val keys: List<String>,
    val args: List<String>,
)

private class RedisMutationScriptBuilder {
    private val lines = mutableListOf<String>()
    val keys = mutableListOf<String>()
    val args = mutableListOf<String>()

    fun key(value: String): String {
        keys += value
        return "KEYS[${keys.size}]"
    }

    fun arg(value: String): String {
        args += value
        return "ARGV[${args.size}]"
    }

    fun line(value: String) {
        lines += value
    }

    fun build(): RedisMutationScript =
        RedisMutationScript(
            source = (lines + "return 1").joinToString(separator = "\n"),
            keys = keys,
            args = args,
        )
}

private fun List<RedisMutationOperation>.toLuaScript(): RedisMutationScript =
    RedisMutationScriptBuilder().also { builder ->
        forEach { operation -> operation.append(builder) }
    }.build()
