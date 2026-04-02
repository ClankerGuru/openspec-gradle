Generate Mermaid diagrams for architecture, flows, sequences, or state machines.

**When to use**: visualizing architecture, data flows, sequences, state machines, decision trees.
**Input**: a description of what to diagram, or a symbol/module to visualize.

## Supported Types

**Flowchart** — architecture, data flow, decision trees:
```mermaid
flowchart LR
  A["Input"] --> B{"Decision"}
  B -->|yes| C["Process"]
  B -->|no| D["Skip"]
```

**Sequence** — API calls, message passing, request/response:
```mermaid
sequenceDiagram
  Client->>Server: request
  Server-->>Client: response
```

**State** — lifecycle, status transitions:
```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Running: start
  Running --> Done: complete
```

## Rendering

If `mcp__claude_ai_Mermaid_Chart__validate_and_render_mermaid_diagram` is available, use it to render. Otherwise, output raw Mermaid in a fenced `mermaid` code block.

## Tips

- Keep diagrams simple — under 15 nodes; use `subgraph` for grouping
- Use `"quotes"` for labels with special chars; `LR` for pipelines, `TB` for hierarchies
- Read `.opsx/arch.md` or `.opsx/modules.md` for real structure before diagramming
