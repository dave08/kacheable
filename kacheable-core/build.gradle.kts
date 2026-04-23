plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.testballoon)
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.testballoon.framework.core)
    testImplementation(libs.kotlin.test)
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
            artifactId = "kacheable-core"
            version = rootProject.version.toString()

            from(components["java"])
        }

        repositories.maven("/tmp/maven")
    }
}
