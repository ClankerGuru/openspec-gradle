package zone.clanker.copilot

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/** Base: runs a copilot CLI command with stdin closed and live output streaming. */
abstract class CopilotBaseTask : Exec() {
    init {
        group = "copilot"
        workingDir = project.rootDir
        standardInput = "".byteInputStream()
    }

    override fun exec() {
        logger.lifecycle("copilot> ${commandLine.joinToString(" ")}")
        super.exec()
    }
}

/** copilot -p <prompt> — full 1:1 wrapper of every CLI flag. */
abstract class CopilotRunTask : CopilotBaseTask() {
    @get:Input abstract val prompt: Property<String>
    @get:Input @get:Optional abstract val copilotModel: Property<String>
    @get:Input @get:Optional abstract val effort: Property<String>
    @get:Input @get:Optional abstract val agent: Property<String>
    @get:Input @get:Optional abstract val outputFormat: Property<String>
    @get:Input @get:Optional abstract val stream: Property<String>
    @get:Input @get:Optional abstract val logLevel: Property<String>
    @get:Input @get:Optional abstract val logDir: Property<String>
    @get:Input @get:Optional abstract val configDir: Property<String>
    @get:Input @get:Optional abstract val maxAutopilotContinues: Property<String>
    @get:Input @get:Optional abstract val bashEnv: Property<String>
    @get:Input @get:Optional abstract val mouse: Property<String>
    @get:Input @get:Optional abstract val share: Property<String>
    @get:Input @get:Optional abstract val interactive: Property<String>

    // Boolean flags
    @get:Input @get:Optional abstract val silent: Property<Boolean>
    @get:Input @get:Optional abstract val noAskUser: Property<Boolean>
    @get:Input @get:Optional abstract val autopilot: Property<Boolean>
    @get:Input @get:Optional abstract val allowAll: Property<Boolean>
    @get:Input @get:Optional abstract val allowAllTools: Property<Boolean>
    @get:Input @get:Optional abstract val allowAllPaths: Property<Boolean>
    @get:Input @get:Optional abstract val allowAllUrls: Property<Boolean>
    @get:Input @get:Optional abstract val yolo: Property<Boolean>
    @get:Input @get:Optional abstract val acp: Property<Boolean>
    @get:Input @get:Optional abstract val banner: Property<Boolean>
    @get:Input @get:Optional abstract val experimental: Property<Boolean>
    @get:Input @get:Optional abstract val noExperimental: Property<Boolean>
    @get:Input @get:Optional abstract val noAutoUpdate: Property<Boolean>
    @get:Input @get:Optional abstract val noColor: Property<Boolean>
    @get:Input @get:Optional abstract val noCustomInstructions: Property<Boolean>
    @get:Input @get:Optional abstract val noBashEnv: Property<Boolean>
    @get:Input @get:Optional abstract val noMouse: Property<Boolean>
    @get:Input @get:Optional abstract val plainDiff: Property<Boolean>
    @get:Input @get:Optional abstract val screenReader: Property<Boolean>
    @get:Input @get:Optional abstract val shareGist: Property<Boolean>
    @get:Input @get:Optional abstract val disableBuiltinMcps: Property<Boolean>
    @get:Input @get:Optional abstract val disallowTempDir: Property<Boolean>
    @get:Input @get:Optional abstract val enableAllGithubMcpTools: Property<Boolean>
    @get:Input @get:Optional abstract val enableReasoningSummaries: Property<Boolean>

    // List flags (can be used multiple times)
    @get:Input @get:Optional abstract val addDir: ListProperty<String>
    @get:Input @get:Optional abstract val addGithubMcpTool: ListProperty<String>
    @get:Input @get:Optional abstract val addGithubMcpToolset: ListProperty<String>
    @get:Input @get:Optional abstract val additionalMcpConfig: ListProperty<String>
    @get:Input @get:Optional abstract val allowTool: ListProperty<String>
    @get:Input @get:Optional abstract val allowUrl: ListProperty<String>
    @get:Input @get:Optional abstract val availableTools: ListProperty<String>
    @get:Input @get:Optional abstract val denyTool: ListProperty<String>
    @get:Input @get:Optional abstract val denyUrl: ListProperty<String>
    @get:Input @get:Optional abstract val disableMcpServer: ListProperty<String>
    @get:Input @get:Optional abstract val excludedTools: ListProperty<String>
    @get:Input @get:Optional abstract val pluginDir: ListProperty<String>
    @get:Input @get:Optional abstract val secretEnvVars: ListProperty<String>

    init { description = "Run GitHub Copilot in non-interactive print mode (1:1 CLI wrapper)" }

    override fun exec() {
        val cmd = mutableListOf("copilot", "-p", prompt.get())
        copilotModel.orNull?.let { cmd += listOf("--model", it) }
        effort.orNull?.let { cmd += listOf("--reasoning-effort", it) }
        agent.orNull?.let { cmd += listOf("--agent", it) }
        outputFormat.orNull?.let { cmd += listOf("--output-format", it) }
        stream.orNull?.let { cmd += listOf("--stream", it) }
        logLevel.orNull?.let { cmd += listOf("--log-level", it) }
        logDir.orNull?.let { cmd += listOf("--log-dir", it) }
        configDir.orNull?.let { cmd += listOf("--config-dir", it) }
        maxAutopilotContinues.orNull?.let { cmd += listOf("--max-autopilot-continues", it) }
        bashEnv.orNull?.let { cmd += listOf("--bash-env", it) }
        mouse.orNull?.let { cmd += listOf("--mouse", it) }
        share.orNull?.let { cmd += listOf("--share", it) }
        interactive.orNull?.let { cmd += listOf("-i", it) }

        if (silent.getOrElse(false)) cmd += "-s"
        if (noAskUser.getOrElse(false)) cmd += "--no-ask-user"
        if (autopilot.getOrElse(false)) cmd += "--autopilot"
        if (yolo.getOrElse(false)) cmd += "--yolo"
        else if (allowAll.getOrElse(false)) cmd += "--allow-all"
        else {
            if (allowAllTools.getOrElse(false)) cmd += "--allow-all-tools"
            if (allowAllPaths.getOrElse(false)) cmd += "--allow-all-paths"
            if (allowAllUrls.getOrElse(false)) cmd += "--allow-all-urls"
        }
        if (acp.getOrElse(false)) cmd += "--acp"
        if (banner.getOrElse(false)) cmd += "--banner"
        if (experimental.getOrElse(false)) cmd += "--experimental"
        if (noExperimental.getOrElse(false)) cmd += "--no-experimental"
        if (noAutoUpdate.getOrElse(false)) cmd += "--no-auto-update"
        if (noColor.getOrElse(false)) cmd += "--no-color"
        if (noCustomInstructions.getOrElse(false)) cmd += "--no-custom-instructions"
        if (noBashEnv.getOrElse(false)) cmd += "--no-bash-env"
        if (noMouse.getOrElse(false)) cmd += "--no-mouse"
        if (plainDiff.getOrElse(false)) cmd += "--plain-diff"
        if (screenReader.getOrElse(false)) cmd += "--screen-reader"
        if (shareGist.getOrElse(false)) cmd += "--share-gist"
        if (disableBuiltinMcps.getOrElse(false)) cmd += "--disable-builtin-mcps"
        if (disallowTempDir.getOrElse(false)) cmd += "--disallow-temp-dir"
        if (enableAllGithubMcpTools.getOrElse(false)) cmd += "--enable-all-github-mcp-tools"
        if (enableReasoningSummaries.getOrElse(false)) cmd += "--enable-reasoning-summaries"

        addDir.getOrElse(emptyList()).forEach { cmd += listOf("--add-dir", it) }
        addGithubMcpTool.getOrElse(emptyList()).forEach { cmd += listOf("--add-github-mcp-tool", it) }
        addGithubMcpToolset.getOrElse(emptyList()).forEach { cmd += listOf("--add-github-mcp-toolset", it) }
        additionalMcpConfig.getOrElse(emptyList()).forEach { cmd += listOf("--additional-mcp-config", it) }
        allowTool.getOrElse(emptyList()).forEach { cmd += listOf("--allow-tool", it) }
        allowUrl.getOrElse(emptyList()).forEach { cmd += listOf("--allow-url", it) }
        availableTools.getOrElse(emptyList()).forEach { cmd += listOf("--available-tools", it) }
        denyTool.getOrElse(emptyList()).forEach { cmd += listOf("--deny-tool", it) }
        denyUrl.getOrElse(emptyList()).forEach { cmd += listOf("--deny-url", it) }
        disableMcpServer.getOrElse(emptyList()).forEach { cmd += listOf("--disable-mcp-server", it) }
        excludedTools.getOrElse(emptyList()).forEach { cmd += listOf("--excluded-tools", it) }
        pluginDir.getOrElse(emptyList()).forEach { cmd += listOf("--plugin-dir", it) }
        secretEnvVars.getOrElse(emptyList()).forEach { cmd += listOf("--secret-env-vars", it) }

        commandLine = cmd
        super.exec()
    }
}

/** copilot --resume / --continue */
abstract class CopilotResumeTask : CopilotBaseTask() {
    @get:Input @get:Optional abstract val sessionId: Property<String>
    init { description = "Resume a GitHub Copilot conversation" }
    override fun exec() {
        val cmd = mutableListOf("copilot")
        sessionId.orNull?.let { cmd += listOf("--resume", it) } ?: run { cmd += "--continue" }
        commandLine = cmd
        super.exec()
    }
}

/** copilot login */
abstract class CopilotLoginTask : CopilotBaseTask() {
    init {
        description = "Authenticate with GitHub Copilot"
        commandLine = listOf("copilot", "login")
    }
}

/** copilot version */
abstract class CopilotVersionTask : CopilotBaseTask() {
    init {
        description = "Show GitHub Copilot CLI version"
        commandLine = listOf("copilot", "--version")
    }
}

/** copilot update */
abstract class CopilotUpdateTask : CopilotBaseTask() {
    init {
        description = "Download the latest GitHub Copilot CLI version"
        commandLine = listOf("copilot", "update")
    }
}

/** copilot init */
abstract class CopilotInitTask : CopilotBaseTask() {
    init {
        description = "Initialize Copilot instructions for a repository"
        commandLine = listOf("copilot", "init")
    }
}

/** copilot plugin */
abstract class CopilotPluginManageTask : CopilotBaseTask() {
    init {
        description = "Manage GitHub Copilot plugins"
        commandLine = listOf("copilot", "plugin")
    }
}

/** copilot help [topic] */
abstract class CopilotHelpTask : CopilotBaseTask() {
    @get:Input @get:Optional abstract val topic: Property<String>
    init { description = "Display GitHub Copilot help information" }
    override fun exec() {
        val cmd = mutableListOf("copilot", "help")
        topic.orNull?.let { cmd += it }
        commandLine = cmd
        super.exec()
    }
}

/** Settings plugin: zone.clanker.copilot — registers all tasks. */
class CopilotPlugin : Plugin<Settings> {
    companion object {
        const val CLI_VERSION = "1.0.14"
    }

    override fun apply(settings: Settings) {
        val agents = settings.providers.gradleProperty("zone.clanker.opsx.agents")
            .orNull?.lowercase()?.split(",")?.map { it.trim() } ?: listOf("claude")
        if (agents.none { it in listOf("copilot", "github", "github-copilot") }) return
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            if (project.tasks.findByName("copilot-run") != null) return@Action

            // Catalog task
            project.tasks.register("copilot").configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                override fun execute(task: org.gradle.api.Task) {
                    task.group = "copilot"
                    task.description = "List all GitHub Copilot tasks."
                    task.doLast(object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(t: org.gradle.api.Task) {
                            println()
                            println("GitHub Copilot Tasks (copilot)")
                            println("\u2500".repeat(40))
                            println()
                            println("Execution:")
                            println("  copilot-run        Run Copilot in print mode (-Pprompt=...)")
                            println("  copilot-resume     Resume a conversation (-PsessionId=...)")
                            println()
                            println("Management:")
                            println("  copilot-login      Authenticate with GitHub Copilot")
                            println("  copilot-init       Initialize Copilot instructions for a repo")
                            println("  copilot-plugin     Manage plugins")
                            println("  copilot-help       Display help information (-Ptopic=...)")
                            println()
                            println("Maintenance:")
                            println("  copilot-version    Show CLI version")
                            println("  copilot-update     Download latest CLI version")
                            println()
                            println("Run any task:  ./gradlew <task-name>")
                            println("Full details:  ./gradlew help --task <task-name>")
                            println()
                        }
                    })
                }
            })

            fun prop(name: String): String? =
                if (project.hasProperty(name)) project.property(name).toString() else null

            project.tasks.register("copilot-run", CopilotRunTask::class.java).configure(org.gradle.api.Action { t ->
                prop("prompt")?.let { t.prompt.set(it) }
                prop("copilotModel")?.let { t.copilotModel.set(it) }
                prop("effort")?.let { t.effort.set(it) }
                prop("agent")?.let { t.agent.set(it) }
                prop("outputFormat")?.let { t.outputFormat.set(it) }
                prop("stream")?.let { t.stream.set(it) }
                prop("logLevel")?.let { t.logLevel.set(it) }
                prop("logDir")?.let { t.logDir.set(it) }
                prop("configDir")?.let { t.configDir.set(it) }
                prop("maxAutopilotContinues")?.let { t.maxAutopilotContinues.set(it) }
                prop("bashEnv")?.let { t.bashEnv.set(it) }
                prop("mouse")?.let { t.mouse.set(it) }
                prop("share")?.let { t.share.set(it) }
                prop("interactive")?.let { t.interactive.set(it) }
                if (project.hasProperty("silent")) t.silent.set(true)
                if (project.hasProperty("noAskUser")) t.noAskUser.set(true)
                if (project.hasProperty("autopilot")) t.autopilot.set(true)
                if (project.hasProperty("allowAll")) t.allowAll.set(true)
                if (project.hasProperty("allowAllTools")) t.allowAllTools.set(true)
                if (project.hasProperty("allowAllPaths")) t.allowAllPaths.set(true)
                if (project.hasProperty("allowAllUrls")) t.allowAllUrls.set(true)
                if (project.hasProperty("yolo")) t.yolo.set(true)
                if (project.hasProperty("acp")) t.acp.set(true)
                if (project.hasProperty("banner")) t.banner.set(true)
                if (project.hasProperty("experimental")) t.experimental.set(true)
                if (project.hasProperty("noExperimental")) t.noExperimental.set(true)
                if (project.hasProperty("noAutoUpdate")) t.noAutoUpdate.set(true)
                if (project.hasProperty("noColor")) t.noColor.set(true)
                if (project.hasProperty("noCustomInstructions")) t.noCustomInstructions.set(true)
                if (project.hasProperty("noBashEnv")) t.noBashEnv.set(true)
                if (project.hasProperty("noMouse")) t.noMouse.set(true)
                if (project.hasProperty("plainDiff")) t.plainDiff.set(true)
                if (project.hasProperty("screenReader")) t.screenReader.set(true)
                if (project.hasProperty("shareGist")) t.shareGist.set(true)
                if (project.hasProperty("disableBuiltinMcps")) t.disableBuiltinMcps.set(true)
                if (project.hasProperty("disallowTempDir")) t.disallowTempDir.set(true)
                if (project.hasProperty("enableAllGithubMcpTools")) t.enableAllGithubMcpTools.set(true)
                if (project.hasProperty("enableReasoningSummaries")) t.enableReasoningSummaries.set(true)
            })

            project.tasks.register("copilot-resume", CopilotResumeTask::class.java).configure(org.gradle.api.Action { t ->
                prop("sessionId")?.let { t.sessionId.set(it) }
            })

            project.tasks.register("copilot-login", CopilotLoginTask::class.java)
            project.tasks.register("copilot-version", CopilotVersionTask::class.java)
            project.tasks.register("copilot-update", CopilotUpdateTask::class.java)
            project.tasks.register("copilot-init", CopilotInitTask::class.java)
            project.tasks.register("copilot-plugin", CopilotPluginManageTask::class.java)
            project.tasks.register("copilot-help", CopilotHelpTask::class.java).configure(org.gradle.api.Action { t ->
                prop("topic")?.let { t.topic.set(it) }
            })
        })
    }
}
