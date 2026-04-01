package zone.clanker.gradle.exec

import java.security.MessageDigest

/**
 * Detects cycles in retry loops by tracking error signatures.
 * If attempt N's error matches attempt M's error (where M < N-1),
 * we're cycling — fix A breaks B, fix B breaks A.
 */
class CycleDetector {

    private val errorHashes = mutableListOf<String>()

    /**
     * Record an error and return true if a cycle is detected.
     */
    fun recordAndCheck(errorOutput: String): Boolean {
        val hash = hash(errorOutput.trim())
        val cycleDetected = errorHashes.dropLast(1).contains(hash) // skip immediate predecessor
        errorHashes.add(hash)
        return cycleDetected
    }

    /**
     * Get the attempt number that matches the current cycle, or null.
     */
    fun findCycleMatch(errorOutput: String): Int? {
        val hash = hash(errorOutput.trim())
        val matchIndex = errorHashes.dropLast(1).indexOfFirst { it == hash }
        return if (matchIndex >= 0) matchIndex + 1 else null
    }

    /**
     * Get a summary of all recorded error signatures.
     */
    fun summary(): String = errorHashes.mapIndexed { i, h ->
        "Attempt ${i + 1}: $h"
    }.joinToString("\n")

    private fun hash(text: String): String {
        // Normalize: strip timestamps, line numbers, file paths that change
        val normalized = text
            .replace(Regex("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}"), "[TIMESTAMP]")
            .replace(Regex("line \\d+"), "line [N]")
            .trim()

        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(normalized.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }
}
