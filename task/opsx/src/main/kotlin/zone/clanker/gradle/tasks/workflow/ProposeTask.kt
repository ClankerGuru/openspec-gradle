package zone.clanker.gradle.tasks.workflow

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import zone.clanker.gradle.core.TaskCodeGenerator
import java.io.File
import java.time.LocalDate

@org.gradle.api.tasks.UntrackedTask(because = "Creates user content in project directory")
abstract class ProposeTask : DefaultTask() {

    private var changeName: String = ""

    @Option(option = "name", description = "Change name (kebab-case)")
    fun setChangeName(name: String) {
        this.changeName = name
    }

    init {
        group = "opsx"
        description = "[tool] Create a new spec-driven change proposal. " +
            "Output: opsx/changes/<name>/ with proposal.md, design.md, tasks.md. " +
            "Options: --name=<kebab-case-name>. " +
            "Use when: Starting a new feature, fix, or refactor. " +
            "Chain: Fill in artifacts → opsx-apply --name=<name> to implement."
    }

    @TaskAction
    fun propose() {
        val name = changeName.ifEmpty {
            throw org.gradle.api.GradleException(
                "Change name required. Use: ./gradlew opsx-propose --name=my-change"
            )
        }

        val changesDir = File(project.projectDir, "opsx/changes/$name")
        if (changesDir.exists()) {
            throw org.gradle.api.GradleException(
                "Change '$name' already exists at ${changesDir.relativeTo(project.projectDir)}"
            )
        }

        changesDir.mkdirs()

        // Create .opsx.yaml
        File(changesDir, ".opsx.yaml").writeText("""
            |name: $name
            |created: ${LocalDate.now()}
            |status: active
            |schema: spec-driven
        """.trimMargin() + "\n")

        // Create proposal.md template
        File(changesDir, "proposal.md").writeText("""
            |# Proposal: $name
            |
            |## Summary
            |<!-- What is this change about? -->
            |
            |## Motivation
            |<!-- Why is this change needed? -->
            |
            |## Scope
            |<!-- What's in scope and out of scope? -->
            |
        """.trimMargin())

        // Create design.md template
        File(changesDir, "design.md").writeText("""
            |# Design: $name
            |
            |## Approach
            |<!-- How will this be implemented? -->
            |
            |## Key Decisions
            |<!-- Important design decisions and rationale -->
            |
            |## Risks
            |<!-- What could go wrong? -->
            |
        """.trimMargin())

        // Create tasks.md template with task code prefix
        val prefix = TaskCodeGenerator.prefix(name)
        File(changesDir, "tasks.md").writeText("""
            |# Tasks: $name
            |
            |## Implementation Tasks
            |
            |- [ ] `$prefix-1` Task 1: Description
            |- [ ] `$prefix-2` Task 2: Description
            |- [ ] `$prefix-3` Task 3: Description
            |
        """.trimMargin())

        logger.lifecycle("OpenSpec: Created change '$name' at opsx/changes/$name/ (prefix: $prefix)")
        logger.lifecycle("  - proposal.md (what & why)")
        logger.lifecycle("  - design.md (how)")
        logger.lifecycle("  - tasks.md (implementation steps)")
        logger.lifecycle("")
        logger.lifecycle("Fill in the artifacts, then run: ./gradlew opsx-apply --name=$name")
    }
}
