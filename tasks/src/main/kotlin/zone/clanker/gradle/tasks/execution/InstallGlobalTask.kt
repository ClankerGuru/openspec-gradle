package zone.clanker.gradle.tasks.execution

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
        group = "opsx"
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
        if (!gradleProps.exists() || !gradleProps.readText().contains("zone.clanker.openspec.agents")) {
            gradleProps.appendText("\n# OpenSpec: comma-separated agents (github, claude, none)\nzone.clanker.openspec.agents=github\n")
            logger.lifecycle("OpenSpec: Added zone.clanker.openspec.agents=github to ${gradleProps.absolutePath}")
        }

        logger.lifecycle("")
        logger.lifecycle("OpenSpec: Installed global init script")
        logger.lifecycle("  Location: ${initScript.absolutePath}")
        logger.lifecycle("  Configure: zone.clanker.openspec.agents=github,claude in ~/.gradle/gradle.properties")
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
            |//   zone.clanker.openspec.agents=github          (default)
            |//   zone.clanker.openspec.agents=github,claude
            |//   zone.clanker.openspec.agents=none            (disables, cleans files)
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
            |        classpath("zone.clanker:openspec-gradle:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<zone.clanker.gradle.OpenSpecSettingsPlugin>()
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
            |        val lintingEnabled = System.getProperty("openspec.linting.enabled") != "false"
            |        if (!lintingEnabled) return@afterEvaluate
            |
            |        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
            |            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            |            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            |
            |        if (isKotlinProject) {
            |            apply<zone.clanker.gradle.linting.OpenSpecLintingPlugin>()
            |        }
            |    }
            |}
            |""".trimMargin() + "\n"
    }
}
