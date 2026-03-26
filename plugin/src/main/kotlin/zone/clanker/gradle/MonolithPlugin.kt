package zone.clanker.gradle

import org.gradle.api.GradleException
import zone.clanker.gradle.core.MonolithExtension
import zone.clanker.gradle.core.MonolithRepo
import zone.clanker.gradle.core.RepoEntry
import zone.clanker.gradle.tasks.execution.CloneTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

abstract class MonolithPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val home = System.getProperty("user.home")
        val defaultDir = "$home/dev/monolith"

        val rawPath = settings.providers.gradleProperty("zone.clanker.openspec.monolithFile")
            .orNull ?: "$defaultDir/monolith.json"
        val configFile = File(rawPath.replace("~", home))
        val entries = if (configFile.exists()) RepoEntry.parseFile(configFile) else emptyList()

        val extension = MonolithExtension()
        for (entry in entries) {
            require(entry.name.isNotBlank()) { "monolith.json contains a repo with blank 'name'" }
            val propertyName = MonolithExtension.toCamelCase(entry.directoryName)
            extension.register(propertyName, MonolithRepo(
                repoName = entry.name,
                category = entry.category,
                substitutions = entry.substitutions,
                defaultEnabled = entry.enable
            ))
        }
        settings.extensions.add("monolith", extension)

        settings.gradle.rootProject {
            applyToSettings(this, extension)
        }
    }

    companion object {
        internal fun applyToSettings(project: org.gradle.api.Project, extension: MonolithExtension) {
            if (project.tasks.findByName("opsx-clone") != null) return

            project.tasks.register("opsx-clone", CloneTask::class.java).configure {
                val home = System.getProperty("user.home")
                reposDir.convention(
                    project.provider {
                        project.findProperty("zone.clanker.openspec.monolithDir")?.toString()
                            ?: "$home/dev/monolith"
                    }
                )
                reposFile.convention(
                    project.provider {
                        project.findProperty("zone.clanker.openspec.monolithFile")?.toString()
                            ?: "$home/dev/monolith/monolith.json"
                    }
                )
                if (project.hasProperty("dryRun")) {
                    val value = project.property("dryRun").toString().lowercase()
                    if (value !in setOf("true", "false")) {
                        throw GradleException("Invalid dryRun value '$value' — must be 'true' or 'false'")
                    }
                    dryRun.set(value == "true")
                } else {
                    dryRun.convention(true)
                }

                extensionRepos.addAll(extension.allEntries())
            }
        }
    }
}
