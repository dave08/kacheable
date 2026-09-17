package com.github.dave08.kacheable.internal.storage

/** A physically present cache entry whose decoded value may itself be null. */
internal data class CachedValue<R>(
    val value: R,
    /** Guarded hash generation; ordinary cache values have no generation. */
    val generation: String? = null,
)
