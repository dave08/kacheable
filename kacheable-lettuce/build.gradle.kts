plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.testballoon)
    `java-library`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.kacheableCore)

    implementation(libs.kotlinx.serialization.json)
    api(libs.lettuce.core)

    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

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
            artifactId = "kacheable-lettuce"
            version = rootProject.version.toString()

            from(components["java"])
        }

        repositories.maven("/tmp/maven")
    }
}
