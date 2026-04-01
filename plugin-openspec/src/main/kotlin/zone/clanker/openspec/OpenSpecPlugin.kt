package zone.clanker.openspec

import zone.clanker.gradle.adapters.claude.ClaudeAdapter
import zone.clanker.gradle.adapters.codex.CodexAdapter
import zone.clanker.gradle.adapters.copilot.CopilotAdapter
import zone.clanker.gradle.adapters.opencode.OpenCodeAdapter
import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.VersionInfo
import zone.clanker.gradle.core.OpenSpecExtension
import zone.clanker.gradle.generators.ToolAdapterRegistry
import zone.clanker.gradle.tasks.workflow.*
import zone.clanker.gradle.tasks.execution.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import java.io.File

private fun Project.intProperty(name: String): Int? =
    if (hasProperty(name)) {
        val raw = property(name).toString()
        raw.toIntOrNull() ?: throw org.gradle.api.GradleException("Invalid integer value '$raw' for property '$name'")
    } else null

class OpenSpecPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject {
            applyToProject(this)
        }
    }

    companion object {
        val PLUGIN_VERSION: String get() = VersionInfo.PLUGIN_VERSION

        init {
            ToolAdapterRegistry.register(ClaudeAdapter)
            ToolAdapterRegistry.register(CopilotAdapter)
            ToolAdapterRegistry.register(CodexAdapter)
            ToolAdapterRegistry.register(OpenCodeAdapter)
        }

        private val AGENT_ALIASES = mapOf("github" to "github-copilot")

        private fun readGradleProperty(propsFile: File, key: String): String? {
            if (!propsFile.exists()) return null
            return try {
                val props = java.util.Properties()
                propsFile.inputStream().use { props.load(it) }
                props.getProperty(key)?.trim()
            } catch (_: Exception) { null }
        }

        fun parseAgents(value: String): List<String> {
            if (value.isBlank() || value == "none") return emptyList()
            return value.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() && it != "none" }
                .mapNotNull { token ->
                    AGENT_ALIASES[token] ?: token.takeIf { it in ToolAdapterRegistry.supportedTools() }
                }
                .distinct()
        }

        internal fun applyToProject(project: Project) {
            if (project.extensions.findByName("openspec") != null) return

            val extension = project.extensions.create("openspec", OpenSpecExtension::class.java)

            val toolsProvider = project.provider {
                val prop = "zone.clanker.openspec.agents"
                val agentsProp = project.findProperty(prop)?.toString()?.trim()
                    ?: readGradleProperty(File(project.projectDir, "gradle.properties"), prop)
                    ?: readGradleProperty(File(System.getProperty("user.home"), ".gradle/gradle.properties"), prop)
                    ?: "github"
                parseAgents(agentsProp)
            }
            extension.tools.set(toolsProvider)

            // Catalog task
            project.tasks.register("opsx").configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                override fun execute(task: org.gradle.api.Task) {
                    task.group = "opsx"
                    task.description = "List all OpenSpec tasks — the AI tool catalog for this project."
                    task.doLast(object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(t: org.gradle.api.Task) {
                            val tasks = t.project.tasks
                                .filter { it.name.startsWith("opsx-") && it.group == "opsx" }
                                .sortedBy { it.name }
                            val maxLen = tasks.maxOfOrNull { it.name.length } ?: 0
                            println("\nOpenSpec v$PLUGIN_VERSION — AI tool catalog")
                            println("─".repeat(60))
                            for (t2 in tasks) { println("  ${t2.name.padEnd(maxLen + 2)} ${t2.description ?: ""}") }
                            println("\nRun any task:  ./gradlew <task-name>")
                            println("Full details:  ./gradlew help --task <task-name>\n")
                        }
                    })
                }
            })

            // Sync
            project.tasks.register("opsx-sync", SyncTask::class.java).configure(object : org.gradle.api.Action<SyncTask> {
                override fun execute(task: SyncTask) {
                    task.tools.set(extension.tools)
                    task.outputDir.set(File(project.layout.buildDirectory.asFile.get(), "openspec"))
                    task.dependsOn("opsx-context", "opsx-tree", "opsx-deps", "opsx-modules", "opsx-devloop", "opsx-arch")
                }
            })

            // Workflow
            project.tasks.register("opsx-propose", ProposeTask::class.java)
            project.tasks.register("opsx-apply", ApplyTask::class.java)
            project.tasks.register("opsx-archive", ArchiveTask::class.java)
            project.tasks.register("opsx-status", StatusTask::class.java).configure(object : org.gradle.api.Action<StatusTask> {
                override fun execute(task: StatusTask) {
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/status.md"))
                }
            })

            // Execution
            project.tasks.register("opsx-exec", ExecTask::class.java).configure(object : org.gradle.api.Action<ExecTask> {
                override fun execute(task: ExecTask) {
                    if (project.hasProperty("task")) task.taskCodes.set(project.property("task").toString())
                    if (project.hasProperty("prompt")) task.prompt.set(project.property("prompt").toString())
                    if (project.hasProperty("spec")) task.spec.set(project.property("spec").toString())
                    if (project.hasProperty("agent")) task.agent.set(project.property("agent").toString())
                    project.intProperty("maxRetries")?.let { task.maxRetries.set(it) }
                    if (project.hasProperty("verify")) task.verify.set(project.property("verify").toString().lowercase() == "true")
                    if (project.hasProperty("syncBefore")) task.syncBefore.set(project.property("syncBefore").toString().lowercase() == "true")
                    project.intProperty("execTimeout")?.let { task.execTimeout.set(it) }
                    if (project.hasProperty("parallel")) task.parallel.set(project.property("parallel").toString().lowercase() == "true")
                    project.intProperty("parallelThreads")?.let { task.parallelThreads.set(it) }
                    val verifyProp = when {
                        project.hasProperty("opsx.verify") -> project.property("opsx.verify").toString().trim().lowercase()
                        project.hasProperty("zone.clanker.openspec.verifyCommand") -> project.property("zone.clanker.openspec.verifyCommand").toString().trim().lowercase()
                        else -> null
                    }
                    if (verifyProp != null) task.verifyMode.set(verifyProp)
                }
            })

            project.tasks.register("opsx-clean", CleanTask::class.java).configure(object : org.gradle.api.Action<CleanTask> {
                override fun execute(task: CleanTask) { task.tools.set(extension.tools) }
            })

            project.tasks.register("opsx-install", InstallGlobalTask::class.java).configure(object : org.gradle.api.Action<InstallGlobalTask> {
                override fun execute(task: InstallGlobalTask) {
                    task.pluginVersion.set(PLUGIN_VERSION)
                    task.tools.set(extension.tools)
                    val publishTask = project.tasks.findByName("publishToMavenLocal")
                    if (publishTask != null) task.dependsOn(publishTask)
                }
            })

            project.tasks.register("opsx-link", OpsxLinkTask::class.java)

            // Dynamic proposal tasks
            val proposals = ProposalScanner.scan(project.projectDir)
            for (proposal in proposals) {
                for (taskItem in proposal.flatten()) {
                    if (taskItem.code.isBlank()) continue
                    val taskName = "opsx-${taskItem.code}"
                    if (project.tasks.findByName(taskName) != null) continue
                    project.tasks.register(taskName, TaskItemTask::class.java)
                        .configure(object : org.gradle.api.Action<TaskItemTask> {
                            override fun execute(task: TaskItemTask) {
                                task.taskCode.set(taskItem.code)
                                task.proposalName.set(proposal.name)
                                task.description = "[task] ${taskItem.status.icon} ${taskItem.code}: ${taskItem.description}"
                            }
                        })
                }
            }

            // Hooks
            project.tasks.matching { it.name == "clean" }.configureEach { dependsOn("opsx-clean") }
            // opsx-sync is NOT hooked into assemble — run explicitly or via verify
        }
    }
}
