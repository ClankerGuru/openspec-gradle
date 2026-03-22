package zone.clanker.gradle.arch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import zone.clanker.gradle.arch.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceParserTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parses Kotlin file with imports and class`() {
        val file = tempDir.resolve("LoginService.kt").apply {
            writeText("""
                package com.example.auth

                import com.example.data.UserStore
                import com.example.domain.User

                @Service
                class LoginService(private val store: UserStore) : BaseService() {
                    fun login(email: String, password: String) {}
                    fun logout() {}
                }
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertEquals("com.example.auth", result.packageName)
        assertEquals("LoginService", result.simpleName)
        assertEquals("com.example.auth.LoginService", result.qualifiedName)
        assertEquals(listOf("com.example.data.UserStore", "com.example.domain.User"), result.imports)
        assertTrue("Service" in result.annotations)
        assertTrue("BaseService" in result.supertypes)
        assertEquals(SourceFile.Language.KOTLIN, result.language)
        assertTrue(result.methods.contains("login"))
        assertTrue(result.methods.contains("logout"))
    }

    @Test
    fun `parses Java file`() {
        val file = tempDir.resolve("OrderProcessor.java").apply {
            writeText("""
                package com.example.processing;

                import com.example.model.Order;

                @Service
                public class OrderProcessor {
                    public void process(String orderId) {}
                }
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertEquals("com.example.processing", result.packageName)
        assertEquals("OrderProcessor", result.simpleName)
        assertTrue("Service" in result.annotations)
        assertEquals(SourceFile.Language.JAVA, result.language)
    }

    @Test
    fun `parses interface`() {
        val file = tempDir.resolve("DataStore.kt").apply {
            writeText("""
                package com.example.data

                interface DataStore {
                    fun findById(id: String): Any?
                }
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertTrue(result.isInterface)
        assertEquals("DataStore", result.simpleName)
    }

    @Test
    fun `parses data class`() {
        val file = tempDir.resolve("User.kt").apply {
            writeText("""
                package com.example.domain

                data class User(val id: String, val name: String)
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertTrue(result.isDataClass)
    }

    @Test
    fun `parses Java class with extends and implements`() {
        val file = tempDir.resolve("OrderHandler.java").apply {
            writeText("""
                package com.example.web;

                import com.example.processing.OrderProcessor;

                @Controller
                public class OrderHandler extends BaseHandler implements Auditable {
                    public void handle() {}
                }
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertEquals("OrderHandler", result.simpleName)
        assertTrue("BaseHandler" in result.supertypes)
        assertTrue("Auditable" in result.supertypes)
    }

    @Test
    fun `ignores standard library imports`() {
        val file = tempDir.resolve("Foo.kt").apply {
            writeText("""
                package com.example

                import kotlin.collections.List
                import java.util.Date
                import javax.inject.Inject
                import com.example.Bar

                class Foo
            """.trimIndent())
        }

        val result = parseSourceFile(file)
        assertNotNull(result)
        assertEquals(listOf("com.example.Bar"), result.imports)
    }

    @Test
    fun `scanSources finds all files`() {
        val src = tempDir.resolve("src/main/kotlin/com/example").apply { mkdirs() }
        src.resolve("Foo.kt").writeText("package com.example\nclass Foo")
        src.resolve("Bar.kt").writeText("package com.example\nclass Bar")

        val results = scanSources(listOf(tempDir.resolve("src/main/kotlin")))
        assertEquals(2, results.size)
    }
}

class ComponentClassifierTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `classifies annotated Controller`() {
        val source = createSource("OrderHandler", annotations = listOf("RestController"))
        val result = classifyComponent(source)
        assertEquals(ComponentRole.CONTROLLER, result.role)
    }

    @Test
    fun `classifies annotated Service`() {
        val source = createSource("PaymentProcessor", annotations = listOf("Service"))
        val result = classifyComponent(source)
        assertEquals(ComponentRole.SERVICE, result.role)
    }

    @Test
    fun `classifies Manager as smell`() {
        val source = createSource("SessionManager")
        val result = classifyComponent(source)
        assertEquals(ComponentRole.MANAGER, result.role)
    }

    @Test
    fun `classifies Helper as smell`() {
        val source = createSource("DateHelper")
        val result = classifyComponent(source)
        assertEquals(ComponentRole.HELPER, result.role)
    }

    @Test
    fun `classifies Util as smell`() {
        val source = createSource("StringUtils")
        val result = classifyComponent(source)
        assertEquals(ComponentRole.UTIL, result.role)
    }

    @Test
    fun `unannotated class is OTHER`() {
        val source = createSource("OrderProcessor")
        val result = classifyComponent(source)
        assertEquals(ComponentRole.OTHER, result.role)
    }

    @Test
    fun `classifyAll assigns package groups`() {
        val sources = listOf(
            createSource("Handler", packageName = "com.example.web"),
            createSource("Store", packageName = "com.example.storage"),
            createSource("Config", packageName = "com.example.config"),
        )
        val components = classifyAll(sources)
        assertEquals("web", components[0].packageGroup)
        assertEquals("storage", components[1].packageGroup)
        assertEquals("config", components[2].packageGroup)
    }

    @Test
    fun `commonPackagePrefix finds shared prefix`() {
        val result = commonPackagePrefix(listOf(
            "com.example.web",
            "com.example.storage",
            "com.example.config",
        ))
        assertEquals("com.example", result)
    }

    private fun createSource(
        name: String,
        packageName: String = "com.example",
        annotations: List<String> = emptyList(),
    ) = SourceFile(
        file = tempDir.resolve("$name.kt"),
        packageName = packageName,
        qualifiedName = "$packageName.$name",
        simpleName = name,
        imports = emptyList(),
        annotations = annotations,
        supertypes = emptyList(),
        isInterface = false,
        isAbstract = false,
        isObject = false,
        isDataClass = false,
        language = SourceFile.Language.KOTLIN,
        lineCount = 10,
        methods = emptyList(),
    )
}

class DependencyAnalyzerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `builds dependency graph from imports`() {
        val store = createSource("UserStore", "com.example.data", emptyList())
        val handler = createSource("UserHandler", "com.example.web", listOf("com.example.data.UserStore"))

        val components = listOf(store, handler).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)

        assertEquals(1, edges.size)
        assertEquals("UserHandler", edges[0].from.source.simpleName)
        assertEquals("UserStore", edges[0].to.source.simpleName)
    }

    @Test
    fun `finds hub classes`() {
        val hub = createSource("SharedConfig", "com.example.config", emptyList())
        val a = createSource("ModuleA", "com.example.a", listOf("com.example.config.SharedConfig"))
        val b = createSource("ModuleB", "com.example.b", listOf("com.example.config.SharedConfig"))
        val c = createSource("ModuleC", "com.example.c", listOf("com.example.config.SharedConfig"))

        val components = listOf(hub, a, b, c).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)
        val hubs = findHubClasses(components, edges)

        assertEquals(1, hubs.size)
        assertEquals("SharedConfig", hubs[0].first.source.simpleName)
        assertEquals(3, hubs[0].second)
    }

    @Test
    fun `detects cycles`() {
        val a = createSource("ClassA", "com.example", listOf("com.example.ClassB"))
        val b = createSource("ClassB", "com.example", listOf("com.example.ClassA"))

        val components = listOf(a, b).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)
        val cycles = findCycles(components, edges)

        assertTrue(cycles.isNotEmpty())
    }

    @Test
    fun `entry points are root nodes in graph`() {
        val root = createSource("App", "com.example", listOf("com.example.Engine"))
        val engine = createSource("Engine", "com.example", listOf("com.example.Store"))
        val store = createSource("Store", "com.example", emptyList())

        val components = listOf(root, engine, store).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)
        val entryPoints = findEntryPoints(components, edges)

        assertEquals(1, entryPoints.size)
        assertEquals("App", entryPoints[0].source.simpleName)
    }

    private fun createSource(name: String, pkg: String, imports: List<String>) =
        SourceFile(tempDir.resolve("$name.kt"), pkg, "$pkg.$name", name,
            imports, emptyList(), emptyList(), false, false, false, false, SourceFile.Language.KOTLIN, 10, emptyList())
}

class AntiPatternDetectorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects single-impl interface`() {
        val iface = SourceFile(tempDir.resolve("DataStore.kt"), "com.example", "com.example.DataStore", "DataStore",
            emptyList(), emptyList(), emptyList(), true, false, false, false, SourceFile.Language.KOTLIN, 5, emptyList())
        val impl = SourceFile(tempDir.resolve("SqlDataStore.kt"), "com.example", "com.example.SqlDataStore", "SqlDataStore",
            emptyList(), emptyList(), listOf("DataStore"), false, false, false, false, SourceFile.Language.KOTLIN, 20, emptyList())

        val components = listOf(iface, impl).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)
        val patterns = detectAntiPatterns(components, edges, tempDir)

        assertTrue(patterns.any { it.message.contains("only one implementation") })
    }

    @Test
    fun `detects god class`() {
        val god = SourceFile(tempDir.resolve("MegaProcessor.kt"), "com.example", "com.example.MegaProcessor", "MegaProcessor",
            (1..35).map { "com.example.dep.Dep$it" }, emptyList(), emptyList(), false, false, false, false,
            SourceFile.Language.KOTLIN, 600, (1..30).map { "method$it" })

        val components = listOf(classifyComponent(god))
        val patterns = detectAntiPatterns(components, emptyList(), tempDir)

        assertTrue(patterns.any { it.message.contains("doing too much") })
    }

    @Test
    fun `detects manager class`() {
        val mgr = SourceFile(tempDir.resolve("SessionManager.kt"), "com.example", "com.example.SessionManager", "SessionManager",
            emptyList(), emptyList(), emptyList(), false, false, false, false, SourceFile.Language.KOTLIN, 40, emptyList())

        val components = listOf(classifyComponent(mgr))
        val patterns = detectAntiPatterns(components, emptyList(), tempDir)

        assertTrue(patterns.any { it.message.contains("manager class") })
    }

    @Test
    fun `detects circular dependency`() {
        val a = SourceFile(tempDir.resolve("A.kt"), "com.example", "com.example.A", "A",
            listOf("com.example.B"), emptyList(), emptyList(), false, false, false, false, SourceFile.Language.KOTLIN, 10, emptyList())
        val b = SourceFile(tempDir.resolve("B.kt"), "com.example", "com.example.B", "B",
            listOf("com.example.A"), emptyList(), emptyList(), false, false, false, false, SourceFile.Language.KOTLIN, 10, emptyList())

        val components = listOf(a, b).map { classifyComponent(it) }
        val edges = buildDependencyGraph(components)
        val patterns = detectAntiPatterns(components, edges, tempDir)

        assertTrue(patterns.any { it.message.contains("Circular dependency") })
    }
}

class DiagramGeneratorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `generates mermaid flowchart`() {
        val handler = createClassified("OrderHandler", "com.example.web", "web")
        val store = createClassified("OrderStore", "com.example.data", "data")

        val edges = listOf(ClassDependency(handler, store))
        val diagram = generateDependencyDiagram(listOf(handler, store), edges)

        assertTrue(diagram.contains("```mermaid"))
        assertTrue(diagram.contains("flowchart TD"))
        assertTrue(diagram.contains("OrderHandler"))
        assertTrue(diagram.contains("OrderStore"))
        assertTrue(diagram.contains("-->"))
    }

    @Test
    fun `groups by package in diagram`() {
        val a = createClassified("Handler", "com.example.web", "web")
        val b = createClassified("Store", "com.example.data", "data")

        val edges = listOf(ClassDependency(a, b))
        val diagram = generateDependencyDiagram(listOf(a, b), edges)

        assertTrue(diagram.contains("subgraph data"))
        assertTrue(diagram.contains("subgraph web"))
    }

    @Test
    fun `empty components produce empty diagram`() {
        val diagram = generateDependencyDiagram(emptyList(), emptyList())
        assertTrue(diagram.isEmpty())
    }

    @Test
    fun `generates sequence diagram from entry point`() {
        val entry = createClassified("App", "com.example", "(root)", methods = listOf("main"))
        val engine = createClassified("Engine", "com.example.core", "core")

        val edges = listOf(ClassDependency(entry, engine))
        val diagram = generateSequenceDiagrams(listOf(entry, engine), edges)

        assertTrue(diagram.contains("sequenceDiagram"))
        assertTrue(diagram.contains("App"))
        assertTrue(diagram.contains("Engine"))
    }

    private fun createClassified(
        name: String,
        pkg: String,
        group: String,
        methods: List<String> = emptyList(),
    ): ClassifiedComponent {
        val source = SourceFile(tempDir.resolve("$name.kt"), pkg, "$pkg.$name", name,
            emptyList(), emptyList(), emptyList(), false, false, false, false, SourceFile.Language.KOTLIN, 10, methods)
        return ClassifiedComponent(source, ComponentRole.OTHER, group)
    }
}

class ArchIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `full pipeline on sample project`() {
        val srcDir = tempDir.resolve("src/main/kotlin/com/example").apply { mkdirs() }

        srcDir.resolve("web").mkdirs()
        srcDir.resolve("web/OrderHandler.kt").writeText("""
            package com.example.web

            import com.example.processing.OrderProcessor

            @Controller
            class OrderHandler(private val processor: OrderProcessor) {
                fun handle() {}
            }
        """.trimIndent())

        srcDir.resolve("processing").mkdirs()
        srcDir.resolve("processing/OrderProcessor.kt").writeText("""
            package com.example.processing

            import com.example.storage.OrderStore

            class OrderProcessor(private val store: OrderStore) {
                fun process() {}
            }
        """.trimIndent())

        srcDir.resolve("storage").mkdirs()
        srcDir.resolve("storage/OrderStore.kt").writeText("""
            package com.example.storage

            interface OrderStore {
                fun save(order: Any)
            }
        """.trimIndent())

        srcDir.resolve("storage/SqlOrderStore.kt").writeText("""
            package com.example.storage

            class SqlOrderStore : OrderStore {
                override fun save(order: Any) {}
            }
        """.trimIndent())

        srcDir.resolve("util").mkdirs()
        srcDir.resolve("util/DateHelper.kt").writeText("""
            package com.example.util

            class DateHelper {
                fun format() {}
            }
        """.trimIndent())

        // Run pipeline
        val sources = scanSources(listOf(tempDir.resolve("src/main/kotlin")))
        assertEquals(5, sources.size)

        val components = classifyAll(sources)
        val edges = buildDependencyGraph(components)
        val antiPatterns = detectAntiPatterns(components, edges, tempDir)
        val hubs = findHubClasses(components, edges)
        val diagram = generateDependencyDiagram(components, edges)

        // Entry point is the Controller
        val entryPoints = findEntryPoints(components, edges)
        assertTrue(entryPoints.any { it.source.simpleName == "OrderHandler" })

        // Verify edges
        assertTrue(edges.any { it.from.source.simpleName == "OrderHandler" && it.to.source.simpleName == "OrderProcessor" })
        assertTrue(edges.any { it.from.source.simpleName == "OrderProcessor" && it.to.source.simpleName == "OrderStore" })

        // Verify anti-patterns
        assertTrue(antiPatterns.any { it.message.contains("DateHelper") })
        assertTrue(antiPatterns.any { it.message.contains("only one implementation") && it.message.contains("OrderStore") })

        // Verify diagram uses actual packages
        assertTrue(diagram.contains("mermaid"))
        assertTrue(diagram.contains("web"))
        assertTrue(diagram.contains("processing"))

        // Verify package groups
        val groups = components.map { it.packageGroup }.distinct().sorted()
        assertTrue("web" in groups)
        assertTrue("processing" in groups)
        assertTrue("storage" in groups)
        assertTrue("util" in groups)
    }
}
