# Phase 3.5 — Core Cleanup Inventory

## Status: READ-ONLY ANALYSIS

No code changes. Classification of every class across all three server modules.

---

## Module: helianthus-core (28 source files)

### Exceptions

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `NoMappingException` | **KEEP** | Active pipeline: thrown by `ResolveStep`, caught by `HelianthusExceptionHandler` (404) |
| `DataAccessErrorException` | **KEEP** | Active pipeline: thrown by `DataBaseGenericDataAccessImpl` on `SQLException` |
| `IncongruentColumnValueLengthException` | **KEEP** | Active pipeline: thrown by `setParams()` when param count mismatches |
| `InvalidColumnValueException` | **DELETE** | Only thrown by dead `ColumnHandler` classes. Zero external references |

### Beans

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `TableResultBean` | **LEGACY_ADAPTER_TEMPORARY** | Produced by `executeQuery()` (buffered path). Only consumed by legacy `OperationRunnerWorkFlowStep` and legacy `MarshallFormatter` chain. Pipeline uses `CloseableRowStream`/`ResultFrame`. Delete when legacy service is removed |
| `ColumnResultBean` | **DELETE** | Zero references from any source file. Dead code |
| `QueryMappingResult` | **MIGRATE_TO_KOTLIN** | Active: returned by `OperationMappingHelper.getQuery()`, consumed by `ResolveStep`. Simple data holder — natural Kotlin data class |

### Service Layer

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `HelianthusService` | **DELETE** | Interface only consumed by dead `OperationRunnerWorkFlowStep`. Pipeline bypasses it, using `GenericDataAccess` directly |
| `OperationMappingHelianthusServiceImpl` | **DELETE** | Implementation of `HelianthusService`. Still wired as Spring bean but never consumed by active code |

### Data Access

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `GenericDataAccess` | **KEEP** | Active pipeline: injected into `QueryStep`. `executeQueryStream()` is the live method |
| `DataBaseGenericDataAccessImpl` | **KEEP, REFACTOR** | Sole implementation. Candidate for `REPLACE_WITH_SPRING` (JdbcTemplate) later |
| `DataBaseUtils` | **KEEP** | Used by `DataBaseGenericDataAccessImpl` for `closeQuiet()`. Small utility |
| `ConnectionProvider` | **REPLACE_WITH_SPRING** | Custom interface wrapping `DataSource.getConnection()`. The entire chain is an indirect way to get a `Connection` from Spring `DataSource` |
| `ConnectionProviderService` | **REPLACE_WITH_SPRING** | Routes datasource names to providers. Only one datasource ("default"). Replace with `DataSource` lookup |
| `DataSourcePoolConnectionProvider` | **REPLACE_WITH_SPRING** | Wraps `DataSource.getConnection()` — a no-op adapter. Delete when `ConnectionProvider` is removed |
| `TableMappingHandler` | **DELETE** | Only used by `executeQuery()` (buffered path). Pipeline uses `executeQueryStream()` → `JdbcRowStream` |

### Column Handler System (entire `handler/` package — 11 files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `ColumnHandler` (interface) | **DELETE** | Dead code. Never referenced outside its own package |
| `ColumnHandlerFactory` | **DELETE** | Dead code. Singleton never instantiated externally |
| `StringColumnHandler` | **DELETE** | Dead code |
| `IntegerColumnHandler` | **DELETE** | Dead code |
| `LongColumnHandler` | **DELETE** | Dead code |
| `FloatColumnHandler` | **DELETE** | Dead code |
| `DoubleColumnHandler` | **DELETE** | Dead code |
| `BooleanColumnHandler` | **DELETE** | Dead code |
| `BigDecimalColumnHandler` | **DELETE** | Dead code |
| `ByteColumnHandler` | **DELETE** | Dead code |
| `DateColumnHandler` | **DELETE** | Dead code. Bug: `getTypeName()` returns `"integer"` (same as `IntegerColumnHandler`) |

### Result Model (Kotlin)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `ResultFrame` | **KEEP** | Active pipeline output boundary |
| `ResultSchema` | **KEEP** | Used by `ProjectStep`, `JdbcRowStream` |
| `ResultColumn` | **KEEP** | Schema definition |
| `ResultType` | **KEEP** | Column type enum |
| `ResultMetadata` | **KEEP** | Row count, execution time |
| `CloseableRowStream` | **KEEP** | Streaming abstraction between `QueryStep` and `ToResultFrameStep` |
| `DefaultRowStream` | **KEEP** | In-memory `CloseableRowStream` for derived streams |
| `JdbcRowStream` | **KEEP** | Concrete stream wrapping JDBC `ResultSet` |
| `ResultFrameBuilder` | **DELETE** | Dead production code. Has tests but never instantiated. `JdbcRowStream.buildSchema()` duplicates its logic |
| `TableResultBeanAdapter` | **DELETE** | Only used by dead `OperationRunnerWorkFlowStep` |

### Utilities

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `OperationMappingHelper` | **KEEP, MIGRATE_TO_KOTLIN** | Active pipeline: used by `ResolveStep`. Complex mutable state — migrate carefully |

---

## Module: helianthus-web

### Active Pipeline (Kotlin) — ALL KEEP (21 files)

`PipelineComponent`, `Pipeline`, `PipelineFactory`, `PipelineModels.kt` (6 data classes), `ResolveStep`, `BindStep`, `QueryStep`, `ProjectStep`, `FilterStep`, `LimitStep`, `ToResultFrameStep`, `RowStream`, `FilterOperator`, `FilterOperatorRegistry`, `EqOperator`, `NeqOperator`, `GtOperator`, `GteOperator`, `LtOperator`, `LteOperator`, `InOperator`

### Web Layer (Kotlin) — ALL KEEP (8 files)

`HelianthusController`, `HelianthusExceptionHandler`, `HealthController`, `PathHandler`, `InvalidOperationPathException`, `PathMappingResultBean`, `QueryConfigBean`, `QueryParameterBean`

### Spring Boot + Config + Startup — ALL KEEP (5 files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `HelianthusApplication` | **KEEP** | `@SpringBootApplication` entrypoint |
| `HelianthusLegacyConfiguration` | **KEEP, REFACTOR** | Central `@Configuration`. Creates both active and legacy beans. Needs cleanup: remove `helianthusService` bean, remove legacy `MarshallFormatter` registrations |
| `SpringQueryConfigurationFactoryBean` | **KEEP** | `FactoryBean<OperationMappingHelper>` — loads `queryConfig.xml` at startup |
| `ParseQueryConfigurationUtil` | **KEEP** | Parses `queryConfig.xml` via Commons Digester. Temporary until YAML catalog (Phase 4) |
| `InputStreamUtils` | **KEEP** | Used by `SpringQueryConfigurationFactoryBean` for classpath/file loading |

### Active Marshallers — KEEP (4 files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `ResultFrameMarshaller` | **KEEP** | Active output interface. Used by `HelianthusController.renderResult()` |
| `JacksonResultFrameMarshallFormatter` | **KEEP** | Active JSON output for `ResultFrame` |
| `MarshallFormatFactory` | **KEEP** | Registry. Active half: `getResultFrameMarshaller()`. Legacy half: `getMarshallFormatter()` |
| `MarshallFormatterException` | **KEEP** | Used by both active `JacksonResultFrameMarshallFormatter` and legacy formatters |

### Legacy Marshallers — DELETE (3 files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `MarshallFormatter` (interface) | **DELETE** | Legacy output interface for `TableResultBean`. Active pipeline uses `ResultFrameMarshaller` |
| `JacksonTableResultMarshallFormatter` | **DELETE** | Serializes `TableResultBean`. Not on active pipeline path |
| `TableResultHTMLMarshallFormatter` | **DELETE** | Wraps `HTMLTableFormatter` from transformer module. Not on active pipeline path |

### Legacy Workflow System — ALL DELETE (5 files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `WorkFlowStep` | **DELETE** | Legacy interface. Replaced by `PipelineComponent` |
| `WorkFlowContext` | **DELETE** | Legacy `HashMap` carrying `HttpServletRequest`/`HttpServletResponse`. Replaced by `PipelineContext` |
| `WorkFlowFactory` | **DELETE** | Zero references from any file. Completely orphaned |
| `FormatterWorkFlowStep` | **DELETE** | Legacy step. Writes to `HttpServletResponse.getOutputStream()`. Replaced by `HelianthusController.renderResult()` |
| `OperationRunnerWorkFlowStep` | **DELETE** | Legacy step. Reads `HttpServletRequest` for params. Replaced by pipeline `ResolveStep`+`BindStep`+`QueryStep` |

### Already Removed (confirmed absent)

| Class | Status |
|-------|--------|
| `BinaryMarshallFormater` | Already deleted |
| `Context` | Already deleted |
| `ContextUtils` | Already deleted |
| `SpringContextImpl` | Already deleted |
| `ReflectionUtils` | Already deleted |

---

## Module: helianthus-transformer — ENTIRE MODULE DELETE (8 source + 4 test files)

| Class | Classification | Rationale |
|-------|---------------|-----------|
| `FormatterException` | **DELETE** | Only used by dead formatters |
| `BeanUtils` | **DELETE** | Wraps Commons BeanUtils. Only used by dead `HTMLTableFormatter`/`CSVTableFormatter` |
| `HTMLFormatter` | **DELETE** | Dead interface. Not referenced outside the module |
| `HTMLTableFormatter` | **DELETE** | Only referenced by dead `TableResultHTMLMarshallFormatter` in helianthus-web |
| `HTMLConfig` | **DELETE** | Only referenced by dead formatter chain |
| `CSVFormatter` | **DELETE** | Zero references outside its own file |
| `CSVTableFormatter` | **DELETE** | Zero references from any other module. Buggy (uses `HTMLConfig` for CSV) |
| `CSVConfig` | **DELETE** | Zero references from any file. Orphaned enum |
| `HTMLTableFormatterTest` | **DELETE** | Test for dead code |
| `Column`, `Data`, `Table` (test fixtures) | **DELETE** | Test fixtures for dead test |
| `query_config.XML` (test resource) | **DELETE** | Orphaned test fixture, not loaded by any test |

Removes `commons-beanutils:1.9.1` dependency entirely.

---

## Test Files

| Test | Classification | Rationale |
|------|---------------|-----------|
| `PipelineStepTest.kt` | **KEEP** | Tests active pipeline steps |
| `FilterOperatorRegistryTest.kt` | **KEEP** | Tests active filter operators |
| `PathHandlerTest.kt` | **KEEP** | Tests active URL parsing |
| `JacksonResultFrameMarshallFormatterTest.java` | **KEEP** | Tests active JSON output |
| `JdbcRowStreamTest.java` | **KEEP** | Tests active streaming data access |
| `LegacyQueryApiTest.java` | **KEEP** | Integration test for end-to-end API |
| `DataSourceIntegrationTest.java` | **KEEP** | Integration test for DataSource wiring |
| `ResultFrameTest.java` | **KEEP** | Tests active `ResultFrame` data class |
| `JacksonTableResultMarshallFormatterTest.java` | **DELETE** | Tests dead `JacksonTableResultMarshallFormatter` |
| `ResultFrameBuilderTest.java` | **DELETE** | Tests dead `ResultFrameBuilder` |
| `TableResultBeanAdapterTest.java` | **DELETE** | Tests dead `TableResultBeanAdapter` |

---

## Suggested Deletion Order

### Wave 1: Zero-risk deletions (no compilation dependencies)

```
helianthus-core:
  ColumnResultBean.java
  InvalidColumnValueException.java
  handler/ColumnHandler.java
  handler/ColumnHandlerFactory.java
  handler/StringColumnHandler.java
  handler/IntegerColumnHandler.java
  handler/LongColumnHandler.java
  handler/FloatColumnHandler.java
  handler/DoubleColumnHandler.java
  handler/BooleanColumnHandler.java
  handler/BigDecimalColumnHandler.java
  handler/ByteColumnHandler.java
  handler/DateColumnHandler.java
  result/ResultFrameBuilder.java (+ test)

helianthus-web:
  workflow/WorkFlowFactory.java
  workflow/WorkFlowContext.java
  workflow/WorkFlowStep.java
  workflow/step/FormatterWorkFlowStep.java
  workflow/step/OperationRunnerWorkFlowStep.java
  marshall/tableresult/JacksonTableResultMarshallFormatter.java (+ test)
  marshall/tableresult/TableResultHTMLMarshallFormatter.java
  marshall/MarshallFormatter.java
```

Then fix `HelianthusLegacyConfiguration`: remove `helianthusService` bean, remove legacy `MarshallFormatter` registrations from `marshallFormatFactory()`.

Run `mvn clean compile`.

### Wave 2: Cascade deletions (require config cleanup)

```
helianthus-core:
  HelianthusService.java
  impl/OperationMappingHelianthusServiceImpl.java
  access/impl/db/TableMappingHandler.java
  result/TableResultBeanAdapter.java (+ test)
  bean/TableResultBean.java (after removing from GenericDataAccess.executeQuery())

helianthus-transformer:
  ENTIRE MODULE

helianthus-web:
  Remove helianthus-transformer dependency from pom.xml
  Remove helianthus-transformer from server/pom.xml modules
```

Run `mvn clean test`.

### Wave 3: Simplify data access (separate phase)

```
ConnectionProvider → replace with DataSource
ConnectionProviderService → replace with DataSource lookup
DataSourcePoolConnectionProvider → delete
DataBaseGenericDataAccessImpl → refactor to use JdbcTemplate
```

---

## Risks

| Risk | Mitigation |
|------|-----------|
| `GenericDataAccess.executeQuery()` returns `TableResultBean` — removing it changes the interface | Keep `executeQuery()` until `TableResultBean` is fully replaced. Only remove `executeQueryStream()` callers' dependency on the legacy path first |
| `HelianthusLegacyConfiguration` wires `helianthusService` bean — removing it is safe only if no code consumes it | Verified: only `OperationRunnerWorkFlowStep` (dead) consumes it. Safe to delete |
| `MarshallFormatFactory` has both active and legacy halves | Remove legacy `MarshallFormatter` map from the factory. Keep only `ResultFrameMarshaller` map. Consider renaming the factory |
| `TableResultBean` is deeply embedded in `GenericDataAccess`, `DataBaseGenericDataAccessImpl`, `TableMappingHandler` | These are all on the buffered `executeQuery()` path. Remove them together in Wave 2 |
| Removing `helianthus-transformer` removes HTML output capability | HTML output is already broken in the active pipeline (no `ResultFrameMarshaller` registered for "html"). No regression |
| `OperationMappingHelper` is complex mutable Java — migrating to Kotlin is non-trivial | Defer to a later phase. Keep as Java for now |

---

## Verification Commands

```bash
# After each wave:
cd server && mvn clean compile
cd server && mvn clean test

# After Wave 1, verify no broken imports:
cd server && mvn clean compile -pl helianthus
cd server && mvn clean compile -pl helianthus-web

# After Wave 2, verify transformer removal:
cd server && mvn clean compile
curl http://localhost:8080/health
curl http://localhost:8080/api/op/all-products.json

# Verify dead code is gone:
grep -r "WorkFlowStep\|WorkFlowContext\|WorkFlowFactory" server/*/src/main/
grep -r "ColumnHandler" server/*/src/main/
grep -r "TableResultBean" server/*/src/main/
grep -r "helianthus-transformer" server/
```

---

## Summary Counts

| Classification | Count |
|---------------|-------|
| KEEP | 42 |
| DELETE | 30 |
| MIGRATE_TO_KOTLIN | 2 |
| REPLACE_WITH_SPRING | 3 |
| LEGACY_ADAPTER_TEMPORARY | 1 |
| KEEP, REFACTOR | 2 |
