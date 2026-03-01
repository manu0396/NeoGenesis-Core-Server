# Protocol DSL (v1)

Protocols can be represented as a DAG (directed acyclic graph) with typed nodes and parameters. The server validates v1 DSL on publish and wraps legacy JSON in a DSL container for backward compatibility.

## DSL Shape
```json
{
  "dslVersion": "1",
  "capabilities": ["pressure", "thermal", "imaging"],
  "metadata": { "title": "Example Protocol" },
  "graph": {
    "nodes": [
      { "id": "n1", "type": "extrude", "params": { "pressureKpa": 90, "durationMs": 1200 } },
      { "id": "n2", "type": "incubate", "params": { "temperatureC": 37, "durationMs": 3000 } },
      { "id": "n3", "type": "scan", "params": { "resolution": "high" } }
    ],
    "edges": [
      { "from": "n1", "to": "n2" },
      { "from": "n2", "to": "n3" }
    ]
  }
}
```

## Validation Rules
- No cycles in the graph.
- All edge endpoints must reference existing nodes.
- Required params per step:
  - `extrude`: `pressureKpa`, `durationMs`
  - `incubate`: `temperatureC`, `durationMs`
  - `sterilize`: `temperatureC`, `durationMs`
  - `scan`: `resolution`
- Required capabilities:
  - `extrude` -> `pressure`
  - `incubate`/`sterilize` -> `thermal`
  - `scan` -> `imaging`
- Unsafe steps:
  - `override_safety` is rejected.
  - `temperatureC > 60` for `incubate`/`sterilize` is rejected.

## Validator Examples
Invalid (cycle):
```json
{
  "dslVersion": "1",
  "graph": {
    "nodes": [
      { "id": "a", "type": "extrude", "params": { "pressureKpa": 90, "durationMs": 1000 } }
    ],
    "edges": [
      { "from": "a", "to": "a" }
    ]
  }
}
```

Invalid (missing param):
```json
{
  "dslVersion": "1",
  "graph": {
    "nodes": [
      { "id": "a", "type": "incubate", "params": { "durationMs": 1000 } }
    ],
    "edges": []
  }
}
```

## Compatibility Notes
- Existing protocol JSON is treated as `dslVersion: "legacy"` and **will not fail** validation.
- On publish, legacy JSON is stored as-is to avoid breaking gateways that expect the original shape.
- The DSL wrapper is available for new clients and tools; legacy content can be wrapped without altering the original payload.
