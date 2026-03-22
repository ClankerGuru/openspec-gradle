package zone.clanker.gradle.core

import java.io.File

/**
 * Scans opsx/changes/ directories for proposals and parses their tasks.
 */
object ProposalScanner {

    /**
     * Scan the opsx/changes/ directory for all active proposals.
     *
     * @param projectDir The project root directory
     * @return List of parsed proposals with their tasks
     */
    fun scan(projectDir: File): List<Proposal> {
        val changesDir = File(projectDir, "opsx/changes")
        if (!changesDir.exists() || !changesDir.isDirectory) return emptyList()

        return changesDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "archive" }
            ?.mapNotNull { dir -> parseProposal(dir) }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Parse a single proposal directory.
     */
    fun parseProposal(dir: File): Proposal? {
        val tasksFile = File(dir, "tasks.md")
        if (!tasksFile.exists()) return null

        val name = dir.name
        val prefix = TaskCodeGenerator.prefix(name)
        val tasks = TaskParser.parse(tasksFile)

        return Proposal(
            name = name,
            prefix = prefix,
            tasks = tasks
        )
    }

    /**
     * Find a specific proposal by name.
     */
    fun findProposal(projectDir: File, name: String): Proposal? {
        val dir = File(projectDir, "opsx/changes/$name")
        if (!dir.exists()) return null
        return parseProposal(dir)
    }

    /**
     * Find which proposal contains a given task code.
     */
    fun findProposalByTaskCode(projectDir: File, code: String): Pair<Proposal, TaskItem>? {
        val proposals = scan(projectDir)
        for (proposal in proposals) {
            val task = proposal.findByCode(code)
            if (task != null) return proposal to task
        }
        return null
    }
}
