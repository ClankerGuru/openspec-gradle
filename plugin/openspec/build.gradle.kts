plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":generators"))
    implementation(project(":adapters:claude"))
    implementation(project(":adapters:copilot"))
    implementation(project(":adapters:codex"))
    implementation(project(":adapters:opencode"))
    implementation(project(":exec"))
    implementation(files("${rootProject.projectDir}/task/openspec/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:openspec:compileKotlin")
}
