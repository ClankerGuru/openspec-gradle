package zone.clanker.wrkx

import org.gradle.api.plugins.ExtensionAware
import zone.clanker.gradle.core.WrkxExtension
import zone.clanker.gradle.core.WrkxRepo
import zone.clanker.gradle.core.RepoEntry
import zone.clanker.gradle.tasks.execution.CheckoutTask
import zone.clanker.gradle.tasks.execution.CloneTask
import zone.clanker.gradle.tasks.execution.PullTask
import zone.clanker.gradle.tasks.execution.ReposTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

abstract class WrkxPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        // Check if disabled via gradle.properties
        if (settings.providers.gradleProperty("zone.clanker.wrkx.enabled").orNull?.lowercase() == "false") return
        // Guard against double-application (init script + settings.gradle.kts)
        if (settings.extensions.findByName("wrkx") != null ||
            settings.extensions.findByName("monolith") != null) return

        val home = System.getProperty("user.home")
        val settingsDir = settings.settingsDir

        fun resolvePath(raw: String): File {
            val expanded = raw.replace("~", home)
            return if (File(expanded).isAbsolute) File(expanded) else File(settingsDir, expanded)
        }

        val rawPath = settings.providers.gradleProperty("zone.clanker.wrkx.configFile").orNull
        val configFile = if (rawPath != null) resolvePath(rawPath) else File(settingsDir, "workspace.json")
        val entries = if (configFile.exists()) RepoEntry.parseFile(configFile) else emptyList()

        val repoDir = settings.providers.gradleProperty("zone.clanker.wrkx.repoDir")
            .orNull?.let { resolvePath(it) } ?: File(settingsDir.parentFile, "${settingsDir.name}-repos")

        val extension = settings.extensions.create("wrkx", WrkxExtension::class.java)
        extension.baseDir = repoDir
        for (entry in entries) {
            require(entry.name.isNotBlank()) { "${configFile.name} contains a repo with blank 'name'" }
            val propertyName = WrkxExtension.toCamelCase(entry.directoryName)
            val repo = WrkxRepo(
                repoName = entry.name,
                category = entry.category,
                substitutions = entry.substitutions,
                defaultEnabled = entry.enable,
                defaultSubstitute = entry.substitute,
                defaultRef = entry.ref
            )
            repo.clonePath = File(repoDir, entry.directoryName)
            extension.register(propertyName, repo)
            (extension as ExtensionAware).extensions.add(propertyName, repo)
        }

        extension.includeAction = { repo ->
            if (repo.clonePath.exists()) {
                settings.includeBuild(repo.clonePath, org.gradle.api.Action { spec ->
                    spec.name = repo.sanitizedBuildName
                    if (repo.substitute && repo.substitutions.isNotEmpty()) {
                        spec.dependencySubstitution(org.gradle.api.Action { sub ->
                            repo.substitutions.forEach { dep ->
                                val (artifact, projectName) = RepoEntry.parseSubstitution(dep)
                                val projectPath = if (projectName == ":" || projectName.isBlank()) ":" else ":$projectName"
                                sub.substitute(sub.module(artifact)).using(sub.project(projectPath))
                            }
                        })
                    }
                })
            }
        }

        // Auto-include enabled repos if workspace config exists
        if (entries.isNotEmpty()) {
            extension.includeEnabled()
        }

        settings.gradle.rootProject(org.gradle.api.Action { project ->
            applyToSettings(project, extension)
        })
    }

    companion object {
        const val ENABLED_PROP = "zone.clanker.wrkx.enabled"

        internal fun applyToSettings(project: org.gradle.api.Project, extension: WrkxExtension) {
            if (project.tasks.findByName("wrkx-clone") != null) return

            // Catalog task
            project.tasks.register("wrkx").configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                override fun execute(task: org.gradle.api.Task) {
                    task.group = "wrkx"
                    task.description = "List all workspace tasks."
                    task.doLast(object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(t: org.gradle.api.Task) {
                            println()
                            println("Workspace Tasks (wrkx)")
                            println("\u2500".repeat(40))
                            println()
                            println("  wrkx-clone       Clone all repos from workspace.json")
                            println("  wrkx-pull        Pull latest for all repos")
                            println("  wrkx-checkout    Checkout branch across repos")
                            println("  wrkx-repos       List workspace repos and status")
                            println()
                            println("Run any task:  ./gradlew <task-name>")
                            println("Full details:  ./gradlew help --task <task-name>")
                            println()
                        }
                    })
                }
            })

            project.tasks.register("wrkx-checkout", CheckoutTask::class.java).configure(org.gradle.api.Action {
                it.extensionRepos.addAll(extension.allEntries())
            })

            project.tasks.register("wrkx-pull", PullTask::class.java).configure(org.gradle.api.Action {
                it.extensionRepos.addAll(extension.allEntries())
            })

            project.tasks.register("wrkx-repos", ReposTask::class.java).configure(org.gradle.api.Action {
                it.outputFile.set(project.layout.projectDirectory.file(".opsx/repos.md"))
                it.extensionRepos.addAll(extension.allEntries())
            })

            project.tasks.register("wrkx-clone", CloneTask::class.java).configure(org.gradle.api.Action {
                val home = System.getProperty("user.home")
                val rootDir = project.rootDir
                fun resolveProjectPath(raw: String): String {
                    val expanded = raw.replace("~", home)
                    return if (java.io.File(expanded).isAbsolute) expanded else java.io.File(rootDir, expanded).absolutePath
                }
                it.reposDir.convention(
                    project.provider {
                        project.findProperty("zone.clanker.wrkx.repoDir")?.toString()
                            ?.let { raw -> resolveProjectPath(raw) }
                            ?: java.io.File(rootDir.parentFile, "${rootDir.name}-repos").absolutePath
                    }
                )
                it.reposFile.convention(
                    project.provider {
                        project.findProperty("zone.clanker.wrkx.configFile")?.toString()
                            ?.let { raw -> resolveProjectPath(raw) }
                            ?: "${rootDir.absolutePath}/workspace.json"
                    }
                )
                it.extensionRepos.addAll(extension.allEntries())
            })

        }
    }
}
