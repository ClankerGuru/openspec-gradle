package zone.clanker.gradle.generators


/**
 * Registry of all embedded skill templates.
 * Templates reference filesystem operations instead of CLI commands.
 */
object TemplateRegistry {

    private fun loadResource(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path)!!.bufferedReader().readText()

    fun getSkillTemplates(): List<SkillContent> = listOf(
        proposeSkill(),
        applySkill(),
        archiveSkill(),
        exploreSkill(),
        newSkill(),
        syncSkill(),
        verifySkill(),
        findSkill(),
        callsSkill(),
        renameSkill(),
        statusSkill(),
        moveSkill(),
        usagesSkill(),
        extractSkill(),
        inlineSkill(),
        depsSkill(),
        removeSkill(),
    )

    // ── Propose ──────────────────────────────────────────

    private fun proposeSkill() = SkillContent(
        dirName = "opsx-propose",
        description = "Propose a new change with all artifacts generated in one step. Use when the user wants to quickly describe what they want to build and get a complete proposal with design, specs, and tasks ready for implementation.",
        instructions = loadResource("templates/skills/propose.md")
    )

    // ── Apply ────────────────────────────────────────────

    private fun applySkill() = SkillContent(
        dirName = "opsx-apply",
        description = "Implement tasks from an OpenSpec change. Use when the user wants to start implementing, continue implementation, or work through tasks.",
        instructions = loadResource("templates/skills/apply.md")
    )

    // ── Archive ──────────────────────────────────────────

    private fun archiveSkill() = SkillContent(
        dirName = "opsx-archive",
        description = "Archive a completed change. Use when the user wants to finalize and archive a change after implementation is complete.",
        instructions = loadResource("templates/skills/archive.md")
    )

    // ── Explore ──────────────────────────────────────────

    private fun exploreSkill() = SkillContent(
        dirName = "opsx-explore",
        description = "Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements.",
        instructions = loadResource("templates/skills/explore.md")
    )

    // ── New (expanded profile) ───────────────────────────

    private fun newSkill() = SkillContent(
        dirName = "opsx-new",
        description = "Start a new OpenSpec change with scaffolded directory structure.",
        instructions = loadResource("templates/skills/new.md")
    )

    // ── Sync (expanded profile) ──────────────────────────

    private fun syncSkill() = SkillContent(
        dirName = "opsx-sync",
        description = "Sync delta specs from a change to main specs.",
        instructions = loadResource("templates/skills/sync.md")
    )

    // ── Verify (expanded profile) ────────────────────────

    private fun verifySkill() = SkillContent(
        dirName = "opsx-verify",
        description = "Verify that implementation matches the specs and tasks for a change.",
        instructions = loadResource("templates/skills/verify.md")
    )

    // ── Find ─────────────────────────────────────────────

    private fun findSkill() = SkillContent(
        dirName = "opsx-find",
        description = "Find a symbol by name in the project. Use when the user wants to locate a class, function, or variable.",
        instructions = loadResource("templates/skills/find.md")
    )

    // ── Calls ────────────────────────────────────────────

    private fun callsSkill() = SkillContent(
        dirName = "opsx-calls",
        description = "Show the call graph for a symbol. Use when the user wants to understand what calls a function or what a function calls.",
        instructions = loadResource("templates/skills/calls.md")
    )

    // ── Rename ───────────────────────────────────────────

    private fun renameSkill() = SkillContent(
        dirName = "opsx-rename",
        description = "Preview or execute a rename refactoring. Use when the user wants to rename a symbol across the codebase.",
        instructions = loadResource("templates/skills/rename.md")
    )

    // ── Status ───────────────────────────────────────────

    private fun statusSkill() = SkillContent(
        dirName = "opsx-status",
        description = "Show status of all open changes and proposals. Use when the user wants to see what changes are in progress.",
        instructions = loadResource("templates/skills/status.md")
    )

    // ── Move ─────────────────────────────────────────────

    private fun moveSkill() = SkillContent(
        dirName = "opsx-move",
        description = "Move a class or file to a different package safely, updating all imports. Use when the user wants to relocate code.",
        instructions = loadResource("templates/skills/move.md")
    )

    // ── Usages ───────────────────────────────────────────

    private fun usagesSkill() = SkillContent(
        dirName = "opsx-usages",
        description = "Find all usages of a symbol with exact file:line locations. Use when the user wants to see where a symbol is referenced.",
        instructions = loadResource("templates/skills/usages.md")
    )

    // ── Extract ──────────────────────────────────────────

    private fun extractSkill() = SkillContent(
        dirName = "opsx-extract",
        description = "Extract a block of code into a new function or class. Use when the user wants to refactor by extracting code.",
        instructions = loadResource("templates/skills/extract.md")
    )

    // ── Inline ───────────────────────────────────────────

    private fun inlineSkill() = SkillContent(
        dirName = "opsx-inline",
        description = "Inline a function or class — replace call sites with the implementation body. The reverse of extract.",
        instructions = loadResource("templates/skills/inline.md")
    )

    // ── Deps (interactive) ───────────────────────────────

    private fun depsSkill() = SkillContent(
        dirName = "opsx-deps",
        description = "Query project dependencies — resolved versions, transitive deps, and module relationships.",
        instructions = loadResource("templates/skills/deps.md")
    )

    // ── Remove ───────────────────────────────────────────

    private fun removeSkill() = SkillContent(
        dirName = "opsx-remove",
        description = "Remove a symbol (class, function, property) or line range from the codebase, cleaning up imports.",
        instructions = loadResource("templates/skills/remove.md")
    )
}
