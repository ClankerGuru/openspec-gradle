plugins {
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version embeddedKotlinVersion apply false
    id("com.gradle.plugin-publish") version "2.1.0" apply false
    id("com.gradleup.nmcp") version "1.4.4" apply false
    id("com.gradleup.nmcp.aggregation") version "1.4.4"
}

repositories {
    mavenCentral()
}

group = "zone.clanker"
version = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
}.standardOutput.asText.map { it.trim().removePrefix("v") }
    .getOrElse("0.0.0-LOCAL")

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

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

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

tasks.withType<Sign>().configureEach {
    onlyIf { !project.hasProperty("skipSigning") }
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername").getOrElse("")
        password = providers.gradleProperty("sonatypePassword").getOrElse("")
        publishingType = "USER_MANAGED"
    }
}

dependencies {
    nmcpAggregation(project(":plugin"))
}
