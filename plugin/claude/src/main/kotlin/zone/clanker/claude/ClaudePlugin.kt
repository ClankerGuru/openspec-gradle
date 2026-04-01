package zone.clanker.claude

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/** claude -p <prompt> — full 1:1 wrapper of every CLI flag. */
abstract class ClaudeRunTask : Exec() {
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

    init {
        group = "claude"
        description = "Run Claude Code in non-interactive print mode (1:1 CLI wrapper)"
    }

    override fun exec() {
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
        commandLine(cmd)
        super.exec()
    }
}

/** claude --resume / --continue */
abstract class ClaudeResumeTask : Exec() {
    @get:Input @get:Optional abstract val sessionId: Property<String>
    @get:Input @get:Optional abstract val forkSession: Property<Boolean>
    init {
        group = "claude"
        description = "Resume a Claude Code conversation"
    }
    override fun exec() {
        val cmd = mutableListOf("claude")
        sessionId.orNull?.let { cmd += listOf("--resume", it) } ?: run { cmd += "--continue" }
        if (forkSession.getOrElse(false)) cmd += "--fork-session"
        commandLine(cmd)
        super.exec()
    }
}

/** claude --from-pr <number> */
abstract class ClaudeFromPrTask : Exec() {
    @get:Input abstract val pr: Property<String>
    init {
        group = "claude"
        description = "Resume a Claude session linked to a PR"
    }
    override fun exec() {
        commandLine("claude", "--from-pr", pr.get())
        super.exec()
    }
}

abstract class ClaudeAuthTask : Exec() {
    init {
        group = "claude"
        description = "Manage Claude Code authentication"
        commandLine("claude", "auth")
    }
}

abstract class ClaudeVersionTask : Exec() {
    init {
        group = "claude"
        description = "Show Claude Code version"
        commandLine("claude", "--version")
    }
}

abstract class ClaudeDoctorTask : Exec() {
    init {
        group = "claude"
        description = "Check Claude Code auto-updater health"
        commandLine("claude", "doctor")
    }
}

abstract class ClaudeMcpTask : Exec() {
    init {
        group = "claude"
        description = "Configure and manage MCP servers"
        commandLine("claude", "mcp")
    }
}

abstract class ClaudeAgentsTask : Exec() {
    init {
        group = "claude"
        description = "List configured Claude agents"
        commandLine("claude", "agents")
    }
}

abstract class ClaudeUpdateTask : Exec() {
    init {
        group = "claude"
        description = "Check for Claude Code updates"
        commandLine("claude", "update")
    }
}

abstract class ClaudeSetupTokenTask : Exec() {
    init {
        group = "claude"
        description = "Set up a long-lived authentication token"
        commandLine("claude", "setup-token")
    }
}

abstract class ClaudeAutoModeTask : Exec() {
    init {
        group = "claude"
        description = "Inspect auto mode classifier configuration"
        commandLine("claude", "auto-mode")
    }
}

abstract class ClaudeInstallTask : Exec() {
    @get:Input @get:Optional abstract val target: Property<String>
    init {
        group = "claude"
        description = "Install Claude Code native build"
    }
    override fun exec() {
        val cmd = mutableListOf("claude", "install")
        target.orNull?.let { cmd += it }
        commandLine(cmd)
        super.exec()
    }
}

abstract class ClaudePluginsTask : Exec() {
    init {
        group = "claude"
        description = "Manage Claude Code plugins"
        commandLine("claude", "plugins")
    }
}

/** Settings plugin: zone.clanker.claude — registers all tasks. */
class ClaudePlugin : Plugin<Settings> {
    companion object {
        const val CLI_VERSION = "2.1.81"
        const val ENABLED_PROP = "zone.clanker.claude.enabled"
    }

    override fun apply(settings: Settings) {
        if (settings.providers.gradleProperty(ENABLED_PROP).orNull?.lowercase() == "false") return
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            if (project.tasks.findByName("claude-run") != null) return@Action

            // Catalog task
            project.tasks.register("claude").configure(object : org.gradle.api.Action<org.gradle.api.Task> {
                override fun execute(task: org.gradle.api.Task) {
                    task.group = "claude"
                    task.description = "List all Claude Code tasks."
                    task.doLast(object : org.gradle.api.Action<org.gradle.api.Task> {
                        override fun execute(t: org.gradle.api.Task) {
                            println()
                            println("Claude Code Tasks (claude)")
                            println("\u2500".repeat(40))
                            println()
                            println("Execution:")
                            println("  claude-run         Run Claude Code in print mode (-Pprompt=...)")
                            println("  claude-resume      Resume a conversation (-PsessionId=...)")
                            println("  claude-from-pr     Resume session linked to a PR (-Ppr=...)")
                            println()
                            println("Management:")
                            println("  claude-auth        Manage authentication")
                            println("  claude-setup-token Set up long-lived auth token")
                            println("  claude-mcp         Configure MCP servers")
                            println("  claude-agents      List configured agents")
                            println("  claude-plugins     Manage plugins")
                            println("  claude-auto-mode   Inspect auto mode classifier")
                            println()
                            println("Maintenance:")
                            println("  claude-version     Show CLI version")
                            println("  claude-update      Check for updates")
                            println("  claude-doctor      Check auto-updater health")
                            println("  claude-install     Install native build (-Ptarget=...)")
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
