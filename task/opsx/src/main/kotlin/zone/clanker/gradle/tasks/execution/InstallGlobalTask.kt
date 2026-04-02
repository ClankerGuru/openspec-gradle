package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.GlobalGitignore
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Installs init script to user Gradle home")
abstract class InstallGlobalTask : DefaultTask() {

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val tools: ListProperty<String>

    init {
        group = OPSX_GROUP
        description = "[tool] Install plugin globally via init script. " +
            "Use when: Setting up the plugin on a new machine or updating."
    }

    @TaskAction
    fun install() {
        val gradleHome = project.gradle.gradleUserHomeDir
        val initDir = File(gradleHome, "init.d")
        initDir.mkdirs()

        val initScript = File(initDir, "openspec.init.gradle.kts")
        val version = pluginVersion.get()

        initScript.writeText(generateInitScript(version))

        // Ensure OpenSpec patterns in global gitignore
        GlobalGitignore.ensurePatterns(logger)

        // Ensure default property exists in gradle.properties
        val gradleProps = File(gradleHome, "gradle.properties")
        val propsText = if (gradleProps.exists()) gradleProps.readText() else ""
        if (!propsText.contains("zone.clanker.opsx.agents")) {
            gradleProps.appendText("\n# OpenSpec: comma-separated agents (github, claude, none)\nzone.clanker.opsx.agents=github\n")
            logger.lifecycle("OpenSpec: Added zone.clanker.opsx.agents=github to ${gradleProps.absolutePath}")
        }

        logger.lifecycle("")
        logger.lifecycle("OpenSpec: Installed global init script")
        logger.lifecycle("  Location: ${initScript.absolutePath}")
        logger.lifecycle("  Configure: zone.clanker.opsx.agents=github,claude in ~/.gradle/gradle.properties")
        logger.lifecycle("  Values: github, claude, none (comma-separated)")
        logger.lifecycle("  Default: github")
        logger.lifecycle("")
        logger.lifecycle("  All Gradle projects now have openspec tasks available.")
        logger.lifecycle("  To uninstall, delete: ${initScript.absolutePath}")
    }

    companion object {
        fun generateInitScript(version: String): String = """
            |// OpenSpec Gradle Init Script
            |// Installed by: ./gradlew opsx-install
            |// To uninstall, delete this file.
            |//
            |// Configure agents in ~/.gradle/gradle.properties:
            |//   zone.clanker.opsx.agents=github          (default)
            |//   zone.clanker.opsx.agents=github,claude
            |//   zone.clanker.opsx.agents=none            (disables, cleans files)
            |//
            |// Linting (detekt + ktlint) - enabled by default for Kotlin projects
            |// Disable via system property:
            |//   -Dopenspec.linting.enabled=false   (disable both)
            |//   -Dopenspec.detekt.enabled=false    (disable detekt only)
            |//   -Dopenspec.ktlint.enabled=false    (disable ktlint only)
            |
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:plugin-srcx:$version")
            |        classpath("zone.clanker:plugin-opsx:$version")
            |        classpath("zone.clanker:plugin-wrkx:$version")
            |        classpath("zone.clanker:srcx-tasks:$version")
            |        classpath("zone.clanker:opsx-tasks:$version")
            |        classpath("zone.clanker:wrkx-tasks:$version")
            |        classpath("zone.clanker:plugin-claude:$version")
            |        classpath("zone.clanker:quality:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<zone.clanker.srcx.SrcxPlugin>()
            |    apply<zone.clanker.opsx.OpsxPlugin>()
            |    apply<zone.clanker.wrkx.WrkxPlugin>()
            |    apply<zone.clanker.claude.ClaudePlugin>()
            |}
            |
            |allprojects {
            |    // Inject detekt/ktlint onto the project's buildscript classpath so they share
            |    // the classloader with the Kotlin Gradle plugin (needed for KMP support classes)
            |    buildscript {
            |        repositories {
            |            mavenCentral()
            |            gradlePluginPortal()
            |        }
            |        dependencies {
            |            classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
            |            classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
            |        }
            |    }
            |    afterEvaluate {
            |        val disabled = System.getProperty("zone.clanker.quality.enabled")?.lowercase() == "false" ||
            |            findProperty("zone.clanker.quality.enabled")?.toString()?.lowercase() == "false" ||
            |            System.getProperty("openspec.linting.enabled")?.lowercase() == "false" ||
            |            findProperty("openspec.linting.enabled")?.toString()?.lowercase() == "false"
            |        if (disabled) return@afterEvaluate
            |
            |        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
            |            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            |            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            |
            |        if (isKotlinProject) {
            |            apply<zone.clanker.gradle.linting.OpenSpecLintingPlugin>()
            |        }
            |    }
            |
            |    // Auto-create symlinks from project agent dirs to ~/.clkx/
            |    afterEvaluate {
            |        val clkxDir = java.io.File(System.getProperty("user.home"), ".clkx")
            |        if (!clkxDir.isDirectory) return@afterEvaluate
            |
            |        // Agent → list of (project-relative link path, clkx-relative target path)
            |        data class LinkSpec(val projectRel: String, val clkxRel: String)
            |        val agentLinks = mapOf(
            |            "claude-run" to listOf(
            |                LinkSpec(".claude/skills", "skills/claude"),
            |                LinkSpec(".claude/CLAUDE.md", "instructions/CLAUDE.md"),
            |            ),
            |            "copilot-run" to listOf(
            |                LinkSpec(".github/skills", "skills/copilot"),
            |                LinkSpec(".github/copilot-instructions.md", "instructions/copilot-instructions.md"),
            |            ),
            |            "codex-run" to listOf(
            |                LinkSpec(".agents/skills", "skills/codex"),
            |                LinkSpec("AGENTS.md", "instructions/AGENTS.md"),
            |            ),
            |            "opencode-run" to listOf(
            |                LinkSpec(".opencode/skills", "skills/opencode"),
            |            ),
            |        )
            |
            |        for ((taskName, specs) in agentLinks) {
            |            if (tasks.findByName(taskName) == null) continue
            |            for (spec in specs) {
            |                val linkPath = projectDir.toPath().resolve(spec.projectRel)
            |                val targetPath = clkxDir.toPath().resolve(spec.clkxRel)
            |                if (java.nio.file.Files.isSymbolicLink(linkPath)) {
            |                    val existing = java.nio.file.Files.readSymbolicLink(linkPath)
            |                    if (existing == targetPath ||
            |                        linkPath.parent.resolve(existing).normalize() == targetPath.normalize()) {
            |                        continue // already correct
            |                    }
            |                    java.nio.file.Files.delete(linkPath) // stale — remove and recreate
            |                } else if (linkPath.toFile().exists()) {
            |                    continue // real file/directory — don't overwrite
            |                }
            |                linkPath.parent?.toFile()?.mkdirs()
            |                try {
            |                    java.nio.file.Files.createSymbolicLink(linkPath, targetPath)
            |                } catch (_: Exception) {
            |                    // symlink failed (e.g. Windows without dev mode) — skip silently
            |                }
            |            }
            |        }
            |    }
            |}
            |""".trimMargin() + "\n"
    }
}
