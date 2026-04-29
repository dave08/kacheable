@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
data class SetPrimaryKey<P1 : Any>(
    val name: String,
    val key: KeyPart<P1>,
) {
    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(
            name,
            cacheArgs(
                primaryPartArgs = listOf(key.encodePart(value)),
                primaryPartNames = listOf(key.name),
            ),
        )
}

@ExperimentalKacheableApi
data class SetStoredCache2<P1 : Any, P2 : Any>(
    val name: String,
    val key: KeyPartCompositionGroup2<P1, P2>,
) {
    fun key(p1: P1, p2: P2): SetMembershipCacheEntryRef =
        SetMembershipCacheEntryRef(
            name = name,
            cacheArgs = cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(p1),
                primaryPartNames = key.primaryPartNames(),
                secondaryPartArgs = key.encodeSecondaryParts(p2),
                secondaryPartNames = key.secondaryPartNames(),
            ),
        )

    fun keyPart(value: P1): SetMembershipCachePartRef =
        SetMembershipCachePartRef(
            name,
            cacheArgs(
                primaryPartArgs = key.encodePrimaryParts(value),
                primaryPartNames = key.primaryPartNames(),
            ),
        )
}
