plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":core"))
    implementation(files("${rootProject.projectDir}/task/monolith/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:monolith:compileKotlin")
}
