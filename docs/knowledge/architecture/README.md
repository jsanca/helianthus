# Architecture

Current system structure, module boundaries, and runtime relationships for the Helianthus server.

## Scope

Durable, evidence-based architecture of the `server/` Kotlin codebase. Diagrams here are derived from current source, current KDoc, and current Maven structure — not from historical documentation or assumptions.

## Contents

| File | Subject | Type |
| --- | --- | --- |
| [`diagrams/modules.md`](./diagrams/modules.md) | High-level module diagram (`web → runtime → core`) | `flowchart` |
| [`diagrams/classes-core.md`](./diagrams/classes-core.md) | `helianthus-core` — result model, data-access contracts, JDBC impl | `classDiagram` |
| [`diagrams/classes-runtime.md`](./diagrams/classes-runtime.md) | `helianthus-runtime` — runtime facade, pipeline, catalog, security, entity service | `classDiagram` |
| [`diagrams/classes-web.md`](./diagrams/classes-web.md) | `helianthus-web` — Spring adapter layer | `classDiagram` |
| [`diagrams/workflow-catalog.md`](./diagrams/workflow-catalog.md) | Catalog load flow: `operations.yml` → runtime availability | `flowchart` |
| [`diagrams/workflow-operation.md`](./diagrams/workflow-operation.md) | Operation execution stages from HTTP request to response | `flowchart` |
| [`diagrams/sequence-operation.md`](./diagrams/sequence-operation.md) | Detailed method-level sequence of a successful operation execution | `sequenceDiagram` |

## Visibility notation

Diagrams use the following `<<stereotype>>` annotations consistent with `HEL-KOTLIN-006`:

| Stereotype | Meaning |
| --- | --- |
| `<<public>>` | Supported public API (visible outside the declaring module) |
| `<<contract>>` | Cross-module data-access contract (`GenericDataAccess`, `SqlDialect`, `NamedParameterSql`, `DataAccessFactory`) |
| `<<internal>>` | Implementation detail not visible outside the declaring module |

`internal` types are intentionally not surfaced as consumers of any diagram; they appear only where they materially explain architecture.

## Provenance

These diagrams were produced from source-code evidence. See [`../../engineering/agents/reports/HEL-DOC-001.md`](../../engineering/agents/reports/HEL-DOC-001.md) for the verification log, evidence sources, and any `DIAGRAM_FINDING`s raised during diagramming.
