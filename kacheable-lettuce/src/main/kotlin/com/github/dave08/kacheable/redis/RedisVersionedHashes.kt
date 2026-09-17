package com.github.dave08.kacheable.redis

import com.github.dave08.kacheable.blocking.store.BlockingVersionedHashOperations
import com.github.dave08.kacheable.store.HashPublishResult
import com.github.dave08.kacheable.store.HashPublishManyResult
import com.github.dave08.kacheable.store.HashReadSnapshot
import com.github.dave08.kacheable.store.HashVersion
import com.github.dave08.kacheable.store.VersionedHashOperations
import com.github.dave08.kacheable.store.VersionedHashValue
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import java.util.UUID
import kotlin.time.Duration

internal class RedisVersionedHashes(
    connection: StatefulRedisConnection<String, String>,
    chunkSize: Int,
    scanCount: Long,
    deleteMode: RedisDeleteMode,
) : VersionedHashOperations {
    private val scripts = RedisScriptExecutor(connection)
    private val deletion = RedisKeyDeletion(connection, chunkSize, scanCount, deleteMode)

    override suspend fun deleteHashes(keyPattern: String) =
        deletion.delete(versionedRedisKey(keyPattern)) { true }

    override suspend fun openHash(key: String, expiry: Duration?): HashVersion =
        parseVersion(execute(OPEN, key, UUID.randomUUID().toString(), expiryArg(expiry)))

    override suspend fun readHashSnapshot(
        key: String,
        expiry: Duration?,
        fields: List<String>?,
    ): HashReadSnapshot = parseSnapshot(execute(
        READ_SNAPSHOT,
        key,
        UUID.randomUUID().toString(),
        expiryArg(expiry),
        *selectionArgs(fields),
    ))

    override suspend fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? =
        parseRead(execute(READ, key, *readArgs(version, fields)))

    override suspend fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? =
        parseMetadata(execute(READ_METADATA, key, version.generation, version.revision.toString()))

    override suspend fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult =
        parsePublish(execute(PUBLISH, key, *publishArgs(version, field, value, expiry, ifAbsent, metadata)))

    override suspend fun publishHashes(
        key: String,
        version: HashVersion,
        values: Map<String, VersionedHashValue>,
        expiry: Duration?,
        ifAbsent: Boolean,
    ): HashPublishManyResult =
        parsePublishMany(execute(PUBLISH_MANY, key, *publishManyArgs(version, values, expiry, ifAbsent)))

    private suspend fun execute(script: String, key: String, vararg args: String): List<String> =
        checkNotNull(scripts.execute(script, ScriptOutputType.MULTI, arrayOf(versionedRedisKey(key)), *args))
}

internal class RedisBlockingVersionedHashes(
    connection: StatefulRedisConnection<String, String>,
    chunkSize: Int,
    scanCount: Long,
    deleteMode: RedisDeleteMode,
) : BlockingVersionedHashOperations {
    private val scripts = RedisScriptExecutor(connection)
    private val deletion = RedisKeyDeletion(connection, chunkSize, scanCount, deleteMode)

    override fun deleteHashes(keyPattern: String) =
        deletion.deleteBlocking(versionedRedisKey(keyPattern)) { true }

    override fun openHash(key: String, expiry: Duration?): HashVersion =
        parseVersion(execute(OPEN, key, UUID.randomUUID().toString(), expiryArg(expiry)))

    override fun readHashSnapshot(
        key: String,
        expiry: Duration?,
        fields: List<String>?,
    ): HashReadSnapshot = parseSnapshot(execute(
        READ_SNAPSHOT,
        key,
        UUID.randomUUID().toString(),
        expiryArg(expiry),
        *selectionArgs(fields),
    ))

    override fun readHash(key: String, version: HashVersion, fields: List<String>?): Map<String, String>? =
        parseRead(execute(READ, key, *readArgs(version, fields)))

    override fun readHashMetadata(key: String, version: HashVersion): Map<String, String?>? =
        parseMetadata(execute(READ_METADATA, key, version.generation, version.revision.toString()))

    override fun publishHash(
        key: String,
        version: HashVersion,
        field: String,
        value: String,
        expiry: Duration?,
        ifAbsent: Boolean,
        metadata: String?,
    ): HashPublishResult =
        parsePublish(execute(PUBLISH, key, *publishArgs(version, field, value, expiry, ifAbsent, metadata)))

    override fun publishHashes(
        key: String,
        version: HashVersion,
        values: Map<String, VersionedHashValue>,
        expiry: Duration?,
        ifAbsent: Boolean,
    ): HashPublishManyResult =
        parsePublishMany(execute(PUBLISH_MANY, key, *publishManyArgs(version, values, expiry, ifAbsent)))

    private fun execute(script: String, key: String, vararg args: String): List<String> =
        checkNotNull(scripts.executeBlocking(script, ScriptOutputType.MULTI, arrayOf(versionedRedisKey(key)), *args))
}

private fun expiryArg(expiry: Duration?): String {
    require(expiry == null || (expiry.isFinite() && expiry.inWholeMilliseconds > 0)) {
        "Hash expiry must be finite and at least one millisecond."
    }
    return expiry?.inWholeMilliseconds?.toString().orEmpty()
}

private fun readArgs(version: HashVersion, fields: List<String>?): Array<String> {
    return arrayOf(version.generation, version.revision.toString(), *selectionArgs(fields))
}

private fun selectionArgs(fields: List<String>?): Array<String> =
    (listOf(if (fields == null) "all" else "selected") + fields.orEmpty()).toTypedArray()

private fun publishArgs(
    version: HashVersion,
    field: String,
    value: String,
    expiry: Duration?,
    ifAbsent: Boolean,
    metadata: String?,
): Array<String> = arrayOf(
    version.generation,
    version.revision.toString(),
    field,
    value,
    expiryArg(expiry),
    if (ifAbsent) "1" else "0",
    if (metadata == null) "0" else "1",
    metadata.orEmpty(),
)

private fun publishManyArgs(
    version: HashVersion,
    values: Map<String, VersionedHashValue>,
    expiry: Duration?,
    ifAbsent: Boolean,
): Array<String> {
    return buildList {
        add(version.generation)
        add(version.revision.toString())
        add(expiryArg(expiry))
        add(if (ifAbsent) "1" else "0")
        add(values.size.toString())
        values.forEach { (field, value) ->
            add(field)
            add(value.value)
            add(if (value.metadata == null) "0" else "1")
            add(value.metadata.orEmpty())
        }
    }.toTypedArray()
}

private fun parseVersion(result: List<String>) = HashVersion(result[0], result[1].toLong())

private fun parseSnapshot(result: List<String>): HashReadSnapshot {
    require(result.size >= 2 && (result.size - 2) % 2 == 0) {
        "Redis versioned hash snapshot returned a malformed response."
    }
    return HashReadSnapshot(
        version = HashVersion(result[0], result[1].toLong()),
        values = result.drop(2).chunked(2).associate { it[0] to it[1] },
    )
}

private fun parseRead(result: List<String>): Map<String, String>? =
    if (result.isEmpty()) null else result.drop(1).chunked(2).associate { it[0] to it[1] }

private fun parseMetadata(result: List<String>): Map<String, String?>? =
    if (result.isEmpty()) null else result.drop(1).chunked(3).associate { field ->
        field[0] to field[2].takeIf { field[1] == "1" }
    }

private fun parsePublish(result: List<String>): HashPublishResult = when (result[0]) {
    "published" -> HashPublishResult.Published(HashVersion(result[1], result[2].toLong()), result[3])
    "existing" -> HashPublishResult.Existing(HashVersion(result[1], result[2].toLong()), result[3])
    else -> HashPublishResult.Conflict
}

private fun parsePublishMany(result: List<String>): HashPublishManyResult {
    if (result[0] == "conflict") return HashPublishManyResult.Conflict
    val publishedCount = result[3].toInt()
    val fields = result.drop(4).chunked(3)
    return HashPublishManyResult.Success(
        version = HashVersion(result[1], result[2].toLong()),
        values = fields.associate { it[0] to it[1] },
        publishedFields = fields.take(publishedCount).mapTo(linkedSetOf()) { it[0] },
    )
}

internal const val VERSIONED_HASH_GENERATION = "__kacheable_versioned_hash_generation_v1"
private const val REVISION = "__kacheable_versioned_hash_revision_v1"

private const val OPEN = """
local kind = redis.call('TYPE', KEYS[1]).ok
if kind ~= 'none' then
  if kind ~= 'hash' then return redis.error_reply('Existing key is not a versioned hash') end
  local g = redis.call('HGET', KEYS[1], '$VERSIONED_HASH_GENERATION')
  local r = redis.call('HGET', KEYS[1], '$REVISION')
  if not g or not r then return redis.error_reply('Existing key is not a versioned hash') end
  return {g, r}
end
redis.call('HSET', KEYS[1], '$VERSIONED_HASH_GENERATION', ARGV[1], '$REVISION', '0')
if ARGV[2] ~= '' then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
return {ARGV[1], '0'}
"""
private const val READ_SNAPSHOT = """
local kind = redis.call('TYPE', KEYS[1]).ok
local generation
local revision
if kind == 'none' then
  generation = ARGV[1]
  revision = '0'
  redis.call('HSET', KEYS[1], '$VERSIONED_HASH_GENERATION', generation, '$REVISION', revision)
  if ARGV[2] ~= '' then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
else
  if kind ~= 'hash' then return redis.error_reply('Existing key is not a versioned hash') end
  generation = redis.call('HGET', KEYS[1], '$VERSIONED_HASH_GENERATION')
  revision = redis.call('HGET', KEYS[1], '$REVISION')
  if not generation or not revision then return redis.error_reply('Existing key is not a versioned hash') end
end
local result = {generation, revision}
if ARGV[3] == 'all' then
  local entries = redis.call('HGETALL', KEYS[1])
  for i = 1, #entries, 2 do
    if string.sub(entries[i], 1, 2) == 'd:' then
      table.insert(result, string.sub(entries[i], 3))
      table.insert(result, entries[i + 1])
    end
  end
else
  for i = 4, #ARGV do
    local value = redis.call('HGET', KEYS[1], 'd:' .. ARGV[i])
    if value then table.insert(result, ARGV[i]); table.insert(result, value) end
  end
end
return result
"""
private const val MATCH = """
if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then return {} end
if redis.call('HGET', KEYS[1], '$VERSIONED_HASH_GENERATION') ~= ARGV[1] or
   redis.call('HGET', KEYS[1], '$REVISION') ~= ARGV[2] then return {} end
"""
private const val READ = MATCH + """
local result = {'ok'}
if ARGV[3] == 'all' then
  local entries = redis.call('HGETALL', KEYS[1])
  for i = 1, #entries, 2 do
    if string.sub(entries[i], 1, 2) == 'd:' then
      table.insert(result, string.sub(entries[i], 3))
      table.insert(result, entries[i + 1])
    end
  end
else
  for i = 4, #ARGV do
    local value = redis.call('HGET', KEYS[1], 'd:' .. ARGV[i])
    if value then table.insert(result, ARGV[i]); table.insert(result, value) end
  end
end
return result
"""
private const val READ_METADATA = MATCH + """
local result = {'ok'}
local fields = redis.call('HKEYS', KEYS[1])
for i = 1, #fields do
  if string.sub(fields[i], 1, 2) == 'd:' then
    local field = string.sub(fields[i], 3)
    local metadata = redis.call('HGET', KEYS[1], 'm:' .. field)
    table.insert(result, field)
    table.insert(result, metadata and '1' or '0')
    table.insert(result, metadata or '')
  end
end
return result
"""
private const val PUBLISH = """
if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then return {'conflict'} end
if redis.call('HGET', KEYS[1], '$VERSIONED_HASH_GENERATION') ~= ARGV[1] or
   redis.call('HGET', KEYS[1], '$REVISION') ~= ARGV[2] then return {'conflict'} end
local field = 'd:' .. ARGV[3]
if ARGV[6] == '1' then
  local existing = redis.call('HGET', KEYS[1], field)
  if existing then return {'existing', ARGV[1], ARGV[2], existing} end
end
redis.call('HINCRBY', KEYS[1], '$REVISION', 1)
redis.call('HSET', KEYS[1], field, ARGV[4])
if ARGV[7] == '1' then redis.call('HSET', KEYS[1], 'm:' .. ARGV[3], ARGV[8])
else redis.call('HDEL', KEYS[1], 'm:' .. ARGV[3]) end
if ARGV[5] ~= '' then redis.call('PEXPIRE', KEYS[1], ARGV[5]) end
return {'published', ARGV[1], redis.call('HGET', KEYS[1], '$REVISION'), ARGV[4]}
"""

private const val PUBLISH_MANY = """
if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then return {'conflict'} end
if redis.call('HGET', KEYS[1], '$VERSIONED_HASH_GENERATION') ~= ARGV[1] or
   redis.call('HGET', KEYS[1], '$REVISION') ~= ARGV[2] then return {'conflict'} end
local publications = {}
local existing = {}
local arg = 6
for i = 1, tonumber(ARGV[5]) do
  local field = 'd:' .. ARGV[arg]
  local stored = nil
  if ARGV[4] == '1' then stored = redis.call('HGET', KEYS[1], field) end
  if stored then
    table.insert(existing, ARGV[arg])
    table.insert(existing, stored)
  else
    table.insert(publications, ARGV[arg])
    table.insert(publications, ARGV[arg + 1])
    table.insert(publications, ARGV[arg + 2])
    table.insert(publications, ARGV[arg + 3])
  end
  arg = arg + 4
end
if #publications > 0 then
  redis.call('HINCRBY', KEYS[1], '$REVISION', 1)
  for i = 1, #publications, 4 do
    redis.call('HSET', KEYS[1], 'd:' .. publications[i], publications[i + 1])
    if publications[i + 2] == '1' then redis.call('HSET', KEYS[1], 'm:' .. publications[i], publications[i + 3])
    else redis.call('HDEL', KEYS[1], 'm:' .. publications[i]) end
  end
  if ARGV[3] ~= '' then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
end
local result = {'success', ARGV[1], redis.call('HGET', KEYS[1], '$REVISION'), tostring(#publications / 4)}
for i = 1, #publications, 4 do
  table.insert(result, publications[i])
  table.insert(result, publications[i + 1])
  table.insert(result, '1')
end
for i = 1, #existing, 2 do
  table.insert(result, existing[i])
  table.insert(result, existing[i + 1])
  table.insert(result, '0')
end
return result
"""
