plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(files("${rootProject.projectDir}/task/monolith/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:monolith:compileKotlin")
}
