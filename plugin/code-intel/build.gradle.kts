plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":psi"))
    implementation(project(":quality"))
    // Cross-nested dep: use classes dir to avoid compileKotlin → jar cycle
    implementation(files("${rootProject.projectDir}/task/code-intel/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:code-intel:compileKotlin")
}
