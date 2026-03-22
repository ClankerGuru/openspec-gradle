// generators: File generators (commands, skills, instructions, templates)
dependencies {
    api(project(":core"))
    api(project(":psi"))
    compileOnly(gradleApi())
}
