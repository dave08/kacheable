plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinxSerialization)
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(projects.kacheableCore)

    implementation(libs.kotlinx.serialization.json)
    api(libs.lettuce.core)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.testContainers)
    testImplementation(libs.testContainers.core)
    testImplementation(libs.strikt.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("libCore") {
            groupId = "com.github.dave08.kacheable"
            artifactId = "kacheable-lettuce"
            version = rootProject.version.toString()

            from(components["java"])
        }

        repositories.maven("/tmp/maven")
    }
}