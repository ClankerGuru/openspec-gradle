plugins {
    id("openspec-plugin")
    id("openspec-publish")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":lib:core"))
    implementation(project(":lib:psi"))
    implementation(project(":lib:quality"))
    // Cross-nested dep: use classes dir to avoid compileKotlin → jar cycle
    implementation(files("${rootProject.projectDir}/task/srcx/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        register("srcx") {
            id = "zone.clanker.srcx"
            implementationClass = "zone.clanker.srcx.SrcxPlugin"
            displayName = "Source Intelligence Gradle Plugin (srcx)"
            description = "Code discovery, analysis, and refactoring for Gradle projects."
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(":task:srcx:compileKotlin")
}
