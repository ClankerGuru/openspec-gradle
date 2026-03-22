rootProject.name = "openspec-gradle"

include(
    ":core",
    ":psi",
    ":arch",
    ":exec",
    ":generators",
    ":adapters:copilot",
    ":adapters:claude",
    ":adapters:codex",
    ":adapters:opencode",
    ":tasks",
    ":plugin",
)
