package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.WRKX_GROUP

import zone.clanker.gradle.core.WrkxRepo
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@org.gradle.api.tasks.UntrackedTask(because = "Reads external repo state from disk")
abstract class ReposTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    val extensionRepos: MutableList<WrkxRepo> = mutableListOf()

    init {
        group = WRKX_GROUP
        description = "[tool] List all monolith repos with their config and disk status. " +
            "Output: .opsx/repos.md. " +
            "Use when: Setting up a workspace, reviewing which repos are enabled/cloned/substituted."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        if (extensionRepos.isEmpty()) {
            out.writeText("# Workspace Repos\n\nNo repos configured. Add repos to `workspace.json`.\n")
            logger.lifecycle("OpenSpec: No repos configured.")
            return
        }

        val sb = StringBuilder()
        sb.appendLine("# Monolith Repos")
        sb.appendLine()

        val grouped = extensionRepos.groupBy { it.category.ifBlank { "default" } }
        val total = extensionRepos.size
        val enabled = extensionRepos.count { it.enabled }
        val cloned = extensionRepos.count { it.clonePath.exists() }
        val substituted = extensionRepos.count { it.substitute }

        sb.appendLine("**${total} repos** — $enabled enabled, $cloned cloned on disk, $substituted with substitution")
        sb.appendLine()

        // Summary table
        sb.appendLine("## All Repos")
        sb.appendLine()
        sb.appendLine("| # | Repo | Category | Enabled | Cloned | Substitute | Ref | Substitutions |")
        sb.appendLine("|---|------|----------|---------|--------|------------|-----|---------------|")

        var idx = 0
        for ((category, repos) in grouped.entries.sortedBy { it.key }) {
            for (repo in repos.sortedBy { it.repoName }) {
                idx++
                val onDisk = if (repo.clonePath.exists()) "yes" else "no"
                val enabledStr = if (repo.enabled) "yes" else "no"
                val subStr = if (repo.substitute) "yes" else "no"
                val subs = if (repo.substitutions.isNotEmpty()) {
                    repo.substitutions.joinToString(", ") { "`$it`" }
                } else {
                    "—"
                }
                sb.appendLine("| $idx | `${repo.repoName}` | $category | $enabledStr | $onDisk | $subStr | `${repo.ref}` | $subs |")
            }
        }

        sb.appendLine()

        // By category detail
        sb.appendLine("## By Category")
        sb.appendLine()
        for ((category, repos) in grouped.entries.sortedBy { it.key }) {
            sb.appendLine("### $category")
            sb.appendLine()
            for (repo in repos.sortedBy { it.repoName }) {
                val status = mutableListOf<String>()
                if (repo.enabled) status.add("enabled") else status.add("disabled")
                if (repo.clonePath.exists()) status.add("cloned") else status.add("not cloned")
                if (repo.substitute) status.add("substitution on")
                sb.appendLine("- **${repo.repoName}** — ${status.joinToString(", ")} — ref: `${repo.ref}`")
                if (repo.substitute && repo.substitutions.isNotEmpty()) {
                    for (sub in repo.substitutions) {
                        sb.appendLine("  - substitutes: `$sub`")
                    }
                }
            }
            sb.appendLine()
        }

        // JSON-like structured output for agent parsing
        sb.appendLine("## Machine-Readable Config")
        sb.appendLine()
        sb.appendLine("```")
        for (repo in extensionRepos.sortedBy { it.repoName }) {
            val onDisk = repo.clonePath.exists()
            sb.appendLine("repo=${repo.repoName} category=${repo.category} enabled=${repo.enabled} cloned=$onDisk substitute=${repo.substitute} ref=${repo.ref} substitutions=[${repo.substitutions.joinToString(";")}]")
        }
        sb.appendLine("```")

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated repos catalog at ${out.name} ($total repos)")
    }
}
