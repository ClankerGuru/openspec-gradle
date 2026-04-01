package zone.clanker.opencode

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class OpencodeRunTask : DefaultTask() {

    @get:Input
    abstract val prompt: Property<String>

    init {
        group = "opencode"
        description = "Run opencode with a prompt"
    }

    @TaskAction
    fun run() {
        val promptValue = prompt.get()
        val command = listOf("opencode", "run", "--prompt", promptValue)

        logger.lifecycle("Running: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .directory(project.rootDir)
            .redirectInput(File("/dev/null"))
            .start()

        // Stream stdout live
        val stdoutThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { logger.lifecycle(it) }
            }
        }
        // Stream stderr live
        val stderrThread = Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { logger.error(it) }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        stdoutThread.join()
        stderrThread.join()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw org.gradle.api.GradleException("opencode exited with code $exitCode")
        }
    }
}

class OpencodePlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            project.tasks.register("opencode-run", OpencodeRunTask::class.java) { task ->
                task.prompt.set(project.providers.gradleProperty("prompt").orElse(""))
            }
        })
    }
}
