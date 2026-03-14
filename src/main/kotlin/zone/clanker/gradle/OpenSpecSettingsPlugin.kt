package zone.clanker.gradle

import zone.clanker.gradle.tasks.*
import zone.clanker.gradle.tracking.ProposalScanner
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import java.io.File

class OpenSpecSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject {
            applyToProject(this)
        }
    }

    companion object {
        val PLUGIN_VERSION: String by lazy {
            OpenSpecSettingsPlugin::class.java.classLoader
                .getResourceAsStream("openspec-gradle.properties")
                ?.let { java.util.Properties().apply { load(it) }.getProperty("version") }
                ?: "0.0.0"
        }

        private val AGENT_MAP = mapOf(
            "github" to "github-copilot",
            "claude" to "claude",
            "codex" to "codex",
            "opencode" to "opencode"
        )

        /**
         * Reads a property from a gradle.properties file.
         */
        private fun readGradleProperty(propsFile: File, key: String): String? {
            if (!propsFile.exists()) return null
            return try {
                val props = java.util.Properties()
                propsFile.inputStream().use { props.load(it) }
                props.getProperty(key)?.trim()
            } catch (_: Exception) {
                null
            }
        }

        fun parseAgents(value: String): List<String> {
            if (value.isBlank() || value == "none") return emptyList()
            return value.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it != "none" }
                .mapNotNull { AGENT_MAP[it] }
                .distinct()
        }

        internal fun applyToProject(project: Project) {
            // Guard against double-application
            if (project.extensions.findByName("openspec") != null) {
                return
            }

            val extension = project.extensions.create("openspec", OpenSpecExtension::class.java)

            // Resolve agents lazily via provider so project-level gradle.properties is visible
            // even when applied from an init script (beforeSettings).
            // Falls back to reading the project's gradle.properties file directly
            // since findProperty() may not see project-level properties from init scripts.
            val toolsProvider = project.provider {
                val prop = "zone.clanker.openspec.agents"
                // Resolution order: -P flag > project gradle.properties > global ~/.gradle/gradle.properties > default
                val agentsProp = project.findProperty(prop)?.toString()?.trim()
                    ?: readGradleProperty(File(project.projectDir, "gradle.properties"), prop)
                    ?: readGradleProperty(File(System.getProperty("user.home"), ".gradle/gradle.properties"), prop)
                    ?: "github"
                parseAgents(agentsProp)
            }
            extension.tools.set(toolsProvider)

            project.tasks.register("openspecSync", OpenSpecSyncTask::class.java).configure(object : org.gradle.api.Action<OpenSpecSyncTask> {
                override fun execute(task: OpenSpecSyncTask) {
                    task.tools.set(extension.tools)
                    task.outputDir.set(File(project.layout.buildDirectory.asFile.get(), "openspec"))
                    task.dependsOn("openspecContext")
                }
            })

            project.tasks.register("openspecContext", OpenSpecContextTask::class.java).configure(object : org.gradle.api.Action<OpenSpecContextTask> {
                override fun execute(task: OpenSpecContextTask) {
                    // Collect build files as inputs
                    val rootDir = project.rootProject.projectDir
                    task.buildFiles.from(
                        project.rootProject.fileTree(rootDir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                            override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                                ft.include("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                                    "gradle.properties", "*.lockfile", "gradle/libs.versions.toml")
                                ft.include("**/build.gradle", "**/build.gradle.kts", "**/gradle.properties")
                                ft.exclude("build/", "**/build/", ".gradle/", "**/.gradle/")
                            }
                        })
                    )
                    task.contextFile.set(project.layout.projectDirectory.file(".openspec/context.md"))
                }
            })

            project.tasks.register("openspecPropose", OpenSpecProposeTask::class.java)
            project.tasks.register("openspecApply", OpenSpecApplyTask::class.java)
            project.tasks.register("openspecArchive", OpenSpecArchiveTask::class.java)

            // Dashboard task
            project.tasks.register("openspecStatus", OpenSpecStatusTask::class.java)

            // Dynamic task registration from proposals
            registerProposalTasks(project)

            project.tasks.register("openspecClean", OpenSpecCleanTask::class.java).configure(object : org.gradle.api.Action<OpenSpecCleanTask> {
                override fun execute(task: OpenSpecCleanTask) {
                    task.tools.set(extension.tools)
                }
            })

            // Register global gitignore for proposals
            project.tasks.register("openspecInstallGlobal", OpenSpecInstallGlobalTask::class.java).configure(object : org.gradle.api.Action<OpenSpecInstallGlobalTask> {
                override fun execute(task: OpenSpecInstallGlobalTask) {
                    task.pluginVersion.set(PLUGIN_VERSION)
                    task.tools.set(extension.tools)
                    val publishTask = project.tasks.findByName("publishToMavenLocal")
                    if (publishTask != null) {
                        task.dependsOn(publishTask)
                    }
                }
            })
        }

        /**
         * Dynamically register Gradle tasks for each task item in all proposals.
         * Scans openspec/changes/ at configuration time.
         */
        private fun registerProposalTasks(project: Project) {
            val proposals = ProposalScanner.scan(project.projectDir)
            for (proposal in proposals) {
                for (taskItem in proposal.flatten()) {
                    if (taskItem.code.isBlank()) continue
                    val taskName = "openspecTask-${taskItem.code}"
                    // Avoid duplicate registration
                    if (project.tasks.findByName(taskName) != null) continue
                    project.tasks.register(taskName, OpenSpecTaskItemTask::class.java)
                        .configure(object : org.gradle.api.Action<OpenSpecTaskItemTask> {
                            override fun execute(task: OpenSpecTaskItemTask) {
                                task.taskCode.set(taskItem.code)
                                task.proposalName.set(proposal.name)
                                task.description = "[task] ${taskItem.status.icon} ${taskItem.code}: ${taskItem.description}"
                            }
                        })
                }
            }
        }
    }
}
