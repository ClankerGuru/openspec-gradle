Generate Mermaid diagrams for architecture, flows, sequences, or state machines.

## Supported Types

**Flowchart** -- architecture, data flow, decision trees:
```mermaid
flowchart LR
  A["Input"] --> B{"Decision"}
  B -->|yes| C["Process"]
  B -->|no| D["Skip"]
```

**Sequence** -- API calls, message passing:
```mermaid
sequenceDiagram
  Client->>Server: request
  Server-->>Client: response
```

**State** -- lifecycle, status transitions:
```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Running: start
  Running --> Done: complete
```

## Rendering

If `mcp__claude_ai_Mermaid_Chart__validate_and_render_mermaid_diagram` is available, use it. Otherwise, output raw Mermaid in a fenced block.

## Tips

- Under 15 nodes; use `subgraph` for grouping
- `"quotes"` for labels with special chars; `LR` for pipelines, `TB` for hierarchies
- Read `.opsx/arch.md` or `.opsx/modules.md` for real structure before diagramming
