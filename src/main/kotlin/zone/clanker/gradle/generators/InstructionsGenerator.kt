package zone.clanker.gradle.generators

import java.io.File

/**
 * Generates root agent instructions files (CLAUDE.md, copilot-instructions.md, etc.)
 * that teach agents about OPSX tasks.
 */
object InstructionsGenerator {

    private val template: String by lazy {
        this::class.java.classLoader.getResourceAsStream("templates/instructions.md")!!
            .bufferedReader().readText()
    }

    fun generate(
        buildDir: File,
        tools: List<String>
    ): List<GeneratedFile> {
        val generated = mutableListOf<GeneratedFile>()

        for (toolId in tools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            val relativePath = adapter.getInstructionsFilePath()
            val file = File(buildDir, relativePath)
            file.parentFile.mkdirs()
            file.writeText(template)
            generated.add(GeneratedFile(relativePath, file))
        }
        return generated
    }
}
