plugins {
    id("openspec-plugin")
    id("openspec-publish")
}

dependencies {
    implementation(project(":lib:core"))
    implementation(files("${rootProject.projectDir}/task/wrkx/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

tasks.named("compileKotlin") {
    dependsOn(":task:wrkx:compileKotlin")
}
