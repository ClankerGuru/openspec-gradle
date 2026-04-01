package zone.clanker.copilot

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

class CopilotPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            project.tasks.register("copilot-run", CopilotRunTask::class.java)
        })
    }
}

abstract class CopilotRunTask : DefaultTask() {

    @get:Input
    val prompt = project.objects.property(String::class.java)

    init {
        group = "copilot"
        description = "Run GitHub Copilot with a prompt"
    }

    @TaskAction
    fun run() {
        val promptValue = prompt.orNull
            ?: throw IllegalArgumentException("Missing required property 'prompt'. Pass -Pprompt=\"your prompt\"")

        val command = listOf(
            "copilot",
            "-p", promptValue,
            "--yolo",
            "-s",
            "--no-ask-user",
        )

        logger.lifecycle("Running: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .directory(project.rootDir)
            .redirectInput(File("/dev/null"))
            .redirectErrorStream(false)
            .start()

        // Stream stdout live
        val stdoutThread = Thread {
            process.inputStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    logger.lifecycle(line)
                }
            }
        }

        // Stream stderr live
        val stderrThread = Thread {
            process.errorStream.bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    logger.error(line)
                }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()

        if (exitCode != 0) {
            throw RuntimeException("copilot exited with code $exitCode")
        }
    }
}
