plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "kacheable"

include(":kacheable-core")
include(":kacheable-lettuce")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
