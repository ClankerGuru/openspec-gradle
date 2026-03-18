package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.arch.*
import zone.clanker.gradle.psi.SourceDiscovery
import java.io.File

@UntrackedTask(because = "Verify reads dynamic project state and architecture rules")
abstract class OpenSpecVerifyTask : DefaultTask() {

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:Input @get:Optional
    abstract val maxWarnings: Property<Int>

    @get:Input @get:Optional
    abstract val failOnWarning: Property<Boolean>

    @get:Input @get:Optional
    abstract val noCycles: Property<Boolean>

    @get:Input @get:Optional
    abstract val maxInheritanceDepth: Property<Int>

    @get:Input @get:Optional
    abstract val maxClassSize: Property<Int>

    @get:Input @get:Optional
    abstract val maxImports: Property<Int>

    @get:Input @get:Optional
    abstract val maxMethods: Property<Int>

    @get:Input @get:Optional
    abstract val noSmells: Property<Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Enforce architecture rules — fails build on violations. " +
            "Output: .opsx/verify.md. " +
            "Options: -PmaxWarnings=N (default: unlimited), -PfailOnWarning=true (default: false), " +
            "-PnoCycles=true (fail on circular deps), -PmaxInheritanceDepth=N (default: 3), " +
            "-PmaxClassSize=N (max lines, default: 500), -PmaxImports=N (default: 30), " +
            "-PmaxMethods=N (default: 25), -PnoSmells=true (fail on Manager/Helper/Util classes). " +
            "Use when: You want CI to enforce architecture constraints. " +
            "Chain: Run opsx-arch first to see the full report, then opsx-verify to enforce rules."
    }

    @TaskAction
    fun verify() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            val msg = "# Verify\n\n> No source files found.\n"
            out.writeText(msg)
            logger.lifecycle(msg.trimEnd())
            return
        }

        logger.lifecycle("OpenSpec: Verifying architecture rules across ${sourceFiles.size} files...")

        // Parse sources using the arch module's parser
        val parsed = sourceFiles.mapNotNull { file ->
            try {
                parseSourceFile(file)
            } catch (_: Exception) {
                null
            }
        }

        val components = classifyAll(parsed)
        val edges = buildDependencyGraph(components)
        val antiPatterns = detectAntiPatterns(components, edges, rootDir)

        // Apply custom thresholds
        val customViolations = mutableListOf<Violation>()
        val maxDepth = maxInheritanceDepth.getOrElse(3)
        val maxSize = maxClassSize.getOrElse(500)
        val maxImp = maxImports.getOrElse(30)
        val maxMeth = maxMethods.getOrElse(25)

        for (c in components) {
            if (c.source.lineCount > maxSize) {
                customViolations.add(Violation(
                    Violation.Level.WARNING,
                    "`${c.source.simpleName}` exceeds max class size (${c.source.lineCount} > $maxSize lines)",
                    c.source.file.relativeTo(rootDir),
                ))
            }
            if (c.source.imports.size > maxImp) {
                customViolations.add(Violation(
                    Violation.Level.WARNING,
                    "`${c.source.simpleName}` exceeds max imports (${c.source.imports.size} > $maxImp)",
                    c.source.file.relativeTo(rootDir),
                ))
            }
            if (c.source.methods.size > maxMeth) {
                customViolations.add(Violation(
                    Violation.Level.WARNING,
                    "`${c.source.simpleName}` exceeds max methods (${c.source.methods.size} > $maxMeth)",
                    c.source.file.relativeTo(rootDir),
                ))
            }
        }

        // Check cycles
        val cycles = findCycles(components, edges)
        val enforceCycles = noCycles.getOrElse(false)
        val enforceSmells = noSmells.getOrElse(false)
        val failOnWarn = failOnWarning.getOrElse(false)
        val maxWarn = maxWarnings.orNull

        // Categorize anti-patterns
        val cyclePatterns = antiPatterns.filter { it.message.startsWith("Circular dependency") }
        val smellPatterns = antiPatterns.filter {
            it.message.contains("manager class", ignoreCase = true) ||
            it.message.contains("helper class", ignoreCase = true) ||
            it.message.contains("util class", ignoreCase = true)
        }
        val otherPatterns = antiPatterns - cyclePatterns.toSet() - smellPatterns.toSet()

        // Build report
        val sb = StringBuilder()
        sb.appendLine("# Architecture Verification")
        sb.appendLine()

        val errors = mutableListOf<String>()

        // Report cycles
        if (cycles.isNotEmpty()) {
            sb.appendLine("## 🔄 Circular Dependencies (${cycles.size})")
            sb.appendLine()
            for (cycle in cycles.take(10)) {
                sb.appendLine("- ${cycle.joinToString(" → ")}")
            }
            sb.appendLine()
            if (enforceCycles) {
                errors.add("${cycles.size} circular dependency violation(s) found (noCycles=true)")
            }
        }

        // Report smells
        if (smellPatterns.isNotEmpty()) {
            sb.appendLine("## 🦨 Code Smells (${smellPatterns.size})")
            sb.appendLine()
            for (p in smellPatterns) {
                sb.appendLine("- ${p.severity.icon} ${p.message} — `${p.file.path}`")
            }
            sb.appendLine()
            if (enforceSmells) {
                errors.add("${smellPatterns.size} code smell(s) found (noSmells=true)")
            }
        }

        // Report threshold violations
        if (customViolations.isNotEmpty()) {
            sb.appendLine("## 📏 Threshold Violations (${customViolations.size})")
            sb.appendLine()
            for (v in customViolations) {
                sb.appendLine("- ⚠️ ${v.message} — `${v.file.path}`")
            }
            sb.appendLine()
        }

        // Report other patterns
        if (otherPatterns.isNotEmpty()) {
            sb.appendLine("## 🔍 Other Findings (${otherPatterns.size})")
            sb.appendLine()
            for (p in otherPatterns) {
                sb.appendLine("- ${p.severity.icon} ${p.message} — `${p.file.path}`")
            }
            sb.appendLine()
        }

        val totalWarnings = antiPatterns.size + customViolations.size

        // Summary
        sb.appendLine("---")
        sb.appendLine()
        if (totalWarnings == 0) {
            sb.appendLine("✅ **All checks passed.** No architecture violations found.")
        } else {
            sb.appendLine("**${totalWarnings} finding(s)** across ${sourceFiles.size} files.")
        }

        // Enforce maxWarnings
        if (maxWarn != null && totalWarnings > maxWarn) {
            errors.add("$totalWarnings warnings exceed maximum allowed ($maxWarn)")
        }

        // Enforce failOnWarning
        if (failOnWarn && totalWarnings > 0) {
            errors.add("$totalWarnings warning(s) found (failOnWarning=true)")
        }

        if (errors.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## ❌ FAILED")
            sb.appendLine()
            for (err in errors) {
                sb.appendLine("- $err")
            }
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())

        if (errors.isNotEmpty()) {
            throw GradleException("Architecture verification failed:\n${errors.joinToString("\n") { "  - $it" }}")
        }
    }

    private data class Violation(
        val level: Level,
        val message: String,
        val file: File,
    ) {
        enum class Level { WARNING, ERROR }
    }
}
