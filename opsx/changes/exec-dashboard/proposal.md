# Proposal: exec-dashboard

## Summary

Add a live execution dashboard to `opsx-status` that renders the current state of running `opsx-exec` task chains. When agents are running, show a visual table with task states, agents, durations, and a progress bar.

## Motivation

When `opsx-exec` runs a task chain (sequential or parallel), the user has no way to see what's happening. Agent subprocesses produce output that gets buffered by Gradle or interleaved. There's no quick way to check how many agents are spawned, which are running, or how far along execution is.

The data already exists — `ExecStatus` writes live state to `.opsx/exec/status.json` during execution — but nothing reads it in a user-friendly way.

## Scope

**In scope:**
- Read and render `status.json` in `StatusTask` when it exists
- Enrich `ExecStatus` with missing fields (PID, parallel thread count)
- Visual table with icons, progress bar, elapsed times
- `ExecStatusReader` in `:exec` module for parsing

**Out of scope:**
- Live auto-refreshing terminal UI
- Log tailing or streaming output
- Changes to how agents are spawned
