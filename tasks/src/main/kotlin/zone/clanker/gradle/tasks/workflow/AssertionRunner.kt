package zone.clanker.gradle.tasks.workflow

import org.gradle.api.Project
import zone.clanker.gradle.core.VerifyAssertion
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs machine-checkable verify assertions declared on tasks.
 *
 * Each assertion type checks a specific condition:
 * - `symbol-exists`: symbol is present in the project's symbol index
 * - `symbol-not-in`: symbol does NOT exist (e.g., after extraction/removal)
 * - `file-exists`: file exists on disk relative to project dir
 * - `file-changed`: file appears in `git diff --name-only`
 * - `build-passes`: configured Gradle command exits 0
 */
object AssertionRunner {

    data class AssertionResult(
        val assertion: VerifyAssertion,
        val passed: Boolean,
        val message: String,
    )

    /**
     * Run all assertions against the project. Returns results for each assertion.
     */
    fun run(
        assertions: List<VerifyAssertion>,
        project: Project,
        verifyCommand: String = "build",
    ): List<AssertionResult> {
        return run(assertions, project.projectDir, project.rootDir, verifyCommand) {
            buildSymbolIndex(project)
        }
    }

    /**
     * Run assertions with explicit directories — usable without a Gradle Project instance.
     * The [indexProvider] is called lazily only if symbol assertions are present.
     */
    fun run(
        assertions: List<VerifyAssertion>,
        projectDir: File,
        rootDir: File = projectDir,
        verifyCommand: String = "build",
        indexProvider: () -> SymbolIndex = { SymbolIndex(emptyList(), emptyList()) },
    ): List<AssertionResult> {
        val symbolIndex by lazy { indexProvider() }

        return assertions.map { assertion ->
            when (assertion.type) {
                "symbol-exists" -> checkSymbolExists(assertion, symbolIndex)
                "symbol-not-in" -> checkSymbolNotIn(assertion, symbolIndex)
                "file-exists" -> checkFileExists(assertion, projectDir)
                "file-changed" -> checkFileChanged(assertion, rootDir)
                "build-passes" -> checkBuildPasses(assertion, rootDir, projectDir, verifyCommand)
                else -> AssertionResult(assertion, false, "Unknown assertion type: ${assertion.type}")
            }
        }
    }

    private fun buildSymbolIndex(project: Project): SymbolIndex {
        val projects = SourceDiscovery.resolveProjects(project, null)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)
        return SymbolIndex.build(sourceFiles)
    }

    private fun checkSymbolExists(assertion: VerifyAssertion, index: SymbolIndex): AssertionResult {
        val name = assertion.argument
        val found = index.symbols.any { it.name == name || it.qualifiedName.endsWith(".$name") }
        return AssertionResult(
            assertion,
            found,
            if (found) "Symbol '$name' exists" else "Symbol '$name' not found in source"
        )
    }

    private fun checkSymbolNotIn(assertion: VerifyAssertion, index: SymbolIndex): AssertionResult {
        val arg = assertion.argument
        // Format: "Class.method" — check method doesn't exist in class
        val dotIdx = arg.lastIndexOf('.')
        if (dotIdx > 0) {
            val className = arg.substring(0, dotIdx)
            val memberName = arg.substring(dotIdx + 1)
            val classExists = index.symbols.any { it.name == className }
            val memberExists = index.symbols.any {
                it.name == memberName && it.qualifiedName.contains(className)
            }
            return AssertionResult(
                assertion,
                !memberExists,
                if (!memberExists) "'$arg' not found (as expected)"
                else "'$arg' still exists in source"
            )
        }
        // Simple name — just check it doesn't exist
        val found = index.symbols.any { it.name == arg }
        return AssertionResult(
            assertion,
            !found,
            if (!found) "'$arg' not found (as expected)" else "'$arg' still exists in source"
        )
    }

    private fun checkFileExists(assertion: VerifyAssertion, projectDir: File): AssertionResult {
        val file = File(projectDir, assertion.argument)
        val exists = file.exists()
        return AssertionResult(
            assertion,
            exists,
            if (exists) "File '${assertion.argument}' exists"
            else "File '${assertion.argument}' not found"
        )
    }

    private fun checkFileChanged(assertion: VerifyAssertion, rootDir: File): AssertionResult {
        return try {
            val proc = ProcessBuilder("git", "diff", "--name-only", "HEAD")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(30, TimeUnit.SECONDS)
            val changed = output.lines().any { it.trim() == assertion.argument }
            AssertionResult(
                assertion,
                changed,
                if (changed) "File '${assertion.argument}' was modified"
                else "File '${assertion.argument}' has no changes in git diff"
            )
        } catch (e: Exception) {
            AssertionResult(assertion, false, "Failed to check git diff: ${e.message}")
        }
    }

    private fun checkBuildPasses(
        assertion: VerifyAssertion,
        rootDir: File,
        projectDir: File,
        verifyCommand: String,
    ): AssertionResult {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val wrapperName = if (isWindows) "gradlew.bat" else "gradlew"
        val gradlew = File(rootDir, wrapperName).absolutePath

        val proc = ProcessBuilder(
            gradlew, verifyCommand,
            "--no-daemon", "-p", projectDir.absolutePath,
        )
            .directory(rootDir)
            .inheritIO()
            .start()

        val completed = proc.waitFor(10, TimeUnit.MINUTES)
        if (!completed) {
            proc.destroyForcibly()
            return AssertionResult(
                assertion, false,
                "Build gate timed out after 10 minutes. Process terminated."
            )
        }

        val exitCode = proc.exitValue()
        return AssertionResult(
            assertion,
            exitCode == 0,
            if (exitCode == 0) "Build passed ($verifyCommand)"
            else "Build failed ($verifyCommand, exit code $exitCode)"
        )
    }

    /**
     * Format assertion failures into a human-readable error message.
     */
    fun formatFailures(code: String, failures: List<AssertionResult>): String {
        return buildString {
            appendLine("Verification failed for task '$code':")
            for (f in failures) {
                appendLine("  ✗ ${f.assertion.type} ${f.assertion.argument}: ${f.message}")
            }
            appendLine("Task stays IN_PROGRESS. Fix the issues and try again, or use --force interactively.")
        }
    }
}
