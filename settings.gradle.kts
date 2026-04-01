pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "openspec-gradle"

include(
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
    ":gradle-tasks",
    ":openspec-tasks",
    ":monolith-tasks",
    ":plugin-gradle",
    ":plugin-openspec",
    ":plugin-monolith",
)
