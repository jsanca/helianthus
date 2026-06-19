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

# Phase 3 — Operation Pipeline Framework

## Goal

Replace the legacy workflow model with a typed, declarative, stream-oriented operation pipeline.

Helianthus must not execute arbitrary SQL from HTTP requests.  
A request only selects:

```text
operationId + configurationId + format
````

The catalog decides:

```text
query source
datasource
parameters
security
pipeline
limits
rendering format
```

---

## Core Idea

Separate reusable SQL/query definitions from public operations and their configurations.

```text
datasources
  ↓
queries
  ↓
operations
  ↓
configurations
  ↓
pipeline
  ↓
result format
```

Meaning:

```text
Query = reusable data source definition
Operation = public executable contract
Configuration = named shape/pipeline of the operation
Format = representation: json/html/csv/xml
```

---

## Request Shape

Canonical route:

```http
GET /api/op/{operationId}/{configurationId}.{format}
```

Examples:

```http
GET /api/op/users/all.json
GET /api/op/users/onlyNamesGreaterThan18.json
GET /api/op/users/nameAndAges.json
GET /api/op/users/cards.html
GET /api/op/users/export.csv
```

Optional default shortcut:

```http
GET /api/op/{operationId}.{format}
```

Equivalent to:

```http
GET /api/op/{operationId}/default.{format}
```

---

## Catalog Shape

Helianthus supports both reusable queries and inline operation queries.

### Reusable Query

```yaml
queries:
  users.base:
    datasource: default
    sql: |
      select id, name, last_name, age, email, status
      from users
    parameters:
      minAge:
        type: integer
        required: false
```

### Operation Using Query Reference

```yaml
operations:
  users:
    queryRef: users.base
    security:
      roles: [USER_READER]

    configurations:
      all:
        pipeline:
          - limit: 100

      onlyNamesGreaterThan18:
        pipeline:
          - filter:
              age:
                gt: 18
          - project: [id, name, last_name]

      nameAndAges:
        pipeline:
          - derive:
              full_name: "name + ' ' + last_name"
          - project: [full_name, age]
          - limit: 50
```

### Operation With Inline SQL

```yaml
operations:
  activeUsers:
    datasource: default
    query: |
      select id, name, last_name, age
      from users
      where status = 'ACTIVE'

    configurations:
      default:
        pipeline:
          - project: [id, name, last_name]
          - limit: 100
```

Rule:

```text
An operation must define either queryRef or query, but not both.
```

---

## Security Model Placement

Security belongs to the operation/configuration layer, not the raw query layer.

Example:

```yaml
operations:
  users.public:
    queryRef: users.base
    security:
      roles: [USER_READER]
    configurations:
      default:
        pipeline:
          - project: [id, name]

  users.admin:
    queryRef: users.base
    security:
      realm: admin
      roles: [ADMIN, SUPPORT]
    configurations:
      default:
        pipeline:
          - project: [id, name, last_name, age, email, status]
```

Configuration-level security can restrict further:

```yaml
configurations:
  export:
    security:
      roles: [ADMIN]
    pipeline:
      - limit: 10000
```

Rule:

```text
Configuration security can restrict operation security, but must not silently relax it.
```

---

## Pipeline Mental Model

The pipeline should feel like Java Stream, Kotlin Sequence, or Kotlin Flow.

Conceptual fluent form:

```kotlin
Operation("users")
    .configuration("nameAndAges")
    .query()
    .filter(...)
    .map(...)
    .limit(50)
    .toResultFrame()
```

Internal flow:

```text
OperationRequest
  ↓
ResolvedOperation
  ↓
BoundOperation
  ↓
RowStream
  ↓
TransformedRowStream
  ↓
ResultFrame / StreamingResultFrame
```

---

## Initial Pipeline Slots

### resolve

Resolve operation and configuration.

```text
operationId + configurationId → OperationConfiguration
```

### bind

Validate and convert request parameters.

```text
query params → typed parameters
```

### query

Execute the declared query against the selected datasource.

```text
datasource + sql + typed params → row stream
```

### project

Keep only selected columns.

```yaml
- project: [id, name, age]
```

### map

Transform, clean, hydrate, or rename values.

Possible uses:

```text
normalize text
rename fields
clean nulls
convert values
hydrate values from local lookup
derive display fields
```

### derive

Create a new field from existing fields.

```yaml
- derive:
    full_name: "name + ' ' + last_name"
```

### filter

Filter rows after query execution.

```yaml
- filter:
    age:
      gt: 18
```

Prefer SQL `WHERE` when possible, but keep pipeline filtering for post-query or dynamic cases.

### limit

Stop after N rows.

```yaml
- limit: 100
```

This is both a feature and a safety guard.

### toResultFrame

Collect the final result into a buffered `ResultFrame`.

Later:

```text
toStreamingResultFrame
```

for large exports.

---

## Future Pipeline Slots

Potential future slots:

```text
rename
hide
mask
sort
aggregate
groupBy
join
hydrate
cache
tap/debug
audit
chartShape
exportShape
```

---

## External Invocation Slot

The pipeline may later call external functions or cloud functions for custom behavior.

Recommended slot name:

```yaml
- invoke:
    function: enrich-user-profile
    mode: batch
```

Function definitions live separately:

```yaml
functions:
  enrich-user-profile:
    type: http
    url: ${ENRICH_USER_PROFILE_URL}
    timeoutMs: 1500
```

Supported invocation modes:

```text
perRow      → one call per row; dangerous/costly
batch       → chunks of rows
wholeFrame  → entire result frame
```

Rules:

```text
timeout required
allowlist required
no secrets directly in YAML
audit every invocation
row/batch limits required
retries controlled
circuit breaker later
```

Implementation order:

```text
1. local declarative pipeline
2. external HTTP invoke
3. cloud functions
4. embedded JS/GraalJS only if clearly justified
```

---

## Rendering Boundary

Pipeline does not own HTTP rendering.

```text
OperationPipeline → OperationResult → Renderer/Spring MVC
```

Renderers:

```text
json → Jackson / Spring MVC
html → template engine + Helianthus helpers
csv  → CSV renderer, preferably streaming
xml  → Jackson XML later
```

No pipeline step should write directly to `HttpServletResponse`.

---

## Large Result Safety

Default protections:

```text
default max rows
per-configuration max rows
query timeout
fetch size
streaming disabled by default
```

A normal operation must not accidentally load millions of rows into memory.

---

## Rules

```text
No SQL from HTTP requests.
Requests only select declared operations/configurations.
GET is for declared operations with simple query parameters.
Dynamic pipelines are not part of this phase.
Dynamic pipelines, if added later, must use a secured POST endpoint.
Pipeline must not depend on Servlet API.
Pipeline must return data objects, not write responses.
```

---

## Phase 3 Done Criteria

* Legacy `WorkFlowStep` model is replaced or fully isolated behind an adapter.
* No pipeline component depends on `HttpServletRequest`.
* No pipeline component depends on `HttpServletResponse`.
* Request model includes:

    * operationId
    * configurationId
    * format
    * params
* Catalog can resolve:

    * inline query operation
    * queryRef operation
    * default configuration
    * named configuration
* Initial slots implemented:

    * resolve
    * bind
    * query
    * project
    * filter
    * limit
    * toResultFrame
* JSON output works from `ResultFrame`.
* Legacy behavior maps to a `default` configuration.
* No arbitrary SQL is accepted from request input.

---

# Phase 4 — YAML Catalog + JSON Schema

## Goal

Introduce the canonical Helianthus declarative catalog.

The catalog defines reusable queries, public operations, named configurations, pipeline steps, and future security metadata.

HTTP requests must never provide SQL.  
Requests only select:

```text
operationId + configurationId + format
````

## Canonical File

```text
operations.yml
```

Later, this may become:

```text
helianthus.yml
```

but for now `operations.yml` is the runtime catalog.

## Catalog Shape

```yaml
app:
  name: Store Admin

datasources:
  default:
    type: postgres

queries:
  products.base:
    datasource: default
    sql: |
      select productCode, productName, productLine, buyPrice, quantityInStock
      from products
    parameters:
      productLine:
        type: string
        required: false

operations:
  products:
    queryRef: products.base
    security:
      roles: [PRODUCT_READER]

    configurations:
      default:
        pipeline:
          - limit: 100

      compact:
        pipeline:
          - project: [productCode, productName, productLine]
          - limit: 50

      expensive:
        pipeline:
          - filter:
              buyPrice:
                gt: 50
          - project: [productCode, productName, buyPrice]
          - limit: 100
```

## Inline Query Support

For small catalogs, an operation may define SQL inline:

```yaml
operations:
  productLines:
    datasource: default
    query: |
      select productLine, textDescription
      from productlines

    configurations:
      default:
        pipeline:
          - limit: 100
```

Rule:

```text
An operation must define either queryRef or query, but not both.
```

## URL Mapping

Named configuration:

```http
GET /api/op/products/compact.json
```

Default configuration shortcut:

```http
GET /api/op/products.json
```

Equivalent to:

```http
GET /api/op/products/default.json
```

## Pipeline Steps for Phase 4

Support at least:

```text
project
filter
limit
```

Optional if already implemented:

```text
derive
map
```

## JSON Schema

Add:

```text
schemas/operations.schema.json
```

Used for:

```text
autocomplete
validation
docs
LLM generation contract
future admin UI forms
safer configuration
```

## Legacy Compatibility

Legacy list-style YAML may remain temporarily:

```yaml
operations:
  - name: /all-products
    query: select * from products
```

but it should be marked as compatibility mode.

`queryConfig.xml` stays only in `docs/legacy`.

## Done Criteria

* Rich YAML catalog loads.
* Catalog supports `queries`.
* Catalog supports `operations`.
* Operations support `queryRef` or inline `query`.
* Operations support named `configurations`.
* Pipeline config loads from YAML.
* `/api/op/{operation}.{format}` resolves default configuration.
* `/api/op/{operation}/{configuration}.{format}` resolves named configuration.
* JSON Schema validates catalog.
* Invalid catalog fails fast with clear errors.
* At least one operation runs from rich YAML.
* Legacy XML is not part of runtime.



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
