plugins {
    id("openspec-plugin")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":gradle-tasks"))
    implementation(project(":psi"))
    implementation(project(":quality"))
    compileOnly(gradleApi())
}
