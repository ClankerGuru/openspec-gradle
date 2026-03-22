package zone.clanker.gradle.generators


/**
 * Registry of all embedded prompt/skill templates.
 * Templates reference filesystem operations instead of CLI commands.
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
        verifyCommand(),
        findCommand(),
        callsCommand(),
        renameCommand(),
        statusCommand(),
        moveCommand(),
        usagesCommand(),
        extractCommand(),
        inlineCommand(),
        depsCommand(),
        removeCommand(),
    )

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

    private fun proposeCommand() = CommandContent(
        id = "propose",
        name = "OPSX: Propose",
        description = "Propose a new change - create it and generate all artifacts in one step",
        category = "Workflow",
        tags = listOf("workflow", "artifacts", "experimental"),
        body = loadResource("templates/commands/propose.md")
    )

    private fun proposeSkill() = SkillContent(
        dirName = "opsx-propose",
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
        dirName = "opsx-apply",
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
        dirName = "opsx-archive",
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
        dirName = "opsx-explore",
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
        dirName = "opsx-new",
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
        dirName = "opsx-sync",
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
        dirName = "opsx-verify",
        description = "Verify that implementation matches the specs and tasks for a change.",
        instructions = loadResource("templates/skills/verify.md")
    )

    // ── Find ─────────────────────────────────────────────

    private fun findCommand() = CommandContent(
        id = "find",
        name = "OPSX: Find",
        description = "Find a symbol by name in the project",
        category = "Code Intelligence",
        tags = listOf("symbols", "search"),
        body = loadResource("templates/commands/find.md")
    )

    private fun findSkill() = SkillContent(
        dirName = "opsx-find",
        description = "Find a symbol by name in the project. Use when the user wants to locate a class, function, or variable.",
        instructions = loadResource("templates/skills/find.md")
    )

    // ── Calls ────────────────────────────────────────────

    private fun callsCommand() = CommandContent(
        id = "calls",
        name = "OPSX: Calls",
        description = "Show call graph for a symbol",
        category = "Code Intelligence",
        tags = listOf("symbols", "call-graph"),
        body = loadResource("templates/commands/calls.md")
    )

    private fun callsSkill() = SkillContent(
        dirName = "opsx-calls",
        description = "Show the call graph for a symbol. Use when the user wants to understand what calls a function or what a function calls.",
        instructions = loadResource("templates/skills/calls.md")
    )

    // ── Rename ───────────────────────────────────────────

    private fun renameCommand() = CommandContent(
        id = "rename",
        name = "OPSX: Rename",
        description = "Preview or execute a rename refactoring",
        category = "Code Intelligence",
        tags = listOf("refactoring", "rename"),
        body = loadResource("templates/commands/rename.md")
    )

    private fun renameSkill() = SkillContent(
        dirName = "opsx-rename",
        description = "Preview or execute a rename refactoring. Use when the user wants to rename a symbol across the codebase.",
        instructions = loadResource("templates/skills/rename.md")
    )

    // ── Status ───────────────────────────────────────────

    private fun statusCommand() = CommandContent(
        id = "status",
        name = "OPSX: Status",
        description = "Show status of all open changes/proposals",
        category = "Workflow",
        tags = listOf("workflow", "status"),
        body = loadResource("templates/commands/status.md")
    )

    private fun statusSkill() = SkillContent(
        dirName = "opsx-status",
        description = "Show status of all open changes and proposals. Use when the user wants to see what changes are in progress.",
        instructions = loadResource("templates/skills/status.md")
    )

    // ── Move ─────────────────────────────────────────────

    private fun moveCommand() = CommandContent(
        id = "move",
        name = "OPSX: Move",
        description = "Move a class/file to a different package",
        category = "Code Intelligence",
        tags = listOf("refactoring", "move"),
        body = loadResource("templates/commands/move.md")
    )

    private fun moveSkill() = SkillContent(
        dirName = "opsx-move",
        description = "Move a class or file to a different package safely, updating all imports. Use when the user wants to relocate code.",
        instructions = loadResource("templates/skills/move.md")
    )

    // ── Usages ───────────────────────────────────────────

    private fun usagesCommand() = CommandContent(
        id = "usages",
        name = "OPSX: Usages",
        description = "Show all usages of a symbol with file:line locations",
        category = "Code Intelligence",
        tags = listOf("symbols", "usages"),
        body = loadResource("templates/commands/usages.md")
    )

    private fun usagesSkill() = SkillContent(
        dirName = "opsx-usages",
        description = "Find all usages of a symbol with exact file:line locations. Use when the user wants to see where a symbol is referenced.",
        instructions = loadResource("templates/skills/usages.md")
    )

    // ── Extract ──────────────────────────────────────────

    private fun extractCommand() = CommandContent(
        id = "extract",
        name = "OPSX: Extract",
        description = "Extract a block of code into a new function",
        category = "Code Intelligence",
        tags = listOf("refactoring", "extract"),
        body = loadResource("templates/commands/extract.md")
    )

    private fun extractSkill() = SkillContent(
        dirName = "opsx-extract",
        description = "Extract a block of code into a new function or class. Use when the user wants to refactor by extracting code.",
        instructions = loadResource("templates/skills/extract.md")
    )

    // ── Inline ───────────────────────────────────────────

    private fun inlineCommand() = CommandContent(
        id = "inline",
        name = "OPSX: Inline",
        description = "Inline a function — replace call sites with the body",
        category = "Code Intelligence",
        tags = listOf("refactoring", "inline"),
        body = loadResource("templates/commands/inline.md")
    )

    private fun inlineSkill() = SkillContent(
        dirName = "opsx-inline",
        description = "Inline a function or class — replace call sites with the implementation body. The reverse of extract.",
        instructions = loadResource("templates/skills/inline.md")
    )

    // ── Deps (interactive) ───────────────────────────────

    private fun depsCommand() = CommandContent(
        id = "deps",
        name = "OPSX: Deps",
        description = "Query resolved project dependencies",
        category = "Code Intelligence",
        tags = listOf("dependencies", "query"),
        body = loadResource("templates/commands/deps.md")
    )

    private fun depsSkill() = SkillContent(
        dirName = "opsx-deps",
        description = "Query project dependencies — resolved versions, transitive deps, and module relationships.",
        instructions = loadResource("templates/skills/deps.md")
    )

    private fun removeCommand() = CommandContent(
        id = "remove",
        name = "OPSX: Remove",
        description = "Remove a symbol or code lines from the codebase",
        category = "Code Intelligence",
        tags = listOf("refactoring", "remove", "delete"),
        body = loadResource("templates/commands/remove.md")
    )

    private fun removeSkill() = SkillContent(
        dirName = "opsx-remove",
        description = "Remove a symbol (class, function, property) or line range from the codebase, cleaning up imports.",
        instructions = loadResource("templates/skills/remove.md")
    )
}
