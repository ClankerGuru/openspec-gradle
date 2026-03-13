package zone.clanker.gradle.templates

import zone.clanker.gradle.generators.CommandContent
import zone.clanker.gradle.generators.SkillContent

/**
 * Registry of all embedded prompt/skill templates.
 * Templates reference filesystem operations instead of openspec CLI.
 */
object TemplateRegistry {

    private fun loadResource(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path)!!.bufferedReader().readText()

    fun getCommandTemplates(): List<CommandContent> = listOf(
        proposeCommand(),
        applyCommand(),
        archiveCommand(),
        exploreCommand(),
        newCommand(),
        syncCommand(),
        verifyCommand()
    )

    fun getSkillTemplates(): List<SkillContent> = listOf(
        proposeSkill(),
        applySkill(),
        archiveSkill(),
        exploreSkill(),
        newSkill(),
        syncSkill(),
        verifySkill()
    )

    // ── Propose ──────────────────────────────────────────

    private fun proposeCommand() = CommandContent(
        id = "propose",
        name = "OPSX: Propose",
        description = "Propose a new change - create it and generate all artifacts in one step",
        category = "Workflow",
        tags = listOf("workflow", "artifacts", "experimental"),
        body = loadResource("templates/commands/propose.md")
    )

    private fun proposeSkill() = SkillContent(
        dirName = "openspec-propose",
        description = "Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.",
        instructions = loadResource("templates/skills/propose.md")
    )

    // ── Apply ────────────────────────────────────────────

    private fun applyCommand() = CommandContent(
        id = "apply",
        name = "OPSX: Apply",
        description = "Implement tasks from an OpenSpec change (Experimental)",
        category = "Workflow",
        tags = listOf("workflow", "implementation"),
        body = loadResource("templates/commands/apply.md")
    )

    private fun applySkill() = SkillContent(
        dirName = "openspec-apply-change",
        description = "Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks.",
        instructions = loadResource("templates/skills/apply.md")
    )

    // ── Archive ──────────────────────────────────────────

    private fun archiveCommand() = CommandContent(
        id = "archive",
        name = "OPSX: Archive",
        description = "Archive a completed change in the experimental workflow",
        category = "Workflow",
        tags = listOf("workflow", "archive"),
        body = loadResource("templates/commands/archive.md")
    )

    private fun archiveSkill() = SkillContent(
        dirName = "openspec-archive-change",
        description = "Archive a completed change. Use when the user wants to finalize and archive a change after implementation is complete.",
        instructions = loadResource("templates/skills/archive.md")
    )

    // ── Explore ──────────────────────────────────────────

    private fun exploreCommand() = CommandContent(
        id = "explore",
        name = "OPSX: Explore",
        description = "Enter explore mode - think through ideas, investigate problems, clarify requirements",
        category = "Workflow",
        tags = listOf("workflow", "explore", "thinking"),
        body = loadResource("templates/commands/explore.md")
    )

    private fun exploreSkill() = SkillContent(
        dirName = "openspec-explore",
        description = "Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements.",
        instructions = loadResource("templates/skills/explore.md")
    )

    // ── New (expanded profile) ───────────────────────────

    private fun newCommand() = CommandContent(
        id = "new",
        name = "OPSX: New",
        description = "Start a new change with scaffolded directory structure",
        category = "Workflow",
        tags = listOf("workflow", "artifacts"),
        body = loadResource("templates/commands/new.md")
    )

    private fun newSkill() = SkillContent(
        dirName = "openspec-new-change",
        description = "Start a new OpenSpec change with scaffolded directory structure.",
        instructions = loadResource("templates/skills/new.md")
    )

    // ── Sync (expanded profile) ──────────────────────────

    private fun syncCommand() = CommandContent(
        id = "sync",
        name = "OPSX: Sync",
        description = "Sync delta specs from a change to main specs",
        category = "Workflow",
        tags = listOf("workflow", "specs"),
        body = loadResource("templates/commands/sync.md")
    )

    private fun syncSkill() = SkillContent(
        dirName = "openspec-sync-specs",
        description = "Sync delta specs from a change to main specs.",
        instructions = loadResource("templates/skills/sync.md")
    )

    // ── Verify (expanded profile) ────────────────────────

    private fun verifyCommand() = CommandContent(
        id = "verify",
        name = "OPSX: Verify",
        description = "Verify implementation matches specs and tasks",
        category = "Workflow",
        tags = listOf("workflow", "verification"),
        body = loadResource("templates/commands/verify.md")
    )

    private fun verifySkill() = SkillContent(
        dirName = "openspec-verify-change",
        description = "Verify that implementation matches the specs and tasks for a change.",
        instructions = loadResource("templates/skills/verify.md")
    )
}
