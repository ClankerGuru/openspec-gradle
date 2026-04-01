package zone.clanker.codex

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Base: runs a codex CLI command with stdin from /dev/null and live output streaming. */
abstract class CodexBaseTask : DefaultTask() {
    init { group = "codex" }

    protected fun exec(cmd: List<String>) {
        val devNull = File("/dev/null")
        logger.lifecycle("codex> ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(cmd)
            .directory(project.rootDir)
            .redirectInput(devNull)
            .redirectErrorStream(false)
            .start()
        val out = Thread { process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) } }
        val err = Thread { process.errorStream.bufferedReader().forEachLine { logger.error(it) } }
        out.start(); err.start()
        val exit = process.waitFor()
        out.join(5000); err.join(5000)
        if (exit != 0) throw GradleException("codex exited with code $exit")
    }
}

/** codex exec <prompt> — full 1:1 wrapper of every CLI flag. */
abstract class CodexExecTask : CodexBaseTask() {
    @get:Input abstract val prompt: Property<String>
    @get:Input @get:Optional abstract val codexModel: Property<String>
    @get:Input @get:Optional abstract val config: ListProperty<String>
    @get:Input @get:Optional abstract val enable: ListProperty<String>
    @get:Input @get:Optional abstract val disable: ListProperty<String>
    @get:Input @get:Optional abstract val remote: Property<String>
    @get:Input @get:Optional abstract val remoteAuthTokenEnv: Property<String>
    @get:Input @get:Optional abstract val image: ListProperty<String>
    @get:Input @get:Optional abstract val oss: Property<Boolean>
    @get:Input @get:Optional abstract val localProvider: Property<String>
    @get:Input @get:Optional abstract val profile: Property<String>
    @get:Input @get:Optional abstract val sandbox: Property<String>
    @get:Input @get:Optional abstract val askForApproval: Property<String>
    @get:Input @get:Optional abstract val fullAuto: Property<Boolean>
    @get:Input @get:Optional abstract val dangerouslyBypassApprovalsAndSandbox: Property<Boolean>
    @get:Input @get:Optional abstract val cd: Property<String>
    @get:Input @get:Optional abstract val search: Property<Boolean>
    @get:Input @get:Optional abstract val addDir: ListProperty<String>

    init { description = "Run Codex CLI in non-interactive exec mode (1:1 CLI wrapper)" }

    @TaskAction fun run() {
        val cmd = mutableListOf("codex", "exec", prompt.get())
        codexModel.orNull?.let { cmd += listOf("--model", it) }
        config.getOrElse(emptyList()).forEach { cmd += listOf("--config", it) }
        enable.getOrElse(emptyList()).forEach { cmd += listOf("--enable", it) }
        disable.getOrElse(emptyList()).forEach { cmd += listOf("--disable", it) }
        remote.orNull?.let { cmd += listOf("--remote", it) }
        remoteAuthTokenEnv.orNull?.let { cmd += listOf("--remote-auth-token-env", it) }
        image.getOrElse(emptyList()).forEach { cmd += listOf("--image", it) }
        if (oss.getOrElse(false)) cmd += "--oss"
        localProvider.orNull?.let { cmd += listOf("--local-provider", it) }
        profile.orNull?.let { cmd += listOf("--profile", it) }
        if (dangerouslyBypassApprovalsAndSandbox.getOrElse(false)) cmd += "--dangerously-bypass-approvals-and-sandbox"
        else {
            sandbox.orNull?.let { cmd += listOf("--sandbox", it) }
            askForApproval.orNull?.let { cmd += listOf("--ask-for-approval", it) }
            if (fullAuto.getOrElse(false)) cmd += "--full-auto"
        }
        cd.orNull?.let { cmd += listOf("--cd", it) }
        if (search.getOrElse(false)) cmd += "--search"
        addDir.getOrElse(emptyList()).forEach { cmd += listOf("--add-dir", it) }
        exec(cmd)
    }
}

/** codex review — non-interactive code review. */
abstract class CodexReviewTask : CodexBaseTask() {
    init { description = "Run a Codex code review non-interactively" }
    @TaskAction fun run() = exec(listOf("codex", "review"))
}

/** codex resume — resume a previous session. */
abstract class CodexResumeTask : CodexBaseTask() {
    @get:Input @get:Optional abstract val last: Property<Boolean>
    init { description = "Resume a previous Codex interactive session" }
    @TaskAction fun run() {
        val cmd = mutableListOf("codex", "resume")
        if (last.getOrElse(false)) cmd += "--last"
        exec(cmd)
    }
}

/** codex fork — fork a previous session. */
abstract class CodexForkTask : CodexBaseTask() {
    @get:Input @get:Optional abstract val last: Property<Boolean>
    init { description = "Fork a previous Codex interactive session" }
    @TaskAction fun run() {
        val cmd = mutableListOf("codex", "fork")
        if (last.getOrElse(false)) cmd += "--last"
        exec(cmd)
    }
}

/** codex apply — apply latest diff as git apply. */
abstract class CodexApplyTask : CodexBaseTask() {
    init { description = "Apply the latest diff produced by Codex agent" }
    @TaskAction fun run() = exec(listOf("codex", "apply"))
}

/** codex login */
abstract class CodexLoginTask : CodexBaseTask() {
    init { description = "Manage Codex login" }
    @TaskAction fun run() = exec(listOf("codex", "login"))
}

/** codex logout */
abstract class CodexLogoutTask : CodexBaseTask() {
    init { description = "Remove stored Codex authentication credentials" }
    @TaskAction fun run() = exec(listOf("codex", "logout"))
}

/** codex mcp */
abstract class CodexMcpTask : CodexBaseTask() {
    init { description = "Manage external MCP servers for Codex" }
    @TaskAction fun run() = exec(listOf("codex", "mcp"))
}

/** codex mcp-server */
abstract class CodexMcpServerTask : CodexBaseTask() {
    init { description = "Start Codex as an MCP server (stdio)" }
    @TaskAction fun run() = exec(listOf("codex", "mcp-server"))
}

/** codex app */
abstract class CodexAppTask : CodexBaseTask() {
    init { description = "Launch the Codex desktop app" }
    @TaskAction fun run() = exec(listOf("codex", "app"))
}

/** codex app-server */
abstract class CodexAppServerTask : CodexBaseTask() {
    init { description = "Run the Codex app server" }
    @TaskAction fun run() = exec(listOf("codex", "app-server"))
}

/** codex completion <shell> */
abstract class CodexCompletionTask : CodexBaseTask() {
    @get:Input @get:Optional abstract val shell: Property<String>
    init { description = "Generate shell completion scripts for Codex" }
    @TaskAction fun run() {
        val cmd = mutableListOf("codex", "completion")
        shell.orNull?.let { cmd += it }
        exec(cmd)
    }
}

/** codex sandbox */
abstract class CodexSandboxTask : CodexBaseTask() {
    init { description = "Run commands within a Codex-provided sandbox" }
    @TaskAction fun run() = exec(listOf("codex", "sandbox"))
}

/** codex debug */
abstract class CodexDebugTask : CodexBaseTask() {
    init { description = "Codex debugging tools" }
    @TaskAction fun run() = exec(listOf("codex", "debug"))
}

/** codex cloud */
abstract class CodexCloudTask : CodexBaseTask() {
    init { description = "Browse tasks from Codex Cloud and apply changes locally" }
    @TaskAction fun run() = exec(listOf("codex", "cloud"))
}

/** codex features */
abstract class CodexFeaturesTask : CodexBaseTask() {
    init { description = "Inspect Codex feature flags" }
    @TaskAction fun run() = exec(listOf("codex", "features"))
}

/** codex --version */
abstract class CodexVersionTask : CodexBaseTask() {
    init { description = "Show Codex CLI version" }
    @TaskAction fun run() = exec(listOf("codex", "--version"))
}

/** Settings plugin: zone.clanker.codex — registers all tasks. */
class CodexPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            if (project.tasks.findByName("codex-exec") != null) return@Action

            fun prop(name: String): String? =
                if (project.hasProperty(name)) project.property(name).toString() else null

            project.tasks.register("codex-exec", CodexExecTask::class.java).configure(org.gradle.api.Action { t ->
                prop("prompt")?.let { t.prompt.set(it) }
                prop("codexModel")?.let { t.codexModel.set(it) }
                if (project.hasProperty("oss")) t.oss.set(true)
                prop("localProvider")?.let { t.localProvider.set(it) }
                prop("profile")?.let { t.profile.set(it) }
                prop("sandbox")?.let { t.sandbox.set(it) }
                prop("askForApproval")?.let { t.askForApproval.set(it) }
                if (project.hasProperty("fullAuto")) t.fullAuto.set(true)
                if (project.hasProperty("dangerouslyBypassApprovalsAndSandbox")) t.dangerouslyBypassApprovalsAndSandbox.set(true)
                prop("cd")?.let { t.cd.set(it) }
                if (project.hasProperty("search")) t.search.set(true)
                prop("remote")?.let { t.remote.set(it) }
                prop("remoteAuthTokenEnv")?.let { t.remoteAuthTokenEnv.set(it) }
            })

            project.tasks.register("codex-review", CodexReviewTask::class.java)

            project.tasks.register("codex-resume", CodexResumeTask::class.java).configure(org.gradle.api.Action { t ->
                if (project.hasProperty("last")) t.last.set(true)
            })

            project.tasks.register("codex-fork", CodexForkTask::class.java).configure(org.gradle.api.Action { t ->
                if (project.hasProperty("last")) t.last.set(true)
            })

            project.tasks.register("codex-apply", CodexApplyTask::class.java)
            project.tasks.register("codex-login", CodexLoginTask::class.java)
            project.tasks.register("codex-logout", CodexLogoutTask::class.java)
            project.tasks.register("codex-mcp", CodexMcpTask::class.java)
            project.tasks.register("codex-mcp-server", CodexMcpServerTask::class.java)
            project.tasks.register("codex-app", CodexAppTask::class.java)
            project.tasks.register("codex-app-server", CodexAppServerTask::class.java)
            project.tasks.register("codex-completion", CodexCompletionTask::class.java).configure(org.gradle.api.Action { t ->
                prop("shell")?.let { t.shell.set(it) }
            })
            project.tasks.register("codex-sandbox", CodexSandboxTask::class.java)
            project.tasks.register("codex-debug", CodexDebugTask::class.java)
            project.tasks.register("codex-cloud", CodexCloudTask::class.java)
            project.tasks.register("codex-features", CodexFeaturesTask::class.java)
            project.tasks.register("codex-version", CodexVersionTask::class.java)
        })
    }
}
