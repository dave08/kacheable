@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> TypedPrimaryKey<P1>.plus(
    secondary: KeyPart<P2>,
): TypedPrimarySecondaryKey2<P1, P2> {
    require(storedAs == CacheStorage.HashMap) { "Primary-secondary composition currently supports CacheStorage.HashMap." }
    return TypedPrimarySecondaryKey2(name, key * secondary)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any> SetPrimaryKey<P1>.plus(
    member: KeyPart<P2>,
): SetStoredCache2<P1, P2> = SetStoredCache2(name, key * member)

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any> TypedPrimaryKey<P1>.plus(
    secondary: KeyPartComposition2<P2, P3>,
): TypedPrimarySecondaryKey3<P1, P2, P3> {
    require(storedAs == CacheStorage.HashMap) { "Primary-secondary composition currently supports CacheStorage.HashMap." }
    return TypedPrimarySecondaryKey3(name, key * secondary)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any> TypedPrimaryKey<P1>.plus(
    secondary: KeyPartComposition3<P2, P3, P4>,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4> {
    require(storedAs == CacheStorage.HashMap) { "Primary-secondary composition currently supports CacheStorage.HashMap." }
    return TypedPrimarySecondaryKey4(name, key * secondary)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any> TypedPrimaryKey<P1>.plus(
    secondary: KeyPartComposition4<P2, P3, P4, P5>,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5> {
    require(storedAs == CacheStorage.HashMap) { "Primary-secondary composition currently supports CacheStorage.HashMap." }
    return TypedPrimarySecondaryKey5(name, key * secondary)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any> TypedPrimaryKey<P1>.plus(
    secondary: KeyPartComposition5<P2, P3, P4, P5, P6>,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6> {
    require(storedAs == CacheStorage.HashMap) { "Primary-secondary composition currently supports CacheStorage.HashMap." }
    return TypedPrimarySecondaryKey6(name, key * secondary)
}
