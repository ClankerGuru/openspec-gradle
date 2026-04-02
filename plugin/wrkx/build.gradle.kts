plugins {
    id("openspec-plugin")
    id("openspec-publish")
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":lib:core"))
    implementation(files("${rootProject.projectDir}/task/wrkx/build/classes/kotlin/main"))
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        register("wrkx") {
            id = "zone.clanker.wrkx"
            implementationClass = "zone.clanker.wrkx.WrkxPlugin"
            displayName = "Workspace Gradle Plugin (wrkx)"
            description = "Multi-repo workspace management with composite build wiring."
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(":task:wrkx:compileKotlin")
}
