package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.tasks.WRKX_GROUP

import zone.clanker.gradle.core.WrkxRepo
import zone.clanker.gradle.core.RepoEntry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@org.gradle.api.tasks.UntrackedTask(because = "Clones external repositories based on config file")
abstract class CloneTask : DefaultTask() {

    @get:Input
    abstract val reposFile: Property<String>

    @get:Input
    abstract val reposDir: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    /** Populated by the plugin from the MonolithExtension. If empty, falls back to JSON file. */
    @get:Internal
    val extensionRepos: MutableList<WrkxRepo> = mutableListOf()

    init {
        group = WRKX_GROUP
        description = "[tool] Clone repositories from workspace.json via gh. " +
            "Use when: Setting up a workspace or syncing missing repos. " +
            "Params: -PdryRun=false to clone, -PreposDir=path."
    }

    private data class CloneEntry(val name: String, val category: String, val directoryName: String)

    private sealed class CloneResult {
        data class Cloned(val entry: CloneEntry, val targetDir: File) : CloneResult()
        data class Failed(val entry: CloneEntry, val exitCode: Int, val output: String) : CloneResult()
    }

    @TaskAction
    fun clone() {
        val home = System.getProperty("user.home")
        val baseDir = File(reposDir.get().replace("~", home))
        val isDryRun = dryRun.get()

        // Resolve entries: prefer extension, fall back to JSON file
        val entries: List<CloneEntry> = if (extensionRepos.isNotEmpty()) {
            extensionRepos.filter { it.enabled }.map { repo ->
                val dirName = RepoEntry(repo.repoName, true, repo.category, repo.substitutions).directoryName
                CloneEntry(repo.repoName, repo.category, dirName)
            }
        } else {
            val configFile = File(reposFile.get().replace("~", home))
            if (!configFile.exists()) {
                logger.warn("OpenSpec: Config file not found: ${configFile.absolutePath}")
                logger.warn("OpenSpec: Create it with an array of repo entries, e.g.:")
                logger.warn("""  [{"name":"MyOrg/my-repo","enable":true,"category":"internal","substitutions":[]}]""")
                return
            }
            RepoEntry.parseFile(configFile).filter { it.enable }.map {
                CloneEntry(it.name, it.category, it.directoryName)
            }
        }

        if (entries.isEmpty()) {
            logger.lifecycle("OpenSpec: No enabled repos to clone.")
            return
        }

        // Check gh is available
        if (!isDryRun) {
            try {
                val proc = ProcessBuilder("gh", "--version")
                    .redirectErrorStream(true)
                    .start()
                val exitCode = proc.waitFor()
                if (exitCode != 0) {
                    throw GradleException(
                        "gh (GitHub CLI) returned exit code $exitCode. Ensure it is installed and authenticated: https://cli.github.com/"
                    )
                }
            } catch (e: GradleException) {
                throw e
            } catch (e: Exception) {
                throw GradleException(
                    "gh (GitHub CLI) is not available on PATH. Install it: https://cli.github.com/", e
                )
            }
            baseDir.mkdirs()
        }

        val results = mutableMapOf<String, MutableList<String>>()
        var cloned = 0
        var skipped = 0
        var failed = 0

        // Separate skip/dry-run from actual clones
        val toClone = mutableListOf<CloneEntry>()

        for (entry in entries) {
            val targetDir = File(baseDir, entry.directoryName)
            val category = entry.category.ifBlank { "default" }
            val categoryList = results.getOrPut(category) { mutableListOf() }

            if (targetDir.exists()) {
                categoryList.add("  SKIP  ${entry.name} (already exists)")
                skipped++
                continue
            }

            if (isDryRun) {
                categoryList.add("  CLONE ${entry.name} → ${targetDir.absolutePath}")
                cloned++
                continue
            }

            toClone.add(entry)
        }

        // Clone in parallel
        if (toClone.isNotEmpty()) {
            val threads = minOf(toClone.size, Runtime.getRuntime().availableProcessors(), 4)
            logger.lifecycle("Cloning ${toClone.size} repo(s) with $threads threads...")

            val executor = Executors.newFixedThreadPool(threads)
            try {
                val futures = toClone.map { entry ->
                    val targetDir = File(baseDir, entry.directoryName)
                    executor.submit(Callable {
                        val process = ProcessBuilder("gh", "repo", "clone", entry.name, targetDir.absolutePath)
                            .redirectErrorStream(true)
                            .start()
                        val output = process.inputStream.bufferedReader().readText()
                        val finished = process.waitFor(5, TimeUnit.MINUTES)
                        if (!finished) {
                            process.destroyForcibly()
                            CloneResult.Failed(entry, -1, "Clone timed out after 5 minutes")
                        } else {
                            val exitCode = process.exitValue()
                            if (exitCode == 0) CloneResult.Cloned(entry, targetDir)
                            else CloneResult.Failed(entry, exitCode, output)
                        }
                    })
                }

                for (future in futures) {
                    when (val result = future.get()) {
                        is CloneResult.Cloned -> {
                            val category = result.entry.category.ifBlank { "default" }
                            results.getOrPut(category) { mutableListOf() }
                                .add("  CLONE ${result.entry.name} → ${result.targetDir.absolutePath}")
                            cloned++
                        }
                        is CloneResult.Failed -> {
                            val category = result.entry.category.ifBlank { "default" }
                            val categoryList = results.getOrPut(category) { mutableListOf() }
                            categoryList.add("  FAIL  ${result.entry.name} — exit code ${result.exitCode}")
                            if (result.output.isNotBlank()) {
                                categoryList.add("        ${result.output}")
                            }
                            failed++
                        }
                    }
                }
            } finally {
                executor.shutdown()
            }
        }

        // Print summary
        println()
        if (isDryRun) println("DRY RUN — no repos will be cloned. Use -PdryRun=false to clone.")
        println()
        for ((category, lines) in results.entries.sortedBy { it.key }) {
            println("[$category]")
            lines.forEach { println(it) }
            println()
        }
        println("Summary: $cloned cloned, $skipped skipped, $failed failed (${entries.size} total)")
        if (isDryRun) println("Base directory: ${baseDir.absolutePath}")
    }
}
