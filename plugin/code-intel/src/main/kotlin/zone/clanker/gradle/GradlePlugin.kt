package zone.clanker.gradle

import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.tasks.discovery.*
import zone.clanker.gradle.tasks.intelligence.*
import zone.clanker.gradle.tasks.refactoring.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings

private fun Project.intProperty(name: String): Int? =
    if (hasProperty(name)) {
        val raw = property(name).toString()
        raw.toIntOrNull() ?: throw org.gradle.api.GradleException("Invalid integer value '$raw' for property '$name'")
    } else null

class GradlePlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.gradle.rootProject(org.gradle.api.Action { project ->
            applyToProject(project)
        })
    }

    companion object {
        internal fun applyToProject(project: Project) {
            if (project.tasks.findByName("opsx-context") != null) return

            val sourceFileTree = project.files().apply {
                val projects = SourceDiscovery.resolveProjects(project, null)
                val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
                for (dir in srcDirs) {
                    from(project.fileTree(dir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                        override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                            ft.include("**/*.kt", "**/*.java")
                        }
                    }))
                }
            }
            val rootDir = project.rootProject.projectDir
            val buildFileTree = project.rootProject.fileTree(rootDir, object : org.gradle.api.Action<org.gradle.api.file.ConfigurableFileTree> {
                override fun execute(ft: org.gradle.api.file.ConfigurableFileTree) {
                    ft.include("build.gradle.kts", "settings.gradle.kts",
                        "gradle.properties", "*.lockfile", "gradle/libs.versions.toml")
                    ft.include("**/build.gradle.kts", "**/gradle.properties")
                    ft.exclude("build/", "**/build/", ".gradle/", "**/.gradle/")
                }
            })

            // Discovery
            project.tasks.register("opsx-context", ContextTask::class.java).configure(object : org.gradle.api.Action<ContextTask> {
                override fun execute(task: ContextTask) {
                    task.buildFiles.from(buildFileTree)
                    task.contextFile.set(project.layout.projectDirectory.file(".opsx/context.md"))
                }
            })

            project.tasks.register("opsx-tree", TreeTask::class.java).configure(object : org.gradle.api.Action<TreeTask> {
                override fun execute(task: TreeTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("scope")) task.scope.set(project.property("scope").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/tree.md"))
                }
            })

            project.tasks.register("opsx-deps", DepsTask::class.java).configure(object : org.gradle.api.Action<DepsTask> {
                override fun execute(task: DepsTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/deps.md"))
                }
            })

            project.tasks.register("opsx-modules", ModulesTask::class.java).configure(object : org.gradle.api.Action<ModulesTask> {
                override fun execute(task: ModulesTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/modules.md"))
                }
            })

            project.tasks.register("opsx-devloop", DevloopTask::class.java).configure(object : org.gradle.api.Action<DevloopTask> {
                override fun execute(task: DevloopTask) {
                    task.buildFiles.from(buildFileTree)
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/devloop.md"))
                }
            })

            project.tasks.register("opsx-symbols", SymbolsTask::class.java).configure(object : org.gradle.api.Action<SymbolsTask> {
                override fun execute(task: SymbolsTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("file")) task.targetFile.set(project.property("file").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/symbols.md"))
                }
            })

            // Intelligence
            project.tasks.register("opsx-arch", ArchTask::class.java).configure(object : org.gradle.api.Action<ArchTask> {
                override fun execute(task: ArchTask) {
                    task.sourceFiles.from(sourceFileTree)
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/arch.md"))
                }
            })

            project.tasks.register("opsx-find", FindTask::class.java).configure(object : org.gradle.api.Action<FindTask> {
                override fun execute(task: FindTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/find.md"))
                }
            })

            project.tasks.register("opsx-calls", CallsTask::class.java).configure(object : org.gradle.api.Action<CallsTask> {
                override fun execute(task: CallsTask) {
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/calls.md"))
                }
            })

            project.tasks.register("opsx-usages", UsagesTask::class.java).configure(object : org.gradle.api.Action<UsagesTask> {
                override fun execute(task: UsagesTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/usages.md"))
                }
            })

            project.tasks.register("opsx-verify", VerifyTask::class.java).configure(object : org.gradle.api.Action<VerifyTask> {
                override fun execute(task: VerifyTask) {
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    project.intProperty("maxWarnings")?.let { task.maxWarnings.set(it) }
                    if (project.hasProperty("failOnWarning")) task.failOnWarning.set(project.property("failOnWarning").toString().lowercase() == "true")
                    if (project.hasProperty("noCycles")) task.noCycles.set(project.property("noCycles").toString().lowercase() == "true")
                    project.intProperty("maxInheritanceDepth")?.let { task.maxInheritanceDepth.set(it) }
                    project.intProperty("maxClassSize")?.let { task.maxClassSize.set(it) }
                    project.intProperty("maxImports")?.let { task.maxImports.set(it) }
                    project.intProperty("maxMethods")?.let { task.maxMethods.set(it) }
                    if (project.hasProperty("noSmells")) task.noSmells.set(project.property("noSmells").toString().lowercase() == "true")
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/verify.md"))
                }
            })

            // Refactoring
            project.tasks.register("opsx-rename", RenameTask::class.java).configure(object : org.gradle.api.Action<RenameTask> {
                override fun execute(task: RenameTask) {
                    if (project.hasProperty("from")) task.from.set(project.property("from").toString())
                    if (project.hasProperty("to")) task.to.set(project.property("to").toString())
                    if (project.hasProperty("dryRun")) task.dryRun.set(project.property("dryRun").toString().lowercase() == "true")
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/rename.md"))
                }
            })

            project.tasks.register("opsx-move", MoveTask::class.java).configure(object : org.gradle.api.Action<MoveTask> {
                override fun execute(task: MoveTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("targetPackage")) task.targetPackage.set(project.property("targetPackage").toString())
                    if (project.hasProperty("dryRun")) task.dryRun.set(project.property("dryRun").toString().lowercase() == "true")
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/move.md"))
                }
            })

            project.tasks.register("opsx-extract", ExtractTask::class.java).configure(object : org.gradle.api.Action<ExtractTask> {
                override fun execute(task: ExtractTask) {
                    if (project.hasProperty("sourceFile")) task.sourceFile.set(project.property("sourceFile").toString())
                    project.intProperty("startLine")?.let { task.startLine.set(it) }
                    project.intProperty("endLine")?.let { task.endLine.set(it) }
                    if (project.hasProperty("newName")) task.newName.set(project.property("newName").toString())
                    if (project.hasProperty("dryRun")) task.dryRun.set(project.property("dryRun").toString().lowercase() == "true")
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/extract.md"))
                }
            })

            project.tasks.register("opsx-remove", RemoveTask::class.java).configure(object : org.gradle.api.Action<RemoveTask> {
                override fun execute(task: RemoveTask) {
                    if (project.hasProperty("symbol")) task.symbol.set(project.property("symbol").toString())
                    if (project.hasProperty("file")) task.sourceFile.set(project.property("file").toString())
                    project.intProperty("startLine")?.let { task.startLine.set(it) }
                    project.intProperty("endLine")?.let { task.endLine.set(it) }
                    if (project.hasProperty("dryRun")) task.dryRun.set(project.property("dryRun").toString().lowercase() == "true")
                    if (project.hasProperty("module")) task.module.set(project.property("module").toString())
                    task.outputFile.set(project.layout.projectDirectory.file(".opsx/remove.md"))
                }
            })
        }
    }
}
