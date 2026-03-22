plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish") version "2.1.0"
    id("com.gradleup.nmcp") version "1.4.4"
    id("com.gradleup.nmcp.aggregation") version "1.4.4"
}

group = "zone.clanker"
version = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
}.standardOutput.asText.map { it.trim().removePrefix("v") }
    .getOrElse("0.0.0-LOCAL")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.0")
    implementation("com.github.javaparser:javaparser-core:3.26.4")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website = "https://github.com/ClankerGuru/openspec-gradle"
    vcsUrl = "https://github.com/ClankerGuru/openspec-gradle"
    plugins {
        register("openspec") {
            id = "zone.clanker.gradle"
            implementationClass = "zone.clanker.gradle.OpenSpecSettingsPlugin"
            displayName = "OpenSpec Gradle Plugin"
            description = "Gradle-native alternative to OpenSpec for Kotlin/JVM projects. Extracts project context from the Gradle build model (dependencies, module graph, frameworks) and generates command/skill files for AI coding assistants. Supports GitHub Copilot, Claude Code, Codex, and OpenCode. Zero-config via init script — no plugins block or DSL required."
            tags = listOf("ai", "copilot", "claude", "codex", "opencode", "openspec", "kotlin", "android", "skills", "prompts", "coding-assistant", "context")
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

// Don't fail build if signing isn't available
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
    nmcpAggregation(project(":"))
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("OpenSpec Gradle Plugin")
            description.set("Gradle-native alternative to OpenSpec for Kotlin/JVM projects. Extracts project context from the build model and generates AI assistant command/skill files.")
            url.set("https://github.com/ClankerGuru/openspec-gradle")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("ClankerGuru")
                    name.set("ClankerGuru")
                    url.set("https://github.com/ClankerGuru")
                }
            }
            scm {
                url.set("https://github.com/ClankerGuru/openspec-gradle")
                connection.set("scm:git:https://github.com/ClankerGuru/openspec-gradle.git")
                developerConnection.set("scm:git:git@github.com:ClankerGuru/openspec-gradle.git")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Inject version into plugin at build time
tasks.named<Copy>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}

// Note: Use ./gradlew opsx-install (from any project with the plugin applied)
// to install the init script globally. No install task needed here.
