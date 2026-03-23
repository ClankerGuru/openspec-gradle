// Convention plugin for all openspec-gradle submodules — shared build config only.
plugins {
    `java-library`
    kotlin("jvm")
}

group = "zone.clanker"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Gradle 9.x strict task dependency: signing tasks must run before publish tasks
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.withType<Sign>())
}
