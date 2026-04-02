#!/usr/bin/env bash
# OpenSpec Gradle — one-line installer
#
# Usage:
#   bash install.sh                              Interactive (default)
#   bash install.sh --agents copilot,claude       Install specific agents
#   bash install.sh --all                         Install everything
#   bash install.sh --core                        Core only (srcx + opsx + wrkx)
#   bash install.sh --core --agents copilot       Core + specific agents
#   OPENSPEC_COMPONENTS=copilot curl ... | bash   Env var (piped, non-interactive)
#
# To uninstall:
#   bash install.sh --uninstall

set -euo pipefail

# Resolve version: env var > latest GitHub release > fallback
if [ -n "${OPENSPEC_VERSION:-}" ]; then
    VERSION="$OPENSPEC_VERSION"
else
    VERSION=$(curl -fsSL https://api.github.com/repos/ClankerGuru/openspec-gradle/releases/latest 2>/dev/null \
        | sed -n 's/.*"tag_name": *"v\{0,1\}\([0-9.]*\)".*/\1/p' || true)
    VERSION="${VERSION:-0.35.0}"
fi
INIT_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/init.d"
PROPS_FILE="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

mkdir -p "$INIT_DIR"

echo ""
echo "  OpenSpec Gradle v${VERSION}"
echo "  ─────────────────────────────"
echo ""

# ── Component writers ──

write_wrkx() {
    cat > "$INIT_DIR/00-wrkx.init.gradle.kts" << WRKX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-wrkx:$VERSION")
        classpath("zone.clanker:wrkx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.wrkx.WrkxPlugin>() }
WRKX
}

write_srcx() {
    cat > "$INIT_DIR/01-srcx.init.gradle.kts" << SRCX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-srcx:$VERSION")
        classpath("zone.clanker:srcx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
        classpath("zone.clanker:openspec-psi:$VERSION")
        classpath("zone.clanker:openspec-arch:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.srcx.SrcxPlugin>() }
SRCX
}

write_opsx() {
    cat > "$INIT_DIR/02-opsx.init.gradle.kts" << OPSX
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:plugin-opsx:$VERSION")
        classpath("zone.clanker:opsx-tasks:$VERSION")
        classpath("zone.clanker:openspec-core:$VERSION")
        classpath("zone.clanker:openspec-exec:$VERSION")
        classpath("zone.clanker:openspec-generators:$VERSION")
        classpath("zone.clanker:openspec-adapter-claude:$VERSION")
        classpath("zone.clanker:openspec-adapter-copilot:$VERSION")
        classpath("zone.clanker:openspec-adapter-codex:$VERSION")
        classpath("zone.clanker:openspec-adapter-opencode:$VERSION")
    }
}
beforeSettings { apply<zone.clanker.opsx.OpsxPlugin>() }
OPSX
}

write_quality() {
    cat > "$INIT_DIR/02-quality.init.gradle.kts" << QUALITY
// Linting: detekt + ktlint for Kotlin projects
// Disable per-project: zone.clanker.quality.enabled=false in gradle.properties
// Disable globally: -Dzone.clanker.quality.enabled=false
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:quality:$VERSION")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    }
}
allprojects {
    afterEvaluate {
        // Check both new and legacy property names, both system props and project props
        // Check: system property, project property, env var
        // Note: for workspaces with included builds, use ~/.gradle/gradle.properties
        // or -D flag — per-project gradle.properties won't propagate to included builds
        val disabled = System.getProperty("zone.clanker.quality.enabled")?.lowercase() == "false" ||
            findProperty("zone.clanker.quality.enabled")?.toString()?.lowercase() == "false" ||
            System.getenv("OPENSPEC_QUALITY_ENABLED")?.lowercase() == "false" ||
            System.getProperty("openspec.linting.enabled")?.lowercase() == "false" ||
            findProperty("openspec.linting.enabled")?.toString()?.lowercase() == "false"
        if (disabled) return@afterEvaluate
        val isKotlinProject = plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
            plugins.hasPlugin("org.jetbrains.kotlin.android") ||
            plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        if (isKotlinProject) {
            apply<zone.clanker.gradle.linting.OpenSpecLintingPlugin>()
        }
    }
}
QUALITY
}

write_agent() {
    local artifact="$1" class="$2" file="$3"
    cat > "$INIT_DIR/$file" << AGENT
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("zone.clanker:$artifact:$VERSION")
    }
}
beforeSettings { apply<$class>() }
AGENT
}

# ── Normalize input ──
normalize_selection() {
    local input="$1"
    case "$input" in
        9|all)  echo "wrkx,srcx,opsx,quality,claude,copilot,codex,opencode" ;;
        none)   echo "none" ;;
        1)      echo "srcx" ;;
        2)      echo "opsx" ;;
        3)      echo "wrkx" ;;
        4)      echo "quality" ;;
        5)      echo "claude" ;;
        6)      echo "copilot" ;;
        7)      echo "codex" ;;
        8)      echo "opencode" ;;
        *)      echo "$input" ;;
    esac
}

# ── Parse CLI args ──
CLI_CORE=false
CLI_ALL=false
CLI_AGENTS=""
CLI_UNINSTALL=false

while [ $# -gt 0 ]; do
    case "$1" in
        --all)        CLI_ALL=true ;;
        --core)       CLI_CORE=true ;;
        --uninstall)  CLI_UNINSTALL=true ;;
        --agents)     shift; if [ $# -eq 0 ]; then echo "Error: --agents requires a value"; exit 1; fi; CLI_AGENTS="$1" ;;
        --agents=*)   CLI_AGENTS="${1#--agents=}" ;;
        --help|-h)
            echo "Usage:"
            echo "  bash install.sh                        Interactive (default)"
            echo "  bash install.sh --agents copilot       Install core + specific agents"
            echo "  bash install.sh --all                  Install everything"
            echo "  bash install.sh --core                 Core only (srcx + opsx + wrkx)"
            echo "  bash install.sh --core --agents claude Core + Claude"
            echo "  bash install.sh --uninstall            Remove all init scripts"
            exit 0 ;;
        *)
            # Treat bare args as agent names: bash install.sh copilot claude
            CLI_AGENTS="${CLI_AGENTS:+$CLI_AGENTS,}$1" ;;
    esac
    shift
done

if [ "$CLI_UNINSTALL" = true ]; then
    echo "  Removing OpenSpec init scripts..."
    for f in "$INIT_DIR"/00-wrkx.init.gradle.kts \
             "$INIT_DIR"/01-srcx.init.gradle.kts \
             "$INIT_DIR"/02-opsx.init.gradle.kts \
             "$INIT_DIR"/02-quality.init.gradle.kts \
             "$INIT_DIR"/03-claude.init.gradle.kts \
             "$INIT_DIR"/03-copilot.init.gradle.kts \
             "$INIT_DIR"/03-codex.init.gradle.kts \
             "$INIT_DIR"/03-opencode.init.gradle.kts; do
        [ -f "$f" ] && rm -f "$f" && echo "  Removed $(basename "$f")"
    done
    echo "  Done."
    exit 0
fi

# ── Selection ──
if [ "$CLI_ALL" = true ]; then
    SELECTED="wrkx,srcx,opsx,quality,claude,copilot,codex,opencode"
elif [ "$CLI_CORE" = true ] || [ -n "$CLI_AGENTS" ]; then
    SELECTED="wrkx,srcx,opsx"
    if [ -n "$CLI_AGENTS" ]; then
        SELECTED="$SELECTED,$CLI_AGENTS"
    fi
elif [ -n "${OPENSPEC_COMPONENTS:-}" ]; then
    SELECTED=$(normalize_selection "$OPENSPEC_COMPONENTS")
elif [ -t 0 ] || [ -e /dev/tty ]; then
    # Interactive prompt — works both directly and via curl | bash (reads from /dev/tty)
    echo "  What would you like to install?"
    echo ""
    echo "  Core plugins (recommended):"
    echo "    srcx      Source intelligence — discovery, analysis, refactoring"
    echo "    opsx      Workflow engine — proposals, exec, agent sync"
    echo "    wrkx      Workspace — multi-repo composite builds"
    echo "    quality   Linting — detekt + ktlint for Kotlin projects"
    echo ""
    echo "  Agent wrappers (install the ones you use):"
    echo "    claude    Claude Code CLI"
    echo "    copilot   GitHub Copilot CLI"
    echo "    codex     OpenAI Codex CLI"
    echo "    opencode  OpenCode CLI"
    echo ""
    echo "  Shortcuts:"
    echo "    all       Install everything"
    echo "    core      Just srcx + opsx + wrkx (no agents)"
    echo ""
    printf "  Enter components (comma-separated): "
    read -r CHOICE < /dev/tty
    case "$CHOICE" in
        all)  SELECTED="wrkx,srcx,opsx,quality,claude,copilot,codex,opencode" ;;
        core) SELECTED="wrkx,srcx,opsx" ;;
        *)    SELECTED="$CHOICE" ;;
    esac
else
    # No terminal at all (CI, Docker, etc.) — install core + claude + copilot
    SELECTED="wrkx,srcx,opsx,claude,copilot"
fi

if [ "$SELECTED" = "none" ]; then
    echo "  No components selected. Nothing to install."
    exit 0
fi

# ── Clean previous install (only remove OUR init scripts, not others) ──
for f in "$INIT_DIR"/00-wrkx.init.gradle.kts \
         "$INIT_DIR"/01-srcx.init.gradle.kts \
         "$INIT_DIR"/02-opsx.init.gradle.kts \
         "$INIT_DIR"/02-quality.init.gradle.kts \
         "$INIT_DIR"/03-claude.init.gradle.kts \
         "$INIT_DIR"/03-copilot.init.gradle.kts \
         "$INIT_DIR"/03-codex.init.gradle.kts \
         "$INIT_DIR"/03-opencode.init.gradle.kts; do
    [ -f "$f" ] && rm -f "$f"
done

echo ""
echo "  Installing..."

INSTALLED_AGENTS=""

IFS=',' read -ra COMPONENTS <<< "$SELECTED"
for comp in "${COMPONENTS[@]}"; do
    comp=$(echo "$comp" | xargs)
    # Normalize numeric selections to component names
    case "$comp" in
        1) comp="srcx" ;; 2) comp="opsx" ;; 3) comp="wrkx" ;; 4) comp="quality" ;;
        5) comp="claude" ;; 6) comp="copilot" ;; 7) comp="codex" ;; 8) comp="opencode" ;;
    esac
    case "$comp" in
        srcx)
            write_srcx
            echo "  [01] srcx — source intelligence"
            ;;
        opsx)
            write_opsx
            echo "  [02] opsx — workflow engine"
            ;;
        wrkx)
            write_wrkx
            echo "  [00] wrkx — workspace management"
            ;;
        quality)
            write_quality
            echo "  [02] quality — linting (detekt + ktlint)"
            ;;
        claude)
            write_agent "plugin-claude" "zone.clanker.claude.ClaudePlugin" "03-claude.init.gradle.kts"
            echo "  [03] claude — Claude Code CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}claude"
            ;;
        copilot|github|github-copilot)
            write_agent "plugin-copilot" "zone.clanker.copilot.CopilotPlugin" "03-copilot.init.gradle.kts"
            echo "  [03] copilot — GitHub Copilot CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}copilot"
            ;;
        codex)
            write_agent "plugin-codex" "zone.clanker.codex.CodexPlugin" "03-codex.init.gradle.kts"
            echo "  [03] codex — OpenAI Codex CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}codex"
            ;;
        opencode)
            write_agent "plugin-opencode" "zone.clanker.opencode.OpencodePlugin" "03-opencode.init.gradle.kts"
            echo "  [03] opencode — OpenCode CLI"
            INSTALLED_AGENTS="${INSTALLED_AGENTS:+$INSTALLED_AGENTS,}opencode"
            ;;
        *)
            echo "  Unknown component: $comp (skipping)"
            ;;
    esac
done

# ── Set agent config if opsx installed ──
if [ -f "$INIT_DIR/02-opsx.init.gradle.kts" ]; then
    AGENTS_VALUE="${INSTALLED_AGENTS:-none}"
    if [ -f "$PROPS_FILE" ] && grep -q "zone.clanker.opsx.agents" "$PROPS_FILE" 2>/dev/null; then
        sed -i.bak "s/zone.clanker.opsx.agents=.*/zone.clanker.opsx.agents=$AGENTS_VALUE/" "$PROPS_FILE"
        rm -f "${PROPS_FILE}.bak"
    elif [ -f "$PROPS_FILE" ]; then
        echo "" >> "$PROPS_FILE"
        echo "zone.clanker.opsx.agents=$AGENTS_VALUE" >> "$PROPS_FILE"
    else
        echo "zone.clanker.opsx.agents=$AGENTS_VALUE" > "$PROPS_FILE"
    fi
fi

# ── Remove legacy init script ──
rm -f "$INIT_DIR/openspec.init.gradle.kts" "$INIT_DIR/openspec.init.gradle.kts.bak"

# ── Create ~/.clkx/ directory structure ──
CLKX_DIR="$HOME/.clkx"
echo ""
echo "  Setting up ~/.clkx/ shared directory..."

mkdir -p "$CLKX_DIR/skills/claude"
mkdir -p "$CLKX_DIR/skills/copilot"
mkdir -p "$CLKX_DIR/skills/codex"
mkdir -p "$CLKX_DIR/skills/opencode"
mkdir -p "$CLKX_DIR/instructions"
echo "  [ok] ~/.clkx/ directory structure created"
echo "       Run 'opsx-sync --global' in any Gradle project to populate skills."

# ── Configure Claude ~/.claude/settings.json with additionalDirs ──
CLAUDE_SETTINGS="$HOME/.claude/settings.json"
if echo "$INSTALLED_AGENTS" | grep -q "claude"; then
    mkdir -p "$HOME/.claude"
    if [ -f "$CLAUDE_SETTINGS" ]; then
        # Check if additionalDirs already contains ~/.clkx
        if command -v jq &>/dev/null; then
            if ! jq -e '.additionalDirs // [] | index("~/.clkx")' "$CLAUDE_SETTINGS" &>/dev/null; then
                # Add ~/.clkx to additionalDirs array (create array if absent)
                jq '.additionalDirs = ((.additionalDirs // []) + ["~/.clkx"] | unique)' \
                    "$CLAUDE_SETTINGS" > "${CLAUDE_SETTINGS}.tmp" && \
                    mv "${CLAUDE_SETTINGS}.tmp" "$CLAUDE_SETTINGS"
                echo "  [ok] Added ~/.clkx to Claude additionalDirs"
            else
                echo "  [ok] Claude additionalDirs already includes ~/.clkx"
            fi
        else
            # Fallback without jq: check if already present, append if not
            if ! grep -q '"~/.clkx"' "$CLAUDE_SETTINGS" 2>/dev/null; then
                echo "  [!!] Install jq to auto-configure Claude additionalDirs, or manually add:"
                echo "       \"additionalDirs\": [\"~/.clkx\"] to $CLAUDE_SETTINGS"
            else
                echo "  [ok] Claude additionalDirs already includes ~/.clkx"
            fi
        fi
    else
        # Create a fresh settings.json
        cat > "$CLAUDE_SETTINGS" << 'CLAUDE_JSON'
{
  "additionalDirs": ["~/.clkx"]
}
CLAUDE_JSON
        echo "  [ok] Created $CLAUDE_SETTINGS with additionalDirs"
    fi
fi

# ── Create ~/.codex/skills symlink → ~/.clkx/skills/codex/ ──
if echo "$INSTALLED_AGENTS" | grep -q "codex"; then
    CODEX_SKILLS="$HOME/.codex/skills"
    mkdir -p "$HOME/.codex"
    if [ -L "$CODEX_SKILLS" ]; then
        EXISTING_TARGET=$(readlink "$CODEX_SKILLS")
        if [ "$EXISTING_TARGET" = "$CLKX_DIR/skills/codex" ] || [ "$EXISTING_TARGET" = "$CLKX_DIR/skills/codex/" ]; then
            echo "  [ok] ~/.codex/skills already linked to ~/.clkx/skills/codex/"
        else
            rm -f "$CODEX_SKILLS"
            ln -s "$CLKX_DIR/skills/codex" "$CODEX_SKILLS"
            echo "  [ok] Updated ~/.codex/skills symlink → ~/.clkx/skills/codex/"
        fi
    elif [ -d "$CODEX_SKILLS" ]; then
        echo "  [--] ~/.codex/skills/ is a real directory — skipping symlink"
    else
        ln -s "$CLKX_DIR/skills/codex" "$CODEX_SKILLS"
        echo "  [ok] Created ~/.codex/skills → ~/.clkx/skills/codex/"
    fi
fi

# ── Ensure global gitignore has OpenSpec patterns ──
resolve_global_gitignore() {
    local configured
    configured=$(git config --global core.excludesFile 2>/dev/null || true)
    if [ -n "$configured" ]; then
        # Expand ~ if present
        echo "${configured/#\~/$HOME}"
    else
        # Set XDG default and register with git
        local fallback="$HOME/.config/git/ignore"
        git config --global core.excludesFile "$fallback" 2>/dev/null || true
        echo "$fallback"
    fi
}

GLOBAL_GITIGNORE=$(resolve_global_gitignore)
GITIGNORE_PATTERNS=(
    ".opsx/"
    ".agents/skills/opsx-*"
    ".agents/skills/srcx-*"
    ".opencode/skills/opsx-*"
    ".opencode/skills/srcx-*"
    ".claude/skills/opsx-*"
    ".claude/skills/srcx-*"
)

mkdir -p "$(dirname "$GLOBAL_GITIGNORE")"
touch "$GLOBAL_GITIGNORE"
ADDED_PATTERNS=0
for pattern in "${GITIGNORE_PATTERNS[@]}"; do
    if ! grep -qxF "$pattern" "$GLOBAL_GITIGNORE" 2>/dev/null; then
        if [ "$ADDED_PATTERNS" -eq 0 ]; then
            echo "" >> "$GLOBAL_GITIGNORE"
            echo "# OpenSpec generated files (managed by openspec-gradle plugin)" >> "$GLOBAL_GITIGNORE"
        fi
        echo "$pattern" >> "$GLOBAL_GITIGNORE"
        ADDED_PATTERNS=$((ADDED_PATTERNS + 1))
    fi
done
if [ "$ADDED_PATTERNS" -gt 0 ]; then
    echo "  [ok] Added $ADDED_PATTERNS patterns to global gitignore ($GLOBAL_GITIGNORE)"
else
    echo "  [ok] Global gitignore already up to date ($GLOBAL_GITIGNORE)"
fi

echo ""
echo "  Done! OpenSpec v${VERSION}"
echo ""
echo "  Init scripts:"
ls -1 "$INIT_DIR"/0*.init.gradle.kts 2>/dev/null | while read -r f; do echo "    $(basename "$f")"; done
echo ""
echo "  Run in any Gradle project:"
[ -f "$INIT_DIR/01-srcx.init.gradle.kts" ] && echo "    ./gradlew srcx       # source intelligence"
[ -f "$INIT_DIR/02-opsx.init.gradle.kts" ] && echo "    ./gradlew opsx       # workflow engine"
[ -f "$INIT_DIR/00-wrkx.init.gradle.kts" ] && echo "    ./gradlew wrkx       # workspace management"
[ -f "$INIT_DIR/03-claude.init.gradle.kts" ] && echo "    ./gradlew claude     # Claude Code CLI"
[ -f "$INIT_DIR/03-copilot.init.gradle.kts" ] && echo "    ./gradlew copilot    # GitHub Copilot CLI"
[ -f "$INIT_DIR/03-codex.init.gradle.kts" ] && echo "    ./gradlew codex      # OpenAI Codex CLI"
[ -f "$INIT_DIR/03-opencode.init.gradle.kts" ] && echo "    ./gradlew opencode   # OpenCode CLI"
echo ""
echo "  To uninstall: rm $INIT_DIR/0*-*.init.gradle.kts"
