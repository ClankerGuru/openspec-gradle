package zone.clanker.gradle.generators


/**
 * Registry of all embedded skill templates.
 * Templates use shell injection (`!` lines) to pre-load context for the agent.
 */
object TemplateRegistry {

    private fun loadResource(path: String): String =
        this::class.java.classLoader.getResourceAsStream(path)!!.bufferedReader().readText()

    fun getSkillTemplates(): List<SkillContent> = listOf(
        opsxSkill(),
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
        wrkxWorkflowSkill(),
        diagramSkill(),
    )

    /**
     * Returns skill templates for installed agent CLI wrappers.
     * Pass the list of agent identifiers that are configured in the project
     * (e.g., "claude", "copilot", "codex", "opencode").
     */
    fun getAgentSkillTemplates(installedAgents: List<String>): List<SkillContent> =
        installedAgents.mapNotNull { agent ->
            when (agent) {
                "claude" -> claudeTasksSkill()
                "copilot" -> copilotTasksSkill()
                "codex" -> codexTasksSkill()
                "opencode" -> opencodeTasksSkill()
                else -> null
            }
        }

    /**
     * Returns all skill templates: core skills + agent skills for installed agents.
     */
    fun getAllSkillTemplates(installedAgents: List<String> = emptyList()): List<SkillContent> =
        getSkillTemplates() + getAgentSkillTemplates(installedAgents)

    // ── OPSX (bootstrap) ────────────────────────────────

    private fun opsxSkill() = SkillContent(
        dirName = "opsx-dashboard",
        description = "Show running agents, pending questions, and exec dashboards per proposal. Also lists available OPSX tasks and included builds. Use at session start, when monitoring agents, or when switching context.",
        instructions = loadResource("templates/skills/opsx.md"),
        effort = "low",
        allowedTools = "Read, Bash(./gradlew *)"
    )

    // ── Propose ──────────────────────────────────────────

    private fun proposeSkill() = SkillContent(
        dirName = "opsx-propose",
        description = "Create a change proposal with design, specs, and tasks. Use when planning work, building a feature, or fixing a bug.",
        instructions = loadResource("templates/skills/propose.md"),
        effort = "high"
    )

    // ── Apply ────────────────────────────────────────────

    private fun applySkill() = SkillContent(
        dirName = "opsx-apply",
        description = "Implement tasks from a change proposal. Use when starting to code, continuing implementation, or working through tasks.",
        instructions = loadResource("templates/skills/apply.md"),
        allowedTools = "Read, Edit, Write, Grep, Glob, Bash, Agent"
    )

    // ── Archive ──────────────────────────────────────────

    private fun archiveSkill() = SkillContent(
        dirName = "opsx-archive",
        description = "Archive a completed change. Use when implementation is done and the user wants to finalize.",
        instructions = loadResource("templates/skills/archive.md"),
        effort = "low",
        allowedTools = "Read, Bash(./gradlew *), Bash(mkdir *), Bash(mv *)"
    )

    // ── Explore ──────────────────────────────────────────

    private fun exploreSkill() = SkillContent(
        dirName = "opsx-explore",
        description = "Enter explore mode -- a thinking partner. Use when investigating, brainstorming, asking 'how does X work?', or clarifying requirements.",
        instructions = loadResource("templates/skills/explore.md"),
        context = "fork",
        agent = "Explore",
        effort = "high"
    )

    // ── New ──────────────────────────────────────────────

    private fun newSkill() = SkillContent(
        dirName = "opsx-new",
        description = "Start a new change with scaffolded directory. Use when beginning to track new work or creating a proposal from scratch.",
        instructions = loadResource("templates/skills/new.md"),
        disableModelInvocation = true,
        effort = "low",
        allowedTools = "Read, Bash(./gradlew *), Bash(ls *)"
    )

    // ── Sync ─────────────────────────────────────────────

    private fun syncSkill() = SkillContent(
        dirName = "opsx-sync",
        description = "Regenerate all OPSX agent files (skills, instructions, task commands). Use after config changes or plugin upgrades.",
        instructions = loadResource("templates/skills/sync.md"),
        disableModelInvocation = true,
        effort = "low",
        allowedTools = "Bash(./gradlew *)"
    )

    // ── Verify ───────────────────────────────────────────

    private fun verifySkill() = SkillContent(
        dirName = "srcx-verify",
        description = "Check architecture rules and constraints. Use when the user says 'verify', 'check the build', or wants to validate structure.",
        instructions = loadResource("templates/skills/verify.md"),
        effort = "low",
        paths = "*.kt,*.java",
        allowedTools = "Read, Bash(./gradlew srcx-verify*)"
    )

    // ── Find ─────────────────────────────────────────────

    private fun findSkill() = SkillContent(
        dirName = "srcx-find",
        description = "Find a symbol (class, function, property) by name. Use when searching for code, locating a definition, or asking 'where is X?'",
        argumentHint = "[symbol-name]",
        instructions = loadResource("templates/skills/find.md"),
        effort = "low",
        allowedTools = "Read, Grep, Glob, Bash(./gradlew srcx-find*)"
    )

    // ── Calls ────────────────────────────────────────────

    private fun callsSkill() = SkillContent(
        dirName = "srcx-calls",
        description = "Show the call graph for a symbol -- what calls it and what it calls. Use when asking 'what uses this?' or tracing execution flow.",
        argumentHint = "[symbol-name]",
        instructions = loadResource("templates/skills/calls.md"),
        effort = "low",
        allowedTools = "Read, Grep, Glob, Bash(./gradlew srcx-calls*)"
    )

    // ── Rename ───────────────────────────────────────────

    private fun renameSkill() = SkillContent(
        dirName = "srcx-rename",
        description = "Rename a symbol across the codebase safely. Use when the user says 'rename X to Y' or wants to refactor a name.",
        argumentHint = "[old-name new-name]",
        instructions = loadResource("templates/skills/rename.md"),
        effort = "medium",
        allowedTools = "Read, Bash(./gradlew srcx-rename*)"
    )

    // ── Status ───────────────────────────────────────────

    private fun statusSkill() = SkillContent(
        dirName = "opsx-status",
        description = "Show status of all open changes and proposals. Use when asking 'what are we working on?', 'what's in progress?', or starting a session.",
        instructions = loadResource("templates/skills/status.md"),
        effort = "low",
        allowedTools = "Read, Bash(./gradlew opsx-status*)"
    )

    // ── Move ─────────────────────────────────────────────

    private fun moveSkill() = SkillContent(
        dirName = "srcx-move",
        description = "Move a class or file to a different package, updating imports. Use when the user says 'move X to Y' or wants to reorganize code.",
        argumentHint = "[symbol-name target-package]",
        instructions = loadResource("templates/skills/move.md"),
        effort = "medium",
        allowedTools = "Read, Bash(./gradlew srcx-move*)"
    )

    // ── Usages ───────────────────────────────────────────

    private fun usagesSkill() = SkillContent(
        dirName = "srcx-usages",
        description = "Find all usages of a symbol with file:line locations. Use when asking 'where is X referenced?' or before renaming/removing.",
        argumentHint = "[symbol-name]",
        instructions = loadResource("templates/skills/usages.md"),
        effort = "low",
        allowedTools = "Read, Grep, Glob, Bash(./gradlew srcx-usages*)"
    )

    // ── Extract ──────────────────────────────────────────

    private fun extractSkill() = SkillContent(
        dirName = "srcx-extract",
        description = "Extract code into a new function or class. Use when the user says 'extract this' or wants to refactor by pulling out code.",
        instructions = loadResource("templates/skills/extract.md"),
        effort = "medium",
        allowedTools = "Read, Edit, Bash(./gradlew srcx-extract*)"
    )

    // ── Inline ───────────────────────────────────────────

    private fun inlineSkill() = SkillContent(
        dirName = "srcx-inline",
        description = "Inline a function -- replace call sites with the body. Use when the user says 'inline this' or wants to undo an extraction.",
        instructions = loadResource("templates/skills/inline.md"),
        effort = "medium",
        allowedTools = "Read, Edit, Grep, Bash(./gradlew *)"
    )

    // ── Deps ─────────────────────────────────────────────

    private fun depsSkill() = SkillContent(
        dirName = "srcx-deps",
        description = "Query project dependencies -- versions, transitive deps, module relationships. Use when asking about libraries, versions, or 'what depends on X?'",
        instructions = loadResource("templates/skills/deps.md"),
        effort = "low",
        allowedTools = "Read, Bash(./gradlew srcx-deps*)"
    )

    // ── Remove ───────────────────────────────────────────

    private fun removeSkill() = SkillContent(
        dirName = "srcx-remove",
        description = "Remove a symbol or code block, cleaning up imports. Use when the user says 'delete this', 'remove X', or wants to clean dead code.",
        argumentHint = "[symbol-name]",
        instructions = loadResource("templates/skills/remove.md"),
        effort = "medium",
        allowedTools = "Read, Bash(./gradlew srcx-remove*)"
    )

    // ── Workspace Workflow ─────────────────────────────────

    private fun wrkxWorkflowSkill() = SkillContent(
        dirName = "wrkx-workflow",
        description = "Workspace management -- pull repos, switch branches, check status, and work with included builds. Use when managing multi-repo workspaces.",
        instructions = loadResource("templates/skills/wrkx-workflow.md"),
        effort = "low",
        allowedTools = "Read, Bash(./gradlew wrkx-*)"
    )

    // ── Diagram ────────────────────────────────────────────

    private fun diagramSkill() = SkillContent(
        dirName = "opsx-diagram",
        description = "Generate Mermaid diagrams for architecture, flows, sequences, or state machines. Use when visualizing or documenting.",
        instructions = loadResource("templates/skills/diagram.md")
    )

    // ── Agent CLI Skills ───────────────────────────────────

    private fun claudeTasksSkill() = SkillContent(
        dirName = "clkx-claude",
        description = "Run Claude Code headlessly via Gradle -- claude-run, claude-resume, claude-auth. Use when dispatching work to Claude or managing Claude sessions.",
        instructions = loadResource("templates/skills/claude-tasks.md"),
        allowedTools = "Bash(./gradlew claude-*)"
    )

    private fun copilotTasksSkill() = SkillContent(
        dirName = "clkx-copilot",
        description = "Run GitHub Copilot headlessly via Gradle -- copilot-run, copilot-resume. Use when dispatching work to Copilot or managing Copilot sessions.",
        instructions = loadResource("templates/skills/copilot-tasks.md"),
        allowedTools = "Bash(./gradlew copilot-*)"
    )

    private fun codexTasksSkill() = SkillContent(
        dirName = "clkx-codex",
        description = "Run OpenAI Codex headlessly via Gradle -- codex-exec, codex-review. Use when dispatching work to Codex or running code reviews.",
        instructions = loadResource("templates/skills/codex-tasks.md"),
        allowedTools = "Bash(./gradlew codex-*)"
    )

    private fun opencodeTasksSkill() = SkillContent(
        dirName = "clkx-opencode",
        description = "Run opencode headlessly via Gradle -- opencode-run, opencode-serve. Use when dispatching work to opencode or starting a headless server.",
        instructions = loadResource("templates/skills/opencode-tasks.md"),
        allowedTools = "Bash(./gradlew opencode-*)"
    )
}
