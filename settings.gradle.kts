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
    ":quality",
    ":adapters:copilot",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:opencode",
    // Tasks
    ":task:code-intel",
    ":task:openspec",
    ":task:monolith",
    // Plugins
    ":plugin:code-intel",
    ":plugin:openspec",
    ":plugin:monolith",
)
