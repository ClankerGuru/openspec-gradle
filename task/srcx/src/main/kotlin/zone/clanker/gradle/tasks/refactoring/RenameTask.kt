package zone.clanker.gradle.tasks.refactoring

import zone.clanker.gradle.tasks.SRCX_GROUP

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.Renamer
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import java.io.File

@UntrackedTask(because = "Rename modifies source files across the project")
abstract class RenameTask : DefaultTask() {

    @get:Input
    abstract val from: Property<String>

    @get:Input
    abstract val to: Property<String>

    @get:Input @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = SRCX_GROUP
        description = "[tool] Safe rename across the codebase. " +
            "Output: .opsx/rename.md. " +
            "Options: -Pfrom=OldName -Pto=NewName (required), -PdryRun=true (preview only). " +
            "Use when: You need to rename a class, function, or property safely. " +
            "Chain: Run srcx-find first for impact analysis."
    }

    @TaskAction
    fun rename() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir
        val fromName = from.get()
        val toName = to.get()
        val preview = dryRun.getOrElse(false)

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Rename\n\n> No source files found.\n")
            return
        }

        logger.lifecycle("OpenSpec: Computing rename `$fromName` → `$toName`...")
        val index = SymbolIndex.build(sourceFiles)
        val renamer = Renamer(index)
        val edits = renamer.computeRename(fromName, toName)

        val sb = StringBuilder()
        sb.appendLine("# Rename: `$fromName` → `$toName`")
        sb.appendLine()

        if (edits.isEmpty()) {
            sb.appendLine("> No symbol found matching `$fromName`.")
        } else {
            val byFile = edits.groupBy { it.file }
            sb.appendLine("**${edits.size} edits** across **${byFile.size} files**")
            sb.appendLine()

            if (preview) {
                sb.appendLine("⚠️ **DRY RUN** — no files were modified.")
                sb.appendLine()
            }

            for ((file, fileEdits) in byFile.entries.sortedBy { it.key.name }) {
                val relPath = file.relativeTo(rootDir).path
                sb.appendLine("### `$relPath`")
                sb.appendLine()
                for (edit in fileEdits.sortedBy { it.line }) {
                    sb.appendLine("- Line ${edit.line} (${edit.kind}): `${edit.oldText}` → `${edit.newText}`")
                }
                sb.appendLine()
            }

            if (!preview) {
                val modified = renamer.applyRename(edits)
                sb.appendLine("✅ **Applied.** ${modified.size} files modified.")
            }
        }

        out.writeText(sb.toString())
        logger.lifecycle(sb.toString().trimEnd())
    }
}
