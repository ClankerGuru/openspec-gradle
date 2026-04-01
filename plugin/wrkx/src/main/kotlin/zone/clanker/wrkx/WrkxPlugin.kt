package zone.clanker.wrkx

import org.gradle.api.GradleException
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
        // Guard against double-application (init script + settings.gradle.kts)
        if (settings.extensions.findByName("wrkx") != null ||
            settings.extensions.findByName("monolith") != null) return

        val home = System.getProperty("user.home")
        val defaultDir = settings.settingsDir.absolutePath

        val rawPath = settings.providers.gradleProperty("zone.clanker.wrkx.configFile").orNull
        val configFile = if (rawPath != null) {
            File(rawPath.replace("~", home))
        } else {
            File("$defaultDir/workspace.json")
        }
        val entries = if (configFile.exists()) RepoEntry.parseFile(configFile) else emptyList()

        val monolithDir = settings.providers.gradleProperty("zone.clanker.wrkx.repoDir")
            .orNull?.let { File(it.replace("~", home)) } ?: File(defaultDir)

        val extension = settings.extensions.create("wrkx", WrkxExtension::class.java)
        extension.baseDir = monolithDir
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
            repo.clonePath = File(monolithDir, entry.directoryName)
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
                it.reposDir.convention(
                    project.provider {
                        project.findProperty("zone.clanker.wrkx.repoDir")?.toString()
                            ?: project.rootDir.absolutePath
                    }
                )
                it.reposFile.convention(
                    project.provider {
                        project.findProperty("zone.clanker.wrkx.configFile")?.toString()
                            ?: "${project.rootDir.absolutePath}/workspace.json"
                    }
                )
                if (project.hasProperty("dryRun")) {
                    val value = project.property("dryRun").toString().lowercase()
                    if (value !in setOf("true", "false")) {
                        throw GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                    }
                    it.dryRun.set(value == "true")
                } else {
                    it.dryRun.convention(true)
                }

                it.extensionRepos.addAll(extension.allEntries())
            })

            // Aggregate: wire root opsx tasks to all included builds.
            // Only cacheable discovery + lifecycle tasks propagate — they're free when UP-TO-DATE.
            // Intelligence tasks (find, calls, usages, verify) are parameterized and expensive,
            // so they stay per-build: use ./gradlew :gort:srcx-find -Psymbol=Foo
            project.afterEvaluate {
                val aggregate = project.findProperty("zone.clanker.wrkx.aggregate")?.toString() != "false"
                if (!aggregate) return@afterEvaluate

                val tasksToAggregate = listOf(
                    // Lifecycle
                    "opsx-sync", "opsx-clean",
                    // Discovery (all @CacheableTask — skip when inputs unchanged)
                    "srcx-context", "srcx-tree", "srcx-modules", "srcx-deps", "srcx-devloop", "srcx-symbols", "srcx-arch",
                )

                for (taskName in tasksToAggregate) {
                    val rootTask = project.tasks.findByName(taskName) ?: continue
                    for (build in project.gradle.includedBuilds) {
                        try {
                            rootTask.dependsOn(build.task(":$taskName"))
                        } catch (_: Exception) {
                            // Included build may not have this task
                        }
                    }
                }
            }
        }
    }
}
