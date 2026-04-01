package zone.clanker.claude

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

/** Base: runs a claude CLI command with stdin from /dev/null and live output streaming. */
abstract class ClaudeBaseTask : DefaultTask() {
    init { group = "claude" }

    protected fun exec(cmd: List<String>) {
        val devNull = File("/dev/null")
        logger.lifecycle("claude> ${cmd.joinToString(" ")}")
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
        if (exit != 0) throw GradleException("claude exited with code $exit")
    }
}

/** claude -p <prompt> — full 1:1 wrapper of every CLI flag. */
abstract class ClaudeRunTask : ClaudeBaseTask() {
    @get:Input abstract val prompt: Property<String>
    @get:Input @get:Optional abstract val claudeModel: Property<String>
    @get:Input @get:Optional abstract val effort: Property<String>
    @get:Input @get:Optional abstract val fallbackModel: Property<String>
    @get:Input @get:Optional abstract val permissionMode: Property<String>
    @get:Input @get:Optional abstract val dangerouslySkipPermissions: Property<Boolean>
    @get:Input @get:Optional abstract val allowedTools: Property<String>
    @get:Input @get:Optional abstract val disallowedTools: Property<String>
    @get:Input @get:Optional abstract val tools: Property<String>
    @get:Input @get:Optional abstract val outputFormat: Property<String>
    @get:Input @get:Optional abstract val jsonSchema: Property<String>
    @get:Input @get:Optional abstract val includePartialMessages: Property<Boolean>
    @get:Input @get:Optional abstract val verbose: Property<Boolean>
    @get:Input @get:Optional abstract val systemPrompt: Property<String>
    @get:Input @get:Optional abstract val appendSystemPrompt: Property<String>
    @get:Input @get:Optional abstract val sessionId: Property<String>
    @get:Input @get:Optional abstract val sessionName: Property<String>
    @get:Input @get:Optional abstract val noSessionPersistence: Property<Boolean>
    @get:Input @get:Optional abstract val maxBudgetUsd: Property<String>
    @get:Input @get:Optional abstract val agent: Property<String>
    @get:Input @get:Optional abstract val agents: Property<String>
    @get:Input @get:Optional abstract val addDir: ListProperty<String>
    @get:Input @get:Optional abstract val mcpConfig: ListProperty<String>
    @get:Input @get:Optional abstract val pluginDir: ListProperty<String>
    @get:Input @get:Optional abstract val settingSources: Property<String>
    @get:Input @get:Optional abstract val settings: Property<String>
    @get:Input @get:Optional abstract val bare: Property<Boolean>
    @get:Input @get:Optional abstract val brief: Property<Boolean>
    @get:Input @get:Optional abstract val disableSlashCommands: Property<Boolean>
    @get:Input @get:Optional abstract val strictMcpConfig: Property<Boolean>
    @get:Input @get:Optional abstract val worktree: Property<String>
    @get:Input @get:Optional abstract val tmux: Property<Boolean>
    @get:Input @get:Optional abstract val inputFormat: Property<String>
    @get:Input @get:Optional abstract val file: ListProperty<String>
    @get:Input @get:Optional abstract val betas: ListProperty<String>
    @get:Input @get:Optional abstract val debug: Property<String>
    @get:Input @get:Optional abstract val debugFile: Property<String>

    init { description = "Run Claude Code in non-interactive print mode (1:1 CLI wrapper)" }

    @TaskAction fun run() {
        val cmd = mutableListOf("claude", "-p", prompt.get())
        claudeModel.orNull?.let { cmd += listOf("--model", it) }
        effort.orNull?.let { cmd += listOf("--effort", it) }
        fallbackModel.orNull?.let { cmd += listOf("--fallback-model", it) }
        if (dangerouslySkipPermissions.getOrElse(false)) cmd += "--dangerously-skip-permissions"
        else permissionMode.orNull?.let { cmd += listOf("--permission-mode", it) }
        allowedTools.orNull?.let { cmd += listOf("--allowed-tools", it) }
        disallowedTools.orNull?.let { cmd += listOf("--disallowed-tools", it) }
        tools.orNull?.let { cmd += listOf("--tools", it) }
        outputFormat.orNull?.let { cmd += listOf("--output-format", it) }
        jsonSchema.orNull?.let { cmd += listOf("--json-schema", it) }
        if (includePartialMessages.getOrElse(false)) cmd += "--include-partial-messages"
        if (verbose.getOrElse(false)) cmd += "--verbose"
        systemPrompt.orNull?.let { cmd += listOf("--system-prompt", it) }
        appendSystemPrompt.orNull?.let { cmd += listOf("--append-system-prompt", it) }
        sessionId.orNull?.let { cmd += listOf("--session-id", it) }
        sessionName.orNull?.let { cmd += listOf("--name", it) }
        if (noSessionPersistence.getOrElse(false)) cmd += "--no-session-persistence"
        maxBudgetUsd.orNull?.let { cmd += listOf("--max-budget-usd", it) }
        agent.orNull?.let { cmd += listOf("--agent", it) }
        agents.orNull?.let { cmd += listOf("--agents", it) }
        addDir.getOrElse(emptyList()).forEach { cmd += listOf("--add-dir", it) }
        mcpConfig.getOrElse(emptyList()).forEach { cmd += listOf("--mcp-config", it) }
        pluginDir.getOrElse(emptyList()).forEach { cmd += listOf("--plugin-dir", it) }
        settingSources.orNull?.let { cmd += listOf("--setting-sources", it) }
        settings.orNull?.let { cmd += listOf("--settings", it) }
        if (bare.getOrElse(false)) cmd += "--bare"
        if (brief.getOrElse(false)) cmd += "--brief"
        if (disableSlashCommands.getOrElse(false)) cmd += "--disable-slash-commands"
        if (strictMcpConfig.getOrElse(false)) cmd += "--strict-mcp-config"
        worktree.orNull?.let { cmd += listOf("--worktree", it) }
        if (tmux.getOrElse(false)) cmd += "--tmux"
        inputFormat.orNull?.let { cmd += listOf("--input-format", it) }
        file.getOrElse(emptyList()).forEach { cmd += listOf("--file", it) }
        betas.getOrElse(emptyList()).forEach { cmd += listOf("--betas", it) }
        debug.orNull?.let { cmd += listOf("--debug", it) }
        debugFile.orNull?.let { cmd += listOf("--debug-file", it) }
        exec(cmd)
    }
}

/** claude --resume / --continue */
abstract class ClaudeResumeTask : ClaudeBaseTask() {
    @get:Input @get:Optional abstract val sessionId: Property<String>
    @get:Input @get:Optional abstract val forkSession: Property<Boolean>
    init { description = "Resume a Claude Code conversation" }
    @TaskAction fun run() {
        val cmd = mutableListOf("claude")
        sessionId.orNull?.let { cmd += listOf("--resume", it) } ?: run { cmd += "--continue" }
        if (forkSession.getOrElse(false)) cmd += "--fork-session"
        exec(cmd)
    }
}

/** claude --from-pr <number> */
abstract class ClaudeFromPrTask : ClaudeBaseTask() {
    @get:Input abstract val pr: Property<String>
    init { description = "Resume a Claude session linked to a PR" }
    @TaskAction fun run() = exec(listOf("claude", "--from-pr", pr.get()))
}

abstract class ClaudeAuthTask : ClaudeBaseTask() {
    init { description = "Manage Claude Code authentication" }
    @TaskAction fun run() = exec(listOf("claude", "auth"))
}

abstract class ClaudeVersionTask : ClaudeBaseTask() {
    init { description = "Show Claude Code version" }
    @TaskAction fun run() = exec(listOf("claude", "--version"))
}

abstract class ClaudeDoctorTask : ClaudeBaseTask() {
    init { description = "Check Claude Code auto-updater health" }
    @TaskAction fun run() = exec(listOf("claude", "doctor"))
}

abstract class ClaudeMcpTask : ClaudeBaseTask() {
    init { description = "Configure and manage MCP servers" }
    @TaskAction fun run() = exec(listOf("claude", "mcp"))
}

abstract class ClaudeAgentsTask : ClaudeBaseTask() {
    init { description = "List configured Claude agents" }
    @TaskAction fun run() = exec(listOf("claude", "agents"))
}

abstract class ClaudeUpdateTask : ClaudeBaseTask() {
    init { description = "Check for Claude Code updates" }
    @TaskAction fun run() = exec(listOf("claude", "update"))
}

abstract class ClaudeSetupTokenTask : ClaudeBaseTask() {
    init { description = "Set up a long-lived authentication token" }
    @TaskAction fun run() = exec(listOf("claude", "setup-token"))
}

abstract class ClaudeAutoModeTask : ClaudeBaseTask() {
    init { description = "Inspect auto mode classifier configuration" }
    @TaskAction fun run() = exec(listOf("claude", "auto-mode"))
}

abstract class ClaudeInstallTask : ClaudeBaseTask() {
    @get:Input @get:Optional abstract val target: Property<String>
    init { description = "Install Claude Code native build" }
    @TaskAction fun run() {
        val cmd = mutableListOf("claude", "install")
        target.orNull?.let { cmd += it }
        exec(cmd)
    }
}

abstract class ClaudePluginsTask : ClaudeBaseTask() {
    init { description = "Manage Claude Code plugins" }
    @TaskAction fun run() = exec(listOf("claude", "plugins"))
}

/** Settings plugin: zone.clanker.claude — registers all tasks. */
class ClaudePlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            if (project.tasks.findByName("claude-run") != null) return@Action

            fun prop(name: String): String? =
                if (project.hasProperty(name)) project.property(name).toString() else null

            project.tasks.register("claude-run", ClaudeRunTask::class.java).configure(org.gradle.api.Action { t ->
                prop("prompt")?.let { t.prompt.set(it) }
                prop("claudeModel")?.let { t.claudeModel.set(it) }
                prop("effort")?.let { t.effort.set(it) }
                prop("fallbackModel")?.let { t.fallbackModel.set(it) }
                prop("permissionMode")?.let { t.permissionMode.set(it) }
                if (project.hasProperty("dangerouslySkipPermissions")) t.dangerouslySkipPermissions.set(true)
                prop("allowedTools")?.let { t.allowedTools.set(it) }
                prop("disallowedTools")?.let { t.disallowedTools.set(it) }
                prop("tools")?.let { t.tools.set(it) }
                prop("outputFormat")?.let { t.outputFormat.set(it) }
                prop("jsonSchema")?.let { t.jsonSchema.set(it) }
                if (project.hasProperty("includePartialMessages")) t.includePartialMessages.set(true)
                if (project.hasProperty("verbose")) t.verbose.set(true)
                prop("systemPrompt")?.let { t.systemPrompt.set(it) }
                prop("appendSystemPrompt")?.let { t.appendSystemPrompt.set(it) }
                prop("sessionId")?.let { t.sessionId.set(it) }
                prop("sessionName")?.let { t.sessionName.set(it) }
                if (project.hasProperty("noSessionPersistence")) t.noSessionPersistence.set(true)
                prop("maxBudgetUsd")?.let { t.maxBudgetUsd.set(it) }
                prop("agent")?.let { t.agent.set(it) }
                prop("agents")?.let { t.agents.set(it) }
                if (project.hasProperty("bare")) t.bare.set(true)
                if (project.hasProperty("brief")) t.brief.set(true)
                if (project.hasProperty("disableSlashCommands")) t.disableSlashCommands.set(true)
                prop("worktree")?.let { t.worktree.set(it) }
                if (project.hasProperty("tmux")) t.tmux.set(true)
                prop("inputFormat")?.let { t.inputFormat.set(it) }
                prop("settingSources")?.let { t.settingSources.set(it) }
                prop("settings")?.let { t.settings.set(it) }
                prop("debug")?.let { t.debug.set(it) }
                prop("debugFile")?.let { t.debugFile.set(it) }
            })

            project.tasks.register("claude-resume", ClaudeResumeTask::class.java).configure(org.gradle.api.Action { t ->
                prop("sessionId")?.let { t.sessionId.set(it) }
                if (project.hasProperty("forkSession")) t.forkSession.set(true)
            })

            project.tasks.register("claude-from-pr", ClaudeFromPrTask::class.java).configure(org.gradle.api.Action { t ->
                prop("pr")?.let { t.pr.set(it) }
            })

            project.tasks.register("claude-auth", ClaudeAuthTask::class.java)
            project.tasks.register("claude-version", ClaudeVersionTask::class.java)
            project.tasks.register("claude-doctor", ClaudeDoctorTask::class.java)
            project.tasks.register("claude-mcp", ClaudeMcpTask::class.java)
            project.tasks.register("claude-agents", ClaudeAgentsTask::class.java)
            project.tasks.register("claude-update", ClaudeUpdateTask::class.java)
            project.tasks.register("claude-setup-token", ClaudeSetupTokenTask::class.java)
            project.tasks.register("claude-auto-mode", ClaudeAutoModeTask::class.java)
            project.tasks.register("claude-install", ClaudeInstallTask::class.java).configure(org.gradle.api.Action { t ->
                prop("target")?.let { t.target.set(it) }
            })
            project.tasks.register("claude-plugins", ClaudePluginsTask::class.java)
        })
    }
}
