package zone.clanker.gradle.tasks.discovery

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import java.io.File

@UntrackedTask(because = "Reads dynamic project model state (subprojects, source dirs) not captured as inputs")
abstract class TreeTask : DefaultTask() {

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
            "Chain: Use with srcx-context for full project understanding."
    }

    @TaskAction
    fun generate() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()

        val root = project.rootProject
        val isMultiModule = root.subprojects.isNotEmpty() && !module.isPresent
        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val scopeFilter = if (scope.isPresent) scope.get().lowercase() else null

        if (projects.isEmpty()) {
            out.writeText("# Source Tree\n\n> No modules found.\n")
            return
        }

        if (isMultiModule) {
            generateMultiModule(out, root, projects, scopeFilter)
        } else {
            generateSingleModule(out, root, projects, scopeFilter)
        }
    }

    private fun generateSingleModule(out: File, root: org.gradle.api.Project, projects: List<org.gradle.api.Project>, scopeFilter: String?) {
        val sb = StringBuilder()
        sb.appendLine("# Source Tree")
        sb.appendLine()

        var totalFiles = 0
        var totalDirs = 0
        var renderedModules = 0

        for (proj in projects) {
            val filteredDirs = filterDirs(proj, scopeFilter)
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

        val summary = "**$totalFiles source files** across **$renderedModules modules**\n\n"
        sb.insert(sb.indexOf("\n") + 1, "\n$summary")

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated source tree ($totalFiles files) at ${out.relativeTo(root.projectDir)}")
    }

    private fun generateMultiModule(out: File, root: org.gradle.api.Project, projects: List<org.gradle.api.Project>, scopeFilter: String?) {
        val subDir = out.parentFile.resolve("tree")
        subDir.mkdirs()

        data class ModuleStats(val name: String, val displayName: String, val files: Int)
        val stats = mutableListOf<ModuleStats>()

        for (proj in projects) {
            val filteredDirs = filterDirs(proj, scopeFilter)
            if (filteredDirs.isEmpty()) continue

            val moduleName = if (proj == root) "root" else proj.name
            val displayName = if (proj == root) ":root" else proj.path

            val sb = StringBuilder()
            sb.appendLine("# Source Tree — $displayName")
            sb.appendLine()

            var totalFiles = 0
            for (srcDir in filteredDirs) {
                val relPath = if (srcDir.toPath().startsWith(root.projectDir.toPath())) {
                    srcDir.relativeTo(root.projectDir).path
                } else {
                    srcDir.absolutePath
                }
                val fileStats = countFiles(srcDir)
                totalFiles += fileStats.first

                sb.appendLine("### `$relPath/` (${fileStats.first} files)")
                sb.appendLine()
                sb.appendLine("```")
                renderDirectoryTree(sb, srcDir, "")
                sb.appendLine("```")
                sb.appendLine()
            }

            File(subDir, "$moduleName.md").writeText(sb.toString())
            stats.add(ModuleStats(moduleName, displayName, totalFiles))
        }

        // Write index
        val idx = StringBuilder()
        idx.appendLine("# Source Tree")
        idx.appendLine()
        if (stats.isEmpty()) {
            idx.appendLine("> No source files found.")
        } else {
            idx.appendLine("| Module | Files |")
            idx.appendLine("|--------|-------|")
            for (s in stats) {
                idx.appendLine("| [${s.displayName}](tree/${s.name}.md) | ${s.files} |")
            }
        }
        idx.appendLine()
        out.writeText(idx.toString())
        logger.lifecycle("OpenSpec: Generated source tree (${stats.size} modules) at ${out.relativeTo(root.projectDir)}")
    }

    private fun filterDirs(proj: org.gradle.api.Project, scopeFilter: String?): List<File> {
        val srcDirs = SourceDiscovery.discoverSourceDirs(proj)
        return if (scopeFilter != null) {
            srcDirs.filter { dir ->
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
