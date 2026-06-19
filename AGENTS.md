# AGENTS.md — Helianthus

## Project Context

Helianthus is a middleware that exposes SQL operations as HTTP endpoints returning JSON or HTML.

The current goal: modernize the legacy Java codebase, remove obsolete dependencies, make the build healthy, and prepare for a Kotlin migration. Do not redesign the product yet.

The project compiles and runs on Java 25 with Spring Boot 4.1.0.

## Module Structure

```
server/pom.xml                        (parent POM, Spring Boot BOM imported)
├── server/helianthus/                (artifactId: helianthus-core — interfaces, beans, JDBC, services)
├── server/helianthus-transformer/    (artifactId: helianthus-transformer — HTML/CSV formatters)
└── server/helianthus-web/            (artifactId: helianthus-web — Spring Boot app, HTTP layer)
```

**Dependency order** (matters for `mvn install` from subdirectories):
- `helianthus-core` — no module deps
- `helianthus-transformer` — no module deps (uses commons-beanutils)
- `helianthus-web` — depends on `helianthus-core`, `helianthus-transformer`

## Build Commands

All Maven commands must be run from the `server/` directory.

```bash
mvn clean compile          # compiles all modules
mvn clean test             # runs all tests (JUnit Jupiter via spring-boot-starter-test)
mvn clean package          # compiles + packages (skip tests with -DskipTests)
mvn clean install          # installs all modules into local ~/.m2

# Target a single module:
mvn clean test -pl helianthus-web
mvn clean compile -pl helianthus-transformer

# Run the Spring Boot app:
mvn clean package -pl helianthus-web -DskipTests
java -jar helianthus-web/target/helianthus-web-1.0.jar
```

No lint or typecheck commands exist. `mvn clean compile` is the verification step.

## Setup & Running Locally

```bash
# Start PostgreSQL 17 (defaults in docker-compose.yml):
docker compose up -d

# Build and run:
cd server
mvn clean install -DskipTests
java -jar helianthus-web/target/helianthus-web-1.0.jar

# Health check:
curl http://localhost:8080/health
# -> {"status":"ok","service":"helianthus"}
```

DB config reads environment variables: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. Defaults in `application.yml` point to the `docker-compose.yml` PostgreSQL container. Copy `.env.example` to `.env` for `docker compose` defaults.

## Entrypoints

- **Spring Boot app:** `server/helianthus-web/src/main/java/helianthus/core/HelianthusApplication.java`
- **Health endpoint:** `GET /health` (Kotlin: `HealthController.kt`)
- **API controller:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/HelianthusController.kt` — `@GetMapping("/api/op/**")`, dispatches to workflow
- **Exception handler:** `server/helianthus-web/src/main/kotlin/helianthus/core/web/HelianthusExceptionHandler.kt` — `@RestControllerAdvice`
- **Wiring/configuration:** `server/helianthus-web/src/main/java/helianthus/core/config/HelianthusLegacyConfiguration.java` — `@Configuration`, manually wires beans from queryConfig.xml
- **Core service interface:** `server/helianthus/src/main/java/helianthus/core/HelianthusService.java`
- **Operation catalog:** `server/helianthus-web/src/main/resources/queryConfig.xml` — parsed by Commons Digester at startup

## API Shapes

- Pattern: `/api/op/{operationId}.{format}`
- Supported formats: `json`, `html` (`.xml` and `.bin` are not wired in the current `MarshallFormatFactory`)
- Parameters are passed as query params matching the names declared in `queryConfig.xml`

## Source Layout Conventions

- Kotlin sources live in `src/main/kotlin` (separate from `src/main/java`)
- The `kotlin-maven-plugin` only compiles `src/main/kotlin` and `src/test/kotlin` — Java compilation is handled by `maven-compiler-plugin`
- Data beans that were migrated to Kotlin (`PathMappingResultBean`, `QueryConfigBean`, `QueryParameterBean`) live in `helianthus-web/src/main/kotlin`, not in `helianthus-core`

## Testing

- JUnit Jupiter via `spring-boot-starter-test`
- Test database: H2 in-memory (`jdbc:h2:mem:helianthus`), configured in `src/test/resources/application.properties`
- Spring Boot integration tests use `@SpringBootTest` with `@Sql` for schema/data setup
- Run a single test class:
  ```bash
  mvn test -pl helianthus-web -Dtest=LegacyQueryApiTest
  ```

## Important Gotchas

- `helianthus-transformer` had JUnit 4.11 in **compile scope** (not test). This was fixed during Phase 0 modernization but is worth knowing if build warnings appear.
- `Context.java`, `ContextUtils.java`, `SpringContextImpl.java` in `helianthus-core` are dead code — no longer referenced after the Kotlin controller migration.
- The `.gitignore` is minimal and does not exclude `.env`, `.idea`, or Maven `target/` directories. Be careful not to commit unintended files.
- `queryConfig.xml` is loaded from the classpath by `HelianthusLegacyConfiguration`. Both `src/main/resources/queryConfig.xml` (production) and `src/test/resources/queryConfig.xml` (test) must be kept in sync for the catalog shape, but the test version uses H2-compatible table names.
- The `kotlin-maven-plugin` only compiles `src/main/kotlin`. If you add Kotlin files under `src/main/java`, they will not be compiled.

## Technology Baseline

Use, do not replace:
- Java 25, Kotlin 2.3.21, Maven multi-module
- Spring Boot 4.1.0 (Spring MVC, Spring JDBC), embedded Tomcat
- PostgreSQL runtime, H2 for tests, HikariCP connection pooling
- Jackson for JSON output
- JUnit Jupiter for testing

Do not introduce: WebFlux, R2DBC, Spring Security, Keycloak, MinIO, cloud functions, or LLM/DSL features.

## Modernization Principles

1. **Mechanical first.** Dependency cleanup, build cleanup, package migration, test modernization. Do not redesign the domain model.
2. **Preserve the original shape.** Operation mapping, SQL execution, JDBC data access, table-like result model, output formatters, web entrypoint.
3. **JSON first.** JSON output is the priority. HTML is available. XML is deferred.
4. **Keep the build honest.** After each change, run at minimum `mvn clean package`.
5. **Small commits.** Conventional commit scopes: `build:`, `test:`, `web:`, `core:`.

## Commit Style

```
build: add Spring Boot dependency management
build: replace MySQL driver with PostgreSQL
test: migrate legacy tests to JUnit Jupiter
web: add minimal Spring Boot application entrypoint
web: expose minimal health endpoint
```

## Logging

- Use SLF4J. Rely on Spring Boot's default logging backend.
- Log important events at appropriate levels: startup decisions (info), incoming requests (debug), query execution (debug), failures (error).
- Do not log: passwords, tokens, secrets, connection strings with credentials, sensitive request payloads, raw SQL parameters containing private data.
- Prefer structured context: `operationId`, `format`, `datasource`, `request path`, `durationMs`, `rowCount`.
- Avoid `System.out.println` and `printStackTrace`. Use logger calls.
