plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinxSerialization)
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    // Deliberately mirror the README's single Redis dependency, using published metadata.
    implementation("com.github.dave08.kacheable:kacheable-lettuce:${providers.gradleProperty("kacheableVersion").get()}")
}

application { mainClass.set("PublicationSmokeKt") }
