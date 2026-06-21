# AGENTS.md — Helianthus

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
mvn clean compile                           # compile all modules
mvn clean test                              # run all tests
mvn clean package -DskipTests              # package without tests
mvn clean install -DskipTests              # install into local ~/.m2
mvn clean test -pl helianthus-web          # single module
mvn test -pl helianthus-web -Dtest=StarterOperationsSmokeTest  # single test class
java -jar helianthus-web/target/helianthus-web-1.0.jar
```

No separate lint step — `mvn clean compile` is the verification.

## Client Commands

From `client/`:

```bash
npm install && npm run dev     # development server (Vite, port 5173)
npm run build                  # TypeScript compile + Vite build
npm run lint                   # ESLint
```

## Running Locally

```bash
docker compose up -d           # start PostgreSQL 17 on port 5432
cd server && mvn clean install -DskipTests
java -jar helianthus-web/target/helianthus-web-1.0.jar
curl http://localhost:8080/health
```

Full starter stack (2× PostgreSQL + Keycloak + server + client):
```bash
docker compose -f docker-compose.starter.yml up --build
```

## Request Flow

```
GET /api/op/{operationId}/{configurationId}.{format}
  → HelianthusController
  → PathHandler (parse URL)
  → OperationPermissionEvaluator (check Keycloak roles)
  → OperationCatalog (resolve from operations.yml)
  → PipelineFactory → Pipeline
      ResolveStep → BindStep → QueryStep → ProjectStep → FilterStep → LimitStep → ToResultFrameStep
  → ResultFrame → message converter (JSON/XML/HTML/CSV)
```

`configurationId` defaults to `"default"` when omitted. Format extension selects the converter.

## Key Source Locations

- **Entry point:** `server/helianthus-web/src/main/kotlin/helianthus/core/HelianthusApplication.kt`
- **Main controller:** `helianthus/core/web/HelianthusController.kt`
- **Catalog loader:** `helianthus/core/config/CatalogConfig.kt` (reads `operations.yml`)
- **Pipeline steps:** `helianthus/core/pipeline/` — one file per step
- **JDBC data access:** `server/helianthus/src/main/kotlin/helianthus/core/access/impl/db/JdbcGenericDataAccess.kt`
- **Output converters:** `helianthus/core/web/converter/` — Json, Xml, Html, Csv
- **Security:** `helianthus/core/config/SecurityConfig.kt` + `helianthus/core/security/OperationPermissionEvaluator.kt`
- **Operations catalog (production):** `server/helianthus-web/src/main/resources/operations.yml`
- **Operations catalog (test):** `server/helianthus-web/src/test/resources/operations.yml`
- **Starter catalog + seed data:** `samples/starter/`

## Testing

- JUnit Jupiter via `spring-boot-starter-test`; H2 in-memory (`jdbc:h2:mem:helianthus`) for test database
- Integration tests use `@SpringBootTest` + `@Sql` for schema/data setup
- `server/helianthus-web/src/test/resources/application.properties` configures the H2 URL
- Production `operations.yml` and test `operations.yml` must stay in sync on catalog shape (test version uses H2-compatible SQL)

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
- `Context.java`, `ContextUtils.java`, `SpringContextImpl.java` in `helianthus-core` are dead code from before the Kotlin migration.
- Multi-datasource wiring is in `DataSourceConfig.kt`; datasource names in `operations.yml` must match the bean names registered there.

## Logging

Use SLF4J. Log startup decisions at INFO, query execution and incoming requests at DEBUG, failures at ERROR. Prefer structured context fields: `operationId`, `configurationId`, `format`, `datasource`, `durationMs`, `rowCount`. Never log passwords, tokens, or raw SQL parameters containing private data.

## Commit Style

```
build: add Spring Boot dependency management
test: add smoke tests for secondary datasource operations
web: expose catalog endpoint
core: add named-parameter SQL support
```
