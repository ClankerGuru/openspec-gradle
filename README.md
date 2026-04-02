# openspec-gradle

Gradle as a dynamic context engine for AI coding agents.

Your build tool already knows more about your project than any shell script ever will -- resolved dependencies, module relationships, framework versions, source structure, symbol graphs. openspec-gradle turns that knowledge into structured context that AI coding agents can actually use.

Works with **GitHub Copilot**, **Claude Code**, **OpenAI Codex**, and **OpenCode**.

## Architecture

```
settings.gradle.kts
  |
  plugins { id("zone.clanker.srcx") }     <-- Settings plugins (applied in settings.gradle.kts)
  |
  +-- plugin/claude        zone.clanker.claude       CLI wrapper for Claude Code
  +-- plugin/copilot       zone.clanker.copilot      CLI wrapper for GitHub Copilot
  +-- plugin/codex         zone.clanker.codex        CLI wrapper for OpenAI Codex
  +-- plugin/opencode      zone.clanker.opencode     CLI wrapper for OpenCode
  +-- plugin/srcx          zone.clanker.srcx         Discovery, intelligence, refactoring tasks
  +-- plugin/opsx          zone.clanker.opsx         Workflow, execution, agent sync
  +-- plugin/wrkx          zone.clanker.wrkx         Multi-repo workspace management
  |
  +-- task/srcx            Task classes for srcx plugin
  |     |
  |     +-- lib/core       Data models, extensions, version info
  |     +-- lib/psi        Kotlin/Java source parsing (PSI-like)
  |     +-- lib/arch       Architecture analysis engine
  |     +-- lib/quality    Linting integration (detekt, ktlint)
  |
  +-- task/opsx            Task classes for opsx plugin
  |     |
  |     +-- lib/core       Data models, extensions, version info
  |     +-- lib/generators Agent file generators (instructions, skills)
  |     +-- lib/exec       Agent CLI execution engine
  |     +-- lib/adapters/* Per-agent output adapters
  |
  +-- task/wrkx            Task classes for wrkx plugin
        |
        +-- lib/core       Data models, extensions, version info
```

## Module Map

| Module Path | Artifact ID | Description | README |
|---|---|---|---|
| `plugin/claude` | `plugin-claude` | Settings plugin wrapping the Claude Code CLI | [README](plugin/claude/README.md) |
| `plugin/copilot` | `plugin-copilot` | Settings plugin wrapping the GitHub Copilot CLI | [README](plugin/copilot/README.md) |
| `plugin/codex` | `plugin-codex` | Settings plugin wrapping the OpenAI Codex CLI | [README](plugin/codex/README.md) |
| `plugin/opencode` | `plugin-opencode` | Settings plugin wrapping the OpenCode CLI | [README](plugin/opencode/README.md) |
| `plugin/opsx` | `plugin-opsx` | Workflow engine: proposals, task execution, agent sync | [README](plugin/opsx/README.md) |
| `plugin/srcx` | `plugin-srcx` | Code intelligence: discovery, analysis, refactoring | [README](plugin/srcx/README.md) |
| `plugin/wrkx` | `plugin-wrkx` | Multi-repo workspace with composite build wiring | [README](plugin/wrkx/README.md) |
| `lib/core` | `openspec-core` | Shared data models, extensions, VersionInfo | -- |
| `lib/exec` | `openspec-exec` | Agent CLI execution and process management | -- |
| `lib/generators` | `openspec-generators` | Agent instruction and skill file generators | -- |
| `lib/psi` | `openspec-psi` | Kotlin/Java source parsing and symbol extraction | -- |
| `lib/arch` | `openspec-arch` | Architecture analysis, component classification, smells | -- |
| `lib/quality` | `quality` | Linting plugins (detekt, ktlint auto-apply) | -- |
| `lib/adapters/claude` | `openspec-adapter-claude` | Claude Code output adapter | -- |
| `lib/adapters/copilot` | `openspec-adapter-copilot` | GitHub Copilot output adapter | -- |
| `lib/adapters/codex` | `openspec-adapter-codex` | OpenAI Codex output adapter | -- |
| `lib/adapters/opencode` | `openspec-adapter-opencode` | OpenCode output adapter | -- |
| `task/srcx` | `srcx-tasks` | Task implementations for the srcx plugin | -- |
| `task/opsx` | `opsx-tasks` | Task implementations for the opsx plugin | -- |
| `task/wrkx` | `wrkx-tasks` | Task implementations for the wrkx plugin | -- |

## Quick Start

### Install

![Install with Claude](art/install-claude-2026-04-02.gif)

```bash
# Interactive
bash install.sh

# Specific agents
bash install.sh claude copilot

# With flags
bash install.sh --core --agents claude

# Piped (auto-detect latest version)
curl -fsSL https://raw.githubusercontent.com/ClankerGuru/openspec-gradle/main/install.sh | bash
```

![Multi-agent install](art/install-multi-agent-2026-04-02.gif)

### Workspace setup

![Workspace sync](art/workspace-sync-2026-04-02.gif)

This installs separate init scripts to `~/.gradle/init.d/`:

```
~/.gradle/init.d/
  00-wrkx.init.gradle.kts     ← workspace management (loads first)
  01-srcx.init.gradle.kts     ← source intelligence
  02-opsx.init.gradle.kts     ← workflow engine + linting
  03-claude.init.gradle.kts   ← Claude Code CLI wrapper
```

Every Gradle project on your machine gets all tasks automatically. No per-project config needed.

To pin a specific version:

```bash
OPENSPEC_VERSION=0.34.0 curl -fsSL .../install.sh | bash
```

### Per-project install (alternative)

Add to `settings.gradle.kts`:

```kotlin
plugins {
    id("zone.clanker.wrkx") version "<version>"
    id("zone.clanker.srcx") version "<version>"
    id("zone.clanker.opsx") version "<version>"
}
```

### Install via Gradle task

If you already have at least `plugin-opsx` applied:

```bash
./gradlew opsx-install
```

### Additional agent plugins

Install only the agents you use:

```bash
# Each writes its own init script — mix and match
./gradlew copilot-install   # → 03-copilot.init.gradle.kts
./gradlew codex-install     # → 03-codex.init.gradle.kts
./gradlew opencode-install  # → 03-opencode.init.gradle.kts
```

### Init scripts explained

Scripts load in numeric order. Each plugin is independent — delete any init script to remove that plugin:

| File | Loads | Why this order |
|------|-------|----------------|
| `00-wrkx` | `WrkxPlugin` | Clones repos, wires `includeBuild()` — must run during settings eval first |
| `01-srcx` | `SrcxPlugin` | Registers discovery/analysis tasks on the projects wrkx set up |
| `02-opsx` | `OpsxPlugin` | Workflow + exec — `opsx-sync` depends on srcx tasks. Also applies linting. |
| `03-claude` | `ClaudePlugin` | Agent wrapper, independent |
| `03-copilot` | `CopilotPlugin` | Agent wrapper, independent |
| `03-codex` | `CodexPlugin` | Agent wrapper, independent |
| `03-opencode` | `OpencodePlugin` | Agent wrapper, independent |

### Multiple workspaces

Each workspace is just a directory with a `workspace.json`. The plugin reads the config from the project root — no global path. You can have as many workspaces as you want:

```
~/dev/work/workspace.json        ← work projects
~/dev/personal/workspace.json    ← personal projects
~/dev/oss/workspace.json         ← open source
```

### Uninstall

![Uninstall](art/uninstall-2026-04-02.gif)

```bash
bash install.sh --uninstall
```

### Manual init script

```kotlin
initscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-srcx:<version>")
        classpath("zone.clanker:plugin-opsx:<version>")
        classpath("zone.clanker:plugin-wrkx:<version>")
        classpath("zone.clanker:plugin-claude:<version>")
        classpath("zone.clanker:plugin-copilot:<version>")
        classpath("zone.clanker:plugin-codex:<version>")
        classpath("zone.clanker:plugin-opencode:<version>")
    }
}

apply<zone.clanker.srcx.SrcxPlugin>()
apply<zone.clanker.opsx.OpsxPlugin>()
apply<zone.clanker.claude.ClaudePlugin>()
apply<zone.clanker.copilot.CopilotPlugin>()
apply<zone.clanker.codex.CodexPlugin>()
apply<zone.clanker.opencode.OpencodePlugin>()
```

### Agent configuration

Set your preferred agent in `~/.gradle/gradle.properties` or per-project `gradle.properties`:

```properties
zone.clanker.opsx.agents=claude
```

| Value | Agent | Skills directory |
|---|---|---|
| `github` | GitHub Copilot | `.github/skills/` |
| `claude` | Claude Code | `.claude/skills/` |
| `codex` | OpenAI Codex | `.agents/skills/` |
| `opencode` | OpenCode | `.opencode/skills/` |

Combine agents: `github,claude`. Set `none` to disable.

## Configuration Reference

All properties go in `gradle.properties` (per-project or `~/.gradle/gradle.properties` for global).

### opsx — Workflow & Execution

| Property | Default | Values | Description |
|----------|---------|--------|-------------|
| `zone.clanker.opsx.agents` | `github` | `github`, `claude`, `codex`, `opencode`, comma-separated, or `none` | Which AI agents to generate instruction/skill files for. `github` is an alias for `github-copilot`. Set `none` to disable generation and clean existing files. Combine multiple: `github,claude`. |
| `zone.clanker.opsx.verifyCommand` | `assemble` | `assemble`, `build`, `compileKotlin`, or any Gradle task name | The Gradle task to run as a build gate when marking tasks done via `opsx-{code} --set=done`. If this task fails, the status stays IN_PROGRESS. |

### wrkx — Workspace Management

| Property | Default | Values | Description |
|----------|---------|--------|-------------|
| `zone.clanker.wrkx.configFile` | `workspace.json` in project root | Any path | Path to the JSON file that lists repos to include as composite builds. |
| `zone.clanker.wrkx.repoDir` | Parent directory (`../`) | Any directory path | Base directory where repos are cloned to. `wrkx-clone` puts repos here; `includeBuild()` reads from here. |
| `zone.clanker.wrkx.aggregate` | `true` | `true`, `false` | Whether to wire aggregate tasks (e.g., root `srcx-context` triggers `srcx-context` in all included builds). Set `false` if you only want tasks in the root project. |

### Example `gradle.properties`

```properties
# Generate skills for Claude and Copilot
zone.clanker.opsx.agents=claude,copilot

# Use 'build' (compile + test) as the verification gate
zone.clanker.opsx.verifyCommand=build

# Repos are cloned to ~/dev/workspace/
zone.clanker.wrkx.repoDir=/Users/me/dev/workspace

# Don't aggregate tasks across included builds
zone.clanker.wrkx.aggregate=false
```

## Version

The project version is derived from git tags (`git describe --tags --abbrev=0`). The runtime version is available via `VersionInfo.PLUGIN_VERSION` from `lib/core`, which reads from a `openspec-gradle.properties` resource file stamped at build time.

All modules share the same version. The group ID is `zone.clanker`.

## Plugin IDs

| Plugin ID | Implementation Class | Module |
|---|---|---|
| `zone.clanker.srcx` | `zone.clanker.srcx.SrcxPlugin` | `plugin/srcx` |
| `zone.clanker.opsx` | `zone.clanker.opsx.OpsxPlugin` | `plugin/opsx` |
| `zone.clanker.wrkx` | `zone.clanker.wrkx.WrkxPlugin` | `plugin/wrkx` |
| `zone.clanker.claude` | `zone.clanker.claude.ClaudePlugin` | `plugin/claude` |
| `zone.clanker.copilot` | `zone.clanker.copilot.CopilotPlugin` | `plugin/copilot` |
| `zone.clanker.codex` | `zone.clanker.codex.CodexPlugin` | `plugin/codex` |
| `zone.clanker.opencode` | `zone.clanker.opencode.OpencodePlugin` | `plugin/opencode` |

All plugins are **Settings plugins** (applied in `settings.gradle.kts`, not `build.gradle.kts`).

## Development Setup

After cloning the repo, install the shared git hooks:

```bash
curl -fsSL https://raw.githubusercontent.com/ClankerGuru/git-hooks/main/install.sh | bash
```

This installs a pre-commit hook that runs `./gradlew build` before every commit and a pre-push hook that prevents direct pushes to `main`.

## License

[MIT](LICENSE)
