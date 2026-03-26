package zone.clanker.gradle.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class RepoEntryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parseFile deserializes JSON array`() {
        val file = File(tempDir, "repos.json")
        file.writeText("""
            [
              {
                "name": "my-lib",
                "enable": true,
                "category": "internal",
                "substitutions": ["com.example:my-lib-api,my-lib"]
              }
            ]
        """.trimIndent())
        val entries = RepoEntry.parseFile(file)
        assertEquals(1, entries.size)
        assertEquals("my-lib", entries[0].name)
        assertTrue(entries[0].enable)
        assertEquals("internal", entries[0].category)
        assertEquals(listOf("com.example:my-lib-api,my-lib"), entries[0].substitutions)
    }

    @Test
    fun `parseFile returns empty list for missing file`() {
        val missing = File(tempDir, "nonexistent.json")
        assertEquals(emptyList<RepoEntry>(), RepoEntry.parseFile(missing))
    }

    @Test
    fun `parseFile returns empty list for empty file`() {
        val file = File(tempDir, "empty.json")
        file.writeText("")
        assertEquals(emptyList<RepoEntry>(), RepoEntry.parseFile(file))
    }

    @Test
    fun `parseFile handles multiple entries with mixed enable`() {
        val file = File(tempDir, "repos.json")
        file.writeText("""
            [
              {"name": "a", "enable": true, "category": "cat", "substitutions": []},
              {"name": "b", "enable": false, "category": "cat", "substitutions": []}
            ]
        """.trimIndent())
        val entries = RepoEntry.parseFile(file)
        assertEquals(2, entries.size)
        assertTrue(entries[0].enable)
        assertFalse(entries[1].enable)
    }

    @Test
    fun `parseSubstitution splits correctly`() {
        val (artifact, project) = RepoEntry.parseSubstitution("com.example:my-lib-api,my-lib")
        assertEquals("com.example:my-lib-api", artifact)
        assertEquals("my-lib", project)
    }

    @Test
    fun `parseSubstitution trims whitespace`() {
        val (artifact, project) = RepoEntry.parseSubstitution(" com.example:core , core-project ")
        assertEquals("com.example:core", artifact)
        assertEquals("core-project", project)
    }

    @Test
    fun `parseSubstitution rejects invalid format`() {
        assertThrows(IllegalArgumentException::class.java) {
            RepoEntry.parseSubstitution("no-comma-here")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepoEntry.parseSubstitution(",missing-left")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepoEntry.parseSubstitution("missing-right,")
        }
    }

    @Test
    fun `directoryName extracts from plain name`() {
        assertEquals("my-lib", RepoEntry("my-lib", true, "", emptyList()).directoryName)
    }

    @Test
    fun `directoryName extracts from owner-repo`() {
        assertEquals("my-lib", RepoEntry("org/my-lib", true, "", emptyList()).directoryName)
    }

    @Test
    fun `directoryName strips git suffix from URL`() {
        assertEquals("repo", RepoEntry("https://github.com/user/repo.git", true, "", emptyList()).directoryName)
    }

    @Test
    fun `directoryName extracts from SSH URL`() {
        assertEquals("repo", RepoEntry("git@github.com:user/repo.git", true, "", emptyList()).directoryName)
    }

    @Test
    fun `directoryName extracts from absolute path`() {
        assertEquals("bare-source", RepoEntry("/tmp/repos/bare-source.git", true, "", emptyList()).directoryName)
    }
}
