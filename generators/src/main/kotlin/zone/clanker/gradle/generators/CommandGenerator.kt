package zone.clanker.gradle.generators

import java.io.File

/**
 * Generates command/prompt files into the build directory.
 */
object CommandGenerator {

    fun generate(
        buildDir: File,
        tools: List<String>
    ): List<GeneratedFile> {
        val commands = TemplateRegistry.getCommandTemplates()
        val generated = mutableListOf<GeneratedFile>()

        for (toolId in tools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            for (cmd in commands) {
                val relativePath = adapter.getCommandFilePath(cmd.id)
                val file = File(buildDir, relativePath)
                file.parentFile.mkdirs()
                file.writeText(adapter.formatCommandFile(cmd))
                generated.add(GeneratedFile(relativePath, file))
            }
        }
        return generated
    }
}
