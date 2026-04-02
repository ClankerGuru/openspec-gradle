plugins {
    id("openspec-plugin")
    id("openspec-publish")
    `java-gradle-plugin`
}

dependencies {
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        register("claude") {
            id = "zone.clanker.claude"
            implementationClass = "zone.clanker.claude.ClaudePlugin"
            displayName = "Claude Code Gradle Plugin"
            description = "Wraps the Claude Code CLI as native Gradle tasks."
        }
    }
}
