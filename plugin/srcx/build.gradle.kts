plugins {
    id("openspec-plugin")
    id("openspec-publish")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:psi"))
    implementation(project(":lib:quality"))
    // Cross-nested dep: use classes dir to avoid compileKotlin → jar cycle
    implementation(files("${rootProject.projectDir}/task/srcx/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:srcx:compileKotlin")
}
