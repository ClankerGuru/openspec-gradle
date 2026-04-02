package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.WRKX_GROUP

import zone.clanker.gradle.core.WrkxRepo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@org.gradle.api.tasks.UntrackedTask(because = "Checks out git refs in external repositories")
abstract class CheckoutTask : DefaultTask() {

    @get:Internal
    val extensionRepos: MutableList<WrkxRepo> = mutableListOf()

    init {
        group = WRKX_GROUP
        description = "[tool] Checkout configured ref (branch/tag) in all enabled monolith repos. " +
            "Use when: Switching all repos to a feature branch or syncing to tags."
    }

    private data class RepoResult(
        val name: String,
        val ref: String,
        val status: String,
        val detail: String = ""
    )

    @TaskAction
    fun checkout() {
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
                executor.submit(Callable { checkoutRepo(repo) })
            }
            for (future in futures) {
                results.add(future.get())
            }
        } finally {
            executor.shutdown()
        }

        // Print results
        println()
        for (result in results.sortedBy { it.name }) {
            val icon = when (result.status) {
                "OK" -> "  OK    "
                "CREATED" -> "  NEW   "
                "SKIP" -> "  SKIP  "
                else -> "  FAIL  "
            }
            println("$icon${result.name} @ ${result.ref}")
            if (result.detail.isNotBlank()) {
                println("        ${result.detail}")
            }
        }

        val ok = results.count { it.status in setOf("OK", "CREATED") }
        val skipped = results.count { it.status == "SKIP" }
        val failed = results.count { it.status == "FAIL" }
        println()
        println("Summary: $ok checked out, $skipped skipped, $failed failed (${repos.size} total)")
    }

    private fun checkoutRepo(repo: WrkxRepo): RepoResult {
        val dir = repo.clonePath
        val ref = repo.ref

        // If already on the target ref, skip
        val currentBranch = git(dir, "rev-parse", "--abbrev-ref", "HEAD")
        if (currentBranch.success && currentBranch.output.trim() == ref) {
            return RepoResult(repo.repoName, ref, "SKIP", "already on $ref")
        }

        // Stash any uncommitted changes
        val stashResult = git(dir, "stash", "--include-untracked")
        val stashed = stashResult.success && !stashResult.output.contains("No local changes")

        // Fetch to make sure we have the latest refs
        git(dir, "fetch", "--all", "--tags", "--prune")

        // Try to checkout the ref
        val checkoutResult = git(dir, "checkout", ref)
        if (!checkoutResult.success) {
            // Branch doesn't exist locally — try to create from origin
            val createResult = git(dir, "checkout", "-b", ref, "origin/$ref")
            if (!createResult.success) {
                // Remote branch doesn't exist either — create from current HEAD
                val newBranch = git(dir, "checkout", "-b", ref)
                if (!newBranch.success) {
                    if (stashed) git(dir, "stash", "pop")
                    return RepoResult(repo.repoName, ref, "FAIL", newBranch.output.trim())
                }
                if (stashed) git(dir, "stash", "pop")
                return RepoResult(repo.repoName, ref, "CREATED", "new branch from HEAD")
            }
            if (stashed) git(dir, "stash", "pop")
            return RepoResult(repo.repoName, ref, "OK", "tracking origin/$ref")
        }

        // If checking out main, pull latest
        if (ref == "main" || ref == "master") {
            git(dir, "pull", "--ff-only")
        }

        if (stashed) git(dir, "stash", "pop")
        return RepoResult(repo.repoName, ref, "OK")
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
