plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish")
    id("com.gradleup.nmcp")
}

dependencies {
    api(project(":core"))
    api(project(":psi"))
    api(project(":arch"))
    api(project(":exec"))
    api(project(":generators"))
    api(project(":adapters:copilot"))
    api(project(":adapters:claude"))
    api(project(":adapters:codex"))
    api(project(":adapters:opencode"))
    api(project(":tasks"))
    api(project(":linting"))

    testImplementation(gradleTestKit())
}

java {
    withSourcesJar()
    withJavadocJar()
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

publishing {
    publications.withType<MavenPublication> {
        artifactId = "openspec-gradle"
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

// Inject version into plugin at build time
tasks.named<Copy>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}
