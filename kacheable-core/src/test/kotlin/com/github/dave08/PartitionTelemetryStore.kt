package com.github.dave08

import com.github.dave08.kacheable.store.*
import kotlin.time.Duration
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

/** Controls only publication outcomes; all other storage behavior remains real. */
internal class PartitionTelemetryStore(
    private val backing: InMemoryKacheableStore = InMemoryKacheableStore(),
) : KacheableStore by backing, VersionedHashOperations by backing {
    var winner: String? = null
    var failPublication = false
    var conflictOnce = false

    override suspend fun publishHash(
        key: String, version: HashVersion, field: String, value: String,
        expiry: Duration?, ifAbsent: Boolean, metadata: String?,
    ): HashPublishResult {
        check(!failPublication) { "Publication failed" }
        winner?.let { text ->
            val encoded = buildJsonObject { put("value", cacheCodec(serializer<String>()).encode(text)) }.toString()
            return HashPublishResult.Existing(version, encoded)
        }
        if (conflictOnce) {
            conflictOnce = false
            return HashPublishResult.Conflict
        }
        return backing.publishHash(key, version, field, value, expiry, ifAbsent, metadata)
    }
}
