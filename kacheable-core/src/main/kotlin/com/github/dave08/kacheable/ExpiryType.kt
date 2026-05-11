package com.github.dave08.kacheable

/**
 * Expiration behavior for a configured cache.
 */
enum class ExpiryType {
    none, after_write, after_access
}
