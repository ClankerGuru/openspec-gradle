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
        register("opencode") {
            id = "zone.clanker.opencode"
            implementationClass = "zone.clanker.opencode.OpencodePlugin"
            displayName = "OpenCode Gradle Plugin"
            description = "Wraps the OpenCode CLI as native Gradle tasks."
        }
    }
}
