package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.OPSX_GROUP

import zone.clanker.gradle.generators.GlobalGitignore
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Installs init scripts to user Gradle home")
abstract class InstallGlobalTask : DefaultTask() {

    @get:Input
    abstract val pluginVersion: Property<String>

    @get:Input
    abstract val tools: ListProperty<String>

    init {
        group = OPSX_GROUP
        description = "[tool] Install plugin globally via init scripts. " +
            "Use when: Setting up the plugin on a new machine or updating."
    }

    @TaskAction
    fun install() {
        val gradleHome = project.gradle.gradleUserHomeDir
        val initDir = File(gradleHome, "init.d")
        initDir.mkdirs()

        val version = pluginVersion.get()
        val agents = tools.getOrElse(listOf("claude", "copilot"))

        // Remove legacy single init script
        val legacyScript = File(initDir, "openspec.init.gradle.kts")
        if (legacyScript.exists()) {
            legacyScript.delete()
            logger.lifecycle("OpenSpec: Removed legacy init script")
        }

        // Write numbered init scripts
        File(initDir, "00-wrkx.init.gradle.kts").writeText(generateWrkxInitScript(version))
        logger.lifecycle("  [00] wrkx — workspace management")

        File(initDir, "01-srcx.init.gradle.kts").writeText(generateSrcxInitScript(version))
        logger.lifecycle("  [01] srcx — source intelligence")

        File(initDir, "02-opsx.init.gradle.kts").writeText(generateOpsxInitScript(version))
        logger.lifecycle("  [02] opsx — workflow engine")

        for (agent in agents) {
            val (artifactId, pluginClass, fileName) = when (agent.lowercase().trim()) {
                "claude" -> Triple("plugin-claude", "zone.clanker.claude.ClaudePlugin", "03-claude.init.gradle.kts")
                "copilot", "github", "github-copilot" -> Triple("plugin-copilot", "zone.clanker.copilot.CopilotPlugin", "03-copilot.init.gradle.kts")
                "codex" -> Triple("plugin-codex", "zone.clanker.codex.CodexPlugin", "03-codex.init.gradle.kts")
                "opencode" -> Triple("plugin-opencode", "zone.clanker.opencode.OpencodePlugin", "03-opencode.init.gradle.kts")
                else -> { logger.warn("  Unknown agent: $agent (skipping)"); continue }
            }
            File(initDir, fileName).writeText(generateAgentInitScript(version, artifactId, pluginClass))
            logger.lifecycle("  [03] $agent")
        }

        // Ensure OpenSpec patterns in global gitignore
        GlobalGitignore.ensurePatterns(logger)

        // Set agent config in gradle.properties
        val gradleProps = File(gradleHome, "gradle.properties")
        val agentsValue = agents.joinToString(",") { it.lowercase().trim() }
        val propsText = if (gradleProps.exists()) gradleProps.readText() else ""
        if (propsText.contains("zone.clanker.opsx.agents")) {
            gradleProps.writeText(
                propsText.replace(
                    Regex("zone\\.clanker\\.opsx\\.agents=.*"),
                    "zone.clanker.opsx.agents=$agentsValue"
                )
            )
        } else if (gradleProps.exists()) {
            gradleProps.appendText("\nzone.clanker.opsx.agents=$agentsValue\n")
        } else {
            gradleProps.writeText("zone.clanker.opsx.agents=$agentsValue\n")
        }
        logger.lifecycle("  Set zone.clanker.opsx.agents=$agentsValue in ${gradleProps.absolutePath}")

        // Create ~/.clkx/ directory structure
        val clkxDir = File(System.getProperty("user.home"), ".clkx")
        listOf("skills/claude", "skills/copilot", "skills/codex", "skills/opencode", "instructions").forEach {
            File(clkxDir, it).mkdirs()
        }

        logger.lifecycle("")
        logger.lifecycle("OpenSpec: Installed ${3 + agents.size} init scripts to $initDir")
        logger.lifecycle("")
        logger.lifecycle("  Next: run './gradlew opsx-sync' in any project to populate ~/.clkx/ with skills.")
        logger.lifecycle("  To uninstall: bash install.sh --uninstall")
    }

    companion object {
        fun generateWrkxInitScript(version: String): String = """
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:plugin-wrkx:$version")
            |        classpath("zone.clanker:wrkx-tasks:$version")
            |        classpath("zone.clanker:openspec-core:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<zone.clanker.wrkx.WrkxPlugin>()
            |}
            |""".trimMargin() + "\n"

        fun generateSrcxInitScript(version: String): String = """
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:plugin-srcx:$version")
            |        classpath("zone.clanker:srcx-tasks:$version")
            |        classpath("zone.clanker:openspec-core:$version")
            |        classpath("zone.clanker:openspec-psi:$version")
            |        classpath("zone.clanker:openspec-arch:$version")
            |        classpath("zone.clanker:quality:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<zone.clanker.srcx.SrcxPlugin>()
            |}
            |""".trimMargin() + "\n"

        fun generateOpsxInitScript(version: String): String = """
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:plugin-opsx:$version")
            |        classpath("zone.clanker:opsx-tasks:$version")
            |        classpath("zone.clanker:openspec-core:$version")
            |        classpath("zone.clanker:openspec-generators:$version")
            |        classpath("zone.clanker:openspec-psi:$version")
            |        classpath("zone.clanker:openspec-exec:$version")
            |        classpath("zone.clanker:openspec-adapter-claude:$version")
            |        classpath("zone.clanker:openspec-adapter-copilot:$version")
            |        classpath("zone.clanker:openspec-adapter-codex:$version")
            |        classpath("zone.clanker:openspec-adapter-opencode:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<zone.clanker.opsx.OpsxPlugin>()
            |}
            |""".trimMargin() + "\n"

        fun generateAgentInitScript(version: String, artifactId: String, pluginClass: String): String = """
            |initscript {
            |    repositories {
            |        mavenLocal()
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |    dependencies {
            |        classpath("zone.clanker:$artifactId:$version")
            |    }
            |}
            |
            |beforeSettings {
            |    apply<$pluginClass>()
            |}
            |""".trimMargin() + "\n"
    }
}
