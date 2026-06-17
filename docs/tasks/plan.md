# Helianthus Modernization Roadmap

## Vision

Helianthus will evolve from a legacy Java SQL-to-HTTP middleware into a modern Kotlin-first declarative backend for relational data.

Core idea:

```text
Declarative catalog → operation pipeline → secure data execution → JSON/HTML/CSV/XML output
```

Helianthus should allow users to expose useful backend APIs without writing controllers manually, while keeping the system safe, observable, extensible, and easy to run locally.

---

# Phase 0 — Legacy Stabilization

## Status

Mostly complete.

## Goal

Move the original project from a 2014-style WAR/Spring XML stack into a modern Java/Kotlin/Spring Boot baseline.

## Completed

* Java 25 baseline.
* Spring Boot 4.1.0 dependency management.
* PostgreSQL runtime driver.
* Removed MySQL default driver.
* Removed Commons DBCP.
* Removed old Servlet/JSTL/taglibs stack.
* Migrated to Jakarta Servlet.
* Removed old Spring 4.x dependencies.
* Replaced XStream/Jettison JSON path with Jackson.
* Removed Dom4J and legacy XML formatter.
* Replaced JUnit 3/4 with JUnit Jupiter.
* Added Spring Boot runtime JAR.
* Added `/health`.
* Moved repository to product layout:

```text
helianthus/
├── server/
├── client/
├── docker-compose.yml
├── .env.example
├── AGENTS.md
└── docs/
```

* Moved Spring XML wiring to Java configuration.
* Enabled Kotlin migration.
* Migrated simple beans to Kotlin.

## Remaining Cleanup

* Retire `helianthus-web-deploy`.
* Keep `queryConfig.xml` temporarily.
* Keep Digester temporarily.
* Replace servlet entrypoint with Kotlin controller.
* Move exception handling to `@RestControllerAdvice`.
* Harden `PathHandler`.
* Stop using `HttpServletRequest` / `HttpServletResponse` inside workflow internals.
* Remove binary formatter unless a strong reason appears.

---

# Phase 1 — Legacy Query API Runtime

## Goal

Make the original Helianthus query API work cleanly on Spring Boot.

This phase is still about legacy compatibility, not new product features.

## Target API

```http
GET /api/op/{operationId}.{format}
```

Examples:

```http
GET /api/op/all-products.json
GET /api/op/get-product.json?productCode=S10_1678
```

## Steps

### 1.1 Kotlin Controller

Replace `HelianthusServlet` with `HelianthusController.kt`.

Rules:

* Only support `GET`.
* Use `/api/op/**`.
* Inject dependencies through constructor injection.
* No `ContextUtils.getContext(request)`.
* No manual bean lookup.

### 1.2 Exception Handling

Create:

```text
HelianthusExceptionHandler.kt
```

Use:

```text
@RestControllerAdvice
```

Handle:

* invalid operation path → 400
* operation not found → 404
* unsupported format → 406
* unexpected failure → 500

### 1.3 Harden Path Parsing

Replace loose parsing with strict parsing.

Accepted format:

```text
/api/op/{operationId}.{format}
```

Valid:

```text
/api/op/all-products.json
/api/op/reports/sales.html
```

Invalid:

```text
/api/op/
/api/op/all-products
/api/op/.json
/api/op/all-products.
/products.json
```

### 1.4 Datasource Execution

Replace empty `ConnectionProviderService` wiring with a Spring Boot `DataSource`.

Initial rule:

```text
One default datasource first.
Named datasources later.
```

### 1.5 Legacy Query Execution Test

Add an integration test proving:

```text
queryConfig.xml → operation mapping → SQL execution → JSON response
```

## Done Criteria

* `/health` works.
* `/api/op/{operation}.json` works.
* Bad paths return 400.
* Unknown operations return 404.
* No active servlet entrypoint.
* `mvn clean test` passes.

---

# Phase 2 — Result Model / ResultFrame

## Goal

Replace `TableResultBean` with a modern tabular result abstraction.

## New Concept

```text
ResultFrame
```

Possible model:

```kotlin
data class ResultFrame(
    val schema: ResultSchema,
    val rows: List<ResultRow>,
    val metadata: ResultMetadata = ResultMetadata()
)
```

```kotlin
data class ResultColumn(
    val name: String,
    val type: ResultType = ResultType.UNKNOWN,
    val nullable: Boolean = true
)
```

## Design Decisions

Support two execution styles:

```text
BufferedResultFrame
StreamingResultFrame
```

### Buffered

For normal APIs.

```text
ResultSet → memory → JSON/HTML/CSV
```

### Streaming

For large exports.

```text
ResultSet cursor → stream rows → response
```

## Safety Rules

* Default max rows.
* Per-operation max rows.
* Query timeout.
* Fetch size.
* Streaming disabled by default.
* No unlimited `select *` without limits.

## Compatibility

Create adapter:

```text
TableResultBean → ResultFrame
```

Then gradually migrate internals.

## Done Criteria

* JSON formatter can output `ResultFrame`.
* Existing legacy result can adapt to `ResultFrame`.
* Tests cover schema, rows, null values, and column order.

---

# Phase 3 — Operation Pipeline

## Goal

Preserve the good idea behind workflow, but modernize it into a real operation pipeline.

## Old Model

```text
fetch SQL → formatter writes response
```

## New Model

```text
resolve → bind → execute → transform → render
```

## Proposed Steps

```text
OperationResolutionStep
ParameterBindingStep
QueryExecutionStep
ResultTransformationStep
ResultRenderingStep
```

Rendering may happen outside the core pipeline when Spring can handle it.

## Rules

* Pipeline must not depend on Servlet API.
* Pipeline returns an object.
* Controller handles HTTP.
* Renderer handles format.
* Query execution returns `ResultFrame`.

## Possible Future Pipeline Features

* column rename
* hidden columns
* derived fields
* aggregation
* grouping
* sorting
* limiting
* shape adaptation
* chart adaptation
* export adaptation

## Done Criteria

* Workflow no longer needs `HttpServletRequest`.
* Workflow no longer needs `HttpServletResponse`.
* Pipeline has at least two meaningful steps.
* Formatter no longer writes directly to servlet response.

---

# Phase 4 — YAML Catalog + JSON Schema

## Goal

Introduce the future declarative catalog.

## Keep Legacy XML Temporarily

`queryConfig.xml` remains as legacy compatibility.

## New Canonical Format

```text
helianthus.yml
```

Validated by:

```text
helianthus.schema.json
```

## Example

```yaml
app:
  name: Store Admin

operations:
  products.findAll:
    path: /products
    method: GET
    datasource: default
    sql: |
      select productCode, productName, productLine, buyPrice
      from products
    output:
      defaultFormat: json
```

## Why JSON Schema

* autocomplete
* validation
* docs
* LLM contract
* UI form generation
* safer configuration

## Done Criteria

* YAML catalog loads.
* JSON Schema validates catalog.
* Invalid catalog fails fast.
* One operation can run from YAML.
* XML catalog is marked legacy.

---

# Phase 5 — Declarative CRUD

## Goal

Add controlled automatic CRUD inspired by Supabase/PostgREST, but safe by default.

## Principle

```text
Auto-discover does not mean auto-expose.
```

## Default

```text
Everything is internal unless explicitly exposed.
```

## Example

```yaml
entities:
  Product:
    visibility: api
    table: products
    crud:
      enabled: true
      operations:
        - list
        - get
        - create
        - update
    security:
      read: hasRole('PRODUCT_READER')
      write: hasRole('PRODUCT_ADMIN')
```

## API Shape

```http
GET    /api/products
GET    /api/products/{id}
POST   /api/products
PATCH  /api/products/{id}
DELETE /api/products/{id}
```

## Done Criteria

* CRUD must be opt-in.
* Read-only CRUD supported first.
* Write operations require security model.
* Generated CRUD is documented in runtime registry.

---

# Phase 6 — Security Baseline

## Goal

Add authentication and authorization.

## Default Stack

```text
Keycloak + Spring Security
```

## Design

* Spring Security validates identity.
* Helianthus evaluates operation/entity permissions.
* Keycloak is default, but OIDC provider should be replaceable.

## Example

```yaml
security:
  provider: oidc
  roleClaim: realm_access.roles
```

## Done Criteria

* Unauthenticated access can be denied.
* Roles can protect operations.
* Roles can protect CRUD actions.
* Security decisions are logged/auditable.
* Keycloak works in local Docker stack.

---

# Phase 7 — Observability and Audit

## Goal

Make Helianthus debuggable and production-friendly.

## Logging

Use:

```text
SLF4J + Spring Boot default logging
```

Log:

* operation id
* request path
* format
* datasource
* duration
* row count
* failures

Do not log:

* passwords
* tokens
* secrets
* sensitive payloads

## Observability

Add:

* request id
* structured logs
* operation metrics
* datasource metrics
* error counts
* latency histograms

## Audit

Audit important events:

* operation executed
* CRUD write
* denied request
* failed operation
* catalog reload
* security decision

## Done Criteria

* Logs are useful without being noisy.
* Metrics available through Actuator/Micrometer.
* Audit events persist or can be exported.

---

# Phase 8 — HTML Rendering

## Goal

Reintroduce HTML as a modern first-class output.

## Inspiration

ColdFusion-style data rendering, not a full CMS.

## Avoid

* dotCMS-style templates/containers/pages
* CMS complexity
* content model explosion

## Approach

Use a template engine with helpers.

Candidate:

```text
Handlebars.java
```

Example:

```handlebars
<h1>Products</h1>

{{table result}}
```

The helper renders a `ResultFrame` as an HTML table.

## Supported Modes

```text
default table rendering
custom templates
helpers for ResultFrame
```

## Done Criteria

* `.html` output works.
* ResultFrame can render as table.
* Custom template can iterate rows and columns.
* HTML rendering does not depend on servlet response directly.

---

# Phase 9 — Docker Product Stack

## Goal

Make Helianthus easy to run locally.

## Final Target

```text
docker compose up
```

Should start:

```text
Postgres
Helianthus server
Keycloak
MinIO
Varnish
client/admin UI
```

## Phased Stack

### 9.1

```text
Postgres only
```

### 9.2

```text
Postgres + Helianthus server
```

### 9.3

```text
Postgres + server + Keycloak
```

### 9.4

```text
Postgres + server + Keycloak + MinIO
```

### 9.5

```text
Full stack with Varnish and client
```

## Done Criteria

* New user can clone and run locally.
* Defaults work.
* `.env.example` is complete.
* Docs explain ports and credentials.

---

# Phase 10 — Client Admin UI

## Goal

Create a React/Ant Design admin console.

## Initial Screens

* health/status
* loaded operations
* loaded entities
* datasource status
* operation tester
* audit log
* catalog viewer

## Later Screens

* YAML editor
* reverse engineering wizard
* CRUD exposure wizard
* security role mapper
* template viewer

## Done Criteria

* Client can connect to server.
* Operations can be inspected.
* Operation can be tested from UI.
* UI is useful but not required for runtime.

---

# Phase 11 — Reverse Engineering Wizard

## Goal

Help users create Helianthus definitions from an existing database.

## Flow

```text
connect datasource
inspect tables
inspect columns
detect primary keys
detect foreign keys
suggest entities
mark api/internal
generate YAML
review
apply
```

## Rule

```text
Discovering a table does not expose it.
```

## Done Criteria

* Wizard can inspect Postgres.
* YAML proposal generated.
* User must approve exposure.
* No automatic public CRUD by default.

---

# Phase 12 — Storage

## Goal

Add file/image support.

## Default Local Stack

```text
MinIO
```

## Production-Compatible

```text
S3-compatible storage
```

## Example

```yaml
storage:
  buckets:
    product-images:
      provider: s3
      publicRead: false
```

Entity field:

```yaml
fields:
  image:
    type: file
    bucket: product-images
```

## Done Criteria

* Upload file.
* Generate signed URL.
* Associate file with entity.
* Use images in HTML rendering.

---

# Phase 13 — Functions and Hooks

## Goal

Allow custom logic around operations.

## First Step

External HTTP functions.

```yaml
hooks:
  beforeExecute:
    - validateProduct
```

## Later

* AWS Lambda
* cloud functions
* GraalJS sandbox

## Rule

Do not embed JavaScript runtime until security, timeout, memory, and dependency model are clear.

## Done Criteria

* Hooks are declared.
* External function can be called.
* Timeout and error handling exist.
* Audit records function execution.

---

# Phase 14 — Natural-ish Schema / LLM Layer

## Goal

Use an LLM to generate draft Helianthus artifacts.

## Flow

```text
natural language description
↓
helianthus.yml draft
↓
SQL migration draft
↓
JSON Schema validation
↓
user review
↓
apply
```

## Rule

The LLM never applies changes directly.

It only proposes:

```text
YAML
SQL
diffs
preview
```

## Done Criteria

* Prompt produces valid YAML draft.
* Generated YAML validates against schema.
* Generated SQL is reviewable.
* User approves before applying.

---

# Migration Policy

## Java to Kotlin

Use Kotlin for:

```text
new code
controllers
configuration
models
pipeline abstractions
catalog model
ResultFrame
security model
```

Leave Java temporarily for:

```text
legacy adapters
old Digester parser
old JDBC mapper
classes likely to be deleted
```

## Rule

```text
Do not translate legacy Java just for the sake of translation.
Translate only when the class is part of the future shape.
```

---

# Near-Term Next Steps

1. Finish controller exception handler cleanup.
2. Harden `PathHandler`.
3. Confirm `/api/op/{operation}.json` routing.
4. Connect Spring Boot `DataSource`.
5. Execute one query from `queryConfig.xml`.
6. Remove or isolate binary formatter.
7. Create initial `ResultFrame` design document.
8. Create initial `OperationPipeline` design document.
9. Then start Phase 1 implementation properly.
