package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import zone.clanker.gradle.psi.SourceDiscovery
import zone.clanker.gradle.psi.SymbolIndex
import zone.clanker.gradle.psi.MethodCall
import java.io.File

@UntrackedTask(because = "Call graph reads all project sources dynamically")
abstract class OpenSpecCallsTask : DefaultTask() {

    @get:Input @get:Optional
    abstract val module: Property<String>

    @get:Input @get:Optional
    abstract val symbol: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "opsx"
        description = "[tool] Method-level call graph with Mermaid diagrams. " +
            "Output: .openspec/calls.md. " +
            "Options: -Psymbol=ClassName (filter), -Pmodule=name. " +
            "Use when: You need to understand method-level interactions and call chains. " +
            "Chain: More detailed than opsx-arch dependency graph."
    }

    @TaskAction
    fun analyze() {
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        val rootDir = project.rootProject.projectDir

        val projects = SourceDiscovery.resolveProjects(project, module.orNull)
        val srcDirs = SourceDiscovery.discoverSourceDirs(projects)
        val sourceFiles = SourceDiscovery.collectSourceFiles(srcDirs)

        if (sourceFiles.isEmpty()) {
            out.writeText("# Call Graph\n\n> No source files found.\n")
            logger.lifecycle("OpenSpec: No sources found.")
            return
        }

        logger.lifecycle("OpenSpec: Building call graph from ${sourceFiles.size} files...")
        val index = SymbolIndex.build(sourceFiles)
        val allCalls = index.callGraph()

        val calls = if (symbol.isPresent) {
            val filter = symbol.get()
            allCalls.filter { call ->
                call.caller.qualifiedName.contains(filter) || call.target.qualifiedName.contains(filter)
            }
        } else allCalls

        val sb = StringBuilder()
        sb.appendLine("# Call Graph")
        sb.appendLine()
        sb.appendLine("**${calls.size} method calls** across **${sourceFiles.size} files**")
        sb.appendLine()

        if (calls.isEmpty()) {
            sb.appendLine("> No method calls detected.")
        } else {
            // Mermaid diagram
            sb.appendLine("## Diagram")
            sb.appendLine()
            sb.appendLine("```mermaid")
            sb.appendLine("flowchart TD")

            val nodes = mutableSetOf<String>()
            for (call in calls) {
                val callerId = sanitizeMermaidId(call.caller.qualifiedName)
                val targetId = sanitizeMermaidId(call.target.qualifiedName)
                val callerLabel = call.caller.name
                val targetLabel = call.target.name

                if (callerId !in nodes) {
                    sb.appendLine("    $callerId[\"$callerLabel\"]")
                    nodes.add(callerId)
                }
                if (targetId !in nodes) {
                    sb.appendLine("    $targetId[\"$targetLabel\"]")
                    nodes.add(targetId)
                }
                sb.appendLine("    $callerId --> $targetId")
            }
            sb.appendLine("```")
            sb.appendLine()

            // Sequence diagram grouped by entry classes
            val callerClasses = calls.map { it.caller.qualifiedName.substringBeforeLast('.') }.distinct()
            if (callerClasses.size <= 10) {
                sb.appendLine("## Sequence")
                sb.appendLine()
                sb.appendLine("```mermaid")
                sb.appendLine("sequenceDiagram")

                val participants = mutableSetOf<String>()
                for (call in calls) {
                    val callerClass = call.caller.qualifiedName.substringBeforeLast('.')
                    val targetClass = call.target.qualifiedName.substringBeforeLast('.')
                    if (callerClass !in participants) {
                        val label = callerClass.substringAfterLast('.')
                        sb.appendLine("    participant $label")
                        participants.add(callerClass)
                    }
                    if (targetClass !in participants) {
                        val label = targetClass.substringAfterLast('.')
                        sb.appendLine("    participant $label")
                        participants.add(targetClass)
                    }
                }

                for (call in calls) {
                    val callerClass = call.caller.qualifiedName.substringBeforeLast('.').substringAfterLast('.')
                    val targetClass = call.target.qualifiedName.substringBeforeLast('.').substringAfterLast('.')
                    val methodName = call.target.name.substringAfterLast('.')
                    if (callerClass != targetClass) {
                        sb.appendLine("    $callerClass->>$targetClass: $methodName()")
                    }
                }
                sb.appendLine("```")
                sb.appendLine()
            }

            // Call table
            sb.appendLine("## Call Table")
            sb.appendLine()
            sb.appendLine("| Caller | Calls | Location |")
            sb.appendLine("|--------|-------|----------|")
            for (call in calls.sortedBy { it.caller.qualifiedName }) {
                val relPath = call.file.relativeTo(rootDir).path
                sb.appendLine("| `${call.caller.name}` | `${call.target.name}` | `$relPath:${call.line}` |")
            }
            sb.appendLine()
        }

        out.writeText(sb.toString())
        logger.lifecycle("OpenSpec: Generated call graph at ${out.relativeTo(rootDir)}")
    }

    private fun sanitizeMermaidId(name: String): String =
        name.replace('.', '_').replace('-', '_')
}
