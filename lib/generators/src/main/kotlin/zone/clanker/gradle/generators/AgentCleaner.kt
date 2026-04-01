package zone.clanker.gradle.generators

import java.io.File

/**
 * Cleans all OPSX-generated files for a given agent adapter.
 * Used by both SyncTask (to clean deselected agents) and CleanTask (to clean all agents).
 */
object AgentCleaner {

    /**
     * Remove all OPSX-generated files for the given adapter:
     * - opsx-prefixed skill directories
     * - OPSX marker section from instructions file
     * - Empty parent directories up to the project root
     *
     * @return count of removed items
     */
    fun cleanAgent(projectDir: File, adapter: ToolAdapter): Int {
        var count = 0

        // 1. Clean instructions file (handles append-mode marker stripping)
        if (InstructionsGenerator.clean(projectDir, adapter)) count++

        // 2. Clean all opsx-prefixed skill directories
        val skillsDir = resolveSkillsDir(projectDir, adapter)
        if (skillsDir != null && skillsDir.exists() && skillsDir.isDirectory) {
            skillsDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("opsx-") }?.forEach {
                it.deleteRecursively()
                count++
            }
        }

        // 3. Prune empty parent directories
        if (skillsDir != null) {
            pruneEmptyParents(skillsDir, projectDir)
        }
        val instrFile = File(projectDir, adapter.getInstructionsFilePath())
        pruneEmptyParents(instrFile.parentFile, projectDir)

        return count
    }

    /**
     * Resolve the skills root directory for an adapter.
     * Skills are at <skillsDir>/<dirName>/SKILL.md — the probe path gives us the structure.
     */
    fun resolveSkillsDir(projectDir: File, adapter: ToolAdapter): File? {
        val probePath = adapter.getSkillFilePath("__probe__")
        val probeFile = File(projectDir, probePath)
        // Go up two levels: __probe__/SKILL.md → __probe__ → skills root
        return probeFile.parentFile?.parentFile
    }

    /**
     * Walk up from [dir] deleting empty directories until [stopAt] or a non-empty directory is reached.
     * Never deletes [stopAt] itself (the project root).
     */
    fun pruneEmptyParents(dir: File?, stopAt: File) {
        var current = dir
        while (current != null && current != stopAt && current.startsWith(stopAt)) {
            if (!current.exists()) {
                current = current.parentFile
                continue
            }
            val contents = current.listFiles()
            if (contents == null || contents.isEmpty()) {
                current.delete()
                current = current.parentFile
            } else {
                break
            }
        }
    }
}
