package zone.clanker.gradle.tasks.execution

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.provider.Property
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@UntrackedTask(because = "Edits files across included builds")
abstract class BatchEditTask : DefaultTask() {

    @get:Input
    abstract val find: Property<String>

    @get:Input
    abstract val replace: Property<String>

    @get:Input
    @get:Optional
    abstract val glob: Property<String>

    init {
        group = "opsx"
        description = "[tool] Batch find-and-replace across all included builds. " +
            "Options: -Pfind=old -Preplace=new -Pglob=*.gradle.kts (default: build scripts). " +
            "Use when: Updating versions, dependencies, or config across multiple repos."
    }

    @TaskAction
    fun execute() {
        val findText = find.get()
        val replaceText = replace.get()
        val fileGlob = glob.getOrElse("build.gradle.kts,settings.gradle.kts,libs.versions.toml,gradle.properties")
        val extensions = fileGlob.split(",").map { it.trim() }

        val builds = project.gradle.includedBuilds
        val allDirs = mutableListOf(project.rootProject.projectDir)
        for (build in builds) {
            allDirs.add(build.projectDir)
        }

        val threads = minOf(allDirs.size, Runtime.getRuntime().availableProcessors(), 8)
        val executor = Executors.newFixedThreadPool(threads)
        val results = mutableListOf<Pair<String, Int>>()

        try {
            val futures = allDirs.map { dir ->
                executor.submit(Callable {
                    val count = replaceInDir(dir, findText, replaceText, extensions)
                    dir.name to count
                })
            }
            for (future in futures) {
                results.add(future.get())
            }
        } finally {
            executor.shutdown()
        }

        val total = results.sumOf { it.second }
        println()
        for ((name, count) in results.sortedBy { it.first }) {
            if (count > 0) println("  $name: $count file(s) changed")
        }
        println()
        println("Replaced '$findText' → '$replaceText' in $total file(s) across ${allDirs.size} build(s)")
    }

    private fun replaceInDir(dir: File, find: String, replace: String, extensions: List<String>): Int {
        var count = 0
        dir.walkTopDown()
            .filter { it.isFile }
            .filter { file -> extensions.any { ext -> file.name.endsWith(ext) || file.name == ext } }
            .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") }
            .forEach { file ->
                val content = file.readText()
                if (content.contains(find)) {
                    file.writeText(content.replace(find, replace))
                    count++
                }
            }
        return count
    }
}
