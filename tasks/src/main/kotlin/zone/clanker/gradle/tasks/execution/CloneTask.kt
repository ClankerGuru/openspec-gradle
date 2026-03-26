package zone.clanker.gradle.tasks.execution

import zone.clanker.gradle.core.RepoEntry
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

@org.gradle.api.tasks.UntrackedTask(because = "Clones external repositories based on config file")
abstract class CloneTask : DefaultTask() {

    @get:Input
    abstract val reposFile: Property<String>

    @get:Input
    abstract val reposDir: Property<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    init {
        group = "opsx"
        description = "[tool] Clone repositories from monolith.json via gh. " +
            "Use when: Setting up a workspace or syncing missing repos. " +
            "Params: -PdryRun=false to clone, -PreposDir=path."
    }

    @TaskAction
    fun clone() {
        val home = System.getProperty("user.home")
        val configFile = File(reposFile.get().replace("~", home))
        val baseDir = File(reposDir.get().replace("~", home))
        val isDryRun = dryRun.get()

        if (!configFile.exists()) {
            logger.warn("OpenSpec: Config file not found: ${configFile.absolutePath}")
            logger.warn("OpenSpec: Create it with an array of repo entries, e.g.:")
            logger.warn("""  [{"name":"MyOrg/my-repo","enable":true,"category":"internal","substitutions":[]}]""")
            return
        }

        val entries = RepoEntry.parseFile(configFile)
        val enabled = entries.filter { it.enable }

        if (enabled.isEmpty()) {
            logger.lifecycle("OpenSpec: No enabled repos in ${configFile.absolutePath}")
            return
        }

        // Check gh is available
        if (!isDryRun) {
            try {
                val proc = ProcessBuilder("gh", "--version")
                    .redirectErrorStream(true)
                    .start()
                proc.waitFor()
            } catch (e: Exception) {
                throw GradleException(
                    "gh (GitHub CLI) is not available on PATH. Install it: https://cli.github.com/"
                )
            }
        }

        baseDir.mkdirs()

        val results = mutableMapOf<String, MutableList<String>>()
        var cloned = 0
        var skipped = 0
        var failed = 0

        for (entry in enabled) {
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

            logger.lifecycle("Cloning ${entry.name}...")
            val process = ProcessBuilder("gh", "repo", "clone", entry.name, targetDir.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                categoryList.add("  CLONE ${entry.name} → ${targetDir.absolutePath}")
                cloned++
            } else {
                categoryList.add("  FAIL  ${entry.name} — exit code $exitCode")
                if (output.isNotBlank()) {
                    categoryList.add("        $output")
                }
                failed++
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
        println("Summary: $cloned cloned, $skipped skipped, $failed failed (${enabled.size} total)")
        if (isDryRun) println("Base directory: ${baseDir.absolutePath}")
    }
}
