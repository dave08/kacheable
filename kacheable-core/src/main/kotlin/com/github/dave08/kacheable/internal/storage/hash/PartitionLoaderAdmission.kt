package com.github.dave08.kacheable.internal.storage.hash

import com.github.dave08.kacheable.LoadConcurrencyGroup
import com.github.dave08.kacheable.internal.OperationObservation

/** Allows the blocking boundary to share its existing load admission limits. */
internal fun interface PartitionLoaderAdmission {
    suspend fun run(
        cacheName: String,
        group: LoadConcurrencyGroup?,
        observation: OperationObservation,
        block: suspend () -> Unit,
    )
}
