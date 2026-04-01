package zone.clanker.gradle.psi

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File

/**
 * Discovers source directories across Java, Kotlin JVM, and KMP projects.
 * Shared between opsx-arch and PSI-based tasks.
 */
object SourceDiscovery {

    fun discoverSourceDirs(projects: List<Project>): List<File> =
        projects.flatMap { discoverSourceDirs(it) }.distinctBy { it.absolutePath }

    fun discoverSourceDirs(project: Project): List<File> {
        val dirs = mutableListOf<File>()

        // Java/Kotlin JVM source sets
        val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
        javaExt?.sourceSets?.forEach { ss ->
            dirs.addAll(ss.allSource.srcDirs.filter { it.exists() })
        }

        // KMP source sets — discover via the kotlin extension reflectively
        if (dirs.isEmpty()) {
            try {
                val kotlinExt = project.extensions.findByName("kotlin")
                if (kotlinExt != null) {
                    val sourceSets = kotlinExt.javaClass.getMethod("getSourceSets").invoke(kotlinExt)
                    if (sourceSets is Iterable<*>) {
                        for (ss in sourceSets) {
                            if (ss == null) continue
                            val kotlin = ss.javaClass.getMethod("getKotlin").invoke(ss)
                            if (kotlin != null) {
                                val srcDirSet = kotlin.javaClass.getMethod("getSrcDirs").invoke(kotlin)
                                if (srcDirSet is Set<*>) {
                                    srcDirSet.filterIsInstance<File>().filter { it.exists() }.forEach { dirs.add(it) }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                project.logger.debug("KMP source discovery failed for ${project.path}: ${e.message}")
            }
        }

        // Fallback: scan conventional source directories
        if (dirs.isEmpty()) {
            val srcDir = project.file("src")
            if (srcDir.exists()) {
                srcDir.listFiles()?.forEach { setDir ->
                    val kotlinDir = File(setDir, "kotlin")
                    val javaDir = File(setDir, "java")
                    if (kotlinDir.exists()) dirs.add(kotlinDir)
                    if (javaDir.exists()) dirs.add(javaDir)
                }
            }
        }

        return dirs
    }

    private val SOURCE_EXTENSIONS = setOf("kt", "java", "kts")
    private val BUILD_FILE_NAMES = setOf(
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "libs.versions.toml", "monolith.json"
    )

    fun collectSourceFiles(dirs: List<File>): List<File> =
        dirs.flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension in SOURCE_EXTENSIONS }.toList()
        }.distinctBy { it.absolutePath }

    /**
     * Collect all indexable files — source code + build scripts + config.
     * Used by opsx-find and opsx-symbols to search across everything.
     */
    fun collectAllFiles(dirs: List<File>, projectDirs: List<File> = emptyList()): List<File> {
        val sourceFiles = collectSourceFiles(dirs)
        val buildFiles = projectDirs.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile }
                .filter { it.name in BUILD_FILE_NAMES || it.extension in setOf("toml", "properties") }
                .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") }
                .toList()
        }
        return (sourceFiles + buildFiles).distinctBy { it.absolutePath }
    }

    fun resolveProjects(project: Project, module: String?): List<Project> {
        val root = project.rootProject
        return if (module != null) {
            (root.subprojects + root).filter { it.name == module || it.path == module || it.path == ":$module" }
        } else if (root.subprojects.isNotEmpty()) {
            listOf(root) + root.subprojects.sortedBy { it.path }
        } else {
            listOf(root)
        }
    }
}
