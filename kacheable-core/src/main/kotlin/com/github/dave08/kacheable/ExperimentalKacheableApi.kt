package com.github.dave08.kacheable

@RequiresOptIn(
    message = "The typed kacheable API is experimental and may change as grouped invalidation, key structure, and storage support evolve.",
    level = RequiresOptIn.Level.WARNING,
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalKacheableApi
