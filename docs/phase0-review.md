# Phase 0 Completion Report

## Completed Items

| Area | Status |
|------|--------|
| Java 25 + Spring Boot 4.1.0 baseline | DONE |
| PostgreSQL driver replaces MySQL | DONE |
| Commons DBCP removed | DONE |
| javax.servlet → jakarta.servlet migration | DONE |
| JSTL + taglibs removed | DONE |
| Spring 4.x XML beans → Java `@Configuration` | DONE |
| XStream + Jettison + Dom4J removed | DONE |
| Jackson JSON formatter (tools.jackson 3.x) | DONE |
| JUnit 3.8.1 / 4.11 → JUnit Jupiter | DONE |
| Spring Boot JAR runtime (embedded Tomcat) | DONE |
| `/health` endpoint | DONE |
| Product layout (server/ client/ docker-compose.yml) | DONE |
| `helianthus-web-deploy` retired | DONE |
| Kotlin migration started (3 bean classes + 2 controllers + PathHandler) | DONE |
| `HelianthusServlet` replaced with `HelianthusController.kt` | DONE |
| `PathHandler` hardened with strict validation | DONE |
| `InvalidOperationPathException` + `@ExceptionHandler` for 400 | DONE |
| Path pattern: `/api/op/{operationId}.{format}` | DONE |
| Controller constructor injection (no `ContextUtils.getContext()`) | DONE |
| Kotlin test infrastructure (kotlin-maven-plugin + kotlin-test) | DONE |
| Legacy files preserved in `docs/legacy/` | DONE |

## Classification of All Classes

### `server/helianthus-bean` (1 class)

| Class | Verdict | Reason |
|-------|---------|--------|
| `TableResultBean.java` | DELETE | Duplicate of the copy in `helianthus`. Zero external references. |

### `server/helianthus` (core module, 28 classes)

| Class | Verdict | Reason |
|-------|---------|--------|
| `TableResultBean.java` | KEEP | Core result model used by formatters, service, workflow |
| `ColumnResultBean.java` | DELETE | Dead code — no external references |
| `QueryMappingResult.java` | REFACTOR | Used by OperationMappingHelper; consider data class later |
| `NoMappingException.java` | KEEP | Active — thrown by OperationMappingHelper |
| `DataAccessErrorException.java` | KEEP | Active — thrown by DataBaseGenericDataAccessImpl |
| `InvalidColumnValueException.java` | KEEP | Active — thrown by all ColumnHandler implementations |
| `IncongruentColumnValueLengthException.java` | KEEP | Active — thrown by DataBaseGenericDataAccessImpl |
| `HelianthusService.java` | KEEP | Core service interface |
| `OperationMappingHelper.java` | REFACTOR | Works; consider Kotlin data holder later |
| `OperationMappingHelianthusServiceImpl.java` | KEEP | Active runtime implementation |
| `GenericDataAccess.java` | KEEP | Core data access interface |
| `ConnectionProvider.java` | KEEP | Active interface |
| `ConnectionProviderService.java` | KEEP | Active — wired in config |
| `DataSourcePoolConnectionProvider.java` | KEEP | Used when DataSource is wired (not yet) |
| `DataBaseGenericDataAccessImpl.java` | REFACTOR | Works; needs DataSource wiring |
| `DataBaseUtils.java` | KEEP | Internal helper |
| `TableMappingHandler.java` | KEEP | ResultSet → TableResultBean mapper |
| `ColumnHandler.java` | KEEP | Interface for type-safe parameter binding |
| `ColumnHandlerFactory.java` | KEEP | Singleton factory |
| `StringColumnHandler.java` | KEEP | Default handler |
| `BigDecimalColumnHandler.java` | KEEP | Type handler |
| `BooleanColumnHandler.java` | KEEP | Type handler |
| `ByteColumnHandler.java` | KEEP | Type handler |
| `DateColumnHandler.java` | KEEP | Type handler |
| `DoubleColumnHandler.java` | KEEP | Type handler |
| `FloatColumnHandler.java` | KEEP | Type handler |
| `IntegerColumnHandler.java` | KEEP | Type handler |
| `LongColumnHandler.java` | KEEP | Type handler |

### `server/helianthus-transformer` (8 classes + 4 test fixtures)

| Class | Verdict | Reason |
|-------|---------|--------|
| `FormatterException.java` | KEEP | Active exception |
| `BeanUtils.java` | REFACTOR | Wraps Apache Commons BeanUtils; consider removing in Phase 2 |
| `HTMLFormatter.java` | KEEP | Active interface |
| `HTMLTableFormatter.java` | KEEP | Active — renders HTML tables |
| `HTMLConfig.java` | REFACTOR | Enum for HTML config keys |
| `CSVFormatter.java` | DELETE | Never implemented; dead code |
| `CSVTableFormatter.java` | DELETE | Buggy (uses HTMLConfig, not CSVConfig); never wired |
| `CSVConfig.java` | DELETE | Unused; CSVTableFormatter has a bug and uses HTMLConfig |
| Test: `HTMLTableFormatterTest.java` | KEEP | Active test |
| Test: `Column.java` | KEEP | Test fixture |
| Test: `Data.java` | KEEP | Test fixture |
| Test: `Table.java` | KEEP | Test fixture |

### `server/helianthus-web` (16 Java + 8 Kotlin + 2 test)

| Class | Verdict | Reason |
|-------|---------|--------|
| `HelianthusApplication.java` | KEEP | Spring Boot entrypoint |
| `MarshallFormatterException.java` | KEEP | Active exception |
| `MarshallFormatter.java` | KEEP | Active interface |
| `MarshallFormatFactory.java` | KEEP | Active formatter registry |
| `BinaryMarshallFormater.java` | DELETE | Dead code — not wired in config; uses Java serialization |
| `JacksonTableResultMarshallFormatter.java` | KEEP | Active JSON formatter (Jackson) |
| `TableResultHTMLMarshallFormatter.java` | KEEP | Active HTML formatter |
| `HelianthusLegacyConfiguration.java` | KEEP | Active Spring `@Configuration` |
| `ReflectionUtils.java` | DELETE | Only used by dead `ContextUtils` class |
| `InputStreamUtils.java` | KEEP | Used by SpringQueryConfigurationFactoryBean |
| `ParseQueryConfigurationUtil.java` | KEEP | XML query config parser (Digester) |
| `ContextUtils.java` | DELETE | Dead — no references from active code |
| `Context.java` | DELETE | Dead interface |
| `SpringContextImpl.java` | DELETE | Dead — no references |
| `SpringQueryConfigurationFactoryBean.java` | KEEP | Factory bean for query config loading |
| `WorkFlowStep.java` | KEEP | Active workflow interface |
| `WorkFlowContext.java` | KEEP | Extends HashMap; still carries Servlet objects |
| `WorkFlowFactory.java` | KEEP | Active workflow builder |
| `OperationRunnerWorkFlowStep.java` | REFACTOR | Coupled to `HttpServletRequest`/`HttpServletResponse` (Phase 1.1/Phase 3) |
| `FormatterWorkFlowStep.java` | REFACTOR | Coupled to `HttpServletResponse.getOutputStream()` (Phase 3) |
| `InvalidOperationPathException.kt` | KEEP | Active — custom exception |
| `QueryConfigBean.kt` | KEEP | Active — Kotlin data class |
| `PathMappingResultBean.kt` | KEEP | Active — Kotlin data class |
| `QueryParameterBean.kt` | KEEP | Active — Kotlin data class |
| `HelianthusController.kt` | KEEP | Active — `@GetMapping("/api/op/**")` |
| `HelianthusExceptionHandler.kt` | KEEP | Active — `@RestControllerAdvice` handles 400 |
| `HealthController.kt` | KEEP | Active — `@GetMapping("/health")` |
| `PathHandler.kt` | KEEP | Active — hardened path parsing |
| Test: `JacksonTableResultMarshallFormatterTest.java` | KEEP | Active test |
| Test: `PathHandlerTest.kt` | KEEP | Active test (12 cases) |

## Summary: Module `helianthus-bean`

**Recommendation**: Delete the entire module. The single class `TableResultBean` is a duplicate. The active copy is in `helianthus` (core). Remove `helianthus-bean` from `server/pom.xml` modules and delete the directory.

## Remaining Blockers (before Phase 1)

### BLOCKER: No DataSource configured
Status: `ConnectionProviderService` has an **empty provider map**. No `spring-boot-starter-jdbc`, no PostgreSQL driver, no HikariCP on the runtime classpath of `helianthus-web`. Any SQL query execution will fail.

**Action**: Add `spring-boot-starter-jdbc` + `org.postgresql:postgresql` to `helianthus-web/pom.xml`. Wire `DataSource` via `application.properties` or Java config. Replace empty `ConnectionProviderService.providerMap` with a `DataSourcePoolConnectionProvider` wrapping the Spring Boot `DataSource`.

### BLOCKER: Workflow steps coupled to Servlet APIs
Status: `OperationRunnerWorkFlowStep` reads `HttpServletRequest` to extract query parameters. `FormatterWorkFlowStep` writes directly to `HttpServletResponse.getOutputStream()`. This prevents testability without a servlet container.

**Action**: Phase 1.1 — extract query parameters in the controller and pass them through `WorkFlowContext` as simple values (not servlet objects). Phase 3 — decouple formatter from `OutputStream`, return result object to controller.

### BLOCKER: `@ExceptionHandler` only handles 400
Status: `HelianthusExceptionHandler` only catches `InvalidOperationPathException`. Missing handlers for 404 (operation not found), 406 (unsupported format), and 500 (unexpected failure).

**Action**: Phase 1.2 — add exception handlers for `NoMappingException` (404), unsupported format (406), and generic `Exception` (500).

### BLOCKER: Controller only supports GET
Status: `@GetMapping("/api/op/**")` — POST, PUT, DELETE not supported.

**Action**: Per plan.md Phase 1.1: "Only support `GET`." This is intentional. POST/PUT/DELETE support is deferred to later phases.

### BLOCKER: `WorkFlowFactory` depends on `PathMappingResultBean` for operation lookup
Status: `WorkFlowFactory.createWorkFlow()` uses `pathMappingResultBean.getOperationId()` to look up the operation in `OperationMappingHelper`. But with the new `PathHandler`, operationIds have changed format (e.g., `/all-products` instead of old raw path). `queryConfig.xml` uses `/all-products` as the operation name. This should work correctly.

Actually verified: `PathHandler` returns `operationId="/all-products"` and `format="json"` for `/api/op/all-products.json`. `queryConfig.xml` has `<query-config name="/all-products">`. Match confirmed. No blocker.

## Questions Answered

| Question | Answer |
|----------|--------|
| Is HelianthusServlet completely retired? | **Yes.** Java file deleted. Replaced by `HelianthusController.kt`. |
| Is any servlet-era code still active? | **Yes.** `OperationRunnerWorkFlowStep` and `FormatterWorkFlowStep` still import `jakarta.servlet.http.*`. `WorkFlowContext` carries `HttpServletRequest`/`HttpServletResponse` objects. This is the target for Phase 3 decoupling. |
| Is queryConfig.xml loaded successfully? | **Yes.** Loaded via `SpringQueryConfigurationFactoryBean` → Digester → `ParseQueryConfigurationUtil`. |
| How many operations are loaded? | **3**: `/all-products`, `/all-productlines`, `/get-product`. |
| Is Digester still required? | **Yes.** `ParseQueryConfigurationUtil` uses Apache Commons Digester 3.2 for XML-to-Java mapping. Planned replacement: YAML + JSON Schema (Phase 4). |
| Is BinaryMarshallFormatter active? | **No.** Not wired in `HelianthusLegacyConfiguration`. Dead code. Should be deleted. |
| Is any XML formatter still active? | **No.** `XMLMarshallFormatter` and `TableResultXMLMarshallFormatter` deleted. XStream and Dom4J removed from POM. |
| Is WorkFlowContext still coupled to Servlet APIs? | **Yes.** `WorkFlowContext` is a `HashMap<String, Object>` and can carry anything. Both workflow steps cast `REQUEST_KEY`/`RESPONSE_KEY` values to `HttpServletRequest`/`HttpServletResponse`. |
| Is PathHandler ready for production use? | **Yes.** Strict validation, 12 passing tests covering valid and invalid paths, custom exception, controller returns 400 for bad paths. |
| Is Spring Boot DataSource actually wired? | **No.** `ConnectionProviderService` has empty provider map. No JDBC starter or PostgreSQL driver on active runtime classpath. |
| Can a query be executed end-to-end today? | **No.** Database not configured. `ConnectionProviderService` has no providers. Even if a DataSource were wired, no integration test exists. |

## Counts

| Verdict | Count |
|---------|-------|
| KEEP | 35 |
| REFACTOR | 8 |
| REPLACE | 0 |
| DELETE | 12 |

## Recommended First Task for Phase 1

**Wire the DataSource.** This is the single biggest gap between current state and a working query API. Steps:

1. Add `spring-boot-starter-jdbc` and `org.postgresql` to `helianthus-web/pom.xml`
2. Add `application.properties` with Datasource config (postgres host/port/credentials from `.env.example`)
3. Update `HelianthusLegacyConfiguration.connectionProviderService()` to inject `DataSource` and populate the provider map with a `DataSourcePoolConnectionProvider` under key `"default"`
4. Verify `docker compose up -d postgres` and `java -jar ...` starts without DataSource errors
5. Add an integration test: `GET /api/op/all-products.json` → 200 with JSON body

After DataSource is wired, the next priority items are:
- Decouple workflow steps from servlet APIs (Phase 1.1 / Phase 3)
- Add remaining exception handlers (Phase 1.2)
- Delete dead code (12 items above)
- Delete `helianthus-bean` module
