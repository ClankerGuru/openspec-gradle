pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "openspec-gradle"

include(
    // Pure JVM libraries
    ":core",
    ":psi",
    ":arch",
    ":exec",
    ":generators",
    ":adapters:copilot",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:opencode",
    // Gradle modules
    ":quality",
    ":gradle-tasks",
    ":openspec-tasks",
    ":monolith-tasks",
    // Settings plugins (nested)
    ":plugin:code-intel",
    ":plugin:openspec",
    ":plugin:monolith",
)
