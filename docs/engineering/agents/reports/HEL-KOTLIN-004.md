# HEL-KOTLIN-004 — Runtime Module Extraction & Application Boundary Refactor

- **Date:** 2026-09-21
- **Role:** Clio — Software Engineer
- **Status:** Complete. Full server suite passes (327 tests).

---

## 1. Module Structure Before / After

**Before** (two modules):

```
server/pom.xml
├── helianthus/          artifactId=helianthus-core   (~730 LOC)
└── helianthus-web/      artifactId=helianthus-web    (~2,938 LOC; mixed)
    web → core
```

**After** (three modules):

```
server/pom.xml
├── helianthus/          artifactId=helianthus-core
├── helianthus-runtime/  artifactId=helianthus-runtime   (new)
└── helianthus-web/      artifactId=helianthus-web
```

Dependency direction is now strictly:

```
helianthus-web → helianthus-runtime → helianthus-core
```

- `helianthus-runtime` depends only on `helianthus-core` (plus framework-neutral
  libraries: `slf4j-api`, `snakeyaml`, `json-schema-validator`, `kotlin-stdlib`).
- `helianthus-web` now depends on `helianthus-runtime` (core arrives transitively);
  its previous direct `helianthus-core` dependency was replaced.
- `helianthus-core`'s test dependency on `spring-boot-starter-test` was replaced
  with `junit-jupiter`, making core fully Spring-free including test scope.

---

## 2. Classes / Packages Moved

Production classes relocated from `helianthus-web` to `helianthus-runtime`
(package names preserved — the boundary is physical, not package-level):

| Package | Classes moved |
|---|---|
| `catalog` | `OperationCatalog`, `EntityCatalog`, `EntityCrudSqlBuilder`, `PhysicalColumnNamingStrategy`, `LowercaseNamingStrategy` |
| `pipeline` | `Pipeline`, `PipelineComponent`, `PipelineFactory`, `PipelineModels` (`PipelineContext`, `OperationRequest`, `ResolvedOperation`, `ParameterDefinition`, `PipelineConfig`, `BoundParameters`), `RowStream`, `ResolveStep`, `BindStep`, `QueryStep`, `ProjectStep`, `FilterStep`, `LimitStep`, `ToResultFrameStep`, `FilterOperator`, `FilterOperatorRegistry`, `Eq/Neq/Gt/Gte/Lt/Lte/InOperator` |
| `security` | `Principal`, `ADMIN_ROLE`, `OperationPermissionEvaluator`, `EntityPermissionEvaluator` |
| `util` | `PathHandler`, `EntityPathHandler` |
| `bean` | `PathMappingResultBean` |
| `exception` | `InvalidOperationPathException` |
| (root) | `InvalidParameterException` |

Resource moved: `schemas/operations.schema.json` → `helianthus-runtime/src/main/resources/schemas/`.

**Kept in `helianthus-web`:** `security/SpringAuthenticationAdapter`, `config/*`,
`web/*`, `web/converter/*`, `HelianthusApplication`.

---

## 3. Spring Annotations / Dependencies Removed from Runtime

`@Component` (and its import) removed from five classes; none were replaced with
another DI framework — construction is now explicit at the Spring bootstrap
boundary:

- `PipelineFactory`
- `OperationPermissionEvaluator`
- `EntityPermissionEvaluator`
- `PathHandler`
- `EntityPathHandler`

The runtime is now constructible without any DI container.

**New runtime type:** `security/AccessDeniedException` — the framework-neutral
representation of an authorization failure. Introduced because the extracted
`EntityService`/facade authorize inside the runtime and cannot throw Spring's
`AccessDeniedException`. The web `HelianthusExceptionHandler` maps it to `403
"Access denied"` (identical body to the existing Spring handler, which is retained).

---

## 4. `EntityService` Extraction

New `helianthus.core.service.EntityService` owns the entity use case previously
implemented in `EntityCrudController`:

- entity existence check (→ `EntityNotFoundException`), then read-permission check
  (→ `AccessDeniedException`) — preserving the 404-before-403 ordering pinned by
  the characterization tests;
- dialect selection, `EntityCrudSqlBuilder` construction;
- limit/offset/orderDir parsing, orderBy/filter column validation;
- PK coercion;
- query execution + row materialization + `ResultFrame` construction.

Public API:

```kotlin
class EntityService(
    entityCatalog, permissionEvaluator, dataAccess, dialects
) {
    fun listEntities(principal: Principal, request: EntityListRequest): ResultFrame
    fun getEntity(principal: Principal, entityName: String, id: String): ResultFrame
}

data class EntityListRequest(
    entityName, filters: Map<String, String>, orderBy: String?, orderDir: String?,
    limit: String?, offset: String?
)
```

`EntityCrudController` is now a transport adapter: it parses the path, extracts
query params into an `EntityListRequest`, converts Spring `Authentication` → `Principal`,
calls the facade, and maps `ResultFrame` → `ResponseEntity`.

---

## 5. `CatalogLoader` Extraction

New `helianthus.core.catalog.CatalogLoader` owns the YAML parsing and JSON-Schema
validation previously embedded in the Spring `CatalogConfig`:

```kotlin
class CatalogLoader(namingStrategy = LowercaseNamingStrategy()) {
    fun load(inputStream: InputStream): LoadedCatalog
}

data class LoadedCatalog(operationCatalog: OperationCatalog, entityCatalog: EntityCatalog)
```

It consumes a plain `InputStream` (no Spring `Resource`). The schema is loaded via
`ClassLoader.getResourceAsStream("schemas/operations.schema.json")`. It parses once
and produces both catalogs (previously the config parsed the document twice — once
per catalog bean).

`CatalogConfig` (web) is now a thin Spring wrapper: it locates the resource and
exposes `catalogLoader`, `loadedCatalog`, `operationCatalog`, `entityCatalog`,
`genericDataAccess`, and `dialects` beans. No `CatalogSource` interface was
introduced (a constructor `InputStream` sufficed).

---

## 6. `HelianthusRuntime` Facade API

New `helianthus.core.HelianthusRuntime` is the framework-neutral entry point:

```kotlin
class HelianthusRuntime(
    operationCatalog, operationPermissionEvaluator, pipelineFactory, entityService
) {
    fun executeOperation(
        principal: Principal, operationId: String,
        configurationId: String = "default", params: Map<String, String> = emptyMap()
    ): ResultFrame

    fun listEntities(principal: Principal, request: EntityListRequest): ResultFrame
    fun getEntity(principal: Principal, entityName: String, id: String): ResultFrame
}
```

It hides `Pipeline`, `PipelineContext`, pipeline steps, and SQL plumbing from
consumers. Both `HelianthusController` and `EntityCrudController` now depend only on
`PathHandler`/`EntityPathHandler` + `HelianthusRuntime`. A new web
`config/RuntimeConfig` wires `pathHandler`, `entityPathHandler`, both permission
evaluators, `pipelineFactory`, `entityService`, and `helianthusRuntime` as beans.

---

## 7. `NamedParameterSql` Relocation

`helianthus.core.access.impl.db.NamedParameterSql` → `helianthus.core.access.sql.NamedParameterSql`
(first-class package; no `impl`). The only affected import is `QueryStep`. No
interface was introduced.

---

## 8. Test Changes

**Moved** to `helianthus-runtime` (framework-neutral, no Spring): `EntityCatalogTest`,
`EntityCrudSqlBuilderTest`, `PhysicalColumnNamingStrategyTest`, `BindStepTest`,
`CaseInsensitiveColumnTest`, `FilterOperatorRegistryTest`, `PipelineStepTest`,
`QueryStepTest`, `EntityPermissionEvaluatorTest`, `OperationPermissionEvaluatorTest`,
`EntityPathHandlerTest`, `PathHandlerTest`.

**Kept in `helianthus-web`:** `OperationCatalogTest` (rewritten to use `CatalogLoader`
+ `FileInputStream` against the production `src/main/resources/operations.yml`;
no Spring imports remain), `ResultFrameJsonContractTest`, the `@SpringBootTest`
suites, converter tests, and the three Java suites.

**Added (runtime):**
- `CatalogLoaderTest` (9) — valid/invalid schema, operations, entities, security/config definitions, validation semantics.
- `EntityServiceTest` (10) — list, get-by-PK, filtering/ordering/pagination plan, permission, admin bypass, invalid inputs.
- `HelianthusRuntimeTest` (6) — public coordination contract only.

---

## 9. Final Test Count / Results

`mvn test` (from `server/`): **BUILD SUCCESS — 327 tests, 0 failures, 0 errors.**

| Module | Tests |
|---|---|
| helianthus-core | 31 |
| helianthus-runtime | 170 |
| helianthus-web | 126 |

The 30 black-box `HttpContractCharacterizationTest` cases and the
`ResultFrameJsonContractTest` remain green with no contract changes.

---

## 10. Zero-Spring Verification

| Check | Result |
|---|---|
| `helianthus-core` main Spring imports | 0 |
| `helianthus-core` test Spring imports | 0 |
| `helianthus-core` pom Spring dependencies | 0 |
| `helianthus-runtime` main Spring imports | 0 |
| `helianthus-runtime` test Spring imports | 0 |
| `helianthus-runtime` pom Spring dependencies | 0 |
| `helianthus-runtime` → `helianthus-web` dependency | none |
| Ktor dependency anywhere | none |

---

## 11. Findings

1. **`OperationRequest.format` is a transport concern in the runtime model.** The
   pipeline never reads `format`, yet `OperationRequest` requires it. The facade
   passes the placeholder `"json"` because it has no transport notion. Recorded;
   not changed (pipeline redesign is out of scope).

2. **A Helianthus-owned `AccessDeniedException` was unavoidable** to keep
   authorization inside the runtime without Spring. It is a faithful, minimal
   addition (same 403 semantics) — not a redesign.

3. **Entity and operation paths are now symmetric** — both route through the
   facade; the prior asymmetry (entity logic embedded in the controller) is gone.

4. **Schema resource now lives in runtime**, making catalog validation
   self-contained for embedded consumers (previously only reachable via Spring's
   `ClassPathResource`).

---

## 12. Deferred Work

- `PipelineContext`/error-in-context Kotlin redesign (unchanged from HEL-KOTLIN-001/002).
- `OperationRequest.format` removal/defaulting (blocked on pipeline Stage 2).
- The three Ktor spikes (concurrency model, Keycloak JWT, four-format responders).
- `ResultFrameJsonContractTest` remains in web (depends on Jackson 3, a web concern);
  its core subject (`ResultFrame`) is unchanged.
