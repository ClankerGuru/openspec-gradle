package zone.clanker.gradle.exec

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import zone.clanker.gradle.core.TaskItem
import zone.clanker.gradle.core.TaskStatus

class LevelSchedulerTest {

    private fun task(
        code: String,
        status: TaskStatus = TaskStatus.TODO,
        deps: List<String> = emptyList(),
    ) = TaskItem(code = code, description = code, status = status, explicitDeps = deps)

    @Test
    fun `linear chain produces sequential levels`() {
        val tasks = listOf(
            task("a"),
            task("b", deps = listOf("a")),
            task("c", deps = listOf("b")),
        )
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(3, levels.size)
        assertEquals(listOf("a"), levels[0].map { it.code })
        assertEquals(listOf("b"), levels[1].map { it.code })
        assertEquals(listOf("c"), levels[2].map { it.code })
    }

    @Test
    fun `diamond produces three levels with parallel middle`() {
        val tasks = listOf(
            task("a"),
            task("b", deps = listOf("a")),
            task("c", deps = listOf("a")),
            task("d", deps = listOf("b", "c")),
        )
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(3, levels.size)
        assertEquals(listOf("a"), levels[0].map { it.code })
        assertEquals(setOf("b", "c"), levels[1].map { it.code }.toSet())
        assertEquals(listOf("d"), levels[2].map { it.code })
    }

    @Test
    fun `wide fan-out produces two levels`() {
        val tasks = listOf(
            task("a"),
            task("b", deps = listOf("a")),
            task("c", deps = listOf("a")),
            task("d", deps = listOf("a")),
            task("e", deps = listOf("a")),
        )
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(2, levels.size)
        assertEquals(listOf("a"), levels[0].map { it.code })
        assertEquals(setOf("b", "c", "d", "e"), levels[1].map { it.code }.toSet())
    }

    @Test
    fun `no deps puts all in level 0`() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(1, levels.size)
        assertEquals(setOf("a", "b", "c"), levels[0].map { it.code }.toSet())
    }

    @Test
    fun `skips DONE tasks`() {
        val tasks = listOf(
            task("a", status = TaskStatus.DONE),
            task("b", deps = listOf("a")),
            task("c", deps = listOf("a")),
        )
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(1, levels.size)
        assertEquals(setOf("b", "c"), levels[0].map { it.code }.toSet())
    }

    @Test
    fun `all DONE returns empty`() {
        val tasks = listOf(
            task("a", status = TaskStatus.DONE),
            task("b", status = TaskStatus.DONE),
        )
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(0, levels.size)
    }

    @Test
    fun `cycle throws exception`() {
        val tasks = listOf(
            task("a", deps = listOf("b")),
            task("b", deps = listOf("a")),
        )
        assertThrows<IllegalStateException> {
            LevelScheduler(tasks).schedule()
        }
    }

    @Test
    fun `single task produces one level`() {
        val tasks = listOf(task("a"))
        val levels = LevelScheduler(tasks).schedule()
        assertEquals(1, levels.size)
        assertEquals(listOf("a"), levels[0].map { it.code })
    }
}
