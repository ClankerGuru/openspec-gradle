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
        register("codex") {
            id = "zone.clanker.codex"
            implementationClass = "zone.clanker.codex.CodexPlugin"
            displayName = "OpenAI Codex Gradle Plugin"
            description = "Wraps the OpenAI Codex CLI as native Gradle tasks."
        }
    }
}
