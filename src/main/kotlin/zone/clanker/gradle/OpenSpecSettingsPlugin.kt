package zone.clanker.gradle

import zone.clanker.gradle.generators.ToolAdapterRegistry
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.tasks.*
import zone.clanker.gradle.tracking.ProposalScanner
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
        val PLUGIN_VERSION: String by lazy {
            OpenSpecSettingsPlugin::class.java.classLoader
                .getResourceAsStream("openspec-gradle.properties")
                ?.let { java.util.Properties().apply { load(it) }.getProperty("version") }
                ?: "0.0.0"
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

            project.tasks.register("opsx-sync", OpenSpecSyncTask::class.java).configure(object : org.gradle.api.Action<OpenSpecSyncTask> {
                override fun execute(task: OpenSpecSyncTask) {
                    task.tools.set(extension.tools)
                    task.outputDir.set(File(project.layout.buildDirectory.asFile.get(), "openspec"))
                    task.dependsOn("opsx-context", "opsx-tree", "opsx-deps", "opsx-modules", "opsx-devloop", "opsx-arch")
                }
            })

            project.tasks.register("opsx-context", OpenSpecContextTask::class.java).configure(object : org.gradle.api.Action<OpenSpecContextTask> {
                override fun execute(task: OpenSpecContextTask) {
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

            project.tasks.register("opsx-arch", OpenSpecArchTask::class.java).configure(object : org.gradle.api.Action<OpenSpecArchTask> {
                override fun execute(task: OpenSpecArchTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/arch.md"))
                }
            })

            project.tasks.register("opsx-propose", OpenSpecProposeTask::class.java)
            project.tasks.register("opsx-apply", OpenSpecApplyTask::class.java)
            project.tasks.register("opsx-archive", OpenSpecArchiveTask::class.java)

            // PSI-based tasks
            project.tasks.register("opsx-symbols", OpenSpecSymbolsTask::class.java).configure(object : org.gradle.api.Action<OpenSpecSymbolsTask> {
                override fun execute(task: OpenSpecSymbolsTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("file")) task.targetFile.set(project.property("file").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/symbols.md"))
                }
            })

            project.tasks.register("opsx-find", OpenSpecFindTask::class.java).configure(object : org.gradle.api.Action<OpenSpecFindTask> {
                override fun execute(task: OpenSpecFindTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/find.md"))
                }
            })

            project.tasks.register("opsx-rename", OpenSpecRenameTask::class.java).configure(object : org.gradle.api.Action<OpenSpecRenameTask> {
                override fun execute(task: OpenSpecRenameTask) {
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

            project.tasks.register("opsx-calls", OpenSpecCallsTask::class.java).configure(object : org.gradle.api.Action<OpenSpecCallsTask> {
                override fun execute(task: OpenSpecCallsTask) {
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/calls.md"))
                }
            })

            project.tasks.register("opsx-move", OpenSpecMoveTask::class.java).configure(object : org.gradle.api.Action<OpenSpecMoveTask> {
                override fun execute(task: OpenSpecMoveTask) {
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

            project.tasks.register("opsx-usages", OpenSpecUsagesTask::class.java).configure(object : org.gradle.api.Action<OpenSpecUsagesTask> {
                override fun execute(task: OpenSpecUsagesTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/usages.md"))
                }
            })

            project.tasks.register("opsx-extract", OpenSpecExtractTask::class.java).configure(object : org.gradle.api.Action<OpenSpecExtractTask> {
                override fun execute(task: OpenSpecExtractTask) {
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

            project.tasks.register("opsx-remove", OpenSpecRemoveTask::class.java).configure(object : org.gradle.api.Action<OpenSpecRemoveTask> {
                override fun execute(task: OpenSpecRemoveTask) {
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
            project.tasks.register("opsx-tree", OpenSpecTreeTask::class.java).configure(object : org.gradle.api.Action<OpenSpecTreeTask> {
                override fun execute(task: OpenSpecTreeTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("scope")) task.scope.set(project.property("scope").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/tree.md"))
                }
            })

            project.tasks.register("opsx-deps", OpenSpecDepsTask::class.java).configure(object : org.gradle.api.Action<OpenSpecDepsTask> {
                override fun execute(task: OpenSpecDepsTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/deps.md"))
                }
            })

            project.tasks.register("opsx-modules", OpenSpecModulesTask::class.java).configure(object : org.gradle.api.Action<OpenSpecModulesTask> {
                override fun execute(task: OpenSpecModulesTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/modules.md"))
                }
            })

            project.tasks.register("opsx-devloop", OpenSpecDevloopTask::class.java).configure(object : org.gradle.api.Action<OpenSpecDevloopTask> {
                override fun execute(task: OpenSpecDevloopTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/devloop.md"))
                }
            })

            // Dashboard task
            project.tasks.register("opsx-status", OpenSpecStatusTask::class.java).configure(object : org.gradle.api.Action<OpenSpecStatusTask> {
                override fun execute(task: OpenSpecStatusTask) {
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/status.md"))
                }
            })

            project.tasks.register("opsx-verify", OpenSpecVerifyTask::class.java).configure(object : org.gradle.api.Action<OpenSpecVerifyTask> {
                override fun execute(task: OpenSpecVerifyTask) {
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

            project.tasks.register("opsx-exec", OpenSpecExecTask::class.java).configure(object : org.gradle.api.Action<OpenSpecExecTask> {
                override fun execute(task: OpenSpecExecTask) {
                    if (project.hasProperty("prompt")) task.prompt.set(project.property("prompt").toString())
                    if (project.hasProperty("spec")) task.spec.set(project.property("spec").toString())
                    if (project.hasProperty("agent")) task.agent.set(project.property("agent").toString())
                    project.intProperty("maxRetries")?.let { task.maxRetries.set(it) }
                    if (project.hasProperty("verify")) task.verify.set(project.property("verify").toString().lowercase() == "true")
                    if (project.hasProperty("syncBefore")) task.syncBefore.set(project.property("syncBefore").toString().lowercase() == "true")
                    project.intProperty("execTimeout")?.let { task.execTimeout.set(it) }
                }
            })

            // Dynamic task registration from proposals
            registerProposalTasks(project)

            project.tasks.register("opsx-clean", OpenSpecCleanTask::class.java).configure(object : org.gradle.api.Action<OpenSpecCleanTask> {
                override fun execute(task: OpenSpecCleanTask) {
                    task.tools.set(extension.tools)
                }
            })

            // Register global gitignore for proposals
            project.tasks.register("opsx-install", OpenSpecInstallGlobalTask::class.java).configure(object : org.gradle.api.Action<OpenSpecInstallGlobalTask> {
                override fun execute(task: OpenSpecInstallGlobalTask) {
                    task.pluginVersion.set(PLUGIN_VERSION)
                    task.tools.set(extension.tools)
                    val publishTask = project.tasks.findByName("publishToMavenLocal")
                    if (publishTask != null) {
                        task.dependsOn(publishTask)
                    }
                }
            })

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
