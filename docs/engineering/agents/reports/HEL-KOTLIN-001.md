# HEL-KOTLIN-001 — Kotlin-Native Architecture & Ktor Migration Assessment

- **Date:** 2026-09-20
- **Role:** Software Architect / Architecture Reviewer
- **Status:** Assessment complete. No production code was modified.

---

## 1. Executive Summary

Helianthus is **not** deeply coupled to Spring. The Java → Kotlin migration
succeeded at the *language* level, and — partly by accident, partly by design —
the code already has a clean seam between a framework-independent core and a thin
Spring MVC "shell".

- `helianthus-core` (~737 LOC) is already framework-independent: it depends only on
  `javax.sql.DataSource` (JDK JDBC) and SLF4J. No Spring import anywhere in the module.
- In `helianthus-web` (~2,974 LOC), the domain logic — catalog model, operation
  resolution, the execution pipeline, entity SQL generation, path parsing, and the
  permission *rules* — is written as plain Kotlin. Only the HTTP/security/configuration
  boundary depends on Spring.
- Spring's heavyweight features are almost entirely **unused**: no transactions
  (`@Transactional`), no `@Async`/`@Scheduled`, no caching, no `JdbcTemplate`/Spring
  Data, no ORM. The data layer is raw JDBC. The application is read-only.

The strongest coupling point is **Spring Security**: the permission evaluators take
`org.springframework.security.core.Authentication` as a parameter, and every controller
reads `SecurityContextHolder`. That single type threads Spring into the application
layer and is the main thing a Ktor migration would have to abstract away.

Answering the task's core question directly: **Spring is primarily a replaceable
runtime shell around a clean core**, not a deep architectural foundation. A Ktor
migration is *technically feasible* with bounded, well-understood work. However, four
specific questions are unresolved and require small experiments before committing.
The recommendation is therefore **INVESTIGATE** — with a concrete, enumerated spike
list — and to begin the gardening/characterization work immediately, since that safety
net is a prerequisite for *any* runtime change and is valuable regardless of the final
decision.

---

## 2. Current Architecture

Two Maven modules (`server/pom.xml`):

```
server/pom.xml                 parent POM (Spring Boot BOM, Java 25 / Kotlin 2.3.21)
├── helianthus/                helianthus-core  — no Spring; interfaces, JDBC, result types
└── helianthus-web/            helianthus-web   — Spring Boot app, HTTP layer, pipeline
```

`helianthus-core` has no module dependencies beyond the JVM and SLF4J.
`helianthus-web` depends on `helianthus-core`.

### Runtime request flow (operations)

```
GET /api/op/{operationId}/{configurationId}.{format}
  → HelianthusController            (@RestController, servlet, HttpServletRequest)
  → PathHandler.parsePath()         (pure string parsing → PathMappingResultBean)
  → OperationPermissionEvaluator    (Spring Security Authentication → roles)
  → OperationCatalog.resolveOperation()
  → PipelineFactory → Pipeline
      ResolveStep → BindStep → QueryStep → ProjectStep → FilterStep → LimitStep → ToResultFrameStep
  → ResultFrame → HttpMessageConverter (JSON/XML/HTML/CSV)
```

### Runtime request flow (entities)

```
GET /api/entities/{entityName}.{format}            (list)
GET /api/entities/{entityName}/{id}.{format}       (get by PK)
  → EntityCrudController
  → EntityPathHandler
  → EntityPermissionEvaluator
  → EntityCatalog
  → EntityCrudSqlBuilder (SQL generation via SqlDialect)
  → JdbcGenericDataAccess → ResultFrame → converter
```

### Key structural facts

- **Catalog is YAML-driven.** `operations.yml` (top-level `app`, `datasources`,
  `queries`, `operations`, `entities`) is parsed by `CatalogConfig` into
  `OperationCatalog` / `EntityCatalog`. Parsing is done with SnakeYAML + unchecked
  casts, and validated against a JSON Schema (`schemas/operations.schema.json`).
- **The pipeline is a small hand-rolled chain.** `PipelineComponent.process(ctx)`
  threads a mutable `PipelineContext` through 7 steps. Errors are captured in
  `context.error` and re-thrown by the controller.
- **Data access is raw JDBC**, not Spring JDBC. `JdbcGenericDataAccess` opens a
  `Connection`/`PreparedStatement`/`ResultSet`, wraps rows in a lazy `Sequence`
  (`JdbcRowStream`), and returns a `CloseableRowStream`. `SqlDialect` (`PostgresDialect`
  / `H2Dialect`) provides identifier quoting and LIMIT/OFFSET.
- **Output** is a `ResultFrame` (`schema` + `rows` + `metadata`) rendered to four
  formats. JSON is produced by Spring Boot's auto-configured Jackson converter; XML,
  CSV and HTML by three custom `HttpMessageConverter`s registered in
  `HelianthusWebConfiguration`.
- **Security** is Spring Security: stateless, JWT OIDC resource server, Keycloak
  `realm_access.roles` mapped to `ROLE_*` authorities, CORS, CSRF disabled.

---

## 3. Spring Responsibility Inventory

What Spring currently does for Helianthus, with the evidence for each item:

| Responsibility | Provided by | Evidence |
|---|---|---|
| Application bootstrap | `@SpringBootApplication` + `runApplication` | `HelianthusApplication.kt` |
| Dependency injection / lifecycle | `@Configuration`, `@Bean`, `@Component`, `@Primary`, `@Qualifier`, constructor injection | `CatalogConfig`, `DataSourceConfig`, `SecurityConfig` |
| HTTP routing / controllers | `@RestController`, `@GetMapping("/api/op/**")`, `@GetMapping("/api/entities/**")` (servlet MVC) | `HelianthusController`, `EntityCrudController`, `CatalogController`, `HealthController` |
| Request/response binding | `HttpServletRequest`, `ResponseEntity`, `produces = [...]` | all controllers |
| Content negotiation | `ResponseEntity.contentType` + message-converter selection | controllers, `HelianthusWebConfiguration` |
| JSON serialization | Spring Boot auto `MappingJackson2HttpMessageConverter` (Jackson) | implicit; no custom JSON converter registered |
| XML/CSV/HTML serialization | custom `AbstractHttpMessageConverter` subclasses | `web/converter/*` |
| Configuration loading | `@Value`, `application.yml`, env-var placeholders, `Resource` (`classpath:`/`file:`) | `application.yml`, `CatalogConfig`, `SecurityConfig`, `DataSourceConfig` |
| YAML handling | SnakeYAML (independent lib), wired by Spring | `CatalogConfig.loadCatalogData()` |
| Validation | JSON Schema via `networknt` (independent) + custom semantic checks; **no** Bean Validation | `CatalogConfig.validateSchema()`, `EntityCatalog.validate()` |
| Exception → HTTP status | `@RestControllerAdvice` + `@ExceptionHandler` + `ResponseStatusException` | `HelianthusExceptionHandler` |
| Authentication / authorization | Spring Security: JWT OIDC resource server, Keycloak realm-role extraction, stateless session, CORS, CSRF off | `SecurityConfig` |
| Permission integration | custom evaluators, but typed against Spring `Authentication` | `OperationPermissionEvaluator`, `EntityPermissionEvaluator` |
| DB connectivity | HikariCP (manually constructed beans; **not** Spring Boot DataSource autoconfig) | `DataSourceConfig` |
| JDBC abstraction | **none** — raw JDBC | `JdbcGenericDataAccess` (in core, no Spring) |
| Transactions | **none** | no `@Transactional` anywhere |
| Async / scheduling / caching | **none** | no `@Async`/`@Scheduled`/`@Cacheable` anywhere |
| Health endpoints | Actuator (`/actuator/health`, `/actuator/info`) + custom `HealthController` (`/health`) | `SecurityConfig`, `HealthController` |
| Observability | Actuator + SLF4J + a hand-written MDC filter | `RequestLoggingFilter`, `application.yml` |
| Logging | SLF4J (independent) | all classes |
| Testing | `spring-boot-starter-test`, `@SpringBootTest`, `@Sql`, `@LocalServerPort`, `RestTemplate`, `MockMvc` | test sources |
| Environment/profile | `application.yml` + `${ENV_VAR:default}` placeholders | `application.yml` |
| Packaging | `spring-boot-maven-plugin` `repackage` (fat jar) + `build-image` (Paketo) | `helianthus-web/pom.xml`, `scripts/build-paketo-server.sh` |

Notable absences: no Spring Data, no `JdbcTemplate`, no `@Transactional`, no
`@Async`/`@Scheduled`, no Bean Validation, no Thymeleaf. Spring's high-ceremony
features are unused; the app leans on Spring MVC + Spring Security + DI + config
loading, all of which are the "shallow" parts.

---

## 4. Coupling Map

### A — Framework-independent (no Spring, no servlet, no Spring Security)

**`helianthus-core` module — entirely framework-independent (~737 LOC):**

- `result/` — `ResultFrame`, `ResultSchema`, `ResultColumn`, `ResultMetadata`,
  `ResultType`, `CloseableRowStream`, `DefaultRowStream`, `ColumnNameResolver`
- `access/` — `GenericDataAccess` (interface), `SqlExecutionPlan`, `SqlDialect`,
  `impl/db/` `JdbcGenericDataAccess`, `JdbcRowStream`, `JdbcParamBinder`,
  `NamedParameterSql`, `H2Dialect`, `PostgresDialect`
- exceptions — `NoMappingException`, `DataAccessErrorException`, `EntityNotFoundException`
  (and `IncongruentColumnValueLengthException`, which is dead code)

Dependency check: only `javax.sql.*` (JDK) and `org.slf4j` imports. Verified — no
`org.springframework.*` import anywhere in the module.

**`helianthus-web` — framework-independent *logic* (Spring only via a `@Component`
marker, removable in a line):**

- `catalog/` — `OperationCatalog`, `EntityCatalog`, `EntityCrudSqlBuilder`,
  `PhysicalColumnNamingStrategy` (all pure Kotlin; `OperationCatalog` defines every
  `*Def` data class)
- `pipeline/` — `Pipeline`, `PipelineFactory`, `PipelineComponent`, `PipelineModels`
  (incl. `PipelineConfig.fromYamlSteps`), all seven steps, `FilterOperatorRegistry` +
  seven operators, `RowStream`
- `util/` — `PathHandler`, `EntityPathHandler` (pure string parsing)
- `bean/PathMappingResultBean` (framework-independent, but Java-shaped — see §6)

### B — Framework adapters (Helianthus responsibility, implemented with Spring)

- `web/HelianthusController` — operation endpoint → pipeline → `ResponseEntity<ResultFrame>`
- `web/EntityCrudController` — entity list/get → SQL builder → `ResponseEntity<ResultFrame>`
- `web/CatalogController` — `/api/admin/catalog` summary (operations + entities)
- `web/HealthController` — `/health`
- `web/HelianthusExceptionHandler` — exception → HTTP status/body mapping
- `web/converter/*` — `ResultFrameHtmlMessageConverter`, `ResultFrameCsvMessageConverter`,
  `ResultFrameXmlMessageConverter`, `ResultFrameHtmlRenderer`
- `config/HelianthusWebConfiguration` — `WebMvcConfigurer` registering converters

### C — Spring infrastructure (exists because of Spring)

- `config/SecurityConfig` — JWT OIDC + role extraction + CORS + stateless session
- `config/DataSourceConfig` — HikariCP beans + `Map<String, DataSource>`
- `HelianthusApplication` — bootstrap
- `config/CatalogConfig` — **mostly C, with D elements**: Spring `@Value` resource
  loading + `@Bean` wiring, but the actual YAML→`*Def` parsing is pure Helianthus logic
  (344 lines, dominated by `parse*` functions using unchecked casts)

### D — Ambiguous / Mixed

- `security/OperationPermissionEvaluator` (77 LOC) and `security/EntityPermissionEvaluator`
  (58 LOC) — **the logic is framework-neutral** (pure role-set comparison against a
  catalog), but the method signature takes `org.springframework.security.core.Authentication`
  and reads `.authorities`. To become category A they need a tiny `Principal`/roles
  abstraction (see §9.2).
- `web/RequestLoggingFilter` — MDC request-correlation logic (neutral) inside a
  `OncePerRequestFilter` (Spring).
- `config/CatalogConfig` — as noted above.

### Dead code (removable now, unrelated to Ktor but relevant to surface estimate)

- `marshall/` — `ResultFrameMarshaller`, `JacksonResultFrameMarshallFormatter`,
  `MarshallFormatterException` (and its Java test). The JSON path actually uses Spring's
  auto Jackson converter; the "marshaller" architecture is a pre-migration leftover.
  Referenced only by its own Java test.
- `bean/QueryConfigBean`, `bean/QueryParameterBean` — legacy JavaBean-shaped holders;
  no production references (confirmed by grep).
- `helianthus-core` `IncongruentColumnValueLengthException` — no references.

---

## 5. Dependency Analysis

Production dependencies and their Ktor-era replacements:

| Dependency (from `helianthus-web/pom.xml`) | What Helianthus gets from it | Ktor / plain-Kotlin replacement |
|---|---|---|
| `spring-boot-starter-web` | Tomcat + Spring MVC + Jackson JSON | `ktor-server-core` + `ktor-server-netty` (or CIO); `ktor-serialization-jackson` **or** `kotlinx-serialization-json` |
| `spring-boot-starter-jdbc` | HikariCP transitively + `spring-jdbc` | **HikariCP alone** (Helianthus never uses `JdbcTemplate`) |
| `postgresql` (runtime) | PG driver | unchanged |
| `h2` (test) | test DB | unchanged |
| `jackson-dataformat-xml` (`tools.jackson`) | XML output | keep (Ktor has no built-in XML) |
| `commons-csv` | CSV output | keep (used by the CSV converter's logic) |
| `json-schema-validator` (networknt) | catalog JSON-Schema validation | unchanged (pure lib) |
| `spring-boot-starter-security` | SecurityFilterChain, CORS, `Authentication` | `ktor-server-auth` + `ktor-server-cors` |
| `spring-boot-starter-oauth2-resource-server` | JWT OIDC resource server | `ktor-server-auth-jwt` (reimplement Keycloak `realm_access` → `ROLE_*` extraction) |
| `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/info` | custom `/health` route (already exists) + optional `ktor-server-metrics` |
| `spring-boot-starter-test`, `spring-security-test` | integration/unit test harness | `ktor-server-test-host` + JUnit/Kotlin test |
| `kotlin-stdlib`, `kotlin-test` | language | unchanged |
| `slf4j-api` | logging | unchanged |
| (no DI framework today — Spring does DI) | constructor injection | Koin (`koin-ktor`) **or manual wiring** — manual is viable at this size |

Two observations worth calling out:

1. **No Spring dependency needs a *direct* Ktor equivalent in most cases.** YAML,
   JSON Schema, CSV, XML, JDBC, HikariCP and SLF4J are all independent libraries that
   carry over unchanged. The only genuinely Spring-specific capabilities to replace are
   MVC routing/converters, DI, Security, config loading, and the test harness.
2. **`spring-boot-starter-jdbc` is effectively unused** — Helianthus constructs
   `HikariDataSource` manually and never touches `JdbcTemplate`. It could be replaced by
   a direct `com.zaxxer:HikariCP` dependency with no behavior change.

---

## 6. Kotlin-Native Opportunities

This section distinguishes **language migration** (Java syntax in Kotlin) from
**idiomatic Kotlin design**. Only items that solve an observed problem are listed.

### 6.1 Java-shaped remnants (language migration, low value individually)

- **JavaBean data classes**: `PathMappingResultBean`, `QueryConfigBean`,
  `QueryParameterBean` use `var` + `@JvmOverloads` + `implements Serializable` +
  `ArrayList`. `PathMappingResultBean` is a `var`-mutable holder for three values that
  never change after parsing — it should be a plain immutable `data class`.
- **`@JvmOverloads`** on `ResultFrame`, `ResultMetadata`, `ResultColumn`, `DataAccessErrorException`
  exist only for Java callers. No Java *production* code remains (only Java *tests*).
  Once the last Java tests are converted, these can go.
- **`@JvmStatic`** on `JdbcRowStream.buildSchema` and `ResultFrameMarshaller : Serializable`
  are Java-interop/Java-serialization artifacts.
- **Java-style overloads**: `ResultSchema.getColumn(name)` and `getColumn(index)`.
- **Manual `closeQuiet`** resource management in `JdbcGenericDataAccess` and
  `JdbcRowStream` (though correct for streaming, it is the Java idiom; `use {}` is
  already used elsewhere, e.g. `ToResultFrameStep`).

### 6.2 Idiomatic-Kotlin opportunities (real design problems)

1. **Mutable `PipelineContext` + error-in-context (highest-value change).**
   `PipelineContext` is a `data class` with `var` fields (`resolvedOperation`,
   `boundParameters`, `rowStream`, `resultFrame`, `error`) and `Pipeline.execute` is an
   imperative `var current` loop that catches exceptions into `context.error` and
   `break`s. The controller then re-throws `result.error`. This is Java-style
   control flow. An idiomatic replacement: each step is a pure function
   `(state) -> state` or the pipeline returns a sealed `PipelineResult` (`Success(frame)`
   / `Failure(error)`), with exceptions propagating normally. This removes the
   nullable-state tango and the `error` field entirely.

2. **Spring `Authentication` leaking into the domain layer.** Both permission
   evaluators take `org.springframework.security.core.Authentication` (see §4-D).
   Extracting a framework-neutral `Principal` (name + `Set<String> roles`) would make the
   entire `security/` package category-A and is a prerequisite for a portable core.

3. **Blocking JDBC with no concurrency model.** The data path is synchronous, blocking
   JDBC wrapped in a lazy `Sequence` — sensible for a servlet/thread-per-request model,
   but there is no `suspend`/`Flow` anywhere. A Ktor migration forces this decision
   (see §7, §13). This is the main architectural fork, not a cosmetic one.

4. **Unchecked-cast YAML parsing.** `CatalogConfig` is 344 lines, most of it
   `parse*` functions full of `@Suppress("UNCHECKED_CAST")` and `as?`. A typed
   deserialization approach (kotlinx.serialization or Jackson `readTree` with a strict
   model, or a small type-safe builder) would remove most of it and strengthen the
   catalog contract. This is independent of the Ktor question.

5. **Case-insensitive column resolution per cell.** `ColumnNameResolver.getRowValueOrThrow`
   does a linear key scan per cell (`row.keys.find { it.equals(columnName, ignoreCase = true) }`),
   called once per column per row by `ProjectStep`/`FilterStep`/converters. Precomputing
   a normalized (lowercased) index once per schema would remove repeated O(columns) scans.
   (Minor, but a real hot path.)

6. **Per-request pipeline allocation.** `PipelineFactory.createPipeline` allocates seven
   step objects per request. A shared, stateless step template (or `object`s for the
   stateless steps) would make the pipeline a static graph.

7. **`FilterOperator` is already good Kotlin** — `object` singletons keyed by `key`
   via `associateBy`. This is the pattern the rest of the code should converge toward
   (and evidence the codebase is *able* to be idiomatic where it matters).

No sealed-class/`when`-exhaustiveness, value-class, or DSL opportunities are recommended
that are not already covered above — the code is small enough that the highest-value
idiomatic wins are the context/error model, the security abstraction, and the parsing.

---

## 7. Ktor Capability Mapping

| Capability | Current (Spring) | Ktor / Kotlin replacement | Difficulty | Notes |
|---|---|---|---|---|
| App modules / bootstrap | `@SpringBootApplication` | Ktor `embeddedServer` / application module | trivial | |
| DI | constructor injection via `@Bean`/`@Component` | **manual wiring** (recommended at this size) or Koin | trivial | ~15 beans, no conditional wiring |
| Routing | `@GetMapping("/api/op/**")` etc. | `routing { get("/api/op/{...}") }` | trivial | wildcard tail + format parsing already happens in `PathHandler` (portable) |
| Content negotiation (4 formats) | `produces` + `HttpMessageConverter` | custom: parse `.json/.xml/.csv/.html` extension, select a per-format responder | moderate | JSON/XML/CSV/HTML renderers are already isolated and portable |
| JSON serialization | auto Jackson | `ktor-serialization-jackson` or `kotlinx-serialization-json` | trivial | `ResultFrame` is a simple data class; either works |
| XML serialization | custom converter + `tools.jackson` | keep `jackson-dataformat-xml`, wrap as a responder | straightforward | |
| CSV serialization | custom converter + `commons-csv` | keep logic, wrap as responder | straightforward | |
| HTML serialization | custom converter + renderer | keep `ResultFrameHtmlRenderer`, wrap as responder | straightforward | `HtmlUtils.htmlEscape` (Spring) → plain `StringEscapeUtils` or hand-rolled |
| Authentication (JWT/OIDC) | `oauth2-resource-server` | `ktor-server-auth-jwt` | moderate | **must reimplement Keycloak `realm_access.roles` → `ROLE_*`** (currently 10 lines in `SecurityConfig`) |
| Authorization | custom evaluators | evaluators unchanged once `Authentication` → `Principal` | trivial | §9.2 refactor |
| CORS | Spring `CorsConfigurationSource` | `ktor-server-cors` | trivial | |
| Configuration | `@Value` + `application.yml` + `Resource` | Ktor `application.conf` (HOCON) or a plain env-var map | straightforward | config is 30 lines of `application.yml` |
| Exception → status | `@RestControllerAdvice` | `StatusPages` | trivial | the mapping rules in `HelianthusExceptionHandler` port directly |
| DB access | raw JDBC + HikariCP (already framework-free) | unchanged | trivial-to-moderate | see coroutines row |
| Transactions | n/a | n/a | n/a | |
| Coroutines / streaming | blocking `Sequence` on servlet threads | decide: run JDBC on `Dispatchers.IO` and expose a suspending `Flow`, or keep blocking | **moderate** | the one real architectural decision — §13 |
| Testing | `@SpringBootTest` + `RestTemplate` | `ktor-server-test-host` + `HttpClient` | moderate | rewrite the 3 integration suites, or replace with HTTP-level contract tests |
| Lifecycle | `SmartInitializingSingleton` | Ktor module init | trivial | |
| Health/observability | Actuator + custom `/health` | custom `/health` route (exists) + `ktor-server-call-logging` | straightforward | `/actuator/health` is not part of the product contract |

**Blockers:** none identified. The one item flagged "moderate" that could become a
blocker if underestimated is the JWT/Keycloak integration — it depends on the exact
token claims and on `ktor-server-auth-jwt`'s role extraction, which should be spiked.

---

## 8. Functional Safety Analysis

Behavior that must remain byte-for-byte (or contract-equivalent) unchanged, and how well
it is currently protected.

### Must preserve

1. **API contracts** — `/api/op/{op}/{config}.{format}` (config optional, defaults to
   `default`), `/api/entities/{entity}.{format}`, `/api/entities/{entity}/{id}.{format}`,
   `/api/admin/catalog`, `/health`.
2. **ResultFrame JSON shape** (`schema.columns[].name/type/nullable`, `rows[]` maps,
   `metadata.rowCount`) — the client UI and any JSON consumer depend on it.
3. **Format behaviors** — JSON (array-of-maps via Jackson), XML (`metadata` + `rows/row`
   structure with sanitized tag names), CSV (header row + minimal quoting), HTML (table).
4. **Error semantics** — `400`/`401`/`403`/`404`/`500` with **plain-text** bodies:
   `Missing required parameter`, `Operation not found`, `Access denied`,
   `Internal server error`, etc. (`HelianthusExceptionHandler`).
5. **Permission model** — `ROLE_ADMIN` bypass; operation roles = op-level ∪ config-level;
   entity `security.read.roles`; empty roles ⇒ any authenticated user.
6. **Parameter binding** — positional `?` binds only non-null in declaration order;
   named `:param` binds all (null for missing); type coercion (`BindStep.coerceType`).
7. **SQL generation & safety** — identifier quoting via `SqlDialect`, LIMIT/OFFSET,
   filter/orderBy whitelisting against `fields`, `PreparedStatement` only.
8. **Case-insensitive column matching** (`ColumnNameResolver`).
9. **Catalog YAML schema + JSON-Schema validation** (fail fast at startup).
10. **Streaming** — `fetchSize` 1000 default, lazy row materialization.
11. **CORS** — allowed origins, `GET`/`OPTIONS`, `Authorization` header exposure.

### Coverage assessment

| Area | Status |
|---|---|
| HTTP happy paths (ops) | **covered** — `StarterOperationsSmokeTest` (24 tests), but asserts substrings, not full shape |
| HTTP happy paths (entities) | **covered** — `EntityCrudSmokeTest` (13 tests), same substring caveat |
| AuthN/AuthZ (401/403/roles) | **covered** — `SecurityIntegrationTest` (9 tests) |
| Catalog loading/validation | **covered** — `OperationCatalogTest`, `EntityCatalogTest` (unit) |
| Entity SQL generation | **covered** — `EntityCrudSqlBuilderTest` (unit) |
| Pipeline steps | **covered** — `PipelineStepTest`, `BindStepTest`, `QueryStepTest`, `FilterOperatorRegistryTest`, `CaseInsensitiveColumnTest` (unit) |
| `OperationPermissionEvaluator` | **gap** — no dedicated unit test; only indirectly via smoke/security suites |
| JSON serialization of `ResultFrame` | **gap** — relies on Spring's default Jackson; no contract test; the one JSON test (`JacksonResultFrameMarshallFormatterTest.java`) tests *dead* code |
| XML/CSV/HTML serialization | **covered** — `ResultFrame{Xml,Csv,Html}MessageConverterTest` (unit) |
| Path parsing | **covered** — `PathHandlerTest`, `EntityPathHandlerTest` |
| Named-parameter SQL | **covered** — `NamedParameterSqlTest` |
| Streaming/JDBC | **covered lightly** — `JdbcRowStreamTest` (Java), `DataSourceIntegrationTest` (Java) |
| Asynchronous behavior | n/a — no async |

Net: unit coverage of the *core logic* is good and is largely framework-independent
(it will survive a migration). The gaps are at the **HTTP contract layer** (substring
assertions instead of golden fixtures) and around **JSON output and the operation
permission evaluator**.

---

## 9. Required Gardening / Test Preparation

Prerequisites before any runtime change. **Not implemented in this task.**

1. **Characterization / contract tests at the HTTP level (highest priority).** Record
   golden request→(status, content-type, body) fixtures for a representative matrix:
   every format × a few operations × a few entities × the error cases (400/401/403/404).
   These must be written against the *running server* (black-box HTTP), not `@SpringBootTest`
   internals, so they are framework-agnostic and can gate a Ktor re-implementation.
   This converts today's substring assertions into a real safety net.

2. **Extract a framework-neutral security principal.** Define
   `Principal(name: String, roles: Set<String>)` (or a tiny `Roles` abstraction) and make
   `OperationPermissionEvaluator`/`EntityPermissionEvaluator` depend on it instead of
   Spring `Authentication`. A one-line adapter in the controller converts Spring's
   `Authentication` → `Principal`. This moves `security/` to category A and is the key
   enabler for a portable core. (Classified: small, safe, framework-neutral.)

3. **Delete dead code** — `marshall/` package, `bean/QueryConfigBean` +
   `QueryParameterBean`, `IncongruentColumnValueLengthException`, and their tests. This
   removes the misleading "marshaller" architecture and reduces migration surface.

4. **Add unit tests for `OperationPermissionEvaluator`** (role union, ADMIN bypass,
   empty-roles pass-through, config-level roles).

5. **Add a JSON serialization contract test** for `ResultFrame` (schema + rows +
   metadata), pinning the exact JSON shape the client consumes.

6. **Resolve the blocking-vs-coroutine data-access policy** as a *documented decision*
   (not code) — it determines whether `GenericDataAccess` gains a suspending variant.

---

## 10. Runtime & Packaging Impact

Treat the Paketo/native work as useful experimentation, not sunk cost.

| Concern | Impact if Spring is removed |
|---|---|
| **Paketo buildpacks** | The current path is `spring-boot:build-image`, which relies on Spring Boot's layered-jar layout and `BP_SPRING_AOT_ENABLED`/`BP_JVM_CDS_ENABLED` hooks. With Ktor, `build-image` has no equivalent goal. The *concepts* survive (CNB buildpacks, Paketo memory calculator, SBOM via Syft, layered images) but would be driven by `pack` CLI or a plain Dockerfile + a fat-jar/shadow build. |
| **Current container build** | `server/Dockerfile` is framework-agnostic (runs `java -jar app.jar`). Reusable with a Ktor fat jar produced by the Gradle Shadow plugin (or `maven-shade-plugin`). |
| **Native image (GraalVM)** | Spring Boot 4 uses Spring AOT. Ktor is designed to be reflection-free, so a Ktor/kotlinx.serialization stack is *plausibly* a cleaner native-image target — **but** `HikariCP`, the PG JDBC driver, SnakeYAML and `commons-csv` all need GraalVM reachability config. Unverified hypothesis; must be spiked. |
| **CRaC** | CRaC (Coordinated Restore at Checkpoint) is JVM-level and framework-agnostic; Spring Boot ships CRaC auto-config, Ktor does not. The *concept* survives; Spring's CRaC hooks do not. A Ktor app would need manual checkpoint integration. |
| **JVM deployment** | Unchanged — a runnable fat jar either way. |
| **Dev workflow** | `mvn spring-boot:run` → `./gradlew run` / `./mvnw exec` or the `application`/`ktor` Gradle plugin; continuous reload via Gradle `-t`. |
| **Observability** | Actuator endpoints `/actuator/health`, `/actuator/info` are internal, not part of the product contract; the custom `/health` route already exists and is portable. |

Runtime optimization opportunities Ktor would introduce/preserve: coroutine-native
servers (Netty/CIO) with a non-blocking I/O model for high-concurrency read loads, and
a leaner, reflection-light classpath that improves native-image feasibility. These are
*hypotheses* pending benchmarks — none are claimed as certain gains.

---

## 11. Candidate Target Architecture

Derived from the repository, not a template. Boundaries:

```
                          HTTP (Netty / CIO)
                                 │
                     Ktor Routing + format responders
                                 │
                        Runtime Adapters
                 (request parsing, Principal extraction,
                  exception → StatusPages, CORS)
                                 │
                     Application Services
              OperationService        EntityService
                                 │
             ┌────────────────────┴────────────────────┐
             │                                         │
      Helianthus Core (framework-independent, already exists)
      catalog models · pipeline steps · permission rules ·
      ResultFrame/ResultSchema · SqlDialect · NamedParameterSql
             │                                         │
      ┌──────┴─────────────────────────────────────────┴──────┐
      │  Persistence (raw JDBC + HikariCP — already independent) │
      └───────────────────────────────────────────────────────┘
```

- **Runtime/framework layer** (new): Ktor `Application` module, routing, `StatusPages`,
  `ktor-server-auth-jwt`, CORS, config loading.
- **Application services** (thin, new): `OperationService` and `EntityService` translate
  an HTTP request into a core call and return a `ResultFrame`; they replace the two
  controllers' orchestration but not their logic.
- **Helianthus core** (unchanged, mostly): today's `helianthus-core` module plus the
  framework-independent parts of `helianthus-web` (`catalog`, `pipeline`, `util`,
  `security` once de-coupled from `Authentication`). Ideally these would be *moved* into
  the core module (or a new `helianthus-app` module) to make the boundary physical, not
  just conceptual.
- **Persistence** (unchanged): raw JDBC + HikariCP behind `GenericDataAccess`.

---

## 12. Migration Surface

Concrete estimate from repository evidence (LOC = lines of Kotlin/Java, measured).

| Category | Files | ~LOC | Action |
|---|---|---|---|
| Framework-independent core | `helianthus-core` module | 737 | keep unchanged |
| Framework-independent web logic | `catalog/*` (4), `pipeline/*` (~21), `util/*` (2), `security/*` logic | ~1,500 | keep, strip `@Component`, adapt `Authentication` type |
| **Spring-coupled — must rewrite/adapt** | `web/HelianthusController` (128), `EntityCrudController` (207), `CatalogController` (154), `HealthController` (13), `HelianthusExceptionHandler` (110), `converter/*` (4, ~162), `config/SecurityConfig` (101), `config/DataSourceConfig` (77), `config/HelianthusWebConfiguration` (18), `HelianthusApplication` (11), `web/RequestLoggingFilter` (77), `config/CatalogConfig` (344, parsing is portable, wiring is not) | ~1,400 | rewrite the boundary, reuse inner logic |
| Dead code to delete | `marshall/*` (40), `bean/Query*Bean` (36), `IncongruentColumnValueLengthException` (3) | ~80 | delete |
| Build/config | `pom.xml` ×3, `application.yml`, `Dockerfile` (minor), `scripts/build-paketo-server.sh`, `docker-compose.starter.paketo.yml` | — | replace/rewrite |
| Tests — framework-bound | `StarterOperationsSmokeTest`, `EntityCrudSmokeTest`, `SecurityIntegrationTest` + 4 Java `@SpringBootTest` suites | ~830 + Java suites | convert to characterization/contract tests |
| Tests — framework-free (survive) | unit tests (`catalog`, `pipeline`, `util`, `security`, `converter`, `NamedParameterSql`, `ColumnNameResolver`) | ~3,400 | keep |

**Bottom line:** roughly **2,200 of ~3,700 production LOC (≈60%) survive unchanged**;
the coupled surface is ~1,400 LOC to adapt plus ~830+ LOC of integration tests to
convert. The answer to the task's question is: **Spring is a replaceable runtime shell,
not a deep embed** — the one deep reach is Spring Security's `Authentication` type, which
is isolated to three files and trivial to abstract.

---

## 13. Risks / Unknowns

1. **Blocking-JDBC vs coroutines (architectural, unresolved).** The pipeline streams
   rows via a blocking `Sequence`. On Ktor, either (a) keep blocking and run handlers on
   `Dispatchers.IO`/a thread pool (simplest, preserves current semantics), or (b) expose
   a suspending `Flow` and push JDBC onto `Dispatchers.IO`. Choice (b) is more idiomatic
   but changes the `GenericDataAccess` contract and touches the pipeline. **Must be
   spiked before committing.**

2. **Keycloak JWT role extraction on `ktor-server-auth-jwt`.** The current mapping
   (`realm_access.roles` → `ROLE_*`) is 10 lines in `SecurityConfig`, but whether the
   Ktor JWT provider can reproduce it exactly (including the no-issuer test mode used by
   the integration suites) is unverified.

3. **Four-format content negotiation.** Ktor has no drop-in "extension ⇒ converter"
   mechanism. The `.json/.xml/.csv/.html` routing is easy, but each responder's exact
   byte output (XML sanitization, CSV quoting, HTML escaping) must match — the existing
   converter logic is portable, but must be verified against golden fixtures.

4. **GraalVM/native feasibility.** `HikariCP`, the PG driver, SnakeYAML, `commons-csv`,
   and `networknt` all need reachability config; native-image is not a free win and is
   unverified. Treat as a hypothesis.

5. **Test migration cost.** The three Kotlin `@SpringBootTest` suites plus four Java
   suites encode a lot of implicit behavior. Converting them to black-box contract tests
   is real work and is the actual gate on any migration.

6. **Module boundary is currently conceptual, not physical.** The framework-independent
   logic lives *inside* `helianthus-web` (same package `helianthus.core`). A migration
   should first *physically* move it to the core (or a new `helianthus-app` module) to
   make the boundary enforceable — a gardening step, not a Ktor step.

---

## 14. Recommendation

**INVESTIGATE** — with three experiments gating a PROCEED decision.

Evidence supports that Spring is a replaceable shell (60% of production LOC is
framework-independent; Spring's heavyweight features are unused; the data layer is
already raw JDBC), so the architecture does not *oppose* Ktor. But committing to a
migration plan today would be premature, because three technical questions cannot be
answered from static analysis and must be spiked:

1. **Spike the concurrency model** — blocking JDBC on a thread pool vs suspending `Flow`;
   this determines the `GenericDataAccess` contract and the shape of the pipeline.
2. **Spike Keycloak JWT auth on `ktor-server-auth-jwt`** — reproduce `realm_access.roles`
   → `ROLE_*` extraction plus the no-issuer test mode.
3. **Spike the four-format responder stack** — JSON/XML/CSV/HTML against the existing
   converters to confirm byte-equivalent output is achievable.

Regardless of the Ktor decision, begin **§9.1–§9.5** (HTTP characterization tests, the
`Principal`/roles abstraction, dead-code removal, the two missing unit tests, and the
JSON contract test) *now*. That work is pure de-risking: it improves the current Spring
codebase, makes the migration surface smaller and provable, and would be equally
valuable if the project stays on Spring.

If the three spikes resolve favorably (expected, given the shallow coupling), the
follow-up path is: gardening/characterization → Ktor migration engineering plan →
adversarial review → experimental branch → functional-equivalence verification →
Spring-vs-Ktor runtime evaluation.
