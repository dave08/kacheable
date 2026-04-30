@file:OptIn(ExperimentalKacheableApi::class)

package com.github.dave08.kacheable

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, S> TypedPrimaryKey<P1, S>.plus(
    secondary: KeyPart<P2>,
): TypedPrimarySecondaryKey2<P1, P2, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsGroupedKeyStorage {
    return TypedPrimarySecondaryKey2(name, key * secondary, storedAs)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, S> TypedPrimaryKey<P1, S>.plus(
    secondary: KeyPartComposition2<P2, P3>,
): TypedPrimarySecondaryKey3<P1, P2, P3, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsGroupedKeyStorage {
    return TypedPrimarySecondaryKey3(name, key * secondary, storedAs)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, S> TypedPrimaryKey<P1, S>.plus(
    secondary: KeyPartComposition3<P2, P3, P4>,
): TypedPrimarySecondaryKey4<P1, P2, P3, P4, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsGroupedKeyStorage {
    return TypedPrimarySecondaryKey4(name, key * secondary, storedAs)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, S> TypedPrimaryKey<P1, S>.plus(
    secondary: KeyPartComposition4<P2, P3, P4, P5>,
): TypedPrimarySecondaryKey5<P1, P2, P3, P4, P5, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsGroupedKeyStorage {
    return TypedPrimarySecondaryKey5(name, key * secondary, storedAs)
}

@ExperimentalKacheableApi
operator fun <P1 : Any, P2 : Any, P3 : Any, P4 : Any, P5 : Any, P6 : Any, S> TypedPrimaryKey<P1, S>.plus(
    secondary: KeyPartComposition5<P2, P3, P4, P5, P6>,
): TypedPrimarySecondaryKey6<P1, P2, P3, P4, P5, P6, S> where S : CacheStorage, S : SupportsPrimaryKeyStorage, S : SupportsGroupedKeyStorage {
    return TypedPrimarySecondaryKey6(name, key * secondary, storedAs)
}
