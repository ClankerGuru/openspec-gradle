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
        register("copilot") {
            id = "zone.clanker.copilot"
            implementationClass = "zone.clanker.copilot.CopilotPlugin"
            displayName = "GitHub Copilot Gradle Plugin"
            description = "Wraps the GitHub Copilot CLI as native Gradle tasks."
        }
    }
}
