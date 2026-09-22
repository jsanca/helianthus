# HEL-KOTLIN-005 — Module Public API & Encapsulation Assessment

- **Date:** 2026-09-21
- **Role:** Deep — Software Architect / Architecture Reviewer
- **Status:** Assessment complete. No production code modified.

---

## 1. Executive Summary

The physical dependency direction established in HEL-KOTLIN-004 is correct, and —
more importantly — the *behavioral* boundary already holds:

- **Web never executes data access.** `helianthus-web` contains zero references to
  `executeQueryStream`, `SqlExecutionPlan`, `buildListSql`, or `buildGetByIdSql`.
  The controllers only call the `HelianthusRuntime` facade.
- **Web never reaches pipeline internals.** Zero references to `PipelineContext`,
  the pipeline steps, or `EntityCrudSqlBuilder` in `helianthus-web`.

What remains is a **visibility problem, not a dependency problem**. Maven's default
transitive compile scope exposes every `public` Kotlin type across module
boundaries, so `helianthus-web` *can* compile against arbitrary
`helianthus-core` implementation classes (e.g. `access.impl.db.JdbcGenericDataAccess`)
even though it currently only uses them for bootstrap wiring. The accidental public
surface is large and unenforced.

Two concrete enforcements are available today and are cheaper than JPMS:

1. **Kotlin `internal`** can hide `helianthus-core` implementation (`access.impl.db`)
   from both runtime and web, and hide runtime internals (pipeline, catalog `*Def`
   models, services, evaluators) from web — because `internal` is scoped to the
   Maven compilation module.
2. **JPMS qualified exports** are the *only* mechanism that can express the
   selective rule "core's data-access contracts visible to runtime but not to web."
   But that rule currently conflicts with reality: `helianthus-web`'s bootstrap
   (`CatalogConfig`, `RuntimeConfig`) constructs and wires `GenericDataAccess` /
   `SqlDialect` / `impl.db` classes, so web legitimately needs those today.

JPMS adoption is further impeded by the third-party graph: several core/runtime
dependencies (`snakeyaml`, `networknt json-schema-validator`, `kotlin-stdlib`,
`HikariCP`, `jackson-databind` 2.x) ship **no `Automatic-Module-Name`**, so they
would become fragile, filename-derived automatic modules. Only `slf4j-api` and
`postgresql` declare one.

**Recommendation: `API_TIGHTEN_FIRST`** — the desired API is now clear, but the
accidental public surface should be cleaned up (via `internal`/package relocation
and, optionally, a runtime-side builder) before any JPMS enforcement; JPMS should
be revisited only after the Ktor migration, and may prove unnecessary.

---

## 2. Current Module Map (from HEL-KOTLIN-004)

```
helianthus-web       (Spring HTTP adapter)
    │  (Maven: helianthus-runtime)
    v
helianthus-runtime   (Helianthus application semantics)
    │  (Maven: helianthus-core)
    v
helianthus-core      (primitives + data access)
```

Package ownership (production):

| Package | Module |
|---|---|
| `helianthus.core.result` | core |
| `helianthus.core.access`, `.access.impl.db`, `.access.sql` | core |
| `helianthus.core` root exceptions (`DataAccessErrorException`, `EntityNotFoundException`, `NoMappingException`) | core |
| `helianthus.core.catalog`, `.pipeline`, `.security`, `.util`, `.service`, `.bean`, `.exception` | runtime |
| `helianthus.core` root (`HelianthusRuntime`, `InvalidParameterException`) | runtime |
| `helianthus.core.config`, `.web`, `.web.converter` | web |

---

## 3. Cross-Module Dependency Matrix

### 3.1 Web → Runtime

| Caller | Referenced type | Purpose | Legitimate? | Desired visibility |
|---|---|---|---|---|
| `HelianthusController`, `EntityCrudController` | `HelianthusRuntime` | facade (execute/list/get) | **yes** | PUBLIC |
| `EntityCrudController` | `service.EntityListRequest` | request DTO | **yes** | PUBLIC |
| `EntityCrudController` | `util.EntityPathHandler` | path parse | yes (wiring) | bootstrap-only |
| `HelianthusController` | `util.PathHandler` | path parse | yes (wiring) | bootstrap-only |
| both controllers, `CatalogController` | `security.toPrincipal` | auth adapter | **yes** | PUBLIC (adapter) |
| `HelianthusExceptionHandler` | `security.AccessDeniedException` | 403 mapping | **yes** | PUBLIC (exception) |
| `HelianthusExceptionHandler` | `InvalidParameterException` | 400 mapping | **yes** | PUBLIC (exception) |
| `HelianthusExceptionHandler` | `exception.InvalidOperationPathException` | 400 mapping | **yes** | PUBLIC (exception) |
| `CatalogConfig` | `catalog.CatalogLoader`, `catalog.LoadedCatalog` | catalog loading | yes (bootstrap) | bootstrap-only |
| `CatalogConfig`, `CatalogController` | `catalog.OperationCatalog`, `catalog.EntityCatalog` | catalog load/summary | yes | borderline (see §4) |
| `RuntimeConfig` | `pipeline.PipelineFactory` | wiring | yes (bootstrap) | bootstrap-only |
| `RuntimeConfig` | `service.EntityService` | wiring | yes (bootstrap) | bootstrap-only |
| `RuntimeConfig` | `security.OperationPermissionEvaluator`, `EntityPermissionEvaluator` | wiring | yes (bootstrap) | bootstrap-only |
| `CatalogController` | `security.OperationPermissionEvaluator`, `EntityPermissionEvaluator` | catalog visibility filter | yes | borderline |

### 3.2 Web → Core (transitive)

| Referenced type | Purpose | Legitimate? | Desired visibility |
|---|---|---|---|
| `result.ResultFrame` | facade return type → converters | **yes** | PUBLIC (core→runtime→consumer) |
| `result.ColumnNameResolver` | converters consume rows case-insensitively | borderline | domain/result utility |
| `access.GenericDataAccess` | bootstrap wiring (`CatalogConfig`, `RuntimeConfig`) | yes (wire only) | runtime-only |
| `access.SqlDialect` | bootstrap wiring (`CatalogConfig`, `RuntimeConfig`) | yes (wire only) | runtime-only |
| `access.impl.db.JdbcGenericDataAccess` | bootstrap construction (`CatalogConfig`) | yes (construct) | **internal — hidden from web** |
| `access.impl.db.PostgresDialect`, `H2Dialect` | bootstrap construction (`CatalogConfig`) | yes (construct) | **internal — hidden from web** |
| `EntityNotFoundException`, `NoMappingException` | exception mapping | **yes** | PUBLIC (exception) |
| `javax.sql.DataSource` | HikariCP construction (`DataSourceConfig`) | yes (JDK) | JDK type |

### 3.3 Runtime → Core

| Referenced type | Classification |
|---|---|
| `result.ResultFrame`, `ResultSchema`, `ResultMetadata` | legitimate domain/result contract |
| `result.CloseableRowStream` | data-access streaming contract |
| `result.DefaultRowStream` | **mild leak** — concrete in-memory stream impl used by `pipeline.RowStream` |
| `result.ColumnNameResolver` | domain/result utility (project/filter steps) |
| `access.GenericDataAccess` | legitimate data-access port |
| `access.SqlDialect` | legitimate dialect port |
| `access.SqlExecutionPlan`, `BoundParameter` | data-access execution-plan contract |
| `access.sql.NamedParameterSql` | SQL parsing utility (used by `QueryStep`) |
| `EntityNotFoundException`, `NoMappingException` | legitimate exceptions |
| `DataAccessErrorException` | **core-internal** — thrown by `JdbcGenericDataAccess`, never referenced by runtime |

**Key fact:** runtime imports **nothing** from `access.impl.db` (the HEL-KOTLIN-004
`NamedParameterSql` re-parenting closed the only prior leak).

---

## 4. Public Runtime API

Derived from the facade (`HelianthusRuntime`) and its collaborators:

| Type | Classification |
|---|---|
| `helianthus.core.HelianthusRuntime` | **PUBLIC** |
| `helianthus.core.security.Principal` | **PUBLIC** |
| `helianthus.core.security.ADMIN_ROLE` | **PUBLIC** (constant) |
| `helianthus.core.service.EntityListRequest` | **PUBLIC** |
| `helianthus.core.result.ResultFrame` (core) | **PUBLIC** (crosses runtime → consumer) |
| `helianthus.core.security.AccessDeniedException` | **PUBLIC** (exception) |
| `helianthus.core.InvalidParameterException` | **PUBLIC** (exception) |
| `helianthus.core.NoMappingException` (core) | **PUBLIC** (exception) |
| `helianthus.core.EntityNotFoundException` (core) | **PUBLIC** (exception) |
| `helianthus.core.exception.InvalidOperationPathException` | **PUBLIC** (exception) |
| `security.toPrincipal` (Spring adapter) | **PUBLIC** (web adapter, not runtime) |

**Everything else in runtime is `INTERNAL`** for API purposes, even though Kotlin
declares it `public`:

- `pipeline.*` (Pipeline, PipelineComponent, PipelineFactory, PipelineContext,
  all steps, models, operators, RowStream) — execution internals.
- `catalog.EntityCrudSqlBuilder`, `catalog.*Def` (all `*Def` data classes) — model
  internals.
- `catalog.CatalogLoader` / `LoadedCatalog` — bootstrap (needed only by web wiring).
- `security.OperationPermissionEvaluator` / `EntityPermissionEvaluator` — internal
  rules (currently reached by `CatalogController` for the catalog summary).
- `service.EntityService` — internal service (wired by web bootstrap).
- `util.PathHandler` / `EntityPathHandler` — request parsing (wired by web).
- `bean.PathMappingResultBean` — internal value object.

**Minimum set a JVM/Kotlin embedder should see:** `HelianthusRuntime`, `Principal`,
`EntityListRequest`, `ResultFrame`, and the exception types above. Nothing else.

---

## 5. Public Core API

### Domain / Result API — PUBLIC (crosses core → runtime → consumer)

- `ResultFrame`, `ResultSchema`, `ResultColumn`, `ResultMetadata`, `ResultType`.

These legitimately appear in runtime signatures (`ResultFrame` is the execution
result) and therefore in the consumer-facing API.

### Data-Access Contracts — runtime-only (core → runtime, not core → web)

- `GenericDataAccess` (port), `SqlDialect` (port), `SqlExecutionPlan`,
  `BoundParameter`, `CloseableRowStream`, `NamedParameterSql`.

### Implementation — internal (hidden from web and, ideally, most of runtime)

- `access.impl.db.JdbcGenericDataAccess`, `JdbcRowStream`, `JdbcParamBinder`,
  `PostgresDialect`, `H2Dialect`.
- `result.DefaultRowStream` (concrete in-memory stream — a small leak into runtime).
- `DataAccessErrorException` (core-internal).

### Borderline

- `result.ColumnNameResolver` — a pure result/domain utility consumed by *both*
  runtime (project/filter) and web (converters). It is genuinely part of consuming
  a `ResultFrame`, so its current visibility is defensible, but it is the one
  non-`ResultFrame` core type that web reaches for directly.

---

## 6. Accidental Public Surface (technical public, not supported API)

Today everything below is reachable from web (transitively) and from any embedded
consumer, yet none is part of Helianthus's intended API:

- **Runtime:** `pipeline.*` (all), `catalog.EntityCrudSqlBuilder`, `catalog.*Def`
  (≈15 data classes), `catalog.CatalogLoader`/`LoadedCatalog`, `security.*Evaluator`,
  `service.EntityService`, `util.PathHandler`/`EntityPathHandler`,
  `bean.PathMappingResultBean`.
- **Core:** `access.impl.db.*` (all), `access.sql.NamedParameterSql`,
  `result.DefaultRowStream`, `access.SqlExecutionPlan`/`BoundParameter`,
  `DataAccessErrorException`.

Notably, `PipelineContext` and the seven pipeline steps are fully `public` Kotlin
classes with public `process(context)` methods — an embedder could bypass the facade
entirely and drive the pipeline by hand, exactly the coupling the facade was meant
to prevent.

---

## 7. Data Access Boundary

The desired rule and its current status:

> Web does not perform data access; runtime owns execution.

**This property holds today.** `helianthus-web` never calls the data-access API for
a use case. Its only involvement is bootstrap:

- `CatalogConfig` **constructs** `JdbcGenericDataAccess` and the dialect map
  (`PostgresDialect`/`H2Dialect`), then exposes them as beans.
- `RuntimeConfig` **wires** `GenericDataAccess` and `Map<String, SqlDialect>` into
  `PipelineFactory` and `EntityService`.

The critical distinction from §5 of the task:

- **"web constructs infrastructure"** — present and legitimate. Building a
  `JdbcGenericDataAccess` and injecting it into runtime is bootstrap, not use-case
  data access.
- **"web performs data access"** — absent. No controller or converter executes a
  query.

So the *behavioral* boundary is clean; the *compile-time* boundary is not. Because
`GenericDataAccess`, `SqlDialect`, and the `impl.db` classes are `public`, web (and
any consumer) *could* import `SqlExecutionPlan`/`JdbcRowStream` and execute data
access directly — nothing prevents it.

Recommended boundary:

| Layer | May see/use |
|---|---|
| web | `HelianthusRuntime`, `Principal`, `EntityListRequest`, `ResultFrame`, exception types, `javax.sql.DataSource` |
| runtime | the above + `access.GenericDataAccess`, `SqlDialect`, `SqlExecutionPlan`, `BoundParameter`, `CloseableRowStream`, `NamedParameterSql` |
| core implementation | only runtime-facing via the ports; `impl.db` hidden from everything |

---

## 8. Transitive Maven Exposure

`helianthus-web → helianthus-runtime → helianthus-core` with default `compile`
scope means:

1. **Can web compile against arbitrary core classes today?** Yes. All `public`
   core types are on web's compile classpath transitively.
2. **Can an embedded consumer of runtime compile against core implementation?**
   Yes, for the same reason.
3. **Would changing Maven scopes help?** Only crudely. Marking core as `runtime`
   scope in runtime's POM would still put core on the *runtime* classpath but remove
   it from *compile* — which would break web's legitimate use of `ResultFrame` (a
   core type in runtime's public signatures). Marking core as `provided`/`optional`
   would make the runtime artifact unusable standalone. Maven scope cannot express
   "this type is public to runtime's signatures but not to consumers."
4. **Interference with legitimate domain objects?** Yes — `ResultFrame` must remain
   on web's compile classpath, so scope tricks can't hide core's `impl.db` without
   also hiding `ResultFrame`.

Conclusion: Maven scopes are too coarse for this boundary. The enforcement must come
from language/module visibility (Kotlin `internal`) or JPMS `exports`, not scopes.

---

## 9. Kotlin Visibility Assessment

Kotlin `internal` is scoped to a single compilation module (here: one Maven module).

What it **can** enforce today:

- Mark `access.impl.db.*` and `result.DefaultRowStream` `internal` in core → hidden
  from runtime *and* web. Runtime already doesn't import `impl.db`, so this is safe.
- Mark runtime internals (`pipeline.*`, `catalog.EntityCrudSqlBuilder`, `catalog.*Def`,
  `security.*Evaluator`, `service.EntityService`, `util.*`, `bean.*`) `internal` →
  hidden from web. Safe provided the facade's public signature only references
  public types (`Principal`, `EntityListRequest`, `ResultFrame`, exceptions).

What it **cannot** enforce:

- The selective rule "core's data-access contracts visible to runtime but not web."
  Kotlin `internal` in core would hide `access.*` from runtime too (runtime is a
  different module), which is unacceptable — runtime consumes those ports.
- Any selective *cross-module* exposure. `internal` is binary (module-wide), not
  per-consumer.

Caveat: `internal` types must not leak into `public` signatures. Several runtime
`public` classes currently reference internal-adjacent types (e.g. `OperationCatalog`
methods return `*Def` types; `PipelineFactory.createPipeline` takes
`OperationRequest`). Converting the facade's collaborators to `internal` would
require the facade to stop exposing them — which it already does — so the change is
confined to non-facade classes.

---

## 10. JPMS / Jigsaw Assessment

### Current state

- `module-info.java`: **none** in any module (verified).
- Modules are running on the **classpath**, not the module path (no `--module-path`
  or `add-modules` in any POM; `jvmTarget=21`, `release=25`).
- Automatic modules: dependencies are only automatic if they ship
  `Automatic-Module-Name` or are on the module path.

### Dependency compatibility (verified from JAR manifests)

| Dependency | Module | Automatic-Module-Name |
|---|---|---|
| `slf4j-api` | core/runtime/web | `org.slf4j` ✓ |
| `org.postgresql:postgresql` | web | `org.postgresql.jdbc` ✓ |
| `org.yaml:snakeyaml` 2.6 | runtime | **none** ✗ |
| `com.networknt:json-schema-validator` 1.5.6 | runtime | **none** ✗ |
| `com.fasterxml.jackson.core:jackson-databind` 2.21.4 | runtime | **none** ✗ |
| `org.jetbrains.kotlin:kotlin-stdlib` 2.3.10 | core/runtime/web | **none** ✗ |
| `com.zaxxer:HikariCP` 6.3.0 | web | **none** ✗ |

Five of seven checked dependencies lack `Automatic-Module-Name`; three of those
(`snakeyaml`, `networknt`, `kotlin-stdlib`) are on the **runtime** module's
classpath, and `kotlin-stdlib` is ubiquitous. They would become automatic modules
with names derived from the JAR filename (fragile across version bumps).

Spring Boot 4 / Spring Framework (web) and any future Ktor adapter run on the
classpath by default and would materially complicate module-path adoption — Spring's
DI/reflection and the servlet/embedded-server stack are not a natural JPMS fit.

### Would JPMS add enforcement value?

Yes, in principle: `exports`/`requires` would turn the boundary into compile-time
constraints that neither Maven scopes nor Kotlin `internal` can express (the
selective "runtime yes, web no" for `core.access`).

### Would it be practical now?

No — three friction sources:
1. Third-party automatic-module gaps (above) require `module-info` `requires`
   against derived names, or wrapping those JARs.
2. It would collide with the pending Spring→Ktor migration (changing the web
   adapter's module shape at the same time).
3. The current public API is not yet clean (§6), so JPMS would freeze the wrong
   surface.

---

## 11. Qualified Exports

A qualified export such as:

```java
module helianthus.core {
    exports helianthus.core.result;
    exports helianthus.core.access to helianthus.runtime;   // data-access ports
    // access.impl.db, access.sql NOT exported
}
```

would elegantly express the core data-access rule: runtime can consume
`GenericDataAccess`/`SqlDialect`/`SqlExecutionPlan`, while web and arbitrary
consumers cannot.

**Assessment: conceptually correct, currently blocked by web's bootstrap.**

`helianthus-web` references `GenericDataAccess`, `SqlDialect`, and `impl.db.*` today
(`CatalogConfig` builds the data-access bean and dialect map; `RuntimeConfig` wires
them). A qualified export `to helianthus.runtime` would make web's own bootstrap
illegal.

The clean resolution (future, out of scope) is to move runtime-object construction
into the runtime: a `HelianthusRuntimeBuilder` (or factory) that accepts a
`Map<String, javax.sql.DataSource>` (a JDK type) and returns a fully wired
`HelianthusRuntime`. Web would then reference only `HelianthusRuntime` +
`javax.sql.DataSource`, and the qualified export becomes viable and valuable.

Verdict: **useful, not premature — but it requires the builder refactor first**, so
it belongs in the "tighten" step, not as an isolated JPMS change.

---

## 12. API Leakage Analysis

Public signatures that expose internal types (all currently unenforced):

| Surface | Exposure |
|---|---|
| `Pipeline.execute(ctx: PipelineContext): PipelineContext` | `PipelineContext` is mutable internal state; public |
| `PipelineFactory.createPipeline(request: OperationRequest)` | `OperationRequest` (has unused `format` field); public |
| `PipelineComponent.process(ctx)` | step interface; public |
| `OperationCatalog.resolveOperation(...): ResolvedCatalogEntry` | returns catalog model; public |
| `EntityCatalog.resolveEntity(...): EntityDef` | returns entity model; public |
| `EntityCrudSqlBuilder.buildListSql(...): SqlExecutionPlan` | returns core execution-plan type; public |
| `*PermissionEvaluator.checkPermission(...): Boolean` | internal rule; public |
| `EntityService(...)` constructor | takes `GenericDataAccess` + `Map<String, SqlDialect>`; public |

These are architectural findings, not defects to fix here. They confirm that making
a package non-exported (JPMS) or `internal` (Kotlin) would currently break the
*facade's* ability to function only if the facade itself exposed them — it does not —
but would require the internal collaborators to stop being referenced in public
signatures (a mechanical follow-up).

---

## 13. Ktor Spike Boundary (allowed/forbidden)

The upcoming Ktor spikes must obey this surface.

**ALLOWED (consume from `helianthus-runtime`):**

- `helianthus.core.HelianthusRuntime` — `executeOperation`, `listEntities`, `getEntity`.
- `helianthus.core.security.Principal`, `ADMIN_ROLE`.
- `helianthus.core.service.EntityListRequest`.
- `helianthus.core.result.ResultFrame` (and `ResultSchema`/`ResultColumn`/
  `ResultMetadata`/`ResultType` when serializing responses).
- Exception types: `helianthus.core.security.AccessDeniedException`,
  `helianthus.core.InvalidParameterException`, `helianthus.core.NoMappingException`,
  `helianthus.core.EntityNotFoundException`,
  `helianthus.core.exception.InvalidOperationPathException`.

**FORBIDDEN (must not be referenced by a spike):**

- Any `helianthus.core.pipeline.*` type (`Pipeline`, `PipelineContext`,
  `PipelineFactory`, steps, `OperationRequest`, operators).
- `helianthus.core.catalog.EntityCrudSqlBuilder`, `EntityCrudSqlBuilder`,
  `*Def` catalog model classes.
- `helianthus.core.security.OperationPermissionEvaluator`,
  `EntityPermissionEvaluator`.
- `helianthus.core.service.EntityService`.
- Any `helianthus.core.access.*` type (`GenericDataAccess`, `SqlDialect`,
  `SqlExecutionPlan`, `BoundParameter`, `NamedParameterSql`, `impl.db.*`).

If a spike finds it needs any FORBIDDEN type, that is an explicit finding to record
and resolve before proceeding.

---

## 14. Recommendation

**`API_TIGHTEN_FIRST`.**

The architecture is sound and the behavioral boundary already holds (web does not
execute data access and never touches pipeline internals). The gap is *visibility*,
not *direction*: Maven's transitive compile scope exposes every `public` Kotlin
type, so the accidental public surface (§6) is large and unenforced.

The desired public API is now unambiguous (§4, §5), which makes a cleanup step
concrete and low-risk:

1. Mark `helianthus-core` `access.impl.db.*` and `result.DefaultRowStream` as
   `internal` (runtime already doesn't import `impl.db`).
2. Mark runtime internals (`pipeline.*`, `catalog.EntityCrudSqlBuilder`,
   `catalog.*Def`, `security.*Evaluator`, `service.EntityService`, `util.*`,
   `bean.*`) as `internal`, keeping only the facade's collaborators public.
3. Optionally introduce a runtime-side builder taking `Map<String, javax.sql.DataSource>`
   so web stops referencing core's data-access ports — which also unblocks the
   qualified-export rule (§11).

Only after this tightening — and only after the Ktor migration — should JPMS be
reconsidered. JPMS's unique value (selective "runtime yes, web no" for
`core.access`) is real but is currently blocked by web's bootstrap wiring, and its
adoption is impeded by five third-party dependencies lacking `Automatic-Module-Name`
(§10). Kotlin `internal` captures most of the value at a fraction of the cost, and
may make JPMS unnecessary — the plausible end state is `NO_JPMS`.

In short: **tighten the API now, migrate Ktor next, and revisit JPMS only if the
`internal`-based enforcement proves insufficient after Ktor.**

---

## 15. Answers to Acceptance Questions

1. **Runtime public API:** `HelianthusRuntime`, `Principal`, `EntityListRequest`,
   `ResultFrame`, and the five exception types (§4).
2. **Core public API:** domain/result (`ResultFrame`/`ResultSchema`/`ResultColumn`/
   `ResultMetadata`/`ResultType`) plus the two public exceptions; the data-access
   ports are runtime-only (§5).
3. **Core types legitimately reaching web:** `ResultFrame`, `ColumnNameResolver`,
   `EntityNotFoundException`, `NoMappingException`, `javax.sql.DataSource` (§3.2).
4. **Can web access core implementation accidentally?** Yes — transitive compile
   scope exposes all public core types; nothing prevents it (§8).
5. **Is data access consumed only by runtime?** Yes, except legitimate
   construction/wiring in `CatalogConfig`/`RuntimeConfig`; web never executes (§7).
6. **Runtime packages to hide:** `pipeline`, `catalog` (minus `CatalogLoader` for
   bootstrap), `security` evaluators, `service`, `util`, `bean` (§4).
7. **Core packages to hide:** `access.impl.db`, `result.DefaultRowStream` (§5).
8. **Would JPMS enforce meaningful rules we can't enforce today?** Yes — the
   selective `core.access → runtime-only` rule, which Kotlin `internal` cannot
   express (§9–§11).
9. **Are qualified exports useful?** Yes for `core.access to runtime`, but blocked
   until web's bootstrap wiring is moved into runtime (§11).
10. **Ktor spike surface:** enumerated in §13.
11. **JPMS before/after Ktor/not at all:** tighten first; JPMS after Ktor, possibly
    never (§14).
