package zone.clanker.gradle

import zone.clanker.gradle.adapters.claude.ClaudeAdapter
import zone.clanker.gradle.adapters.codex.CodexAdapter
import zone.clanker.gradle.adapters.copilot.CopilotAdapter
import zone.clanker.gradle.adapters.opencode.OpenCodeAdapter
import zone.clanker.gradle.core.ProposalScanner
import zone.clanker.gradle.core.VersionInfo
import zone.clanker.gradle.core.OpenSpecExtension
import zone.clanker.gradle.generators.ToolAdapterRegistry
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.tasks.discovery.*
import zone.clanker.gradle.tasks.intelligence.*
import zone.clanker.gradle.tasks.refactoring.*
import zone.clanker.gradle.tasks.workflow.*
import zone.clanker.gradle.tasks.execution.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.initialization.Settings
import java.io.File

private fun Project.intProperty(name: String): Int? =
    if (hasProperty(name)) {
        val raw = property(name).toString()
        raw.toIntOrNull() ?: throw org.gradle.api.GradleException("Invalid integer value '$raw' for property '$name'")
    } else null

class OpenSpecSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject {
            applyToProject(this)
        }
    }

    companion object {
        val PLUGIN_VERSION: String get() = VersionInfo.PLUGIN_VERSION

        init {
            // Register all tool adapters
            ToolAdapterRegistry.register(ClaudeAdapter)
            ToolAdapterRegistry.register(CopilotAdapter)
            ToolAdapterRegistry.register(CodexAdapter)
            ToolAdapterRegistry.register(OpenCodeAdapter)
        }

        // Only aliases that differ from the registry tool ID
        private val AGENT_ALIASES = mapOf(
            "github" to "github-copilot"
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
                .mapNotNull { token ->
                    AGENT_ALIASES[token]
                        ?: token.takeIf { it in ToolAdapterRegistry.supportedTools() }
                }
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

            project.tasks.register("opsx").configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                override fun execute(task: org.gradle.api.Task) {
                    task.group = "opsx"
                    task.description = "List all OpenSpec tasks — the AI tool catalog for this project."
                    task.doLast(object : org.gradle.api.Action<org.gradle.api.Task> {
                    override fun execute(t: org.gradle.api.Task) {
                        val tasks = t.project.tasks
                            .filter { task -> task.name.startsWith("opsx-") && task.group == "opsx" }
                            .sortedBy { task -> task.name }
                        val maxLen = tasks.maxOfOrNull { task -> task.name.length } ?: 0
                        println("")
                        println("OpenSpec v$PLUGIN_VERSION — AI tool catalog")
                        println("─".repeat(60))
                        for (task in tasks) {
                            val desc = task.description ?: ""
                            println("  ${task.name.padEnd(maxLen + 2)} $desc")
                        }
                        println("")
                        println("Run any task:  ./gradlew <task-name>")
                        println("Full details:  ./gradlew help --task <task-name>")
                        println("")
                    }
                })
                }
            })

            // Shared input file collections for cacheable tasks
            val rootDir = project.rootProject.projectDir
            val sourceFileTree = project.files().apply {
                val projects = SourceDiscovery.resolveProjects(project, null)
                val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
                for (dir in srcDirs) {
                    from(project.fileTree(dir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                        override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                            ft.include("**/*.kt", "**/*.java")
                        }
                    }))
                }
            }
            val buildFileTree = project.rootProject.fileTree(rootDir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                    ft.include("build.gradle.kts", "settings.gradle.kts",
                        "gradle.properties", "*.lockfile", "gradle/libs.versions.toml")
                    ft.include("**/build.gradle.kts", "**/gradle.properties")
                    ft.exclude("build/", "**/build/", ".gradle/", "**/.gradle/")
                }
            })

            project.tasks.register("opsx-sync", SyncTask::class.java).configure(object : org.gradle.api.Action<SyncTask> {
                override fun execute(task: SyncTask) {
                    task.tools.set(extension.tools)
                    task.outputDir.set(File(project.layout.buildDirectory.asFile.get(), "openspec"))
                    task.dependsOn("opsx-context", "opsx-tree", "opsx-deps", "opsx-modules", "opsx-devloop", "opsx-arch")
                }
            })

            project.tasks.register("opsx-context", ContextTask::class.java).configure(object : org.gradle.api.Action<ContextTask> {
                override fun execute(task: ContextTask) {
                    // Collect build files as inputs
                    val rootDir = project.rootProject.projectDir
                    task.buildFiles.from(
                        project.rootProject.fileTree(rootDir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                            override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                                ft.include("build.gradle.kts", "settings.gradle.kts",
                                    "gradle.properties", "*.lockfile", "gradle/libs.versions.toml")
                                ft.include("**/build.gradle.kts", "**/gradle.properties")
                                ft.exclude("build/", "**/build/", ".gradle/", "**/.gradle/")
                            }
                        })
                    )
                    task.contextFile.set(project.layout.projectDirectory.file(".opsx/context.md"))
                }
            })

            project.tasks.register("opsx-arch", ArchTask::class.java).configure(object : org.gradle.api.Action<ArchTask> {
                override fun execute(task: ArchTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/arch.md"))
                }
            })

            project.tasks.register("opsx-propose", ProposeTask::class.java)
            project.tasks.register("opsx-apply", ApplyTask::class.java)
            project.tasks.register("opsx-archive", ArchiveTask::class.java)

            // PSI-based tasks
            project.tasks.register("opsx-symbols", SymbolsTask::class.java).configure(object : org.gradle.api.Action<SymbolsTask> {
                override fun execute(task: SymbolsTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("file")) task.targetFile.set(project.property("file").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/symbols.md"))
                }
            })

            project.tasks.register("opsx-find", FindTask::class.java).configure(object : org.gradle.api.Action<FindTask> {
                override fun execute(task: FindTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/find.md"))
                }
            })

            project.tasks.register("opsx-rename", RenameTask::class.java).configure(object : org.gradle.api.Action<RenameTask> {
                override fun execute(task: RenameTask) {
                    if (project.hasProperty("from")) task.from.set(project.property("from").toString())
                    if (project.hasProperty("to")) task.to.set(project.property("to").toString())
                    if (project.hasProperty("dryRun")) {
                        val value = project.property("dryRun").toString().lowercase()
                        if (value !in setOf("true", "false")) {
                            throw org.gradle.api.GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                        }
                        task.dryRun.set(value == "true")
                    }
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/rename.md"))
                    // Only auto-sync after successful mutation (not dry-run)
                    val isDryRun = project.hasProperty("dryRun") && project.property("dryRun").toString().lowercase() == "true"
                    if (!isDryRun) {
                        task.finalizedBy("opsx-sync")
                    }
                }
            })

            project.tasks.register("opsx-calls", CallsTask::class.java).configure(object : org.gradle.api.Action<CallsTask> {
                override fun execute(task: CallsTask) {
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/calls.md"))
                }
            })

            project.tasks.register("opsx-move", MoveTask::class.java).configure(object : org.gradle.api.Action<MoveTask> {
                override fun execute(task: MoveTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("targetPackage")) task.targetPackage.set(project.property("targetPackage").toString())
                    if (project.hasProperty("dryRun")) {
                        val value = project.property("dryRun").toString().lowercase()
                        if (value !in setOf("true", "false")) {
                            throw org.gradle.api.GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                        }
                        task.dryRun.set(value == "true")
                    }
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/move.md"))
                    val isDryRun = project.hasProperty("dryRun") && project.property("dryRun").toString().lowercase() == "true"
                    if (!isDryRun) {
                        task.finalizedBy("opsx-sync")
                    }
                }
            })

            project.tasks.register("opsx-usages", UsagesTask::class.java).configure(object : org.gradle.api.Action<UsagesTask> {
                override fun execute(task: UsagesTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/usages.md"))
                }
            })

            project.tasks.register("opsx-extract", ExtractTask::class.java).configure(object : org.gradle.api.Action<ExtractTask> {
                override fun execute(task: ExtractTask) {
                    if (project.hasProperty("sourceFile")) task.sourceFile.set(project.property("sourceFile").toString())
                    project.intProperty("startLine")?.let { task.startLine.set(it) }
                    project.intProperty("endLine")?.let { task.endLine.set(it) }
                    if (project.hasProperty("newName")) task.newName.set(project.property("newName").toString())
                    if (project.hasProperty("dryRun")) {
                        val value = project.property("dryRun").toString().lowercase()
                        if (value !in setOf("true", "false")) {
                            throw org.gradle.api.GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                        }
                        task.dryRun.set(value == "true")
                    }
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/extract.md"))
                    val isDryRun = project.hasProperty("dryRun") && project.property("dryRun").toString().lowercase() == "true"
                    if (!isDryRun) {
                        task.finalizedBy("opsx-sync")
                    }
                }
            })

            project.tasks.register("opsx-remove", RemoveTask::class.java).configure(object : org.gradle.api.Action<RemoveTask> {
                override fun execute(task: RemoveTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("file")) task.sourceFile.set(project.property("file").toString())
                    project.intProperty("startLine")?.let { task.startLine.set(it) }
                    project.intProperty("endLine")?.let { task.endLine.set(it) }
                    if (project.hasProperty("dryRun")) {
                        val value = project.property("dryRun").toString().lowercase()
                        if (value !in setOf("true", "false")) {
                            throw org.gradle.api.GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                        }
                        task.dryRun.set(value == "true")
                    }
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/remove.md"))
                    val isDryRun = project.hasProperty("dryRun") && project.property("dryRun").toString().lowercase() == "true"
                    if (!isDryRun) {
                        task.finalizedBy("opsx-sync")
                    }
                }
            })

            // Discovery tasks
            project.tasks.register("opsx-tree", TreeTask::class.java).configure(object : org.gradle.api.Action<TreeTask> {
                override fun execute(task: TreeTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("scope")) task.scope.set(project.property("scope").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/tree.md"))
                }
            })

            project.tasks.register("opsx-deps", DepsTask::class.java).configure(object : org.gradle.api.Action<DepsTask> {
                override fun execute(task: DepsTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/deps.md"))
                }
            })

            project.tasks.register("opsx-modules", ModulesTask::class.java).configure(object : org.gradle.api.Action<ModulesTask> {
                override fun execute(task: ModulesTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/modules.md"))
                }
            })

            project.tasks.register("opsx-devloop", DevloopTask::class.java).configure(object : org.gradle.api.Action<DevloopTask> {
                override fun execute(task: DevloopTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/devloop.md"))
                }
            })

            // Dashboard task
            project.tasks.register("opsx-status", StatusTask::class.java).configure(object : org.gradle.api.Action<StatusTask> {
                override fun execute(task: StatusTask) {
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/status.md"))
                }
            })

            project.tasks.register("opsx-verify", VerifyTask::class.java).configure(object : org.gradle.api.Action<VerifyTask> {
                override fun execute(task: VerifyTask) {
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    project.intProperty("maxWarnings")?.let { task.maxWarnings.set(it) }
                    if (project.hasProperty("failOnWarning")) task.failOnWarning.set(project.property("failOnWarning").toString().lowercase() == "true")
                    if (project.hasProperty("noCycles")) task.noCycles.set(project.property("noCycles").toString().lowercase() == "true")
                    project.intProperty("maxInheritanceDepth")?.let { task.maxInheritanceDepth.set(it) }
                    project.intProperty("maxClassSize")?.let { task.maxClassSize.set(it) }
                    project.intProperty("maxImports")?.let { task.maxImports.set(it) }
                    project.intProperty("maxMethods")?.let { task.maxMethods.set(it) }
                    if (project.hasProperty("noSmells")) task.noSmells.set(project.property("noSmells").toString().lowercase() == "true")
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/verify.md"))
                }
            })

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
                    if (project.hasProperty("opsx.verify")) task.verifyMode.set(project.property("opsx.verify").toString())
                }
            })

            // Dynamic task registration from proposals
            registerProposalTasks(project)

            project.tasks.register("opsx-clean", CleanTask::class.java).configure(object : org.gradle.api.Action<CleanTask> {
                override fun execute(task: CleanTask) {
                    task.tools.set(extension.tools)
                }
            })

            // Register global gitignore for proposals
            project.tasks.register("opsx-install", InstallGlobalTask::class.java).configure(object : org.gradle.api.Action<InstallGlobalTask> {
                override fun execute(task: InstallGlobalTask) {
                    task.pluginVersion.set(PLUGIN_VERSION)
                    task.tools.set(extension.tools)
                    val publishTask = project.tasks.findByName("publishToMavenLocal")
                    if (publishTask != null) {
                        task.dependsOn(publishTask)
                    }
                }
            })

            // Link tool configs from ~/.config/opsx
            project.tasks.register("opsx-link", OpsxLinkTask::class.java)

            // Hook opsx-clean into clean
            project.tasks.matching { it.name == "clean" }.configureEach {
                dependsOn("opsx-clean")
            }

            // Auto-generate discovery files on assemble (build depends on assemble, so this covers both)
            project.tasks.matching { it.name == "assemble" }.configureEach {
                dependsOn("opsx-sync")
            }
        }

        /**
         * Dynamically register Gradle tasks for each task item in all proposals.
         * Scans opsx/changes/ at configuration time.
         */
        private fun registerProposalTasks(project: Project) {
            val proposals = ProposalScanner.scan(project.projectDir)
            for (proposal in proposals) {
                for (taskItem in proposal.flatten()) {
                    if (taskItem.code.isBlank()) continue
                    val taskName = "opsx-${taskItem.code}"
                    // Avoid duplicate registration
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
        }
    }
}
