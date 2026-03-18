package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import java.io.File

@CacheableTask
abstract class OpenSpecTreeTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val module: Property<String>

    @get:Input
    @get:Optional
    abstract val scope: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Source tree generator. " +
            "Output: .opsx/tree.md. " +
            "Options: -Pmodule=name -Pscope=main|test. " +
            "Use when: You need to see the file/directory structure of source sets. " +
            "Chain: Use with opsx-context for full project understanding."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val sb = StringBuilder()
        sb.appendLine("# Source Tree")
        sb.appendLine()

        val root = project.rootProject
        val projects = SourceDiscovery.resolveProjects(project, module.orNull)

        if (projects.isEmpty()) {
            sb.appendLine("> No modules found.")
            out.writeText(sb.toString())
            return
        }

        val scopeFilter = if (scope.isPresent) scope.get().lowercase() else null
        var totalFiles = 0
        var totalDirs = 0
        var renderedModules = 0

        for (proj in projects) {
            val srcDirs = SourceDiscovery.discoverSourceDirs(proj)
            val filteredDirs = if (scopeFilter != null) {
                srcDirs.filter { dir ->
                    // Extract the source set segment from paths like src/<sourceSet>/kotlin
                    val segments = dir.toPath().iterator().asSequence().map { it.toString() }.toList()
                    val srcIdx = segments.indexOf("src")
                    val sourceSetName = if (srcIdx >= 0 && srcIdx + 1 < segments.size) segments[srcIdx + 1].lowercase() else ""
                    when (scopeFilter) {
                        "main" -> !sourceSetName.contains("test")
                        "test" -> sourceSetName.contains("test")
                        else -> true
                    }
                }
            } else srcDirs

            if (filteredDirs.isEmpty()) continue
            renderedModules++

            val label = if (proj == root && root.subprojects.isEmpty()) "Root" else proj.path
            sb.appendLine("## $label")
            sb.appendLine()

            for (srcDir in filteredDirs) {
                val relPath = if (srcDir.toPath().startsWith(root.projectDir.toPath())) {
                    srcDir.relativeTo(root.projectDir).path
                } else {
                    srcDir.absolutePath
                }
                val stats = countFiles(srcDir)
                totalFiles += stats.first
                totalDirs += stats.second

                sb.appendLine("### `$relPath/` (${stats.first} files)")
                sb.appendLine()
                sb.appendLine("```")
                renderDirectoryTree(sb, srcDir, "")
                sb.appendLine("```")
                sb.appendLine()
            }
        }

        // Add summary at the top
        val summary = "**$totalFiles source files** across **$renderedModules modules**\n\n"
        sb.insert(sb.indexOf("\n") + 1, "\n$summary")

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated source tree ($totalFiles files) at ${out.relativeTo(root.projectDir)}")
    }

    private fun countFiles(dir: File): Pair<Int, Int> {
        var files = 0
        var dirs = 0
        dir.walkTopDown().forEach {
            if (it.isFile && (it.extension == "kt" || it.extension == "java")) files++
            if (it.isDirectory && it != dir) dirs++
        }
        return files to dirs
    }

    private fun renderDirectoryTree(sb: StringBuilder, dir: File, prefix: String) {
        val children = dir.listFiles()?.sorted() ?: return
        val dirs = children.filter { it.isDirectory }
        val files = children.filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        val allItems = dirs + files

        for ((i, item) in allItems.withIndex()) {
            val isLast = i == allItems.size - 1
            val connector = if (isLast) "└── " else "├── "
            val childPrefix = prefix + if (isLast) "    " else "│   "

            if (item.isDirectory) {
                val fileCount = countFiles(item).first
                if (fileCount == 0) continue // skip empty dirs
                sb.appendLine("$prefix$connector${item.name}/ ($fileCount)")
                renderDirectoryTree(sb, item, childPrefix)
            } else {
                sb.appendLine("$prefix$connector${item.name}")
            }
        }
    }
}
