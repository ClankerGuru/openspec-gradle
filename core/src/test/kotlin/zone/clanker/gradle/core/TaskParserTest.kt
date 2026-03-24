package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TaskParserTest {

    @Test
    fun `parse simple checkbox lines`() {
        val lines = listOf(
            "- [ ] `ttd-1` First task",
            "- [x] `ttd-2` Second task done",
            "- [/] `ttd-3` Third in progress"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(3, tasks.size)
        assertEquals("ttd-1", tasks[0].code)
        assertEquals("First task", tasks[0].description)
        assertEquals(TaskStatus.TODO, tasks[0].status)

        assertEquals("ttd-2", tasks[1].code)
        assertEquals(TaskStatus.DONE, tasks[1].status)

        assertEquals("ttd-3", tasks[2].code)
        assertEquals(TaskStatus.IN_PROGRESS, tasks[2].status)
    }

    @Test
    fun `parse nested tasks`() {
        val lines = listOf(
            "- [ ] `ttd-1` Parent task",
            "  - [ ] `ttd-1.1` Child one",
            "  - [x] `ttd-1.2` Child two",
            "- [ ] `ttd-2` Another parent"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals("ttd-1", tasks[0].code)
        assertEquals(2, tasks[0].children.size)
        assertEquals("ttd-1.1", tasks[0].children[0].code)
        assertEquals(TaskStatus.TODO, tasks[0].children[0].status)
        assertEquals("ttd-1.2", tasks[0].children[1].code)
        assertEquals(TaskStatus.DONE, tasks[0].children[1].status)

        assertEquals("ttd-2", tasks[1].code)
        assertEquals(0, tasks[1].children.size)
    }

    @Test
    fun `parse deeply nested tasks with 4-space indent`() {
        val lines = listOf(
            "- [ ] `a-1` Level 0",
            "    - [ ] `a-1.1` Level 1",
            "        - [ ] `a-1.1.1` Level 2",
            "    - [ ] `a-1.2` Level 1 again"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(1, tasks.size)
        assertEquals(2, tasks[0].children.size)
        assertEquals(1, tasks[0].children[0].children.size)
        assertEquals("a-1.1.1", tasks[0].children[0].children[0].code)
    }

    @Test
    fun `parse tasks without codes`() {
        val lines = listOf(
            "- [ ] A task without a code",
            "- [x] Another done task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals("", tasks[0].code)
        assertEquals("A task without a code", tasks[0].description)
        assertEquals("", tasks[1].code)
        assertEquals(TaskStatus.DONE, tasks[1].status)
    }

    @Test
    fun `parse explicit dependencies`() {
        val lines = listOf(
            "- [ ] `tdg-3` Build DependencyGraph → depends: tdg-1, tdg-2",
            "- [ ] `tdg-4` Validation → depends: tdg-3"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals("Build DependencyGraph", tasks[0].description)
        assertEquals(listOf("tdg-1", "tdg-2"), tasks[0].explicitDeps)
        assertEquals(listOf("tdg-3"), tasks[1].explicitDeps)
    }

    @Test
    fun `skip non-task lines`() {
        val lines = listOf(
            "# Tasks: My Proposal",
            "",
            "## Section Header",
            "",
            "- [ ] `t-1` Real task",
            "Some random text",
            "- Not a checkbox",
            "- [ ] `t-2` Another real task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals("t-1", tasks[0].code)
        assertEquals("t-2", tasks[1].code)
    }

    @Test
    fun `uppercase X in checkbox is DONE`() {
        val lines = listOf(
            "- [X] `t-1` Done with uppercase X"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.DONE, tasks[0].status)
    }

    @Test
    fun `totalCount and doneCount on nested tasks`() {
        val lines = listOf(
            "- [ ] `t-1` Parent",
            "  - [x] `t-1.1` Done child",
            "  - [x] `t-1.2` Done child",
            "  - [ ] `t-1.3` Todo child",
            "- [x] `t-2` Done parent"
        )
        val tasks = TaskParser.parse(lines)

        // t-1: 4 total (itself + 3 children), 2 done
        assertEquals(4, tasks[0].totalCount)
        assertEquals(2, tasks[0].doneCount)
        assertEquals(50, tasks[0].progressPercent)

        // t-2: 1 total, 1 done
        assertEquals(1, tasks[1].totalCount)
        assertEquals(1, tasks[1].doneCount)
        assertEquals(100, tasks[1].progressPercent)
    }

    @Test
    fun `findByCode in tree`() {
        val lines = listOf(
            "- [ ] `t-1` Parent",
            "  - [ ] `t-1.1` Child",
            "  - [ ] `t-1.2` Another child",
            "- [ ] `t-2` Second parent"
        )
        val tasks = TaskParser.parse(lines)

        val found = tasks[0].findByCode("t-1.2")
        assertNotNull(found)
        assertEquals("Another child", found!!.description)

        val notFound = tasks[0].findByCode("t-999")
        assertNull(notFound)
    }

    @Test
    fun `parse real-world tasks md`() {
        val lines = """
            # Tasks: Task Tracking Dashboard

            ## Core Parsing

            - [ ] `ttd-1` Create TaskItem data class and TaskStatus enum
            - [ ] `ttd-2` Build TaskParser
              - [ ] `ttd-2.1` Handle checkboxes
              - [ ] `ttd-2.2` Handle nested tasks
              - [ ] `ttd-2.3` Extract task code from backticks
            - [ ] `ttd-3` Build TaskCodeGenerator
              - [ ] `ttd-3.1` Prefix extraction
              - [ ] `ttd-3.2` Code assignment
        """.trimIndent().lines()

        val tasks = TaskParser.parse(lines)

        assertEquals(3, tasks.size)
        assertEquals("ttd-1", tasks[0].code)
        assertEquals(0, tasks[0].children.size)

        assertEquals("ttd-2", tasks[1].code)
        assertEquals(3, tasks[1].children.size)
        assertEquals("ttd-2.1", tasks[1].children[0].code)

        assertEquals("ttd-3", tasks[2].code)
        assertEquals(2, tasks[2].children.size)
    }

    @Test
    fun `parse verify assertions from blockquote`() {
        val lines = listOf(
            "- [ ] `t-1` Create Foo class",
            "  > verify: symbol-exists Foo, file-exists src/main/kotlin/Foo.kt",
            "- [ ] `t-2` Another task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals(2, tasks[0].verifyAssertions.size)
        assertEquals("symbol-exists", tasks[0].verifyAssertions[0].type)
        assertEquals("Foo", tasks[0].verifyAssertions[0].argument)
        assertEquals("file-exists", tasks[0].verifyAssertions[1].type)
        assertEquals("src/main/kotlin/Foo.kt", tasks[0].verifyAssertions[1].argument)
        // Second task has no assertions
        assertEquals(0, tasks[1].verifyAssertions.size)
    }

    @Test
    fun `task without verify line gets empty assertions`() {
        val lines = listOf(
            "- [ ] `t-1` Simple task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(1, tasks.size)
        assertEquals(0, tasks[0].verifyAssertions.size)
    }

    @Test
    fun `parse build-passes assertion without argument`() {
        val lines = listOf(
            "- [ ] `t-1` Refactor module",
            "  > verify: build-passes"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(1, tasks.size)
        assertEquals(1, tasks[0].verifyAssertions.size)
        assertEquals("build-passes", tasks[0].verifyAssertions[0].type)
        assertEquals("", tasks[0].verifyAssertions[0].argument)
    }

    @Test
    fun `verify line does not break task description`() {
        val lines = listOf(
            "- [ ] `t-1` Create TaskLifecycle object → depends: t-0",
            "  > verify: symbol-exists TaskLifecycle",
            "- [ ] `t-2` Next task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals("Create TaskLifecycle object", tasks[0].description)
        assertEquals(listOf("t-0"), tasks[0].explicitDeps)
        assertEquals(1, tasks[0].verifyAssertions.size)
        assertEquals("TaskLifecycle", tasks[0].verifyAssertions[0].argument)
    }

    @Test
    fun `parse unverified marker`() {
        val lines = listOf(
            "- [x] `t-1` Force-completed task ⚠️ unverified",
            "- [x] `t-2` Properly completed task"
        )
        val tasks = TaskParser.parse(lines)

        assertEquals(2, tasks.size)
        assertEquals(false, tasks[0].verified)
        assertEquals("Force-completed task", tasks[0].description)
        assertEquals(true, tasks[1].verified)
    }
}
