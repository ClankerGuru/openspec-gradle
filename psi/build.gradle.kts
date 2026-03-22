// psi: Symbol parsing + source analysis
dependencies {
    api(project(":core"))
    compileOnly(gradleApi())
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.0")
    implementation("com.github.javaparser:javaparser-core:3.26.4")
}
