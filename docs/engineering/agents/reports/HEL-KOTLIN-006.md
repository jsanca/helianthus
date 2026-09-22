# HEL-KOTLIN-006 — Public API & Internal Boundary Enforcement

- **Date:** 2026-09-21
- **Role:** Clio — Software Engineer
- **Status:** Complete. Full server suite passes (333 tests).

---

## 1. Public API Before / After

### Runtime (`helianthus-runtime`)

**Before** — effectively everything Kotlin-`public`: `HelianthusRuntime`, the entire
`pipeline` package, `catalog` (catalogs + loader + all `*Def` models +
`EntityCrudSqlBuilder` + naming strategies), `security` (Principal + evaluators +
adapter), `service` (`EntityService` + `EntityListRequest`), `util` path handlers,
`bean`, `exception`, and `InvalidParameterException`.

**After** — the supported runtime surface is:

```text
HelianthusRuntime          (facade: executeOperation / listEntities / getEntity / catalogSummary)
HelianthusRuntimeBuilder   (construction entry point)
Principal, ADMIN_ROLE      (identity)
EntityListRequest          (entity list request)
CatalogSummary + 8 DTOs    (catalog read-model)
AccessDeniedException, InvalidParameterException, InvalidOperationPathException
PathHandler, EntityPathHandler          (adapter seam — see §8)
PathMappingResultBean, EntityPathResult (return types of the path handlers)
```

### Core (`helianthus-core`)

**Before** — the whole `access` package, `access.impl.db` (JDBC impl + dialects),
`access.sql`, `result` (including `DefaultRowStream`), and all three root
exceptions were `public`.

**After**:

```text
PUBLIC DOMAIN         ResultFrame, ResultSchema, ResultColumn, ResultMetadata, ResultType,
                      CloseableRowStream, DefaultRowStream, ColumnNameResolver
PUBLIC DATA CONTRACT  GenericDataAccess, SqlDialect, SqlExecutionPlan, BoundParameter,
                      NamedParameterSql, DataAccessFactory
PUBLIC EXCEPTIONS     EntityNotFoundException, NoMappingException
INTERNAL              JdbcGenericDataAccess, JdbcRowStream, JdbcParamBinder,
                      PostgresDialect, H2Dialect, DataAccessErrorException
```

---

## 2. Declarations Changed to `internal`

**Core (`helianthus-core`):**

- `access.impl.db.JdbcGenericDataAccess`, `JdbcRowStream`, `JdbcParamBinder`
- `access.impl.db.PostgresDialect`, `H2Dialect`
- `DataAccessErrorException` (root)

**Runtime (`helianthus-runtime`):**

- `pipeline.*` — `Pipeline`, `PipelineComponent`, `PipelineFactory`,
  `PipelineContext`, `OperationRequest`, `ResolvedOperation`,
  `ParameterDefinition`, `PipelineConfig`, `BoundParameters`, `RowStream`,
  `ResolveStep`, `BindStep`, `QueryStep`, `ProjectStep`, `FilterStep`,
  `LimitStep`, `ToResultFrameStep`, `FilterOperator`, `FilterOperatorRegistry`,
  `EqOperator`, `NeqOperator`, `GtOperator`, `GteOperator`, `LtOperator`,
  `LteOperator`, `InOperator`
- `catalog.*` — `OperationCatalog`, `EntityCatalog`, `EntityCrudSqlBuilder`,
  `CatalogLoader`, `LoadedCatalog`, `PhysicalColumnNamingStrategy`,
  `LowercaseNamingStrategy`, and all `*Def` models (`AppMetadata`, `DatasourceDef`,
  `QueryDef`, `QueryParameterDef`, `OperationDef`, `ParameterDef`, `InputDef`,
  `SecurityDef`, `ConfigurationDef`, `FieldDef`, `EntityDef`, `PrimaryKeyDef`,
  `EntitySecurityDef`, `EntityRoleDef`, `ResolvedCatalogEntry`)
- `security.OperationPermissionEvaluator`, `EntityPermissionEvaluator`
- `service.EntityService`

**Kept public deliberately:** `Principal`, `ADMIN_ROLE`, `AccessDeniedException`,
`EntityListRequest`, `InvalidParameterException`, `InvalidOperationPathException`,
`PathHandler`, `EntityPathHandler`, `PathMappingResultBean`, `EntityPathResult`,
plus the new `CatalogSummary`/`HelianthusRuntimeBuilder` and core `DataAccessFactory`.

---

## 3. Compilation Issues Encountered (and What They Revealed)

The compiler acted as the architectural feedback mechanism described in §1.

1. **`OperationCatalogTest` (web) → internal `OperationCatalog`/`*Def`.**
   Revealed that web had a test reaching the internal catalog representation. This
   test was testing the *production* catalog's shape, which is a runtime concern.
   Resolution: moved it to `helianthus-runtime` (where `internal` is visible) and
   copied the production `operations.yml` as a runtime test fixture
   (`helianthus-runtime/src/test/resources/operations.yml`).

2. **`JdbcRowStreamTest` (web) autowired the removed `GenericDataAccess` bean.**
   Revealed that web had a test exercising the JDBC data-access path via a Spring
   bean. Resolution: it now builds `GenericDataAccess` through the public
   `DataAccessFactory` using the autowired `DataSource` — testing the same
   streaming behavior without a data-access bean.

3. **`DataSourceIntegrationTest` (web) autowired `OperationCatalog`.**
   Resolution: it now autowires `HelianthusRuntime` and asserts the runtime was
   built from the catalog (which implies the catalog loaded and validated).

4. **Three runtime tests instantiated `PostgresDialect`.**
   Revealed tests coupling to a concrete (now internal) dialect. Resolution:
   replaced with a tiny anonymous `SqlDialect` fake, which is exactly what the
   tests needed.

---

## 4. Public-Signature Leakage Removed

The following previously-public signatures exposed internal implementation types;
all are now gone from the supported surface because their owning declarations
became `internal` (or their type is no longer reachable):

- `Pipeline.execute(PipelineContext): PipelineContext`
- `PipelineFactory.createPipeline(OperationRequest): Pipeline`
- `OperationCatalog.resolveOperation(...): ResolvedCatalogEntry`
- `EntityCatalog.resolveEntity(...): EntityDef`
- `EntityCrudSqlBuilder.buildListSql(...): SqlExecutionPlan`
- `EntityService(... GenericDataAccess, Map<String, SqlDialect> ...)`

`HelianthusRuntime`'s constructor was made `internal` (it takes internal
collaborators); consumers obtain instances only through `HelianthusRuntimeBuilder`.
Its public methods reference only public types (`Principal`, `String`,
`Map<String, String>`, `EntityListRequest`, `ResultFrame`, `CatalogSummary`).

---

## 5. Final Web → Runtime/Core Import Inventory

`helianthus-web` production imports (verified after the refactor):

**Runtime:** `HelianthusRuntime`, `HelianthusRuntimeBuilder`, `CatalogSummary`,
`toPrincipal`, `EntityListRequest`, `PathHandler`, `EntityPathHandler`,
`AccessDeniedException`, `InvalidParameterException`, `InvalidOperationPathException`.

**Core:** `ResultFrame`, `ColumnNameResolver`, `EntityNotFoundException`,
`NoMappingException`.

**Absent entirely:** `GenericDataAccess`, `SqlDialect`, `SqlExecutionPlan`,
`BoundParameter`, `NamedParameterSql`, `access.impl.db.*`, `PipelineFactory`,
`PipelineContext`, any pipeline step, `EntityService`, `EntityCrudSqlBuilder`,
`OperationCatalog`, `EntityCatalog`, `CatalogLoader`, evaluators.

---

## 6. Bootstrap / Data-Access Findings

The previous bootstrap (`CatalogConfig` + `RuntimeConfig`) wired data access and
pipeline internals directly in web. That coupling is now gone:

- `CatalogConfig` builds a `HelianthusRuntime` via `HelianthusRuntimeBuilder`
  using only the catalog `Resource` and the `Map<String, DataSource>` bean, then
  exposes `HelianthusRuntime`, `PathHandler`, and `EntityPathHandler` beans.
- `RuntimeConfig` was deleted.
- `CatalogController` consumes `runtime.catalogSummary(...)` instead of
  `OperationCatalog`/`EntityCatalog`/evaluators.

**`web constructs infrastructure` vs `web performs data access`:** web still
constructs the HikariCP `DataSource`s (`DataSourceConfig`, unchanged) and passes
them to the builder — this is legitimate infrastructure construction, not
use-case data access. Web performs **no** data access and references **no**
data-access implementation types.

---

## 7. Builder / Factory Decision and Rationale

Two small construction APIs were introduced, each with a concrete consumer:

**`HelianthusRuntimeBuilder` (runtime, public)** — takes `Map<String, DataSource>`
+ catalog `InputStream`, returns `HelianthusRuntime`. It is justified because it is
*clearly smaller and cleaner* than the prior bootstrap: web went from wiring
`GenericDataAccess`, `SqlDialect`, `CatalogLoader`, `PipelineFactory`, two
evaluators, `EntityService`, and `HelianthusRuntime` (≈15 cross-module imports) to
one call. It is not a general-purpose DI container and exposes no pipeline internals.

**`DataAccessFactory` (core, public)** — constructs `GenericDataAccess` + the
dialect map. It is *required*, not speculative: core's `impl.db` classes are now
`internal`, and Kotlin `internal` is module-scoped, so the runtime builder cannot
see them directly. The factory is the narrow cross-module contract that lets
runtime construct data access while hiding the JDBC/dialect implementation.

---

## 8. Remaining Accidental Public Surface

None unclassified. The following remain public **deliberately**, each with a reason:

- `DefaultRowStream` — cross-module contract: used by core's `JdbcRowStream`
  (`withSchema`/`transformRows`) and by runtime's `RowStream`/tests. It cannot be
  `internal` without breaking runtime.
- `ColumnNameResolver` — domain/result utility consumed by both runtime
  (project/filter) and web (converters).
- `PathHandler` / `EntityPathHandler` (+ their result types) — **adapter seam**:
  web controllers parse HTTP paths (`/api/op/…`, `/api/entities/…`). This parsing
  is transport-specific and currently lives in runtime; keeping these public is
  the smallest change. (Deferred finding — see §11.)

---

## 9. Final Public API Inventory

### `helianthus-core`

| Declaration | Classification |
|---|---|
| `ResultFrame`, `ResultSchema`, `ResultColumn`, `ResultMetadata`, `ResultType` | SUPPORTED API (domain/result) |
| `CloseableRowStream`, `DefaultRowStream`, `ColumnNameResolver` | CROSS-MODULE CONTRACT |
| `GenericDataAccess`, `SqlDialect`, `SqlExecutionPlan`, `BoundParameter`, `NamedParameterSql`, `DataAccessFactory` | CROSS-MODULE CONTRACT (data access) |
| `EntityNotFoundException`, `NoMappingException` | SUPPORTED API (exceptions) |

### `helianthus-runtime`

| Declaration | Classification |
|---|---|
| `HelianthusRuntime`, `HelianthusRuntimeBuilder` | SUPPORTED API |
| `Principal`, `ADMIN_ROLE`, `EntityListRequest` | SUPPORTED API |
| `CatalogSummary` + 8 nested DTOs | SUPPORTED API |
| `AccessDeniedException`, `InvalidParameterException`, `InvalidOperationPathException` | SUPPORTED API (exceptions) |
| `PathHandler`, `EntityPathHandler`, `PathMappingResultBean`, `EntityPathResult` | BOOTSTRAP/ADAPTER SEAM |

No unexplained public declaration remains.

---

## 10. Test Count / Results

`mvn test` (from `server/`): **BUILD SUCCESS — 333 tests, 0 failures, 0 errors.**

| Module | Tests | Change |
|---|---|---|
| helianthus-core | 34 | +3 (`DataAccessFactoryTest`) |
| helianthus-runtime | 187 | +17 (`HelianthusRuntimeBuilderTest` + moved `OperationCatalogTest`) |
| helianthus-web | 112 | −14 (`OperationCatalogTest` moved out) |

New focused tests: `HelianthusRuntimeBuilderTest` (3), `DataAccessFactoryTest` (3).
The 30 black-box `HttpContractCharacterizationTest` cases and the
`ResultFrameJsonContractTest` remain green — no behavioral change.

---

## 11. Deferred Architectural Findings

- **`OperationRequest.format`** is still a transport concern in the (now internal)
  pipeline model; the facade passes `"json"` as a placeholder. Unchanged (pipeline
  redesign is out of scope).
- **Path handlers as an adapter seam.** `PathHandler`/`EntityPathHandler` parse
  HTTP-specific paths and are used only by web. They could later move to
  `helianthus-web` (the transport layer), removing the adapter-seam exception;
  left in place to avoid unrelated package churn.
- **`PathMappingResultBean`** is a Java-shaped (`var` + `Serializable`) value
  object; cleaning it is unrelated Kotlin cleanup, not done here.
- **Selective core data-access visibility** (runtime-but-not-web) remains a future
  JPMS concern; Kotlin `internal` cannot express it, and no JPMS was introduced.

---

## 12. Architectural Verification

- Spring imports in core: **0** (main and test).
- Spring imports in runtime: **0** (main and test).
- Ktor dependencies: **0**.
- No new DI framework; no `module-info.java`.
- Web controllers consume the facade; web performs no data access; web no longer
  references any data-access, pipeline, catalog-internal, or evaluator type.
