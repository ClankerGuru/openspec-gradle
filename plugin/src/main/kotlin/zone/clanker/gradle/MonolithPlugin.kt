package zone.clanker.gradle

import org.gradle.api.GradleException
import zone.clanker.gradle.tasks.execution.CloneTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

abstract class MonolithPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject {
            applyToSettings(this)
        }
    }

    companion object {
        internal fun applyToSettings(project: org.gradle.api.Project) {
            if (project.tasks.findByName("opsx-clone") != null) return

            project.tasks.register("opsx-clone", CloneTask::class.java).configure{
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
            }
        }
    }
}
