package zone.clanker.claude

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ClaudeRunTask : DefaultTask() {

    @get:Input
    abstract val prompt: Property<String>

    @get:Input
    @get:Optional
    abstract val model: Property<String>

    @get:Input
    @get:Optional
    abstract val outputFormat: Property<String>

    init {
        group = "claude"
        description = "Run Claude Code CLI with a prompt"
    }

    @TaskAction
    fun run() {
        val promptValue = prompt.orNull
            ?: throw GradleException("Required property 'prompt' is missing. Pass -Pprompt=\"your prompt\"")

        val cmd = mutableListOf("claude", "-p", promptValue, "--permission-mode", "bypassPermissions")

        model.orNull?.let { cmd.addAll(listOf("--model", it)) }

        val format = outputFormat.orNull ?: "text"
        cmd.addAll(listOf("--output-format", format))

        logger.lifecycle("Running: ${cmd.joinToString(" ")}")

        val process = ProcessBuilder(cmd)
            .directory(project.rootDir)
            .redirectInput(File("/dev/null"))
            .start()

        val stdoutThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { logger.lifecycle(it) }
            }
        }
        val stderrThread = Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { logger.error(it) }
            }
        }

        stdoutThread.start()
        stderrThread.start()

        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()

        if (exitCode != 0) {
            throw GradleException("claude exited with code $exitCode")
        }
    }
}

class ClaudePlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            project.tasks.register("claude-run", ClaudeRunTask::class.java).configure(
                org.gradle.api.Action { task ->
                    if (project.hasProperty("prompt")) {
                        task.prompt.set(project.property("prompt").toString())
                    }
                    if (project.hasProperty("claudeModel")) {
                        task.model.set(project.property("claudeModel").toString())
                    }
                    if (project.hasProperty("outputFormat")) {
                        task.outputFormat.set(project.property("outputFormat").toString())
                    }
                }
            )
        })
    }
}
