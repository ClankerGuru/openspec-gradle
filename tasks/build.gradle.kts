// tasks: All Gradle task classes
dependencies {
    api(project(":core"))
    api(project(":psi"))
    api(project(":arch"))
    api(project(":exec"))
    api(project(":generators"))
    api(project(":linting"))
    compileOnly(gradleApi())
}
