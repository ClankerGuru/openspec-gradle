import com.vanniktech.maven.publish.GradlePublishPlugin

plugins {
    id("openspec-module")
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.vanniktech)
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
    api(project(":quality"))

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
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
        register("monolith") {
            id = "zone.clanker.monolith"
            implementationClass = "zone.clanker.gradle.MonolithPlugin"
            displayName = "Monolith Workspace Plugin"
            description = "Manages multi-repo workspaces via a declarative JSON config. Clones missing repositories and provides dependency substitution rules. Apply via init.gradle.kts for zero-config workspace setup."
            tags = listOf("monolith", "workspace", "clone", "dependency-substitution", "multi-repo")
        }
    }
}

mavenPublishing {
    configure(GradlePublishPlugin())
}

// Inject version into plugin at build time
tasks.named<Copy>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("openspec-gradle.properties") {
        expand(props)
    }
}
