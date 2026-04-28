@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class SetPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
) {
    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(name, PrimarySecondaryCacheArgs(primary = key.encode(value)))
}

@ExperimentalKacheableApi
data class SetStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): SetMembershipCacheEntryRef =
        SetMembershipCacheEntryRef(
            name = name,
            cacheArgs = PrimarySecondaryCacheArgs(
                primary = key.primary.encode(p1),
                secondary = key.secondary.encode(p2),
            ),
        )

    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(name, PrimarySecondaryCacheArgs(primary = key.primary.encode(value)))
}
