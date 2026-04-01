package zone.clanker.codex

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.util.concurrent.TimeUnit

class CodexPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject { project ->
            project.tasks.register("codex-exec", CodexExecTask::class.java) {
                it.group = "codex"
                it.description = "Execute OpenAI Codex with a prompt in full-auto mode. " +
                    "Usage: -Pprompt=\"...\" [-Ptimeout=300]"
            }
        }
    }
}

@UntrackedTask(because = "Codex exec spawns external process with dynamic state")
abstract class CodexExecTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val prompt: Property<String>

    @get:Input
    @get:Optional
    abstract val execTimeout: Property<Long>

    init {
        if (project.hasProperty("prompt")) {
            prompt.set(project.property("prompt").toString())
        }
        if (project.hasProperty("execTimeout")) {
            execTimeout.set(project.property("execTimeout").toString().toLong())
        }
    }

    @TaskAction
    fun execute() {
        if (!prompt.isPresent) {
            throw GradleException("Missing required property: -Pprompt=\"...\"")
        }

        val promptText = prompt.get()
        val timeoutSeconds = if (execTimeout.isPresent) execTimeout.get() else 300L

        val command = listOf("codex", "exec", promptText, "--full-auto")
        logger.lifecycle("codex-exec: ${command.joinToString(" ")}")

        val devNull = if (System.getProperty("os.name").lowercase().contains("win"))
            File("NUL") else File("/dev/null")

        val process = ProcessBuilder(command)
            .directory(project.projectDir)
            .redirectErrorStream(false)
            .redirectInput(devNull)
            .start()

        val startTime = System.currentTimeMillis()

        // Stream stdout live
        val stdoutReader = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                logger.lifecycle(line)
            }
        }
        // Stream stderr live
        val stderrReader = Thread {
            process.errorStream.bufferedReader().forEachLine { line ->
                logger.error(line)
            }
        }

        stdoutReader.start()
        stderrReader.start()

        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            stdoutReader.join(2000)
            stderrReader.join(2000)
            throw GradleException("codex-exec timed out after ${timeoutSeconds}s")
        }

        stdoutReader.join(5000)
        stderrReader.join(5000)

        val durationMs = System.currentTimeMillis() - startTime
        logger.lifecycle("codex-exec finished in ${durationMs / 1000}s (exit code: ${process.exitValue()})")

        if (process.exitValue() != 0) {
            throw GradleException("codex-exec failed with exit code ${process.exitValue()}")
        }
    }
}
