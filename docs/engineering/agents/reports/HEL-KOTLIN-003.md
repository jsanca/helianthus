# HEL-KOTLIN-003 — Core / Runtime / Web Architecture Boundary Assessment

- **Date:** 2026-09-21
- **Role:** Deep — Software Architect / Architecture Reviewer
- **Status:** Assessment complete. No production code modified.

---

## 1. Executive Summary

Helianthus is **not** a two-layer system that happens to be split into two Maven
modules. It is a **three-layer** system compressed into two modules.

The evidence is unambiguous and quantifiable:

- `helianthus-core` (~730 LOC) is a clean, framework-independent layer of
  *primitives* (result model, data-access port, JDBC implementation, SQL
  dialects). Zero Spring imports.
- `helianthus-web` (~2,938 LOC) contains **two distinct layers side by side**:
  - ~1,457 LOC of framework-neutral **application/runtime** behavior (catalog,
    pipeline, permission evaluators, path handlers, entity SQL builder). Most
    of these files import Spring *only* for a single `@Component` marker.
  - ~1,470 LOC of genuine **HTTP/Spring adapter** (controllers, message
    converters, exception handler, security/data-source/catalog configuration,
    bootstrap).

Roughly **half of "helianthus-web" is not web**. That is the central finding.

The current two-module boundary is therefore a **physical lie**: the *application*
runtime (what Helianthus *is*) is physically inseparable from the Spring HTTP
server (what Helianthus is *deployed as*). Because `helianthus-web`'s POM pulls in
`spring-boot-starter-web`, `spring-boot-starter-security`, and
`oauth2-resource-server`, any embedded consumer that wants the runtime **must**
drag Spring in. The embedded-mode hypothesis is blocked by packaging, not by code.

The recommendation is **INTRODUCE_RUNTIME** — a distinct framework-neutral
`helianthus-runtime` module holding the catalog, pipeline, permission evaluators,
path handlers, entity SQL builder, and catalog loader. This is primarily a
**physical relocation of existing code**, not the invention of new abstractions.
The alternative (`KEEP_TWO_MODULES` with the runtime moved into `core`) is viable
and is documented as the fallback; the evidence tips toward three modules because
`core` already has a coherent, distinct identity (primitives + data access) that
predates and differs from the application orchestration, and because embedded and
standalone modes are both first-class hypotheses.

Crucially: the module extraction should be **sequenced before the Ktor migration**,
as a mechanical move gated by the existing framework-neutral unit tests, so that
the Ktor work later touches only the web adapter.

---

## 2. Current Module Map

```
server/pom.xml                          parent POM (Spring Boot BOM, Java 25 / Kotlin 2.3.21)
├── helianthus/        artifactId=helianthus-core   (~730 LOC, framework-independent)
│     deps: kotlin-stdlib, slf4j-api only
└── helianthus-web/    artifactId=helianthus-web    (~2,938 LOC, mixed)
      deps: spring-boot-starter-web, -jdbc, -security, -oauth2-resource-server,
            -actuator, postgresql, h2(test), jackson-dataformat-xml, commons-csv,
            json-schema-validator, helianthus-core
```

Dependency direction: `helianthus-web → helianthus-core`. Correct and single
direction today, but misleading — see §3.

Production packages, by current location:

**`helianthus-core` (`server/helianthus/...`):**

| Package | Classes |
|---|---|
| `result` | `ResultFrame`, `ResultSchema`, `ResultColumn`, `ResultMetadata`, `ResultType`, `CloseableRowStream`, `DefaultRowStream`, `ColumnNameResolver` |
| `access` | `GenericDataAccess` (interface), `SqlDialect` (interface), `SqlExecutionPlan`, `BoundParameter` |
| `access.impl.db` | `JdbcGenericDataAccess`, `JdbcRowStream`, `JdbcParamBinder`, `NamedParameterSql`, `PostgresDialect`, `H2Dialect` |
| (root) | `DataAccessErrorException`, `EntityNotFoundException`, `NoMappingException` |

**`helianthus-web` (`server/helianthus-web/...`):**

| Package | Classes |
|---|---|
| `catalog` | `OperationCatalog`, `EntityCatalog`, `EntityCrudSqlBuilder`, `PhysicalColumnNamingStrategy`, `LowercaseNamingStrategy` |
| `pipeline` | `Pipeline`, `PipelineComponent`, `PipelineFactory`, `PipelineModels` (`PipelineContext`, `OperationRequest`, `PipelineConfig`, …), `RowStream`, 7 steps, `FilterOperator` + registry + 7 operators |
| `security` | `Principal`, `ADMIN_ROLE`, `SpringAuthenticationAdapter`, `OperationPermissionEvaluator`, `EntityPermissionEvaluator` |
| `util` | `PathHandler`, `EntityPathHandler` |
| `bean` | `PathMappingResultBean` |
| `exception` | `InvalidOperationPathException` |
| (root) | `InvalidParameterException` |
| `config` | `CatalogConfig`, `DataSourceConfig`, `SecurityConfig`, `HelianthusWebConfiguration` |
| `web` | `HelianthusController`, `EntityCrudController`, `CatalogController`, `HealthController`, `HelianthusExceptionHandler`, `RequestLoggingFilter` |
| `web.converter` | `ResultFrameXmlMessageConverter`, `ResultFrameCsvMessageConverter`, `ResultFrameHtmlMessageConverter`, `ResultFrameHtmlRenderer` |
| (root) | `HelianthusApplication` |

---

## 3. Responsibility Classification

Classification is by **actual responsibility**, not physical location. "Framework
dependency" counts the imports in `org.springframework.*`, `jakarta.servlet`,
`org.springframework.security.*`. Counts were verified per-file (grep).

| Component / package | Current module | Actual responsibility | Framework dep | Candidate boundary | Notes |
|---|---|---|---|---|---|
| `result/*` | core | Domain/result primitives (frame, schema, column, metadata, type, row stream) | 0 | **core** | Correctly placed. |
| `access/GenericDataAccess` | core | Port: "execute a SQL plan → stream" | 0 | **core** | The one genuine port in the system. |
| `access/SqlDialect`, `SqlExecutionPlan`, `BoundParameter` | core | Domain/data-access primitives | 0 | **core** | Correctly placed. |
| `access.impl.db/*` (JDBC, binder, row stream, dialects) | core | Data-access infrastructure (raw JDBC) | 0 (JDK `java.sql`) | **core** | Correctly placed; JDBC is the impl behind the port. |
| `access.impl.db/NamedParameterSql` | core | SQL parsing utility | 0 | **core** (re-parent) | Pure stateless utility, but sits in an `impl` package while consumed by the runtime (see §7). |
| `DataAccessErrorException`, `EntityNotFoundException`, `NoMappingException` | core | Domain exceptions | 0 | **core** | Correctly placed. |
| `catalog/OperationCatalog`, `EntityCatalog` | web | Application model + resolution (the catalog) | 0 | **runtime** | Pure Kotlin data classes + lookup. Misplaced in web. |
| `catalog/EntityCrudSqlBuilder` | web | Application orchestration (entity SQL generation) | 0 | **runtime** | Depends only on core ports (`SqlDialect`, `SqlExecutionPlan`). Misplaced in web. |
| `catalog/PhysicalColumnNamingStrategy`, `LowercaseNamingStrategy` | web | Application model helper | 0 | **runtime** | Misplaced in web. |
| `pipeline/*` (all steps, operators, models, `RowStream`) | web | Application orchestration (operation execution) | 0 | **runtime** | The execution model. Misplaced in web. |
| `pipeline/PipelineFactory` | web | Application orchestration (wires step chain) | 1 (`@Component` only) | **runtime** | `@Component` is the only Spring touch. |
| `security/Principal`, `ADMIN_ROLE` | web | Domain identity primitive | 0 | **runtime** | HEL-KOTLIN-002 output; framework-neutral. |
| `security/OperationPermissionEvaluator`, `EntityPermissionEvaluator` | web | Application authorization rules | 1 (`@Component` only) | **runtime** | Logic is pure role-set comparison. Misplaced in web. |
| `security/SpringAuthenticationAdapter` | web | Framework adapter (Spring `Authentication` → `Principal`) | 1 (intentional) | **web** | Correctly placed; the single sanctioned Spring Security leak. |
| `util/PathHandler`, `util/EntityPathHandler` | web | Application request parsing (path → structured) | 1 (`@Component` only) | **runtime** | Pure string parsing. Misplaced in web. |
| `exception/InvalidOperationPathException` | web | Application exception | 0 | **runtime** | Misplaced in web. |
| `InvalidParameterException` | web (root) | Application exception | 0 | **runtime** | Misplaced in web. |
| `bean/PathMappingResultBean` | web | Application value object | 0 | **runtime** | Java-shaped (`var`, `Serializable`) but framework-neutral. |
| `config/CatalogConfig` | web | **Mixed**: YAML parsing + JSON-Schema validation (application) **and** Spring `@Bean`/`@Value`/`Resource` wiring (infrastructure) | 11 (Spring + SnakeYAML + networknt + Jackson) | **split** | ~250 LOC of pure parsing entangled with Spring wiring (see §6). |
| `config/DataSourceConfig` | web | Infrastructure (HikariCP construction) | 7 | **web** | Correctly placed. |
| `config/SecurityConfig` | web | Infrastructure (JWT/OIDC, CORS, role extraction) | 14 | **web** | Correctly placed. |
| `config/HelianthusWebConfiguration` | web | Infrastructure (converter registration) | 3 | **web** | Correctly placed. |
| `HelianthusApplication` | web | Bootstrap | 2 | **web** | Correctly placed. |
| `web/HelianthusController` | web | HTTP adapter (operation endpoint) | 9 | **web** | Correctly placed, but contains orchestration (see §4). |
| `web/EntityCrudController` | web | HTTP adapter (entity endpoint) | 9 | **web** | Correctly placed, but **embeds the entire entity execution path** (see §4, §6). |
| `web/CatalogController` | web | HTTP adapter (catalog summary) | 3 | **web** | Correctly placed. |
| `web/HealthController` | web | HTTP adapter | 2 | **web** | Correctly placed. |
| `web/HelianthusExceptionHandler` | web | HTTP adapter (exception → status) | 7 | **web** | Correctly placed. |
| `web/RequestLoggingFilter` | web | Infrastructure (MDC correlation) | 6 | **web** | Correctly placed. |
| `web.converter/*MessageConverter` | web | HTTP adapter (serialization) | 6–8 each | **web** | Correctly placed, but render logic is separable (§6). |
| `web.converter/ResultFrameHtmlRenderer` | web | Application rendering (pure) | 1 (`HtmlUtils`) | **web** (or runtime later) | Single Spring utility leak in an otherwise pure renderer. |

**Key structural facts:**

- Exactly **five** runtime classes carry a Spring dependency, and in every case it
  is *only* the `@Component` stereotype annotation: `PipelineFactory`,
  `OperationPermissionEvaluator`, `EntityPermissionEvaluator`, `PathHandler`,
  `EntityPathHandler`. Removing the annotation makes each class framework-neutral.
- Everything else in `catalog`, `pipeline`, `util`, `security`, `bean`,
  `exception` (and the root `InvalidParameterException`) imports **zero** Spring.
- `helianthus-core` imports **zero** Spring.

---

## 4. Current Execution Architecture

### 4.1 Operation execution path

```
GET /api/op/{opId}/{configId}.{format}
  → HelianthusController            [web]   HttpServletRequest → servletPath
  → PathHandler.parsePath           [runtime]  pure string → PathMappingResultBean
  → SecurityContextHolder.getContext().authentication   [web — Spring Security]
  → auth.toPrincipal()              [web]   SpringAuthenticationAdapter
  → OperationPermissionEvaluator    [runtime] Principal + catalog → Boolean
  → OperationCatalog.resolveOperation [runtime] operationId/configId → ResolvedCatalogEntry
  → PipelineFactory.createPipeline  [runtime] → Pipeline (7 steps)
  → Pipeline.execute(PipelineContext) [runtime] mutable context, error-in-context
      ResolveStep → BindStep → QueryStep → ProjectStep → FilterStep → LimitStep → ToResultFrameStep
  → ResultFrame                     [core]
  → ResponseEntity + mediaType      [web]   → HttpMessageConverter (JSON/XML/CSV/HTML)
```

### 4.2 Entity execution path

```
GET /api/entities/{entity}.{format}  or  /api/entities/{entity}/{id}.{format}
  → EntityCrudController            [web]
  → EntityPathHandler.parsePath     [runtime]  pure string → EntityPathResult
  → auth.toPrincipal()              [web]
  → EntityPermissionEvaluator       [runtime]
  → EntityCatalog.resolveEntity     [runtime]
  → dialects[datasource] lookup     [web — injected Map<String, SqlDialect>]
  → EntityCrudSqlBuilder(dialect)   [runtime] constructed *inside* the controller
  → dataAccess.executeQueryStream   [core port]
  → stream.rows.toList(); close()   [web — hand-rolled materialization]
  → ResultFrame(...)                [core]  constructed *inside* the controller
  → ResponseEntity + mediaType      [web]
```

### 4.3 The critical asymmetry

The **operation** path runs through a framework-neutral `Pipeline`; the controller
is a thin shell that parses, authorizes, executes, and maps a `ResultFrame` to a
response.

The **entity** path is the opposite: `EntityCrudController` (a Spring
`@RestController`) *embodies* the entire application use case — dialect lookup,
`EntityCrudSqlBuilder` construction, `GenericDataAccess` invocation, row
materialization, `ResultFrame` construction, plus the limit/offset/orderBy/filter
parsing and PK coercion. This is application orchestration living inside the HTTP
adapter.

This asymmetry is the single most important "misplaced responsibility" in the
system. It means:

- An embedded consumer could invoke the *operation* use case cleanly (the
  pipeline is portable), but there is **no equivalent portable entry point for
  entities** — that logic is trapped inside a servlet controller.
- The Ktor migration would have to rewrite `EntityCrudController` *and*
  re-extract the use-case logic at the same time, unless the use case is moved
  out first.

### 4.4 Minimal conceptual path (framework-free)

**Operation:** `(Principal, operationId, configId, params) → ResultFrame`
via *resolve → bind → query → project → filter → limit → materialize*.

**Entity:** `(Principal, entityName, filters, orderBy, orderDir, limit, offset) → ResultFrame`
and `(Principal, entityName, id) → ResultFrame` via *authorize → resolve → build SQL → execute → materialize*.

All of the inputs are plain Kotlin values (`Principal`, `String`, `Map<String,String>`,
`Int`). None require HTTP/Spring objects. The only Spring/HTTP objects that appear
today are `HttpServletRequest` (controller boundary), `SecurityContextHolder`
(authentication source), and `ResponseEntity`/`HttpOutputMessage` (response
boundary). **These are all at the edges and are legitimately transport concerns.**

Remaining places where transport/framework leaks into execution behavior:

1. `EntityCrudController` — entire use case embedded in the transport layer (see above).
2. `CatalogConfig` — catalog *loading* (an application bootstrap concern) is fused
   with Spring `Resource`/`@Value` (a transport/config concern).
3. `QueryStep` imports `helianthus.core.access.impl.db.NamedParameterSql` — an
   `impl` package member from another module, i.e., a concrete implementation
   class crossing a module boundary into the application layer.
4. `ResultFrameHtmlRenderer` imports `org.springframework.web.util.HtmlUtils.htmlEscape`.

---

## 5. Embedded Execution Analysis

### 5.1 Feasibility verdict

**Feasible, but blocked by packaging, not by code.** The runtime logic is already
framework-neutral (§3). What prevents clean embedded consumption is that this
logic is physically located in an artifact (`helianthus-web`) whose POM forces
Spring Boot Web + Security + OAuth2 onto the consumer.

### 5.2 Smallest coherent execution surface an embedded consumer requires

1. Build catalogs from YAML (currently only reachable via `CatalogConfig`, a
   Spring `@Configuration`).
2. Build data access from a `Map<String, DataSource>` (already exists:
   `JdbcGenericDataAccess`).
3. Execute an operation: `(Principal, operationId, configId, params) → ResultFrame`.
4. Execute an entity list / get-by-id: `(Principal, entityName, …) → ResultFrame`.
5. Evaluate permissions (already framework-neutral).

That is the entire surface. It is *two* use cases plus *two* construction
functions. Nothing more is needed for the embedded hypothesis.

### 5.3 Components that would "accidentally" become public if exposed directly

If we simply moved the packages and told embedded consumers to use them, the
following would leak as public API:

- `PipelineContext` — a mutable, error-in-context holder (flagged by
  HEL-KOTLIN-001 §6.2.1 as the highest-value Kotlin redesign). It is *internal
  execution state*, not a contract.
- `Pipeline`, `PipelineComponent`, the seven `*Step` classes — the internal step
  chain. Exposing these invites consumers to reorder steps.
- `SqlExecutionPlan`, `BoundParameter` — internal data-access plumbing.
- The full `*Def` catalog data-class surface — larger than what an executor
  actually needs to expose.

This argues for a **thin facade** (one class, e.g. a `HelianthusRuntime` exposing
`executeOperation` / `listEntities` / `getEntity`), not for designing a large
public SDK. The facade is cheap because the use cases already exist; it just
needs a stable entry point that hides the pipeline internals.

### 5.4 What is *not* required (avoid speculative abstraction)

No coroutines/`Flow` rewrite, no reactive API, no new serialization contract. The
blocking-JDBC model is fine for embedded JVM consumption today. A suspending
variant is a separate (HEL-KOTLIN-001 §13.1) decision, orthogonal to module
boundaries.

---

## 6. Standalone / Web Analysis

### 6.1 Responsibilities that should remain exclusively in the web adapter

| Concern | Current location | Should stay in web? |
|---|---|---|
| HTTP routing (`/api/op/**`, `/api/entities/**`, `/api/admin/catalog`, `/health`) | controllers | **yes** |
| HTTP request binding (`HttpServletRequest`, query params, servlet path) | controllers | **yes** |
| Authentication extraction (`SecurityContextHolder` → `Principal`) | controllers + `SpringAuthenticationAdapter` | **yes** |
| HTTP status semantics (`ResponseEntity`, 400/401/403/404/500) | controllers + `HelianthusExceptionHandler` | **yes** |
| CORS | `SecurityConfig` | **yes** |
| JWT/OIDC resource-server config | `SecurityConfig` | **yes** |
| JSON/XML/CSV/HTML response negotiation | controllers + converters + `HelianthusWebConfiguration` | **yes** |
| Server configuration (`application.yml`, `@Value`) | `application.yml`, config classes | **yes** |
| Bootstrap/lifecycle (`@SpringBootApplication`) | `HelianthusApplication` | **yes** |
| HikariCP datasource construction | `DataSourceConfig` | **yes** (infrastructure) |

### 6.2 "The web runtime should consume Helianthus, not define it"

Current state: **partially true.**

- **True** for operations: `HelianthusController` consumes the pipeline/catalog/
  permission logic; the controller defines no operation semantics.
- **False** for entities: `EntityCrudController` *defines* the entity use case
  (SQL-builder construction, execution, materialization, PK coercion, list/filter/
  pagination parsing). The web layer currently *is* the entity runtime.
- **Partially false** for catalog loading: `CatalogConfig` defines how the YAML
  becomes a catalog, entangled with Spring wiring.

To make the property fully true, three moves are needed (none introduce new
semantics, all are relocation/extraction):

1. Extract entity use-case logic out of `EntityCrudController` into a
   framework-neutral `EntityService` (runtime).
2. Extract catalog loading out of `CatalogConfig` into a framework-neutral
   `CatalogLoader` (runtime), leaving `CatalogConfig` as a thin Spring wrapper
   that supplies the `Resource`/`InputStream`.
3. Optionally, thin `HelianthusController` behind the same runtime facade as the
   embedded path (it already is thin; no change required beyond removing the
   direct pipeline/catalog wiring in favor of the facade).

---

## 7. DIP Analysis

Current dependency directions and whether each is appropriate.

| Higher-level behavior | Currently depends on | Verdict |
|---|---|---|
| Pipeline/`QueryStep` → data access | `GenericDataAccess` (interface, core) | **Appropriate.** The one real port; runtime depends on the abstraction, JDBC is the impl. Do not change. |
| Pipeline/`QueryStep` → SQL parsing | `NamedParameterSql` (concrete, `core.access.impl.db`) | **Violation (minor).** Application layer imports a concrete class from another module's `impl` package. Fix by re-parenting `NamedParameterSql` to a first-class `access` (or `access.sql`) package — a move, not a new interface. |
| `EntityCrudSqlBuilder` → dialect | `SqlDialect` (interface, core) + `SqlExecutionPlan` | **Appropriate.** Clean use of core ports. |
| Permission evaluators → identity | `Principal` (framework-neutral, runtime) | **Appropriate.** HEL-KOTLIN-002 already inverted this. `SpringAuthenticationAdapter` is the single Spring→Principal adapter in web. |
| Catalog *loading* → source | Spring `Resource` + `@Value` (web) fused with parsing | **Misplaced.** The parse functions (`parseApp`…`parseEntities`, ~250 LOC) are pure and framework-neutral; only the `Resource` acquisition is Spring. Extract parsing into a `CatalogLoader` that takes an `InputStream`/`Reader`; web supplies the stream. A port is *not* required — a constructor parameter suffices. |
| Execution | — | **No port needed.** Execution is the pipeline, which is application orchestration. Introducing an `OperationExecutor` interface would be pure ceremony at this size. |
| Serialization | Spring converters wrap pure render logic | **Appropriate for now.** `ResultFrame` is a plain data class (JSON is any consumer's choice). XML/CSV render logic is pure; the `HtmlUtils.htmlEscape` import is the only leak. If embedded mode later needs non-JSON output, move renderers (pure part) to runtime — **not now**. |
| Configuration | `@Value`/`application.yml` (web) | **Appropriate.** Runtime classes already take constructor dependencies; no runtime class reads Spring properties. Keep config binding in web. |
| Entity execution | `EntityCrudController` (web) directly calls `GenericDataAccess` and builds `ResultFrame` | **Violation (significant).** Use-case logic in the transport layer. Extract `EntityService`. |

**Ports already justified and present:** `GenericDataAccess` (data access),
`SqlDialect` (dialect). **Ports deliberately not recommended:** `CatalogSource`,
`OperationExecutor`, `EntityExecutor`, a `ResultRenderer` interface, a config
abstraction — each would add ceremony without enabling a real boundary today.

**One genuinely new seam worth having:** a thin **runtime facade**
(`HelianthusRuntime`) exposing `executeOperation` / `listEntities` / `getEntity`.
This is a *facade over existing code*, not a speculative abstraction — it is what
gives both the web adapter and any embedded consumer a single, stable entry point
instead of the pipeline internals (§5.3).

---

## 8. Pipeline Classification

**The pipeline is application/runtime orchestration, not core domain.**

- It encodes Helianthus-specific execution semantics (Resolve → Bind → Query →
  Project → Filter → Limit → ToResultFrame) on top of core primitives.
- It is **not** infrastructure: it is the heart of what Helianthus *does*.
- It is **not** core/domain: core owns the *vocabulary* (ResultFrame, row stream,
  data access) that the pipeline *uses*. The pipeline's steps are use-case logic.
- It is a "mixture" only in the trivial sense that `QueryStep` touches
  `NamedParameterSql` (a core `impl` member) — a minor import leak, not a
  fundamental misplacement.

**Physical-location impact:** none for Ktor or embedded execution. The pipeline is
already framework-neutral (only `PipelineFactory` has a `@Component` marker). It
can stay in `helianthus-web` until the module split, then move wholesale into the
runtime module. No pipeline redesign is required for this assessment; the
mutable-`PipelineContext`/error-in-context design is a separate Stage-2 concern
(HEL-KOTLIN-001 §6.2.1), not a boundary problem.

---

## 9. Two-vs-Three Module Comparison

### Model A — Two modules (core + web, runtime moved into core)

```
helianthus-core    primitives + data access + catalog + pipeline + permissions
       ↑
helianthus-web     HTTP/Spring adapter
```

| Criterion | Assessment |
|---|---|
| Dependency direction | web → core. Simple, single direction. |
| Conceptual cohesion | Lower: `core` becomes "everything that isn't HTTP", mixing reusable primitives with Helianthus-specific orchestration. |
| Embedded consumption | Works (core is Spring-free), but an embedded consumer gets a grab-bag that includes JDBC impl details. |
| Standalone deployment | Unchanged. |
| API exposure | Core's public surface becomes very large (pipeline + catalog + result + JDBC). |
| Testing | Unchanged; framework-neutral unit tests already exist. |
| Ktor migration impact | Web adapter is thin; migration touches only web. Good. |
| Future extensibility | Koog/MCP adapters would depend on `core` and see primitives + orchestration mixed. |
| Complexity/module overhead | Lowest. One less pom/artifact. |
| Risk of premature abstraction | Lowest — no new module. |

### Model B — Three modules (core + runtime + web)

```
helianthus-core      primitives (ResultFrame/schema/row stream), data-access port
       ↑               + JDBC impl + SQL dialects
helianthus-runtime   catalog, pipeline, permission evaluators, path handlers,
       ↑               entity SQL builder, catalog loader, runtime facade
helianthus-web       HTTP/Spring adapter (controllers, converters, config, security)
```

| Criterion | Assessment |
|---|---|
| Dependency direction | web → runtime → core. Two edges, both inward, clean. |
| Conceptual cohesion | Highest: each layer has one clear identity (vocabulary / application / transport). |
| Embedded consumption | Cleanest: depend on `runtime` (+ `core` transitively) with **no** Spring. |
| Standalone deployment | web depends on runtime, unchanged. |
| API exposure | `runtime` facade is the narrow surface; core primitives stay reusable; JDBC impl stays inside core. |
| Testing | Unchanged; the runtime's existing unit tests move with it and remain framework-neutral. |
| Ktor migration impact | Best: migration = rewrite `web` only; `runtime` and `core` untouched. |
| Future extensibility | Koog/MCP depend on `runtime` facade; no fundamental semantics change needed. |
| Complexity/module overhead | One extra pom/artifact (minor at Maven multi-module scale; parent already manages 2). |
| Risk of premature abstraction | Present but bounded: the risk is a *third module*, not new interfaces. The code to move already exists (~1,457 LOC); no abstraction is invented. |

### Which model the evidence supports

Both models fix the core problem (runtime entangled with Spring). The difference
is whether "primitives" and "application orchestration" share a module.

Evidence for the three-module split:

- `helianthus-core` already has a stable, coherent identity (result model +
  data-access port + JDBC impl) that is *independently reusable* — it is not
  "everything except HTTP".
- The runtime layer is a *different* kind of thing: Helianthus-specific use cases.
  Mixing it into core dilutes core's reusability and broadens its API.
- Embedded mode's ideal dependency is "runtime, not primitives" — a consumer wants
  `executeOperation`, not `ResultFrame` + `JdbcRowStream` + a step chain.
- The layering matches the clean-architecture / DIP shape the migration statement
  requires ("migrate the adapter, not Helianthus").

Evidence for the two-module fallback:

- The runtime is not yet *consumed* by anything other than web, so a third module
  is arguably organizing for a consumer that does not exist yet.
- If embedded mode turns out to be a non-requirement, "runtime in core" achieves
  the DIP cleanliness more cheaply.

**Net:** the code supports Model B; the *only* honest caveat is that Model B's
incremental value over "move B into core" depends on the embedded hypothesis
having moderate likelihood. This is reflected in the sequencing recommendation
(§11), which makes the split reversible-by-default until the facade is actually
exercised by a second consumer.

---

## 10. Ktor Migration Implications

### 10.1 Sequencing: **before** the Ktor migration

Do the module extraction **before** migrating Spring → Ktor. Rationale:

1. The extraction is **framework-independent** — it is a pure package move
   (plus the `@Component`-marker removals and the `EntityService`/`CatalogLoader`
   extractions). It can be validated by the existing framework-neutral unit tests
   and the unchanged Spring integration tests.
2. After the split, the Ktor migration is reduced to **one** module (`web`) with
   zero application semantics in it. The migration becomes "replace the adapter".
3. Doing both at once would force the migration to reason about *moving packages*
   and *changing frameworks* simultaneously — the exact conflation §11 warns
   against.

### 10.2 Components that should remain untouched by a Spring → Ktor migration

- Everything in `helianthus-core` (all 730 LOC).
- Everything moving into `helianthus-runtime` (catalog, pipeline, security
  evaluators + `Principal`, path handlers, entity SQL builder, `CatalogLoader`,
  `EntityService`, facade).
- The pure rendering logic inside the converters (CSV/XML/HTML) — only the
  `AbstractHttpMessageConverter` wrapper is Spring.

Only these should change under Ktor: `web/*` controllers, `web/converter/*`
converter wrappers, `config/*` (Security/DataSource/Catalog/Web configuration),
`SpringAuthenticationAdapter`, `RequestLoggingFilter`, `HelianthusExceptionHandler`,
`HelianthusApplication`.

### 10.3 How close is the codebase to "migrate the adapter, not Helianthus"?

**Very close, with three gaps:**

1. `EntityCrudController` still embeds the entity use case — must be extracted to
   `EntityService` first, or the Ktor migration inherits application logic.
2. `CatalogConfig` still embeds catalog parsing — must be extracted to
   `CatalogLoader` first, or the Ktor migration inherits the YAML/validation logic.
3. Five `@Component` markers on runtime classes — trivially removed, but must be
   handled so the runtime does not depend on Spring's DI.

Once (1)–(3) are done, the statement is **true**: Ktor replaces only the web
adapter; Helianthus itself (core + runtime) is untouched.

---

## 11. Future Adapter Pressure Test (evaluate only)

Pressure-test the proposed boundary against hypothetical future adapters, without
designing them:

```
helianthus-core
       ↑
helianthus-runtime
       ↑
       +-- helianthus-web          (Spring → later Ktor)
       +-- embedded application
       +-- potential Koog adapter
       +-- potential MCP adapter
```

Would adding another adapter require changing fundamental Helianthus semantics?
**No**, provided the runtime exposes the thin facade described in §5.3. Each
adapter would:

1. Obtain/construct a `Principal` in its own idiom.
2. Call `HelianthusRuntime.executeOperation(...)` / `listEntities(...)` /
   `getEntity(...)`.
3. Map the resulting `ResultFrame` into its own transport/serialization.

None of that touches catalog parsing, SQL generation, permission rules, or the
pipeline. The pressure test **passes** and, in doing so, **confirms the value of
the facade**: it is the one artifact that keeps adapter concerns out of Helianthus
semantics. Without it, every adapter would reach into pipeline/catalog internals
and the accidental-API problem (§5.3) would compound.

The pressure test also exposes what would be *premature* to build now: any
adapter-specific SPI, any multi-format result-rendering contract in the runtime,
or any event/callback model. None are justified by current requirements.

---

## 12. Risks / Premature Abstractions

### 12.1 Required findings

**A. Misplaced responsibilities** (wrong layer/module today):

1. `EntityCrudController` (web) embodies the entire entity use case — most
   significant.
2. `CatalogConfig` (web) fuses framework-neutral YAML parsing + validation with
   Spring wiring.
3. The entire `catalog` package, `pipeline` package, `security` (Principal +
   evaluators), `util`, `bean`, `exception`, and root `InvalidParameterException`
   — ~1,457 LOC of framework-neutral application behavior — physically live in a
   Spring artifact.

**B. Correct existing boundaries** (leave unchanged):

- `helianthus-core`'s `result/*` and `access/*` (port + impl + dialects).
- `GenericDataAccess` as the data-access port; `SqlDialect` as the dialect port.
- `Principal` as the identity boundary; `SpringAuthenticationAdapter` as the sole
  Spring→Principal conversion.
- The web adapter's transport concerns (routing, status, CORS, converters,
  exception mapping, config, bootstrap).

**C. Dependency violations** (undesirable direction):

- `QueryStep` (application) → `NamedParameterSql` (another module's `impl.db`
  package). Concrete-impl import across a module boundary.
- `EntityCrudController` (transport) → `GenericDataAccess` + `EntityCrudSqlBuilder`
  + `ResultFrame` (use-case orchestration in the transport layer).

**D. Accidental coupling** (historical Spring/web placement):

- The five `@Component` markers (`PipelineFactory`, two evaluators, two path
  handlers) — the only reason these runtime classes compile in `helianthus-web` is
  a stereotype annotation, not a real dependency.
- `ResultFrameHtmlRenderer`'s `org.springframework.web.util.HtmlUtils` import —
  a Spring *utility*, not a Spring *concern*.
- `CatalogConfig`'s `ClassPathResource`/`Resource`/`@Value` — catalog loading
  happens to use Spring's resource abstraction because it lives in a Spring
  config class.

**E. Embedded blockers:**

1. Packaging: runtime logic is inside an artifact whose POM forces Spring Boot
   Web + Security + OAuth2.
2. No runtime facade / entry point — consumers must wire `PipelineFactory` +
   catalogs + data access themselves.
3. No portable entity use case (trapped in `EntityCrudController`).
4. No portable catalog loader (trapped in `CatalogConfig`).
5. `NamedParameterSql` exposed only under an `impl` package.

**F. Ktor migration blockers** (boundary problems to fix before replacing Spring):

1. Entity use case embedded in the controller (must extract `EntityService`).
2. Catalog parsing embedded in `CatalogConfig` (must extract `CatalogLoader`).
3. `@Component` markers on runtime classes (must strip).
4. (Minor) `HtmlUtils.htmlEscape` in the renderer.

**G. Premature abstractions** (explicitly do **not** do):

- No `OperationExecutor`/`EntityExecutor`/`CatalogSource`/`ResultRenderer`
  interfaces — facade over existing code is enough.
- No coroutines/`Flow` rewrite of JDBC.
- No new serialization contract for the runtime.
- No public SDK beyond the thin facade.
- No pipeline redesign (Stage 2, separate).
- No config abstraction; constructor injection of plain values is sufficient.

### 12.2 Other risks

- **Facade without a second consumer.** A `HelianthusRuntime` facade built today
  is used only by `helianthus-web`. It becomes provably useful only when the
  embedded (or Ktor, or Koog/MCP) consumer appears. Mitigation: keep the facade
  one class, thin, and co-located with the use cases it fronts.
- **Module split churn.** Moving ~1,457 LOC touches many `import` statements.
  Mitigation: do it as a mechanical, test-gated move before any framework change;
  it is reversible.
- **Reversible-by-default principle.** If the embedded hypothesis dies, the
  runtime can be folded into core later; the split does not foreclose that.

---

## 13. Recommended Target Architecture

```
helianthus-core          framework-independent primitives + data access
  result/*               ResultFrame, ResultSchema, ResultColumn, ResultMetadata,
                         ResultType, CloseableRowStream, DefaultRowStream,
                         ColumnNameResolver
  access/                GenericDataAccess (port), SqlDialect (port),
                         SqlExecutionPlan, BoundParameter, NamedParameterSql (re-parented)
  access.impl.db/        JdbcGenericDataAccess, JdbcRowStream, JdbcParamBinder,
                         PostgresDialect, H2Dialect
  DataAccessErrorException, EntityNotFoundException, NoMappingException
        ▲
helianthus-runtime       framework-neutral application (Helianthus semantics)
  catalog/               OperationCatalog, EntityCatalog, EntityCrudSqlBuilder,
                         PhysicalColumnNamingStrategy, LowercaseNamingStrategy
  pipeline/              Pipeline, PipelineFactory (minus @Component), PipelineModels,
                         all steps, FilterOperator + registry + operators, RowStream
  security/              Principal, ADMIN_ROLE, OperationPermissionEvaluator,
                         EntityPermissionEvaluator (minus @Component)
  util/                  PathHandler, EntityPathHandler (minus @Component)
  exception/             InvalidOperationPathException
  loader/                CatalogLoader (extracted from CatalogConfig parsing)
  service/               HelianthusRuntime (facade), EntityService (extracted from
                         EntityCrudController)
  InvalidParameterException
        ▲
helianthus-web           HTTP/Spring adapter + infrastructure
  web/                   HelianthusController (thinned), EntityCrudController (thinned),
                         CatalogController, HealthController, HelianthusExceptionHandler,
                         RequestLoggingFilter
  web.converter/         ResultFrame{Json via Spring Jackson, Xml, Csv, Html}MessageConverter,
                         ResultFrameHtmlRenderer
  security/              SpringAuthenticationAdapter
  config/                DataSourceConfig, SecurityConfig, HelianthusWebConfiguration,
                         CatalogConfig (thin Spring wrapper over CatalogLoader)
  HelianthusApplication
```

**Dependency graph:** `helianthus-web → helianthus-runtime → helianthus-core`.

**What moves** (conceptually; not performed in this assessment):

- `helianthus-web/catalog/*`, `pipeline/*`, `security/{Principal,OperationPermissionEvaluator,EntityPermissionEvaluator}`,
  `util/*`, `exception/*`, `bean/*` → `helianthus-runtime`.
- `CatalogConfig` parsing functions + JSON-Schema validation → `helianthus-runtime`
  `CatalogLoader`; the Spring `@Bean`/`@Value`/`Resource` residue stays in `helianthus-web`.
- `EntityCrudController` use-case logic → `helianthus-runtime` `EntityService`.
- `NamedParameterSql` re-parented within `helianthus-core` from `access.impl.db`
  to `access` (or `access.sql`) — no interface added.
- `SpringAuthenticationAdapter` stays in `helianthus-web`.

**What is added:** exactly one new type — a thin `HelianthusRuntime` facade. No
new interfaces beyond that.

---

## 14. Recommended Migration Sequence

1. **Extract the runtime module (mechanical, test-gated).** Move the packages in
   §13, strip `@Component`, re-parent `NamedParameterSql`. Validate with
   `mvn clean test` (framework-neutral unit tests + unchanged Spring integration
   tests). No behavior change.
2. **Extract `EntityService`** out of `EntityCrudController` into the runtime;
   `EntityCrudController` becomes a thin shell. Extract **`CatalogLoader`** out of
   `CatalogConfig`. Add the thin **`HelianthusRuntime`** facade over operations +
   entities.
3. **Confirm the boundary property.** Assert (by inspection/test) that
   `helianthus-core` and `helianthus-runtime` contain zero Spring imports.
4. **(Later, gated) Ktor migration.** Replace `helianthus-web`'s Spring adapter
   with Ktor; `core` and `runtime` remain untouched. This is the point at which
   the HEL-KOTLIN-001 spike list (concurrency model, Keycloak JWT, four-format
   responders) becomes relevant.
5. **(If/ when justified) Second consumer.** Build the embedded consumer (or
   Koog/MCP adapter) against the facade; this retroactively validates the
   runtime boundary.

Step 1–3 are valuable regardless of the Ktor decision and can be performed
independently.

---

## 15. Final Recommendation

**INTRODUCE_RUNTIME.**

The current `core`/`web` boundary misrepresents the system. The code is already a
three-layer architecture — primitives (framework-independent), application
runtime (framework-independent), and HTTP adapter (Spring) — physically compressed
into two modules, with the middle layer trapped inside the Spring artifact. The
quantitative evidence is decisive: ~1,457 of ~2,938 LOC in `helianthus-web`
(≈50%) is framework-neutral application behavior, most of it coupled to Spring
only by a single `@Component` marker.

`INTRODUCE_RUNTIME` is justified on three concrete grounds:

1. **Embedded mode (a stated hypothesis) is blocked by packaging, not code.** A
   consumer cannot obtain the runtime without also obtaining Spring Boot Web +
   Security + OAuth2, because the runtime shares an artifact with the server. A
   `helianthus-runtime` module resolves this with a one-line dependency change.
2. **The web adapter should consume Helianthus, not define it — and today it
   defines the entity use case.** `EntityCrudController` embodies entity
   execution; `CatalogConfig` embodies catalog loading. These are application
   concerns that belong in the runtime and are prerequisites for the stated
   migration property ("migrate the adapter, not Helianthus").
3. **DIP is already 90% correct**, and the remaining 10% (the `NamedParameterSql`
   import leak, the `@Component` markers, the two embedded use cases) is fixed by
   relocation and extraction, not by new abstraction. The recommendation does not
   invent interfaces; it makes the existing de-facto boundary physical.

The **fallback is `KEEP_TWO_MODULES`** with the runtime relocated into
`helianthus-core`. It is architecturally acceptable and lower-ceremony; it is the
right choice **if** embedded consumption is confidently judged a non-requirement.
The recommendation therefore carries one condition: the third module earns its
overhead primarily through the embedded (and future-adapter) consumers. The
migration sequence (§14) keeps this reversible — the split is validated by
existing tests before any framework change, and can be folded back into core at
low cost if the second consumer never materializes.

This is **not** `INVESTIGATE`: the boundary problem is fully evidenced by the
current source, and the decision is a packaging one that can be made now. What
remains genuinely uncertain (coroutines model, Ktor JWT, four-format parity) is
orthogonal to the module question and is already owned by the HEL-KOTLIN-001
spike list.
