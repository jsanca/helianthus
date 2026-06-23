# AGENTS.md — Helianthus

## What Helianthus Is

A declarative backend platform: developers define SQL operations in a YAML catalog (`operations.yml`), and Helianthus automatically exposes them as HTTP endpoints returning JSON, XML, HTML, or CSV. No custom controllers needed.

## Module Structure

```
server/pom.xml                    (parent POM, Spring Boot BOM)
├── server/helianthus/            (helianthus-core — interfaces, JDBC, result types)
└── server/helianthus-web/        (helianthus-web — Spring Boot app, HTTP layer, pipeline)
```

`helianthus-core` has no module dependencies. `helianthus-web` depends on `helianthus-core`.

## Build Commands

All Maven commands run from `server/`:

```bash
mvn clean compile                           # compile all modules (verification — no separate lint)
mvn clean test                              # run all tests
mvn clean package -DskipTests              # package without tests
mvn clean install -DskipTests              # install into local ~/.m2
mvn clean test -pl helianthus-web          # single module
mvn test -pl helianthus-web -Dtest=StarterOperationsSmokeTest  # single test class
java -jar helianthus-web/target/helianthus-web-1.0.jar
```

Paketo OCI image build (from repo root):
```bash
scripts/build-paketo-server.sh [image-name]   # defaults to helianthus-server:paketo
docker compose -f docker-compose.starter.yml -f docker-compose.starter.paketo.yml up
```

## Client Commands

From `client/`:

```bash
npm install && npm run dev     # development server (Vite, port 5173)
npm run build                  # tsc -b && vite build
npm run lint                   # ESLint
```

## Running Locally

```bash
docker compose up -d           # start PostgreSQL 17 on port 5432
cd server && mvn clean install -DskipTests
java -jar helianthus-web/target/helianthus-web-1.0.jar
curl http://localhost:8080/health
```

Full starter stack (2x PostgreSQL + Keycloak + server + client):
```bash
docker compose -f docker-compose.starter.yml up --build
```

Test credentials: `guest/guest` (GUEST role), `admin/admin` (ADMIN role).

## Environment Variables

All have sensible defaults for local dev. Override via environment or `.env`:

| Variable | Default | Purpose |
|---|---|---|
| `HELIANTHUS_CATALOG_PATH` | `classpath:operations.yml` | Path to catalog YAML |
| `HELIANTHUS_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS allowed origins |
| `HELIANTHUS_OIDC_ISSUER_URI` | `http://localhost:8081/realms/helianthus` | Keycloak issuer |
| `HELIANTHUS_OIDC_JWK_SET_URI` | `...protocol/openid-connect/certs` | Keycloak JWKS |
| `POSTGRES_HOST/PORT/DB/USER/PASSWORD` | `localhost/5432/helianthus/helianthus/helianthus` | Primary DB |
| `POSTGRES_SECONDARY_*` | `localhost/5433/helianthus_secondary/...` | Secondary DB |

## Request Flow

```
GET /api/op/{operationId}/{configurationId}.{format}
  -> HelianthusController
  -> PathHandler (parse URL)
  -> OperationPermissionEvaluator (check Keycloak roles)
  -> OperationCatalog (resolve from operations.yml)
  -> PipelineFactory -> Pipeline
      ResolveStep -> BindStep -> QueryStep -> ProjectStep -> FilterStep -> LimitStep -> ToResultFrameStep
  -> ResultFrame -> message converter (JSON/XML/HTML/CSV)
```

`configurationId` defaults to `"default"` when omitted. Format extension selects the converter.

`GET /api/admin/catalog` returns a JSON summary of all operations visible to the authenticated user (used by the client UI).

## Operations YAML Schema

Operations live in `operations.yml`. Each operation must define either an inline `query` or a `queryRef` pointing to the top-level `queries` block.

**SQL parameter styles** — both are supported in the same codebase:
- Positional `?`: parameters bound in declaration order; only non-null values passed.
- Named `:paramName`: detected automatically; all parameters passed (null for missing optional ones). PostgreSQL cast `::` is not treated as a parameter.

## Entities YAML Schema

Entities live in the `entities` section of `operations.yml`. They provide declarative CRUD over database tables without custom SQL.

```yaml
entities:
  products:
    label: Products
    description: Product catalog
    datasource: default
    table: products
    primaryKey: productCode          # single column or list for composite key
    fields:                          # explicit whitelist of exposed columns
      - productCode
      - productName
      - productLine
    security:
      read:
        roles: [GUEST, ADMIN]
```

**Entity endpoints:**
- `GET /api/entities/{entityName}.{format}` — list with optional filters, pagination, sorting
- `GET /api/entities/{entityName}/{id}.{format}` — get by primary key

**Query parameters (list):** `limit`, `offset`, `orderBy`, `orderDir`, plus one param per exposed field for exact-match filtering.

**Rules:**
- `primaryKey` is required (single string or list for composite keys).
- `fields` is required — explicit whitelist; omitted columns are hidden.
- `primaryKey` columns must be in `fields`.
- `datasource` must match a key in the top-level `datasources` map.
- `security.read.roles` controls read access; omit to allow any authenticated user.
- `ROLE_ADMIN` always bypasses permission checks.

## Security Model

- `ROLE_ADMIN` always passes — no per-operation checks applied.
- Operations with no `security.roles` are accessible to any authenticated user.
- Roles in YAML (e.g., `ADMIN`) map to Spring authority `ROLE_ADMIN`.
- Security can be defined at operation level and/or configuration level; both sets are unioned.

## Key Source Locations

- **Entry point:** `server/helianthus-web/src/main/kotlin/helianthus/core/HelianthusApplication.kt`
- **Main controller:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/HelianthusController.kt`
- **Entity CRUD controller:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/EntityCrudController.kt`
- **Catalog controller:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/CatalogController.kt`
- **Catalog loader:** `server/helianthus-web/src/main/kotlin/helianthus/core/config/CatalogConfig.kt`
- **Catalog model + resolver:** `server/helianthus-web/src/main/kotlin/helianthus/core/catalog/OperationCatalog.kt`
- **Entity catalog:** `server/helianthus-web/src/main/kotlin/helianthus/core/catalog/EntityCatalog.kt`
- **Entity SQL builder:** `server/helianthus-web/src/main/kotlin/helianthus/core/catalog/EntityCrudSqlBuilder.kt`
- **Entity path handler:** `server/helianthus-web/src/main/kotlin/helianthus/core/util/EntityPathHandler.kt`
- **Entity permission evaluator:** `server/helianthus-web/src/main/kotlin/helianthus/core/security/EntityPermissionEvaluator.kt`
- **SQL dialect interface:** `server/helianthus/src/main/kotlin/helianthus/core/access/SqlDialect.kt`
- **Pipeline steps:** `server/helianthus-web/src/main/kotlin/helianthus/core/pipeline/` — one file per step
- **Pipeline data models:** `server/helianthus-web/src/main/kotlin/helianthus/core/pipeline/PipelineModels.kt`
- **JDBC data access:** `server/helianthus/src/main/kotlin/helianthus/core/access/impl/db/JdbcGenericDataAccess.kt`
- **Named parameter SQL parser:** `server/helianthus/src/main/kotlin/helianthus/core/access/impl/db/NamedParameterSql.kt`
- **Output converters:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/converter/` — Json, Xml, Html, Csv
- **Security:** `server/helianthus-web/src/main/kotlin/helianthus/core/config/SecurityConfig.kt` + `server/helianthus-web/src/main/kotlin/helianthus/core/security/OperationPermissionEvaluator.kt`
- **Datasource wiring:** `server/helianthus-web/src/main/kotlin/helianthus/core/config/DataSourceConfig.kt`
- **Operations catalog (production):** `server/helianthus-web/src/main/resources/operations.yml`
- **Operations catalog (test):** `server/helianthus-web/src/test/resources/operations.yml`
- **Starter catalog + seed data:** `samples/starter/`

## Testing

- JUnit Jupiter via `spring-boot-starter-test`; H2 in-memory for both datasources in tests.
- Integration tests use `@SpringBootTest` + `@Sql` for schema/data setup.
- `server/helianthus-web/src/test/resources/application.properties` sets H2 URLs and disables OAuth2 (`helianthus.security.oauth2.enabled=false`).
- Production `operations.yml` and test `operations.yml` must stay in sync on catalog shape (test version uses H2-compatible SQL).

## Technology Baseline

Use, do not replace:
- Java 25, Kotlin 2.3, Maven multi-module
- Spring Boot 4.1.0 (Spring MVC, Spring JDBC, Spring Security with Keycloak OIDC)
- PostgreSQL runtime, H2 for tests, HikariCP
- Jackson (JSON), Apache Commons CSV, custom XML/HTML converters
- JUnit Jupiter

Do not introduce: WebFlux, R2DBC, cloud functions, LLM/DSL features.

## Important Gotchas

- Kotlin sources live in `src/main/kotlin` — the `kotlin-maven-plugin` does not compile files placed under `src/main/java`.
- The `.gitignore` is minimal and does not exclude `.env`, `.idea/`, or `target/`.
- Datasource bean names in `DataSourceConfig.kt` are `"default"` and `"secondary"` — the `datasource` field in `operations.yml` must match exactly.
- The secondary datasource runs on port 5433 by default (separate PostgreSQL instance).

## Logging

Use SLF4J. Log startup decisions at INFO, query execution and incoming requests at DEBUG, failures at ERROR. Prefer structured context fields: `operationId`, `configurationId`, `format`, `datasource`, `durationMs`, `rowCount`. Never log passwords, tokens, or raw SQL parameters containing private data.

## Commit Style

```
build: add Spring Boot dependency management
test: add smoke tests for secondary datasource operations
web: expose catalog endpoint
core: add named-parameter SQL support
```
