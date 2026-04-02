plugins {
    id("openspec-plugin")
    id("openspec-publish")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:generators"))
    implementation(project(":lib:adapters:claude"))
    implementation(project(":lib:adapters:copilot"))
    implementation(project(":lib:adapters:codex"))
    implementation(project(":lib:adapters:opencode"))
    implementation(project(":lib:exec"))
    implementation(files("${rootProject.projectDir}/task/opsx/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        register("opsx") {
            id = "zone.clanker.opsx"
            implementationClass = "zone.clanker.opsx.OpsxPlugin"
            displayName = "OpenSpec Workflow Gradle Plugin (opsx)"
            description = "Proposal-driven workflow, agent execution, and skill generation."
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(":task:opsx:compileKotlin")
}
