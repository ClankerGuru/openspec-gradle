plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("com.vanniktech.maven.publish")
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

mavenPublishing {
    // POM metadata configured in plugin/gradle.properties
}

// Inject version into plugin at build time
tasks.named<Copy>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}
