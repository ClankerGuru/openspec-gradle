package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TaskCodeGeneratorTest {

    @Test
    fun `prefix from multi-word name`() {
        assertEquals("ttd", TaskCodeGenerator.prefix("task-tracking-dashboard"))
        assertEquals("aua", TaskCodeGenerator.prefix("add-user-auth"))
        assertEquals("flb", TaskCodeGenerator.prefix("fix-login-bug"))
        assertEquals("lds", TaskCodeGenerator.prefix("local-dependency-substitution"))
        assertEquals("gtp", TaskCodeGenerator.prefix("gradle-task-pipeline"))
    }

    @Test
    fun `prefix from two-word name`() {
        assertEquals("ab", TaskCodeGenerator.prefix("alpha-beta"))
    }

    @Test
    fun `prefix from single word`() {
        assertEquals("sin", TaskCodeGenerator.prefix("single"))
        assertEquals("fi", TaskCodeGenerator.prefix("fi"))
    }

    @Test
    fun `prefix from empty string`() {
        assertEquals("xxx", TaskCodeGenerator.prefix(""))
    }

    @Test
    fun `assignCodes to tasks without codes`() {
        val tasks = listOf(
            TaskItem(code = "", description = "First task", status = TaskStatus.TODO,
                children = listOf(
                    TaskItem(code = "", description = "Subtask A", status = TaskStatus.TODO),
                    TaskItem(code = "", description = "Subtask B", status = TaskStatus.TODO)
                )),
            TaskItem(code = "", description = "Second task", status = TaskStatus.TODO)
        )

        val coded = TaskCodeGenerator.assignCodes("ttd", tasks)

        assertEquals("ttd-1", coded[0].code)
        assertEquals("ttd-1.1", coded[0].children[0].code)
        assertEquals("ttd-1.2", coded[0].children[1].code)
        assertEquals("ttd-2", coded[1].code)
    }

    @Test
    fun `assignCodes preserves existing codes`() {
        val tasks = listOf(
            TaskItem(code = "custom-1", description = "Has code", status = TaskStatus.TODO,
                children = listOf(
                    TaskItem(code = "", description = "No code", status = TaskStatus.TODO),
                    TaskItem(code = "custom-1.5", description = "Has code", status = TaskStatus.TODO)
                )),
            TaskItem(code = "", description = "No code", status = TaskStatus.TODO)
        )

        val coded = TaskCodeGenerator.assignCodes("abc", tasks)

        assertEquals("custom-1", coded[0].code)
        assertEquals("abc-1.1", coded[0].children[0].code)
        assertEquals("custom-1.5", coded[0].children[1].code)
        assertEquals("abc-2", coded[1].code)
    }

    @Test
    fun `injectCodes into lines without codes`() {
        val lines = listOf(
            "# Tasks",
            "",
            "- [ ] First task",
            "  - [ ] Subtask A",
            "  - [x] Subtask B",
            "- [ ] Second task"
        )

        val result = TaskCodeGenerator.injectCodes(lines, "abc")

        assertEquals("# Tasks", result[0])
        assertEquals("", result[1])
        assertEquals("- [ ] `abc-1` First task", result[2])
        assertEquals("  - [ ] `abc-1.1` Subtask A", result[3])
        assertEquals("  - [x] `abc-1.2` Subtask B", result[4])
        assertEquals("- [ ] `abc-2` Second task", result[5])
    }

    @Test
    fun `injectCodes replaces existing codes`() {
        val lines = listOf(
            "- [ ] `old-1` First task",
            "- [ ] `old-2` Second task"
        )

        val result = TaskCodeGenerator.injectCodes(lines, "new")

        assertEquals("- [ ] `new-1` First task", result[0])
        assertEquals("- [ ] `new-2` Second task", result[1])
    }
}
