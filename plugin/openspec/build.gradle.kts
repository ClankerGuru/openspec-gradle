plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:generators"))
    implementation(project(":lib:adapters:claude"))
    implementation(project(":lib:adapters:copilot"))
    implementation(project(":lib:adapters:codex"))
    implementation(project(":lib:adapters:opencode"))
    implementation(project(":lib:exec"))
    implementation(files("${rootProject.projectDir}/task/openspec/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:openspec:compileKotlin")
}
