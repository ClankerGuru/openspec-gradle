package zone.clanker.opencode

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

/** Base: runs an opencode CLI command with stdin from /dev/null and live output streaming. */
abstract class OpencodeBaseTask : DefaultTask() {
    init { group = "opencode" }

    protected fun exec(cmd: List<String>) {
        val devNull = File("/dev/null")
        logger.lifecycle("opencode> ${cmd.joinToString(" ")}")
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
        if (exit != 0) throw GradleException("opencode exited with code $exit")
    }
}

/** opencode run [message..] — full 1:1 wrapper of every CLI flag. */
abstract class OpencodeRunTask : OpencodeBaseTask() {
    @get:Input abstract val prompt: Property<String>
    @get:Input @get:Optional abstract val opencodeModel: Property<String>
    @get:Input @get:Optional abstract val agent: Property<String>
    @get:Input @get:Optional abstract val printLogs: Property<Boolean>
    @get:Input @get:Optional abstract val logLevel: Property<String>
    @get:Input @get:Optional abstract val pure: Property<Boolean>
    @get:Input @get:Optional abstract val port: Property<Int>
    @get:Input @get:Optional abstract val hostname: Property<String>
    @get:Input @get:Optional abstract val mdns: Property<Boolean>
    @get:Input @get:Optional abstract val mdnsDomain: Property<String>
    @get:Input @get:Optional abstract val cors: ListProperty<String>
    @get:Input @get:Optional abstract val continueSession: Property<Boolean>
    @get:Input @get:Optional abstract val session: Property<String>
    @get:Input @get:Optional abstract val fork: Property<Boolean>
    @get:Input @get:Optional abstract val opencodePrompt: Property<String>

    init { description = "Run opencode with a message (1:1 CLI wrapper)" }

    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "run", prompt.get())
        opencodeModel.orNull?.let { cmd += listOf("--model", it) }
        agent.orNull?.let { cmd += listOf("--agent", it) }
        if (printLogs.getOrElse(false)) cmd += "--print-logs"
        logLevel.orNull?.let { cmd += listOf("--log-level", it) }
        if (pure.getOrElse(false)) cmd += "--pure"
        port.orNull?.let { cmd += listOf("--port", it.toString()) }
        hostname.orNull?.let { cmd += listOf("--hostname", it) }
        if (mdns.getOrElse(false)) cmd += "--mdns"
        mdnsDomain.orNull?.let { cmd += listOf("--mdns-domain", it) }
        cors.getOrElse(emptyList()).forEach { cmd += listOf("--cors", it) }
        if (continueSession.getOrElse(false)) cmd += "--continue"
        session.orNull?.let { cmd += listOf("--session", it) }
        if (fork.getOrElse(false)) cmd += "--fork"
        opencodePrompt.orNull?.let { cmd += listOf("--prompt", it) }
        exec(cmd)
    }
}

/** opencode --continue / --session */
abstract class OpencodeResumeTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val session: Property<String>
    @get:Input @get:Optional abstract val fork: Property<Boolean>
    init { description = "Resume an opencode conversation" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode")
        session.orNull?.let { cmd += listOf("--session", it) } ?: run { cmd += "--continue" }
        if (fork.getOrElse(false)) cmd += "--fork"
        exec(cmd)
    }
}

/** opencode pr <number> */
abstract class OpencodePrTask : OpencodeBaseTask() {
    @get:Input abstract val pr: Property<String>
    init { description = "Fetch and checkout a GitHub PR branch, then run opencode" }
    @TaskAction fun run() = exec(listOf("opencode", "pr", pr.get()))
}

/** opencode attach <url> */
abstract class OpencodeAttachTask : OpencodeBaseTask() {
    @get:Input abstract val url: Property<String>
    init { description = "Attach to a running opencode server" }
    @TaskAction fun run() = exec(listOf("opencode", "attach", url.get()))
}

/** opencode upgrade [target] */
abstract class OpencodeUpgradeTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val target: Property<String>
    init { description = "Upgrade opencode to the latest or a specific version" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "upgrade")
        target.orNull?.let { cmd += it }
        exec(cmd)
    }
}

/** opencode models [provider] */
abstract class OpencodeModelsTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val provider: Property<String>
    init { description = "List all available models" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "models")
        provider.orNull?.let { cmd += it }
        exec(cmd)
    }
}

/** opencode export [sessionID] */
abstract class OpencodeExportTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val sessionId: Property<String>
    init { description = "Export session data as JSON" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "export")
        sessionId.orNull?.let { cmd += it }
        exec(cmd)
    }
}

/** opencode import <file> */
abstract class OpencodeImportTask : OpencodeBaseTask() {
    @get:Input abstract val importFile: Property<String>
    init { description = "Import session data from JSON file or URL" }
    @TaskAction fun run() = exec(listOf("opencode", "import", importFile.get()))
}

/** opencode plugin <module> */
abstract class OpencodePluginInstallTask : OpencodeBaseTask() {
    @get:Input abstract val module: Property<String>
    init { description = "Install plugin and update config" }
    @TaskAction fun run() = exec(listOf("opencode", "plugin", module.get()))
}

/** opencode serve — with server options */
abstract class OpencodeServeTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val port: Property<Int>
    @get:Input @get:Optional abstract val hostname: Property<String>
    @get:Input @get:Optional abstract val mdns: Property<Boolean>
    @get:Input @get:Optional abstract val mdnsDomain: Property<String>
    @get:Input @get:Optional abstract val cors: ListProperty<String>
    init { description = "Start a headless opencode server" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "serve")
        port.orNull?.let { cmd += listOf("--port", it.toString()) }
        hostname.orNull?.let { cmd += listOf("--hostname", it) }
        if (mdns.getOrElse(false)) cmd += "--mdns"
        mdnsDomain.orNull?.let { cmd += listOf("--mdns-domain", it) }
        cors.getOrElse(emptyList()).forEach { cmd += listOf("--cors", it) }
        exec(cmd)
    }
}

/** opencode web — with server options */
abstract class OpencodeWebTask : OpencodeBaseTask() {
    @get:Input @get:Optional abstract val port: Property<Int>
    @get:Input @get:Optional abstract val hostname: Property<String>
    init { description = "Start opencode server and open web interface" }
    @TaskAction fun run() {
        val cmd = mutableListOf("opencode", "web")
        port.orNull?.let { cmd += listOf("--port", it.toString()) }
        hostname.orNull?.let { cmd += listOf("--hostname", it) }
        exec(cmd)
    }
}

abstract class OpencodeCompletionTask : OpencodeBaseTask() {
    init { description = "Generate shell completion script" }
    @TaskAction fun run() = exec(listOf("opencode", "completion"))
}

abstract class OpencodeAcpTask : OpencodeBaseTask() {
    init { description = "Start ACP (Agent Client Protocol) server" }
    @TaskAction fun run() = exec(listOf("opencode", "acp"))
}

abstract class OpencodeMcpTask : OpencodeBaseTask() {
    init { description = "Manage MCP (Model Context Protocol) servers" }
    @TaskAction fun run() = exec(listOf("opencode", "mcp"))
}

abstract class OpencodeDebugTask : OpencodeBaseTask() {
    init { description = "Debugging and troubleshooting tools" }
    @TaskAction fun run() = exec(listOf("opencode", "debug"))
}

abstract class OpencodeProvidersTask : OpencodeBaseTask() {
    init { description = "Manage AI providers and credentials" }
    @TaskAction fun run() = exec(listOf("opencode", "providers"))
}

abstract class OpencodeAgentTask : OpencodeBaseTask() {
    init { description = "Manage agents" }
    @TaskAction fun run() = exec(listOf("opencode", "agent"))
}

abstract class OpencodeUninstallTask : OpencodeBaseTask() {
    init { description = "Uninstall opencode and remove all related files" }
    @TaskAction fun run() = exec(listOf("opencode", "uninstall"))
}

abstract class OpencodeStatsTask : OpencodeBaseTask() {
    init { description = "Show token usage and cost statistics" }
    @TaskAction fun run() = exec(listOf("opencode", "stats"))
}

abstract class OpencodeGithubTask : OpencodeBaseTask() {
    init { description = "Manage GitHub agent" }
    @TaskAction fun run() = exec(listOf("opencode", "github"))
}

abstract class OpencodeSessionTask : OpencodeBaseTask() {
    init { description = "Manage sessions" }
    @TaskAction fun run() = exec(listOf("opencode", "session"))
}

abstract class OpencodeDbTask : OpencodeBaseTask() {
    init { description = "Database tools" }
    @TaskAction fun run() = exec(listOf("opencode", "db"))
}

abstract class OpencodeVersionTask : OpencodeBaseTask() {
    init { description = "Show opencode version" }
    @TaskAction fun run() = exec(listOf("opencode", "--version"))
}

/** Settings plugin: zone.clanker.opencode — registers all tasks. */
class OpencodePlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            if (project.tasks.findByName("opencode-run") != null) return@Action

            fun prop(name: String): String? =
                if (project.hasProperty(name)) project.property(name).toString() else null

            project.tasks.register("opencode-run", OpencodeRunTask::class.java).configure(org.gradle.api.Action { t ->
                prop("prompt")?.let { t.prompt.set(it) }
                prop("opencodeModel")?.let { t.opencodeModel.set(it) }
                prop("agent")?.let { t.agent.set(it) }
                if (project.hasProperty("printLogs")) t.printLogs.set(true)
                prop("logLevel")?.let { t.logLevel.set(it) }
                if (project.hasProperty("pure")) t.pure.set(true)
                prop("port")?.let { t.port.set(it.toInt()) }
                prop("hostname")?.let { t.hostname.set(it) }
                if (project.hasProperty("mdns")) t.mdns.set(true)
                prop("mdnsDomain")?.let { t.mdnsDomain.set(it) }
                if (project.hasProperty("continueSession")) t.continueSession.set(true)
                prop("session")?.let { t.session.set(it) }
                if (project.hasProperty("fork")) t.fork.set(true)
                prop("opencodePrompt")?.let { t.opencodePrompt.set(it) }
            })

            project.tasks.register("opencode-resume", OpencodeResumeTask::class.java).configure(org.gradle.api.Action { t ->
                prop("session")?.let { t.session.set(it) }
                if (project.hasProperty("fork")) t.fork.set(true)
            })

            project.tasks.register("opencode-pr", OpencodePrTask::class.java).configure(org.gradle.api.Action { t ->
                prop("pr")?.let { t.pr.set(it) }
            })

            project.tasks.register("opencode-attach", OpencodeAttachTask::class.java).configure(org.gradle.api.Action { t ->
                prop("url")?.let { t.url.set(it) }
            })

            project.tasks.register("opencode-upgrade", OpencodeUpgradeTask::class.java).configure(org.gradle.api.Action { t ->
                prop("target")?.let { t.target.set(it) }
            })

            project.tasks.register("opencode-models", OpencodeModelsTask::class.java).configure(org.gradle.api.Action { t ->
                prop("provider")?.let { t.provider.set(it) }
            })

            project.tasks.register("opencode-export", OpencodeExportTask::class.java).configure(org.gradle.api.Action { t ->
                prop("sessionId")?.let { t.sessionId.set(it) }
            })

            project.tasks.register("opencode-import", OpencodeImportTask::class.java).configure(org.gradle.api.Action { t ->
                prop("importFile")?.let { t.importFile.set(it) }
            })

            project.tasks.register("opencode-plugin", OpencodePluginInstallTask::class.java).configure(org.gradle.api.Action { t ->
                prop("module")?.let { t.module.set(it) }
            })

            project.tasks.register("opencode-serve", OpencodeServeTask::class.java).configure(org.gradle.api.Action { t ->
                prop("port")?.let { t.port.set(it.toInt()) }
                prop("hostname")?.let { t.hostname.set(it) }
                if (project.hasProperty("mdns")) t.mdns.set(true)
                prop("mdnsDomain")?.let { t.mdnsDomain.set(it) }
            })

            project.tasks.register("opencode-web", OpencodeWebTask::class.java).configure(org.gradle.api.Action { t ->
                prop("port")?.let { t.port.set(it.toInt()) }
                prop("hostname")?.let { t.hostname.set(it) }
            })

            project.tasks.register("opencode-completion", OpencodeCompletionTask::class.java)
            project.tasks.register("opencode-acp", OpencodeAcpTask::class.java)
            project.tasks.register("opencode-mcp", OpencodeMcpTask::class.java)
            project.tasks.register("opencode-debug", OpencodeDebugTask::class.java)
            project.tasks.register("opencode-providers", OpencodeProvidersTask::class.java)
            project.tasks.register("opencode-agent", OpencodeAgentTask::class.java)
            project.tasks.register("opencode-uninstall", OpencodeUninstallTask::class.java)
            project.tasks.register("opencode-stats", OpencodeStatsTask::class.java)
            project.tasks.register("opencode-github", OpencodeGithubTask::class.java)
            project.tasks.register("opencode-session", OpencodeSessionTask::class.java)
            project.tasks.register("opencode-db", OpencodeDbTask::class.java)
            project.tasks.register("opencode-version", OpencodeVersionTask::class.java)
        })
    }
}
