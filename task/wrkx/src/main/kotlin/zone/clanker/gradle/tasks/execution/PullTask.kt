package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.core.WrkxRepo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@org.gradle.api.tasks.UntrackedTask(because = "Pulls latest changes in external repositories")
abstract class PullTask : DefaultTask() {

    @get:Internal
    val extensionRepos: MutableList<WrkxRepo> = mutableListOf()

    init {
        group = "opsx"
        description = "[tool] Stash, checkout main, pull for all enabled monolith repos. " +
            "Use when: Syncing all repos to latest main."
    }

    private data class RepoResult(
        val name: String,
        val status: String,
        val detail: String = ""
    )

    @TaskAction
    fun pull() {
        val repos = extensionRepos.filter { it.enabled && it.clonePath.exists() }

        if (repos.isEmpty()) {
            logger.lifecycle("OpenSpec: No enabled repos with existing directories.")
            return
        }

        val threads = minOf(repos.size, Runtime.getRuntime().availableProcessors(), 4)
        val executor = Executors.newFixedThreadPool(threads)
        val results = mutableListOf<RepoResult>()

        try {
            val futures = repos.map { repo ->
                executor.submit(Callable { pullRepo(repo) })
            }
            for (future in futures) {
                results.add(future.get())
            }
        } finally {
            executor.shutdown()
        }

        println()
        for (result in results.sortedBy { it.name }) {
            val icon = when (result.status) {
                "OK" -> "  OK    "
                "SKIP" -> "  SKIP  "
                else -> "  FAIL  "
            }
            println("$icon${result.name}")
            if (result.detail.isNotBlank()) {
                println("        ${result.detail}")
            }
        }

        val ok = results.count { it.status == "OK" }
        val skipped = results.count { it.status == "SKIP" }
        val failed = results.count { it.status == "FAIL" }
        println()
        println("Summary: $ok pulled, $skipped skipped, $failed failed (${repos.size} total)")
    }

    private fun pullRepo(repo: WrkxRepo): RepoResult {
        val dir = repo.clonePath

        // Stash any uncommitted changes
        val stashResult = git(dir, "stash", "--include-untracked")
        val stashed = stashResult.success && !stashResult.output.contains("No local changes")

        // Checkout main
        val checkoutResult = git(dir, "checkout", "main")
        if (!checkoutResult.success) {
            // Try master as fallback
            val masterResult = git(dir, "checkout", "master")
            if (!masterResult.success) {
                if (stashed) git(dir, "stash", "pop")
                return RepoResult(repo.repoName, "FAIL", "could not checkout main or master")
            }
        }

        // Pull latest
        val pullResult = git(dir, "pull", "--ff-only")
        if (!pullResult.success) {
            if (stashed) git(dir, "stash", "pop")
            return RepoResult(repo.repoName, "FAIL", "pull failed: ${pullResult.output.trim()}")
        }

        val detail = if (stashed) "stashed changes restored" else ""
        if (stashed) git(dir, "stash", "pop")
        return RepoResult(repo.repoName, "OK", detail)
    }

    private data class GitResult(val success: Boolean, val output: String)

    private fun git(dir: File, vararg args: String): GitResult {
        return try {
            val process = ProcessBuilder("git", *args)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(2, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                GitResult(false, "timed out")
            } else {
                GitResult(process.exitValue() == 0, output)
            }
        } catch (e: Exception) {
            GitResult(false, e.message ?: "unknown error")
        }
    }
}
