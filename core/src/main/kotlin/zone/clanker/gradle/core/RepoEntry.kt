package zone.clanker.gradle.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class RepoEntry(
    val name: String,
    val enable: Boolean,
    val category: String,
    val substitutions: List<String>
) {
    val directoryName: String
        get() {
            val cleaned = name.trimEnd('/').removeSuffix(".git")
            return cleaned.substringAfterLast("/").substringAfterLast(":")
        }

    companion object {
        fun parseSubstitution(sub: String): Pair<String, String> {
            val parts = sub.split(",", limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "Invalid substitution format '$sub' — expected 'artifact,project'"
            }
            return parts[0].trim() to parts[1].trim()
        }

        fun parseFile(file: File): List<RepoEntry> {
            if (!file.exists()) return emptyList()
            val text = file.readText().trim()
            if (text.isEmpty()) return emptyList()
            return json.decodeFromString<List<RepoEntry>>(text)
        }
    }
}
