plugins {
    id("openspec-plugin")
    id("openspec-publish")
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

tasks.named("compileKotlin") {
    dependsOn(":task:opsx:compileKotlin")
}
