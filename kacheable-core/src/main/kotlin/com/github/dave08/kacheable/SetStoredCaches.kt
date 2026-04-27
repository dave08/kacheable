@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class SetMainKey<P1 : Any>(
    val name: String,
    val key: MainKeyPart<P1>,
) {
    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(name, CacheKeyGroups(main = key.encode(value)))
}

@ExperimentalKacheableApi
data class SetStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: MainSecondaryKey2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): SetMembershipCacheEntryRef =
        SetMembershipCacheEntryRef(
            name = name,
            keyGroups = CacheKeyGroups(
                main = key.main.encode(p1),
                secondary = key.secondary.encode(p2),
            ),
        )

    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(name, CacheKeyGroups(main = key.main.encode(value)))
}
