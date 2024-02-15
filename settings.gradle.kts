dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "kacheable"

include(":kacheable-core")
include(":kacheable-lettuce")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")