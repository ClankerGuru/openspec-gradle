package zone.clanker.gradle.tracking

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProposalScannerTest {

    @TempDir
    lateinit var tempDir: File

    private fun createProposal(name: String, tasksContent: String) {
        val dir = File(tempDir, "openspec/changes/$name")
        dir.mkdirs()
        File(dir, "tasks.md").writeText(tasksContent)
    }

    @Test
    fun `scan finds all proposals`() {
        createProposal("alpha", """
            - [ ] `a-1` First
            - [x] `a-2` Second
        """.trimIndent())

        createProposal("beta", """
            - [ ] `b-1` Only task
        """.trimIndent())

        val proposals = ProposalScanner.scan(tempDir)

        assertEquals(2, proposals.size)
        assertEquals("alpha", proposals[0].name)
        assertEquals("beta", proposals[1].name)
    }

    @Test
    fun `scan returns empty when no changes dir`() {
        val proposals = ProposalScanner.scan(tempDir)
        assertEquals(0, proposals.size)
    }

    @Test
    fun `scan skips archive directory`() {
        createProposal("active", "- [ ] `a-1` Task")
        File(tempDir, "openspec/changes/archive").mkdirs()
        File(tempDir, "openspec/changes/archive/old/tasks.md").apply {
            parentFile.mkdirs()
            writeText("- [x] `o-1` Old task")
        }

        val proposals = ProposalScanner.scan(tempDir)
        assertEquals(1, proposals.size)
        assertEquals("active", proposals[0].name)
    }

    @Test
    fun `scan skips dirs without tasks md`() {
        val dir = File(tempDir, "openspec/changes/no-tasks")
        dir.mkdirs()
        File(dir, "proposal.md").writeText("# No tasks here")

        val proposals = ProposalScanner.scan(tempDir)
        assertEquals(0, proposals.size)
    }

    @Test
    fun `findProposal returns specific proposal`() {
        createProposal("my-feature", "- [ ] `mf-1` Do the thing")

        val proposal = ProposalScanner.findProposal(tempDir, "my-feature")
        assertNotNull(proposal)
        assertEquals("my-feature", proposal!!.name)
        assertEquals("mf", proposal.prefix)
        assertEquals(1, proposal.tasks.size)
    }

    @Test
    fun `findProposal returns null for missing`() {
        val proposal = ProposalScanner.findProposal(tempDir, "nonexistent")
        assertNull(proposal)
    }

    @Test
    fun `findProposalByTaskCode finds correct proposal and task`() {
        createProposal("alpha", "- [ ] `a-1` Alpha task")
        createProposal("beta", """
            - [ ] `b-1` Beta parent
              - [ ] `b-1.1` Beta child
        """.trimIndent())

        val result = ProposalScanner.findProposalByTaskCode(tempDir, "b-1.1")
        assertNotNull(result)
        assertEquals("beta", result!!.first.name)
        assertEquals("b-1.1", result.second.code)
        assertEquals("Beta child", result.second.description)
    }

    @Test
    fun `findProposalByTaskCode returns null for unknown code`() {
        createProposal("alpha", "- [ ] `a-1` Alpha task")
        val result = ProposalScanner.findProposalByTaskCode(tempDir, "z-999")
        assertNull(result)
    }

    @Test
    fun `proposal calculates progress correctly`() {
        createProposal("mixed", """
            - [x] `m-1` Done
            - [x] `m-2` Also done
            - [ ] `m-3` Not done
              - [x] `m-3.1` Child done
              - [ ] `m-3.2` Child not done
        """.trimIndent())

        val proposal = ProposalScanner.findProposal(tempDir, "mixed")!!
        assertEquals(5, proposal.totalCount)
        assertEquals(3, proposal.doneCount)
        assertEquals(60, proposal.progressPercent)
    }
}
