plugins {
    id("openspec-module")
    id("openspec-publish")
}

dependencies {
    api(project(":core"))
    compileOnly(gradleApi())
    compileOnly(libs.kotlin.compiler.embeddable)
    implementation(libs.javaparser.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
