package zone.clanker.gradle.generators

import java.io.File

/**
 * Generates SKILL.md files into the build directory.
 */
object SkillGenerator {

    fun generate(
        buildDir: File,
        tools: List<String>
    ): List<GeneratedFile> {
        val skills = TemplateRegistry.getSkillTemplates()
        val generated = mutableListOf<GeneratedFile>()

        for (toolId in tools) {
            val adapter = ToolAdapterRegistry.get(toolId) ?: continue
            for (skill in skills) {
                val relativePath = adapter.getSkillFilePath(skill.dirName)
                val file = File(buildDir, relativePath)
                file.parentFile.mkdirs()
                file.writeText(adapter.formatSkillFile(skill))
                generated.add(GeneratedFile(relativePath, file))
            }
        }
        return generated
    }
}

data class GeneratedFile(val relativePath: String, val file: File)
