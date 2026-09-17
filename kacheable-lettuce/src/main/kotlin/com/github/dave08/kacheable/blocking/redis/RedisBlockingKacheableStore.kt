package com.github.dave08.kacheable.blocking.redis

import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.redis.RedisBlockingVersionedHashes
import com.github.dave08.kacheable.redis.RedisKeyDeletion
import com.github.dave08.kacheable.redis.RedisScriptExecutor
import com.github.dave08.kacheable.redis.GET_VALUES_REFRESHING_EXPIRE_SCRIPT
import com.github.dave08.kacheable.redis.refreshingValuesByRequestedKey
import com.github.dave08.kacheable.redis.isVersionedRedisKey
import com.github.dave08.kacheable.redis.ordinaryRedisKey
import com.github.dave08.kacheable.blocking.store.BlockingKacheableStore
import com.github.dave08.kacheable.blocking.store.BlockingStoreMutationScope
import com.github.dave08.kacheable.redis.RedisDeleteMode
import com.github.dave08.kacheable.store.HashPublishManyResult
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.VersionedHashValue
import io.lettuce.core.GetExArgs
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlin.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RedisBlockingKacheableStore(
    private val conn: StatefulRedisConnection<String, String>,
    private val deleteFromPatternInChunksOf: Int = 20,
    private val deleteScanCount: Long = 1000,
    private val deleteMode: RedisDeleteMode = RedisDeleteMode.Unlink,
) : BlockingKacheableStore,
    BlockingVersionedHashOperations {
    private val scripts = RedisScriptExecutor(conn)
    private val deletion = RedisKeyDeletion(conn, deleteFromPatternInChunksOf, deleteScanCount, deleteMode)
    private val versionedHashes = RedisBlockingVersionedHashes(conn, deleteFromPatternInChunksOf, deleteScanCount, deleteMode)
    private val mutationLock = ReentrantLock()

    override fun deleteHashes(keyPattern: String) = versionedHashes.deleteHashes(keyPattern)

    override fun openHash(key: String, expiry: Duration?): HashVersion = versionedHashes.openHash(key, expiry)

    override fun readHashSnapshot(
        key: String,
        expiry: Duration?,
        fields: List<String>?,
    ): HashReadSnapshot? = versionedHashes.readHashSnapshot(key, expiry, fields)

    override fun readHash(
        key: String,
        version: HashVersion,
        fields: List<String>?,
    ): Map<String, String>? = versionedHashes.readHash(key, version, fields)

    override fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? =
        versionedHashes.readHashMetadata(key, version)

    override fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult = versionedHashes.publishHash(key, version, field, value, expiry, ifAbsent, metadata)

    override fun publishHashes(
        key: String,
        version: HashVersion,
        values: Map<String, VersionedHashValue>,
        expiry: Duration?,
        ifAbsent: Boolean,
    ): HashPublishManyResult = versionedHashes.publishHashes(key, version, values, expiry, ifAbsent)

    override fun delete(key: String) =
        deletion.deleteBlocking(ordinaryRedisKey(key)) { !isVersionedRedisKey(it) }

    override fun set(key: String, value: String) {
        conn.sync().set(ordinaryRedisKey(key), value)
    }

    override fun setHashValue(key: String, field: String, value: String) {
        conn.sync().hset(ordinaryRedisKey(key), field, value)
    }

    override fun get(key: String): String? = conn.sync().get(ordinaryRedisKey(key))

    override fun getValues(keys: List<String>): Map<String, String> {
        val redisKeys = keys.map(::ordinaryRedisKey)
        if (redisKeys.isEmpty()) return emptyMap()

        return buildMap {
            conn.sync().mget(*redisKeys.toTypedArray()).forEach { entry ->
                if (entry.hasValue()) put(entry.key, entry.value)
            }
        }
    }

    override fun getHashValue(key: String, field: String): String? = conn.sync().hget(ordinaryRedisKey(key), field)

    override fun getHashValues(key: String, fields: List<String>): Map<String, String> {
        val redisKey = ordinaryRedisKey(key)
        if (fields.isEmpty()) return emptyMap()

        return buildMap {
            conn.sync().hmget(redisKey, *fields.toTypedArray()).forEach { entry ->
                if (entry.hasValue()) put(entry.key, entry.value)
            }
        }
    }

    override fun deleteHashValue(key: String, field: String) {
        conn.sync().hdel(ordinaryRedisKey(key), field)
    }

    override fun deleteHashValuesMatching(key: String, fieldPattern: String) {
        val commands = conn.sync()
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result = commands.hscan(ordinaryRedisKey(key), cursor, ScanArgs().match(fieldPattern).limit(deleteScanCount))
            val fields = result.map.keys.toList()
            if (fields.isNotEmpty()) {
                commands.hdel(ordinaryRedisKey(key), *fields.toTypedArray())
            }
            cursor = result
        } while (!cursor.isFinished)
    }

    override fun deleteSetMember(key: String, member: String) {
        conn.sync().srem(ordinaryRedisKey(key), member)
    }

    override fun addSetMember(key: String, member: String) {
        conn.sync().sadd(ordinaryRedisKey(key), member)
    }

    override fun isSetMember(key: String, member: String): Boolean = conn.sync().sismember(ordinaryRedisKey(key), member)

    override fun areSetMembers(key: String, members: List<String>): Set<String> {
        val redisKey = ordinaryRedisKey(key)
        if (members.isEmpty()) return emptySet()

        val commands = conn.sync()
        return try {
            val membership = commands.smismember(redisKey, *members.toTypedArray())
            buildSet {
                members.forEachIndexed { index, member ->
                    if (membership[index]) add(member)
                }
            }
        } catch (_: RedisCommandExecutionException) {
            members.filterTo(linkedSetOf()) { member -> commands.sismember(redisKey, member) }
        }
    }

    override fun setExpire(key: String, expiry: Duration) {
        conn.sync().pexpire(ordinaryRedisKey(key), expiry.inWholeMilliseconds)
    }

    override fun setValueWithExpire(key: String, value: String, expiry: Duration) {
        conn.sync().psetex(ordinaryRedisKey(key), expiry.inWholeMilliseconds, value)
    }

    override fun setHashValueWithExpire(key: String, field: String, value: String, expiry: Duration) {
        scripts.executeBlocking<Long>(
            SET_HASH_VALUE_WITH_EXPIRE_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(ordinaryRedisKey(key)),
            field,
            value,
            expiry.inWholeMilliseconds.toString(),
        )
    }

    override fun getValueRefreshingExpire(key: String, expiry: Duration): String? =
        getValueRefreshingExpireRedisKey(ordinaryRedisKey(key), expiry)

    override fun getValuesRefreshingExpire(keys: List<String>, expiry: Duration): Map<String, String> {
        val redisKeys = keys.map(::ordinaryRedisKey)
        if (redisKeys.isEmpty()) return emptyMap()

        val values = checkNotNull(scripts.executeBlocking<List<String>>(
            GET_VALUES_REFRESHING_EXPIRE_SCRIPT,
            ScriptOutputType.MULTI,
            redisKeys.toTypedArray(),
            expiry.inWholeMilliseconds.toString(),
        ))
        return refreshingValuesByRequestedKey(keys, values)
    }

    private fun getValueRefreshingExpireRedisKey(redisKey: String, expiry: Duration): String? =
        try {
            conn.sync().getex(redisKey, GetExArgs.Builder.px(expiry.inWholeMilliseconds))
        } catch (_: RedisCommandExecutionException) {
            conn.sync().get(redisKey)?.also {
                conn.sync().pexpire(redisKey, expiry.inWholeMilliseconds)
            }
        }

    override fun replaceSetMembership(
        member: String,
        membersKey: String,
        nonMembersKey: String,
        isMember: Boolean,
        expiry: Duration?,
        cacheFalse: Boolean,
    ) {
        mutationLock.withLock {
            val targetIndex =
                when {
                    isMember -> "1"
                    cacheFalse -> "2"
                    else -> "0"
                }
            scripts.executeBlocking<Long>(
                REPLACE_SET_MEMBERSHIP_SCRIPT,
                ScriptOutputType.INTEGER,
                arrayOf(ordinaryRedisKey(membersKey), ordinaryRedisKey(nonMembersKey)),
                member,
                targetIndex,
                expiry?.inWholeMilliseconds?.toString().orEmpty(),
            )
        }
    }

    override fun replaceClassifiedMembership(
        member: String,
        targetKey: String,
        candidateKeys: List<String>,
        expiry: Duration?,
    ) {
        mutationLock.withLock {
            val keys = (listOf(targetKey) + candidateKeys.filterNot { it == targetKey }).map(::ordinaryRedisKey)
            scripts.executeBlocking<Long>(
                REPLACE_CLASSIFIED_MEMBERSHIP_SCRIPT,
                ScriptOutputType.INTEGER,
                keys.toTypedArray(),
                member,
                expiry?.inWholeMilliseconds?.toString().orEmpty(),
            )
        }
    }

    override fun mutate(block: BlockingStoreMutationScope.() -> Unit) {
        val recording = RedisBlockingStoreMutationRecording(deleteMode)
        recording.block()
        val operations = recording.operations
        if (operations.isEmpty()) return

        mutationLock.withLock {
            val commands = conn.sync()
            var transactionOpen = false
            try {
                commands.multi()
                transactionOpen = true
                operations.forEach { it.execute(commands) }
                val results = commands.exec()
                transactionOpen = false
                results.forEach { result -> if (result is Throwable) throw result }
            } catch (t: Throwable) {
                if (transactionOpen) {
                    try {
                        commands.discard()
                    } catch (discardFailure: Throwable) {
                        t.addSuppressed(discardFailure)
                    }
                }
                throw t
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

private class RedisBlockingStoreMutationRecording(
    private val deleteMode: RedisDeleteMode,
) : BlockingStoreMutationScope {
    val operations = mutableListOf<RedisBlockingMutationOperation>()

    override fun delete(key: String) {
        require(!key.contains("*")) { "Pattern deletes are not supported inside atomic Redis mutations." }
        operations += RedisBlockingMutationOperation.Delete(ordinaryRedisKey(key), deleteMode)
    }

    override fun deleteHashValue(key: String, field: String) {
        operations += RedisBlockingMutationOperation.DeleteHashValue(ordinaryRedisKey(key), field)
    }

    override fun deleteSetMember(key: String, member: String) {
        operations += RedisBlockingMutationOperation.DeleteSetMember(ordinaryRedisKey(key), member)
    }

    override fun set(key: String, value: String) {
        operations += RedisBlockingMutationOperation.Set(ordinaryRedisKey(key), value)
    }

    override fun setHashValue(key: String, field: String, value: String) {
        operations += RedisBlockingMutationOperation.SetHashValue(ordinaryRedisKey(key), field, value)
    }

    override fun addSetMember(key: String, member: String) {
        operations += RedisBlockingMutationOperation.AddSetMember(ordinaryRedisKey(key), member)
    }

    override fun setExpire(key: String, expiry: Duration) {
        operations += RedisBlockingMutationOperation.SetExpire(ordinaryRedisKey(key), expiry)
    }
}

private sealed interface RedisBlockingMutationOperation {
    fun execute(commands: RedisCommands<String, String>)

    data class Delete(val key: String, val deleteMode: RedisDeleteMode) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            when (deleteMode) {
                RedisDeleteMode.Del -> commands.del(key)
                RedisDeleteMode.Unlink -> commands.unlink(key)
            }
        }
    }

    data class DeleteHashValue(val key: String, val field: String) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.hdel(key, field)
        }
    }

    data class DeleteSetMember(val key: String, val member: String) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.srem(key, member)
        }
    }

    data class Set(val key: String, val value: String) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.set(key, value)
        }
    }

    data class SetHashValue(val key: String, val field: String, val value: String) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.hset(key, field, value)
        }
    }

    data class AddSetMember(val key: String, val member: String) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.sadd(key, member)
        }
    }

    data class SetExpire(val key: String, val expiry: Duration) : RedisBlockingMutationOperation {
        override fun execute(commands: RedisCommands<String, String>) {
            commands.pexpire(key, expiry.inWholeMilliseconds)
        }
    }
}
