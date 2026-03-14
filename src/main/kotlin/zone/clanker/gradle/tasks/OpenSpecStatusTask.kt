package zone.clanker.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import zone.clanker.gradle.tracking.ProposalScanner
import zone.clanker.gradle.tracking.Proposal
import zone.clanker.gradle.tracking.TaskStatus

/**
 * Displays a dashboard of all proposals and their task progress.
 *
 * [tool] Proposal dashboard.
 * Output: Console (ANSI-formatted)
 * Options: --proposal=<name> to filter to a single proposal
 * Use when: You need to check proposal progress, find active tasks, see what's done.
 * Chain: Read output → opsx-<code> --set=done to work on a task.
 */
@UntrackedTask(because = "Reads and displays proposal status from filesystem")
abstract class OpenSpecStatusTask : DefaultTask() {

    @get:Input
    @get:Optional
    @get:Option(option = "proposal", description = "Filter to a specific proposal name")
    abstract val proposal: Property<String>

    init {
        group = "openspec"
        description = "[tool] Proposal dashboard. " +
            "Options: --proposal=<name>. " +
            "Use when: Check proposal progress, find active tasks. " +
            "Chain: opsx-<code> --set=done to work on a task."
    }

    @TaskAction
    fun execute() {
        val proposals = if (proposal.isPresent) {
            val p = ProposalScanner.findProposal(project.projectDir, proposal.get())
            if (p == null) {
                logger.lifecycle("No proposal found: ${proposal.get()}")
                return
            }
            listOf(p)
        } else {
            ProposalScanner.scan(project.projectDir)
        }

        if (proposals.isEmpty()) {
            logger.lifecycle("No proposals found in openspec/changes/")
            return
        }

        printDashboard(proposals)
    }

    private fun printDashboard(proposals: List<Proposal>) {
        val green = "\u001B[32m"
        val yellow = "\u001B[33m"
        val dim = "\u001B[2m"
        val bold = "\u001B[1m"
        val reset = "\u001B[0m"

        val totalTasks = proposals.sumOf { it.totalCount }
        val doneTasks = proposals.sumOf { it.doneCount }
        val activeCount = proposals.count { it.progressPercent < 100 }
        val completedCount = proposals.count { it.progressPercent == 100 }

        logger.lifecycle("")
        logger.lifecycle("${bold}OpenSpec Dashboard${reset}")
        logger.lifecycle("")
        logger.lifecycle("${bold}Summary:${reset}")
        logger.lifecycle("  ${green}*${reset} Proposals: ${proposals.size}")
        logger.lifecycle("  ${green}*${reset} Active Changes: $activeCount")
        logger.lifecycle("  ${green}*${reset} Completed Changes: $completedCount")
        logger.lifecycle("  ${green}*${reset} Task Progress: ${bold}$doneTasks/$totalTasks${reset} (${progressPercent(doneTasks, totalTasks)}% complete)")
        logger.lifecycle("")

        // Active changes with progress bars
        val active = proposals.filter { it.progressPercent < 100 }
        if (active.isNotEmpty()) {
            logger.lifecycle("${bold}Active Changes${reset}")
            val maxNameLen = active.maxOf { it.name.length }.coerceAtLeast(20)
            for (p in active) {
                val bar = progressBar(p.progressPercent, 20)
                val name = p.name.padEnd(maxNameLen)
                val pct = "${p.progressPercent}%".padStart(4)
                logger.lifecycle("  ${green}*${reset} $name $bar $pct")
            }
            logger.lifecycle("")
        }

        // Completed changes
        val completed = proposals.filter { it.progressPercent == 100 }
        if (completed.isNotEmpty()) {
            logger.lifecycle("${bold}Completed Changes${reset}")
            for (p in completed) {
                logger.lifecycle("  ${green}v${reset} ${p.name}")
            }
            logger.lifecycle("")
        }

        // Detailed task list per proposal
        for (p in proposals) {
            logger.lifecycle("${bold}${p.name}${reset} (${p.prefix}) - ${p.doneCount}/${p.totalCount} tasks done")
            printTaskTree(p.tasks, "  ", green, yellow, dim, reset)
            logger.lifecycle("")
        }
    }

    private fun printTaskTree(
        tasks: List<zone.clanker.gradle.tracking.TaskItem>,
        indent: String,
        green: String,
        yellow: String,
        dim: String,
        reset: String
    ) {
        for (task in tasks) {
            val icon = when (task.status) {
                TaskStatus.DONE -> "${green}[x]${reset}"
                TaskStatus.IN_PROGRESS -> "${yellow}[~]${reset}"
                TaskStatus.TODO -> "[ ]"
            }
            val code = if (task.code.isNotBlank()) "${dim}${task.code}${reset}".padEnd(12 + dim.length + reset.length) else "".padEnd(12)
            logger.lifecycle("$indent$icon $code ${task.description}")
            if (task.children.isNotEmpty()) {
                printTaskTree(task.children, "$indent  ", green, yellow, dim, reset)
            }
        }
    }

    private fun progressBar(percent: Int, width: Int): String {
        val filled = (percent * width) / 100
        val empty = width - filled
        val green = "\u001B[32m"
        val dim = "\u001B[2m"
        val reset = "\u001B[0m"
        return "[${green}${"#".repeat(filled)}${reset}${dim}${"-".repeat(empty)}${reset}]"
    }

    private fun progressPercent(done: Int, total: Int): Int =
        if (total == 0) 0 else (done * 100) / total
}
