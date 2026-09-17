package com.github.dave08.kacheable

/** Distinguishes a missing entry from a published nullable value. */
sealed interface CacheEntry<out V> {
    data class Present<V>(val value: V) : CacheEntry<V>
    data object Missing : CacheEntry<Nothing>
}
