package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyGraphTest {

    private fun task(code: String, status: TaskStatus = TaskStatus.TODO, deps: List<String> = emptyList()) =
        TaskItem(code = code, description = "Task $code", status = status, explicitDeps = deps)

    @Test
    fun `empty graph has no cycles`() {
        val graph = DependencyGraph(emptyList())
        assertFalse(graph.hasCycle())
        assertEquals(emptyList(), graph.findCycles())
    }

    @Test
    fun `linear chain has no cycles and correct topological order`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", deps = listOf("C")),
            task("C")
        )
        val graph = DependencyGraph(tasks)
        assertFalse(graph.hasCycle())

        val order = graph.topologicalOrder()
        // C before B, B before A
        assertTrue(order.indexOf("C") < order.indexOf("B"))
        assertTrue(order.indexOf("B") < order.indexOf("A"))
    }

    @Test
    fun `simple cycle A-B-A detected`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", deps = listOf("A"))
        )
        val graph = DependencyGraph(tasks)
        assertTrue(graph.hasCycle())
        assertTrue(graph.findCycles().isNotEmpty())
    }

    @Test
    fun `diamond has no cycle`() {
        val tasks = listOf(
            task("A", deps = listOf("B", "C")),
            task("B", deps = listOf("D")),
            task("C", deps = listOf("D")),
            task("D")
        )
        val graph = DependencyGraph(tasks)
        assertFalse(graph.hasCycle())
    }

    @Test
    fun `canComplete returns true when deps are DONE`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", status = TaskStatus.DONE)
        )
        val graph = DependencyGraph(tasks)
        assertTrue(graph.canComplete("A"))
    }

    @Test
    fun `canComplete returns false when deps are not DONE`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", status = TaskStatus.TODO)
        )
        val graph = DependencyGraph(tasks)
        assertFalse(graph.canComplete("A"))
    }

    @Test
    fun `unblockedTasks returns correct set`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", status = TaskStatus.DONE),
            task("C", deps = listOf("D")),
            task("D", status = TaskStatus.TODO)
        )
        val graph = DependencyGraph(tasks)
        val unblocked = graph.unblockedTasks()
        assertTrue("A" in unblocked)
        assertFalse("B" in unblocked) // already DONE
        assertFalse("C" in unblocked) // blocked by D
        assertTrue("D" in unblocked) // no deps, not DONE
    }

    @Test
    fun `transitiveDependenciesOf follows chains`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", deps = listOf("C")),
            task("C")
        )
        val graph = DependencyGraph(tasks)
        assertEquals(setOf("B", "C"), graph.transitiveDependenciesOf("A"))
        assertEquals(setOf("C"), graph.transitiveDependenciesOf("B"))
        assertEquals(emptySet(), graph.transitiveDependenciesOf("C"))
    }

    @Test
    fun `complex cycle A-B-C-A detected with correct path`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", deps = listOf("C")),
            task("C", deps = listOf("A"))
        )
        val graph = DependencyGraph(tasks)
        assertTrue(graph.hasCycle())
        val cycles = graph.findCycles()
        assertTrue(cycles.isNotEmpty())
        // At least one cycle should contain all three nodes
        assertTrue(cycles.any { cycle -> cycle.containsAll(listOf("A", "B", "C")) })
    }

    @Test
    fun `dependenciesOf returns direct deps only`() {
        val tasks = listOf(
            task("A", deps = listOf("B")),
            task("B", deps = listOf("C")),
            task("C")
        )
        val graph = DependencyGraph(tasks)
        assertEquals(setOf("B"), graph.dependenciesOf("A"))
    }
}
