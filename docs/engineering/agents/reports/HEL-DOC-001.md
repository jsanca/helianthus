# HEL-DOC-001 — Architecture Diagrams

- **Date:** 2026-09-22
- **Role:** Mini — Documentation / Architecture Visualization
- **Status:** Complete. All seven diagrams created from current source/KDoc evidence and rendered successfully via Mermaid CLI.

---

## 1. Files created

```
docs/knowledge/architecture/README.md
docs/knowledge/architecture/diagrams/modules.md
docs/knowledge/architecture/diagrams/classes-core.md
docs/knowledge/architecture/diagrams/classes-runtime.md
docs/knowledge/architecture/diagrams/classes-web.md
docs/knowledge/architecture/diagrams/workflow-catalog.md
docs/knowledge/architecture/diagrams/workflow-operation.md
docs/knowledge/architecture/diagrams/sequence-operation.md
docs/engineering/agents/reports/HEL-DOC-001.md   (this file)
```

No production code was modified.

---

## 2. Source / classes inspected

### Module ownership and dependency direction

- `server/pom.xml` (parent) — declares `<modules>helianthus, helianthus-runtime, helianthus-web</modules>`.
- `server/helianthus/pom.xml` — depends on no sibling Helianthus modules.
- `server/helianthus-runtime/pom.xml` — depends only on `helianthus:helianthus-core`.
- `server/helianthus-web/pom.xml` — depends only on `helianthus:helianthus-runtime`.
- Direction confirmed: `web → runtime → core`, acyclic, no inverse edges.

### Visibility inventory

Verified by inspecting every Kotlin source file in `server/{helianthus,helianthus-runtime,helianthus-web}/src/main/kotlin/`. Counts:

| Module | `public` types | `internal` types |
| --- | --- | --- |
| `helianthus-core` | 16 (result model + data-access contracts + `DataAccessFactory` + 2 public exceptions) | 6 (all under `access/impl/db/` plus `DataAccessErrorException`) |
| `helianthus-runtime` | 19 (`HelianthusRuntime` facade + `HelianthusRuntimeBuilder` + `Principal` + `CatalogSummary` DTOs + `EntityListRequest` + 4 public exceptions + path handlers + `PathMappingResultBean`) | 47 (catalog internals + pipeline + operators + evaluators + service + models) |
| `helianthus-web` | 15 (Spring `@Configuration`/`@Controller`/`@Component` classes — none internal because Spring needs to instantiate them by name) | 0 |

`HelianthusRuntime` is `public class ... internal constructor` — class visibility is `public` while constructor visibility is `internal`, forcing consumers to use `HelianthusRuntimeBuilder`. This is the factory-enforcement pattern.

### Cross-module dependencies

Every `import helianthus.core.*` line in `helianthus-web` was enumerated. The web module imports ONLY public types from runtime/core:

- From `helianthus-runtime` (public): `HelianthusRuntime`, `HelianthusRuntimeBuilder`, `CatalogSummary`, `EntityListRequest`, `PathHandler`, `EntityPathHandler`, `AccessDeniedException`.
- From `helianthus-core` (public): `ResultFrame`, `ColumnNameResolver`, `EntityNotFoundException`, `InvalidParameterException`, `NoMappingException`.
- Local to `helianthus-web`: `toPrincipal` extension function and the `web/converter` package.

No `internal` type is referenced from web source.

### Specific source verifications for diagram accuracy

| Diagram claim | Source location verified |
| --- | --- |
| `web → runtime → core` dependency direction | `server/{helianthus,helianthus-runtime,helianthus-web}/pom.xml` |
| `HelianthusRuntimeBuilder.build(...)` ordering | `HelianthusRuntimeBuilder.kt:30–56` |
| Pipeline step order (Resolve, Bind, Query, Project, Filter, Limit, ToResultFrame) | `pipeline/PipelineFactory.kt:30–38` |
| `executeOperation` always sets `format = "json"` | `HelianthusRuntime.kt:68` |
| `OperationPermissionEvaluator` consults `OperationCatalog` + `Principal`, ADMIN bypasses | `security/OperationPermissionEvaluator.kt:11–48` |
| `EntityPermissionEvaluator` consults `EntityCatalog` + `Principal` | `security/EntityPermissionEvaluator.kt:11–50` |
| `CatalogLoader.load(...)` ordering | `catalog/CatalogLoader.kt:34–55` |
| Schema validation path: `classpath:schemas/operations.schema.json` | `catalog/CatalogLoader.kt:67–68` |
| `CatalogConfig` is the single `@Bean` calling `HelianthusRuntimeBuilder.build(...)` | `config/CatalogConfig.kt:28–35` |
| Three `ResultFrame*MessageConverter`s registered ahead of Jackson | `config/HelianthusWebConfiguration.kt:13–17` |
| `PathHandler` parses `/api/op/{op}[/{cfg}].{fmt}` | `util/PathHandler.kt:36–95` |
| `EntityPathHandler` parses `/api/entities/{name}[/{id}].{fmt}` | `util/EntityPathHandler.kt:23–63` |
| `DataAccessFactory.jdbc` / `.sqlDialects` are the cross-module seam | `access/DataAccessFactory.kt:21–34` |
| `ToResultFrameStep.process` closes the stream via `stream.use { ... }` | `pipeline/ToResultFrameStep.kt:21–32` |
| `HelianthusRuntime` re-throws `context.error` after pipeline returns | `HelianthusRuntime.kt:75–77` |
| `HelianthusExceptionHandler` maps exceptions to HTTP status codes | `web/HelianthusExceptionHandler.kt:35–119` |

---

## 3. Diagram summary

| # | Diagram | Type | Mermaid block |
| --- | --- | --- | --- |
| 1 | Module diagram | `flowchart` | `modules.md` |
| 2 | Core class diagram | `classDiagram` | `classes-core.md` |
| 3 | Runtime class diagram | `classDiagram` | `classes-runtime.md` |
| 4 | Web class diagram | `classDiagram` | `classes-web.md` |
| 5 | Catalog workflow | `flowchart` | `workflow-catalog.md` |
| 6 | Operation workflow | `flowchart` | `workflow-operation.md` |
| 7 | Operation sequence | `sequenceDiagram` (+ alt branch) | `sequence-operation.md` |

Visibility notation: `<<public>>`, `<<contract>>`, `<<internal>>` are applied consistently across all class diagrams.

Filter operators (`EqOperator`, `NeqOperator`, `GtOperator`, `GteOperator`, `LtOperator`, `LteOperator`, `InOperator`) are grouped into a single `FilterOperators` class in the runtime diagram because they share one interface and one registry — seven near-identical boxes added no information.

Catalog `*Def` models (`OperationDef`, `ParameterDef`, `ConfigurationDef`, `EntityDef`, `FieldDef`, `PrimaryKeyDef`, `EntitySecurityDef`, `EntityRoleDef`, `SecurityDef`, `QueryDef`, `DatasourceDef`, `AppMetadata`, `ResolvedCatalogEntry`) are grouped under a single `CatalogDefinitions` note in the runtime diagram — they are catalog data carriers, not collaborators.

---

## 4. Mermaid validation

Tool: `@mermaid-js/mermaid-cli` (puppeteer + mermaid 11.x).

Each `mermaid` code block was extracted from the seven documents and rendered to SVG. Result:

```
OK block_01.mmd (124,984 bytes)   classes-core.md
OK block_02.mmd (200,090 bytes)   classes-runtime.md
OK block_03.mmd (141,875 bytes)   classes-web.md
OK block_04.mmd ( 19,808 bytes)   modules.md
OK block_05.mmd ( 71,421 bytes)   sequence-operation.md (main)
OK block_06.mmd ( 27,700 bytes)   sequence-operation.md (alt branch)
OK block_07.mmd ( 58,859 bytes)   workflow-catalog.md
OK block_08.mmd ( 47,987 bytes)   workflow-operation.md
```

All 8 blocks render successfully.

Two rendering issues were found and fixed during validation:

1. `class EqOperator~Neq~Gt~Gte~Lt~Lte~In { ... }` — Mermaid's `classDiagram` does not accept `~` inside class names. Renamed to a single grouped class `FilterOperators` with explanatory prose in the diagram caption.
2. `class "Eq / Neq / Gt / Gte<br/>/ Lt / Lte / In" as FilterOperators` — angle brackets inside quoted class names are also rejected. Simplified the label and dropped the alias.

---

## 5. DIAGRAM_FINDINGs

### DIAGRAM_FINDING 1 — Runtime hardcodes `format = "json"` in `OperationRequest`

- **diagram:** operation sequence diagram (`sequence-operation.md`); also implicit in operation workflow.
- **observed relationship:** `HelianthusRuntime.executeOperation(...)` always constructs `OperationRequest(..., format = "json", ...)`. The path's format extension (`{json,html,csv,xml}`) is consumed by the web controller to pick a `MediaType` for the `ResultFrame` body and is never propagated to the runtime.
- **expected boundary:** Either (a) the runtime is genuinely format-agnostic and the format selection belongs entirely to the adapter, or (b) the runtime accepts format as a parameter. Today it is (a) silently — the runtime accepts no format but its `OperationRequest` carries one anyway.
- **source evidence:** `server/helianthus-runtime/src/main/kotlin/helianthus/core/HelianthusRuntime.kt:65–70`.
- **impact:** A non-HTTP adapter (CLI, embedded use, gRPC) that calls `HelianthusRuntime.executeOperation` directly has no way to influence the `format` field that ends up on `OperationRequest`. The value is never consulted by the pipeline, so the practical impact is currently zero; but the surface area is misleading.
- **status:** Logged; not fixed in this task.

### DIAGRAM_FINDING 2 — Catalog schema validation swallows non-`IllegalStateException`

- **diagram:** catalog workflow (`workflow-catalog.md`).
- **observed relationship:** `CatalogLoader.validateSchema(data)` is wrapped in `try { ... } catch (e: Exception) { if (e is IllegalStateException) throw e; log.warn(...) }`. Only `IllegalStateException` (used for explicit "schema validation failed" and "empty document" failures) propagates. Any other exception — e.g., a malformed `operations.schema.json`, a networknt library bug, an `IOException` reading the schema resource — is downgraded to a warning and validation is silently skipped.
- **expected boundary:** Schema validation is a startup safety gate. A configuration error in the schema should fail loudly, not be silently bypassed.
- **source evidence:** `server/helianthus-runtime/src/main/kotlin/helianthus/core/catalog/CatalogLoader.kt:65–93`.
- **impact:** A corrupted or unreadable schema does not abort startup; an invalid catalog can reach the runtime unvalidated. The diagrams correctly depict the *intended* behavior (the diagram shows schema failures as `IllegalStateException → abort`). The actual implementation is more permissive.
- **status:** Logged; not fixed in this task.

### DIAGRAM_FINDING 3 — Filter operators grouped into a single box

- **diagram:** runtime class diagram (`classes-runtime.md`).
- **observed relationship:** Seven filter-operator classes (`EqOperator`, `NeqOperator`, `GtOperator`, `GteOperator`, `LtOperator`, `LteOperator`, `InOperator`) are collapsed into a single `FilterOperators` class for legibility.
- **expected boundary:** Each operator is its own top-level type in `helianthus-runtime`. The diagram documents this grouping explicitly in the explanatory prose so consumers are not misled.
- **source evidence:** `server/helianthus-runtime/src/main/kotlin/helianthus/core/pipeline/{Eq,Neq,Gt,Gte,Lt,Lte,In}Operator.kt`.
- **impact:** None — the grouping is a presentation choice documented in the diagram's text. Each operator is listed by name in the grouping caption.
- **status:** Documented in the diagram caption.

### DIAGRAM_FINDING 4 — `CatalogDefinitions` group hides individual `*Def` types

- **diagram:** runtime class diagram (`classes-runtime.md`).
- **observed relationship:** Thirteen `*Def` data classes that describe the in-memory catalog shape are collapsed into a single `CatalogDefinitions` group for legibility.
- **expected boundary:** Each `*Def` is its own `internal data class`. The diagram documents the grouping explicitly.
- **source evidence:** `server/helianthus-runtime/src/main/kotlin/helianthus/core/catalog/OperationCatalog.kt:64–208`.
- **impact:** None — the diagram caption lists every `*Def` type that is in the group.
- **status:** Documented in the diagram caption.

---

## 6. Cross-diagram consistency

The seven diagrams were cross-checked against one another:

| Cross-check | Result |
| --- | --- |
| Module diagram says `web → runtime → core`; web class diagram shows no web-source import of any internal runtime/core type | ✅ consistent |
| Runtime class diagram says `HelianthusRuntimeBuilder.build` constructs `CatalogLoader`, `DataAccessFactory`, evaluators, `PipelineFactory`, `EntityService`, `HelianthusRuntime`; catalog workflow shows the same sequence | ✅ consistent |
| Runtime class diagram says `PipelineFactory → Pipeline` ordered `Resolve, Bind, Query, Project, Filter, Limit, ToResultFrame`; operation workflow and sequence diagram use the same order | ✅ consistent |
| Operation workflow shows two pre-flight checks (existence, permission); sequence diagram shows the same two checks at the start of `executeOperation` | ✅ consistent |
| Web class diagram shows controllers depending on `HelianthusRuntime` only; operation sequence shows the controller invoking `HelianthusRuntime.executeOperation` | ✅ consistent |
| Operation workflow shows format selected from the path extension by the controller; sequence diagram shows the controller selecting a `MediaType` and the converter writing the body | ✅ consistent |
| Catalog workflow shows `LoadedCatalog → DataAccessFactory → evaluators → PipelineFactory → EntityService → HelianthusRuntime`; runtime class diagram has the same arrows from `HelianthusRuntimeBuilder` | ✅ consistent |
| Core class diagram shows `DataAccessFactory → JdbcGenericDataAccess` and `DataAccessFactory → SqlDialect`; runtime class diagram shows `HelianthusRuntimeBuilder → DataAccessFactory` | ✅ consistent |

No inconsistencies found.

---

## 7. Ambiguity that could not be resolved from source

None. Every diagram relationship was confirmed against source. The two `DIAGRAM_FINDING`s above are documented behavior, not unresolved ambiguity.

---

## 8. Related records

- `HEL-KOTLIN-006.md` — established the `internal`/`public` boundary that this task's diagrams respect.
- `docs/knowledge/architecture/README.md` — index for the area created by this task.
- `docs/knowledge/architecture/diagrams/*.md` — the seven diagrams.
