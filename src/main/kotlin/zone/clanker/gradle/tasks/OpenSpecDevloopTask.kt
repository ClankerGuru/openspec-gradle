package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery

@UntrackedTask(because = "Reads dynamic task graph at execution time")
abstract class OpenSpecDevloopTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Development workflow reference. " +
            "Output: .opsx/devloop.md. " +
            "Use when: You need to know how to build, test, run, and iterate on this project. " +
            "Chain: Read first when starting work on an unfamiliar project."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val sb = StringBuilder()
        val root = project.rootProject
        val allProjects = if (root.subprojects.isNotEmpty()) {
            listOf(root) + root.subprojects.sortedBy { it.path }
        } else {
            listOf(root)
        }

        sb.appendLine("# Development Workflow")
        sb.appendLine()

        // ── Quick Reference ──
        sb.appendLine("## Quick Reference")
        sb.appendLine()
        sb.appendLine("```bash")
        sb.appendLine("./gradlew build          # Full build + tests")
        if (allProjects.any { "test" in it.tasks.names }) {
            sb.appendLine("./gradlew test           # Tests only")
        }
        if (allProjects.any { "run" in it.tasks.names || "bootRun" in it.tasks.names }) {
            val runTask = if (allProjects.any { "bootRun" in it.tasks.names }) "bootRun" else "run"
            sb.appendLine("./gradlew $runTask${" ".repeat(13 - runTask.length)}# Run the app")
        }
        sb.appendLine("```")
        sb.appendLine()

        // ── Build ──
        sb.appendLine("## Build")
        sb.appendLine()

        val buildSystem = detectBuildSystem(root)
        if (buildSystem.isNotEmpty()) {
            sb.appendLine("**Stack:** ${buildSystem.joinToString(", ")}")
            sb.appendLine()
        }

        if (root.subprojects.isNotEmpty()) {
            sb.appendLine("```bash")
            sb.appendLine("./gradlew build                    # Build everything")
            sb.appendLine("./gradlew :module:build            # Build specific module")
            sb.appendLine("./gradlew build -x test            # Build without tests")
            sb.appendLine("```")
        } else {
            sb.appendLine("```bash")
            sb.appendLine("./gradlew build                    # Build + test")
            sb.appendLine("./gradlew build -x test            # Build without tests")
            sb.appendLine("```")
        }
        sb.appendLine()

        // ── Test ──
        val testProjects = allProjects.filter { "test" in it.tasks.names }
        if (testProjects.isNotEmpty()) {
            sb.appendLine("## Test")
            sb.appendLine()

            // Detect test framework
            val testFrameworks = detectTestFrameworks(allProjects)
            if (testFrameworks.isNotEmpty()) {
                sb.appendLine("**Framework:** ${testFrameworks.joinToString(", ")}")
                sb.appendLine()
            }

            sb.appendLine("```bash")
            sb.appendLine("./gradlew test                                    # All tests")
            if (root.subprojects.isNotEmpty()) {
                sb.appendLine("./gradlew :module:test                            # Tests for one module")
            }
            sb.appendLine("./gradlew test --tests \"*.MyTest\"                  # Single test class")
            sb.appendLine("./gradlew test --tests \"*.MyTest.specificMethod\"   # Single test method")
            sb.appendLine("./gradlew test --rerun                            # Force rerun all tests")
            sb.appendLine("./gradlew test --continuous                       # Watch mode")
            sb.appendLine("```")
            sb.appendLine()
        }

        // ── Run ──
        val runProjects = allProjects.filter { "run" in it.tasks.names || "bootRun" in it.tasks.names }
        if (runProjects.isNotEmpty()) {
            sb.appendLine("## Run")
            sb.appendLine()
            sb.appendLine("```bash")
            for (proj in runProjects) {
                val prefix = if (proj == root && root.subprojects.isEmpty()) "" else "${proj.path}:"
                if ("bootRun" in proj.tasks.names) {
                    sb.appendLine("./gradlew ${prefix}bootRun")
                } else {
                    sb.appendLine("./gradlew ${prefix}run")
                }
            }
            sb.appendLine("```")
            sb.appendLine()
        }

        // ── Lint / Format ──
        val lintTasks = mutableSetOf<String>()
        for (proj in allProjects) {
            val prefix = if (proj == root && root.subprojects.isEmpty()) "" else "${proj.path}:"
            for (task in listOf("ktlintCheck", "ktlintFormat", "detekt", "spotlessCheck", "spotlessApply", "lint")) {
                if (task in proj.tasks.names) lintTasks.add(task)
            }
        }
        if (lintTasks.isNotEmpty()) {
            sb.appendLine("## Lint / Format")
            sb.appendLine()
            sb.appendLine("```bash")
            for (task in lintTasks.sorted()) {
                sb.appendLine("./gradlew $task")
            }
            sb.appendLine("```")
            sb.appendLine()
        }

        // ── Modules ──
        if (root.subprojects.isNotEmpty()) {
            sb.appendLine("## Modules")
            sb.appendLine()
            sb.appendLine("| Module | Sources | Has Tests |")
            sb.appendLine("|--------|---------|-----------|")
            for (proj in root.subprojects.sortedBy { it.path }) {
                val srcFiles = SourceDiscovery.collectSourceFiles(SourceDiscovery.discoverSourceDirs(proj))
                val hasTests = "test" in proj.tasks.names
                sb.appendLine("| `${proj.path}` | ${srcFiles.size} | ${if (hasTests) "✅" else "—"} |")
            }
            sb.appendLine()
        }

        // ── Useful Gradle Flags ──
        sb.appendLine("## Useful Flags")
        sb.appendLine()
        sb.appendLine("```bash")
        sb.appendLine("--info                   # Verbose output")
        sb.appendLine("--stacktrace             # Show stacktraces on failure")
        sb.appendLine("--scan                   # Build scan (detailed analysis)")
        sb.appendLine("--no-daemon              # Don't use daemon (CI/debugging)")
        sb.appendLine("--parallel               # Parallel module builds")
        sb.appendLine("--build-cache            # Use build cache")
        sb.appendLine("-x test                  # Skip tests")
        sb.appendLine("```")
        sb.appendLine()

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated devloop at ${out.relativeTo(root.projectDir)}")
    }

    private fun detectBuildSystem(root: org.gradle.api.Project): List<String> {
        val all = if (root.subprojects.isNotEmpty()) root.subprojects + root else setOf(root)
        val detected = mutableListOf<String>()

        for (proj in all) {
            proj.plugins.forEach { plugin ->
                val name = plugin.javaClass.name.lowercase()
                when {
                    name.contains("android") && name.contains("application") -> detected.add("Android")
                    name.contains("multiplatform") -> detected.add("Kotlin Multiplatform")
                    name.contains("compose") -> detected.add("Compose")
                    name.contains("spring") && name.contains("boot") -> detected.add("Spring Boot")
                    name.contains("kotlin") && name.contains("jvm") -> detected.add("Kotlin/JVM")
                    name.contains("java") && !name.contains("kotlin") -> detected.add("Java")
                }
            }
        }
        return detected.distinct()
    }

    private fun detectTestFrameworks(projects: List<org.gradle.api.Project>): List<String> {
        val frameworks = mutableSetOf<String>()
        for (proj in projects) {
            proj.configurations.forEach { config ->
                config.dependencies.forEach { dep ->
                    val id = "${dep.group}:${dep.name}".lowercase()
                    when {
                        id.contains("junit-jupiter") || id.contains("junit5") -> frameworks.add("JUnit 5")
                        id.contains("junit") && !id.contains("jupiter") -> frameworks.add("JUnit 4")
                        id.contains("kotest") -> frameworks.add("Kotest")
                        id.contains("testng") -> frameworks.add("TestNG")
                        id.contains("kotlin-test") -> frameworks.add("kotlin-test")
                    }
                }
            }
        }
        return frameworks.toList()
    }
}
