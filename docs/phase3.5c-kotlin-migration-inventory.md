# Phase 3.5c — Kotlin Migration Inventory

## Summary

After the Phase 3.5 cleanup, 23 Java source files and 6 Java test files remain across two modules. This document classifies each for Kotlin migration.

**Active runtime path:**
```
HelianthusController → PipelineFactory → Pipeline
  → ResolveStep → BindStep → QueryStep → ProjectStep → FilterStep → LimitStep → ToResultFrameStep
  → ResultFrame → JacksonResultFrameMarshallFormatter → HTTP response
```

**Counts:**

| Classification | Count |
|---------------|-------|
| MIGRATE_TO_KOTLIN_NOW | 12 |
| MIGRATE_TO_KOTLIN_LATER | 4 |
| KEEP_JAVA_TEMPORARY | 4 |
| DELETE_LATER | 3 |
| REPLACE_LATER | 3 |

---

## helianthus-core — Java Source Files (14)

### Exceptions

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `NoMappingException` | **MIGRATE_TO_KOTLIN_NOW** | Thrown when operation not found in catalog. Caught by `HelianthusExceptionHandler` → 404 | YES — `ResolveStep` throws it, `HelianthusExceptionHandler` catches it | None. Pure exception class, no dependencies | None |
| `DataAccessErrorException` | **MIGRATE_TO_KOTLIN_NOW** | Thrown on `SQLException` during query execution | YES — `DataBaseGenericDataAccessImpl` throws it, propagates through pipeline to 500 handler | None. Pure exception class | None |
| `IncongruentColumnValueLengthException` | **MIGRATE_TO_KOTLIN_NOW** | Thrown when param count doesn't match type name count | YES — `DataBaseGenericDataAccessImpl.setParams()` throws it | None. Pure exception class | None |

### Beans

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `QueryMappingResult` | **MIGRATE_TO_KOTLIN_NOW** | Holds query SQL, datasource name, and type name array. Returned by `OperationMappingHelper.getQuery()` | YES — `ResolveStep` consumes it | None. Simple data holder with 3 fields. Natural Kotlin data class | None |
| `TableResultBean` | **DELETE_LATER** | Legacy buffered result: rowsCount, columnNames, rowResultBeans. Produced by `TableMappingHandler` via `executeQuery()` | NO — pipeline uses `CloseableRowStream`/`ResultFrame` | N/A | `TableMappingHandler`, `OperationMappingHelianthusServiceImpl` (both dead after Wave 2) |

### Data Access

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `GenericDataAccess` | **MIGRATE_TO_KOTLIN_LATER** | Interface with two methods: `executeQuery()` (legacy) and `executeQueryStream()` (active). Injected into `QueryStep` | YES — `executeQueryStream()` is the live method | Medium — has `TableResultBean` in `executeQuery()` return type. Must remove legacy method first | `TableResultBean`, `CloseableRowStream` |
| `DataBaseGenericDataAccessImpl` | **REPLACE_LATER** | Sole `GenericDataAccess` implementation. Manual JDBC: `Connection`, `PreparedStatement`, `ResultSet`. Uses `java.util.logging` (not SLF4J) | YES — concrete implementation used by `QueryStep` | High — 161 lines, manual resource management, two execution paths. Replace with `JdbcTemplate` rather than migrate | `ConnectionProviderService`, `DataBaseUtils`, `TableMappingHandler`, `JdbcRowStream` |
| `ConnectionProvider` | **DELETE_LATER** | Interface: `Connection getConnection()`. Wraps `DataSource.getConnection()` | YES — part of connection chain, but unnecessary | N/A | `DataSourcePoolConnectionProvider`, `ConnectionProviderService` |
| `ConnectionProviderService` | **DELETE_LATER** | Routes datasource names to `ConnectionProvider` instances. Only one datasource ("default") | YES — used by `DataBaseGenericDataAccessImpl` | N/A | `ConnectionProvider`, `GenericDataAccess.DEFAULT_DATA_SOURCE` |
| `DataSourcePoolConnectionProvider` | **DELETE_LATER** | Wraps `DataSource.getConnection()`. Swallows `SQLException` and returns null — **bug** | YES — sole `ConnectionProvider` implementation | N/A | `ConnectionProvider`, `DataSource` |
| `DataBaseUtils` | **MIGRATE_TO_KOTLIN_NOW** | Single method: `closeQuiet(AutoCloseable...)`. Used by `DataBaseGenericDataAccessImpl` for resource cleanup | YES — used in `executeQueryStream()` error path | None. Trivial utility. Could also be inlined or replaced by Kotlin `use {}` | None |
| `TableMappingHandler` | **DELETE_LATER** | Maps `ResultSet` → `TableResultBean`. Only used by `executeQuery()` (buffered path) | NO — pipeline uses `JdbcRowStream` | N/A | `TableResultBean` |

### Utilities

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `OperationMappingHelper` | **MIGRATE_TO_KOTLIN_LATER** | Operation catalog lookup. 4 `HashMap` fields: mapping, typeNameMapping, parameterListMapping, dataSourceMapping. Populated by `ParseQueryConfigurationUtil` | YES — `ResolveStep` calls `getQuery()`, `getParameterList()`, `getTypeNameList()` | Medium — complex mutable state with 4 maps. Digester populates it via setters. Must keep JavaBean setters until XML loading is replaced | `NoMappingException`, `QueryMappingResult` |

### Adapters

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `TableResultBeanAdapter` | **DELETE_LATER** | Converts `TableResultBean` → `ResultFrame`. Only used by dead `OperationRunnerWorkFlowStep` | NO | N/A | `TableResultBean`, `ResultFrame` |

---

## helianthus-web — Java Source Files (9)

### Spring Boot

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `HelianthusApplication` | **KEEP_JAVA_TEMPORARY** | `@SpringBootApplication` entrypoint with `main()` | YES — application entrypoint | Low — trivial class, but `@SpringBootApplication` on Kotlin `object` has subtle differences. Keep Java until other Kotlin migrations stabilize | None |

### Exceptions

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `MarshallFormatterException` | **MIGRATE_TO_KOTLIN_NOW** | Thrown by `JacksonResultFrameMarshallFormatter` on serialization failure | YES — active JSON output | None. Pure exception class | None |

### Configuration

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `HelianthusLegacyConfiguration` | **MIGRATE_TO_KOTLIN_LATER** | `@Configuration` wiring all beans: `mappingHelper`, `connectionProviderService`, `genericDataAccess`, `marshallFormatFactory`, `pathHandler` | YES — creates all Spring beans | Medium — `@Bean` methods, manual instantiation. Kotlin migration is straightforward but should wait until the beans it creates are themselves migrated | `OperationMappingHelper`, `ConnectionProviderService`, `GenericDataAccess`, `MarshallFormatFactory`, `PathHandler` |

### Marshallers

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `ResultFrameMarshaller` | **MIGRATE_TO_KOTLIN_NOW** | Interface: `process(OutputStream, ResultFrame)`. Active output boundary | YES — `HelianthusController.renderResult()` uses it | None. Single-method interface → Kotlin `fun interface` | `ResultFrame` |
| `MarshallFormatFactory` | **MIGRATE_TO_KOTLIN_NOW** | Registry: `Map<String, ResultFrameMarshaller>`. Lookup by format string | YES — injected into `HelianthusController` | None. Simple wrapper around a `HashMap` | `ResultFrameMarshaller` |
| `JacksonResultFrameMarshallFormatter` | **MIGRATE_TO_KOTLIN_NOW** | Implements `ResultFrameMarshaller`. Serializes `ResultFrame` to JSON via Jackson | YES — the live JSON output | None. 22 lines, trivial | `ResultFrameMarshaller`, `MarshallFormatterException`, `ObjectMapper` |

### Startup / XML Loading

| Class | Classification | Responsibility | Active Path | Migration Risk | Dependencies |
|-------|---------------|---------------|-------------|---------------|--------------|
| `InputStreamUtils` | **MIGRATE_TO_KOTLIN_NOW** | Loads `InputStream` from classpath or filesystem. Used by `SpringQueryConfigurationFactoryBean` | YES — startup only | None. Static utility, 3 methods | None |
| `ParseQueryConfigurationUtil` | **KEEP_JAVA_TEMPORARY** | Parses `queryConfig.xml` via Commons Digester. Populates `OperationMappingHelper` | YES — startup only | High — Digester uses reflection and JavaBean conventions. Migrating to Kotlin risks breaking Digester's `addObjectCreate`/`addSetProperties`. Keep Java until YAML catalog (Phase 4) replaces it | `QueryConfigBean`, `QueryParameterBean`, `OperationMappingHelper`, Commons Digester |
| `SpringQueryConfigurationFactoryBean` | **KEEP_JAVA_TEMPORARY** | Spring `FactoryBean<OperationMappingHelper>` + `InitializingBean`. Loads XML at startup | YES — startup only | Medium — implements Spring `FactoryBean` and `InitializingBean` interfaces. Kotlin can implement these but the interaction with Digester is delicate. Keep Java until YAML catalog | `InputStreamUtils`, `OperationMappingHelper`, `ParseQueryConfigurationUtil` |

---

## Java Test Files (6)

| Test | Classification | What it tests | Migration Risk |
|------|---------------|---------------|---------------|
| `ResultFrameTest.java` | **MIGRATE_TO_KOTLIN_NOW** | `ResultFrame` data class (Kotlin) | None — testing Kotlin code from Java is awkward. Convert to Kotlin test |
| `TableResultBeanAdapterTest.java` | **DELETE_LATER** | `TableResultBeanAdapter` (dead code) | N/A — delete with the class |
| `JacksonResultFrameMarshallFormatterTest.java` | **MIGRATE_TO_KOTLIN_NOW** | Active JSON output | None — straightforward conversion |
| `JdbcRowStreamTest.java` | **MIGRATE_TO_KOTLIN_NOW** | Streaming data access | None — `@SpringBootTest` works in Kotlin |
| `LegacyQueryApiTest.java` | **MIGRATE_TO_KOTLIN_NOW** | End-to-end API integration | Low — `@SpringBootTest` + MockMvc works in Kotlin |
| `DataSourceIntegrationTest.java` | **MIGRATE_TO_KOTLIN_NOW** | DataSource wiring | Low — `@SpringBootTest` works in Kotlin |

---

## Migration Order

### Batch 1: Trivial — Exceptions and simple data classes (zero risk)

These are pure classes with no Spring/Digester/JDBC dependencies. Safe to migrate first.

| Order | Class | Target | Notes |
|-------|-------|--------|-------|
| 1 | `QueryMappingResult` | Kotlin data class | 3 fields: `dataSource`, `query`, `typeNameArray`. Remove `Serializable` |
| 2 | `NoMappingException` | Kotlin class extending `RuntimeException` | Drop all the boilerplate constructors. Keep `(message)` and `(message, cause)` |
| 3 | `DataAccessErrorException` | Kotlin class extending `RuntimeException` | Same pattern |
| 4 | `IncongruentColumnValueLengthException` | Kotlin class extending `RuntimeException` | Same pattern |
| 5 | `MarshallFormatterException` | Kotlin class extending `RuntimeException` | Same pattern |

**Verification:** `mvn clean test` after each file.

### Batch 2: Simple interfaces and utilities

| Order | Class | Target | Notes |
|-------|-------|--------|-------|
| 6 | `ResultFrameMarshaller` | Kotlin `fun interface` | Single method: `process(OutputStream, ResultFrame)` |
| 7 | `MarshallFormatFactory` | Kotlin class | Simple `HashMap` wrapper. Consider using `Map` directly instead |
| 8 | `JacksonResultFrameMarshallFormatter` | Kotlin class | 22 lines. `ObjectMapper` as companion `val` |
| 9 | `DataBaseUtils` | Kotlin `object` | Single method `closeQuiet()`. Or inline into `DataBaseGenericDataAccessImpl` and delete |

**Verification:** `mvn clean test` after each file.

### Batch 3: Startup utilities (low risk, isolated)

| Order | Class | Target | Notes |
|-------|-------|--------|-------|
| 10 | `InputStreamUtils` | Kotlin `object` | 3 static methods → top-level functions or `object` methods |

**Verification:** `mvn clean test`.

### Batch 4: Core catalog (medium risk)

| Order | Class | Target | Notes |
|-------|-------|--------|-------|
| 11 | `OperationMappingHelper` | Kotlin class | 4 `HashMap` fields with setters. **Keep JavaBean setters** until `ParseQueryConfigurationUtil` (Digester) is replaced by YAML. Consider making fields `var` with `@JvmField` or using `@JvmSetter` |

**Verification:** `mvn clean test`. Critical: verify XML loading still works.

### Batch 5: Configuration (medium risk)

| Order | Class | Target | Notes |
|-------|-------|--------|-------|
| 12 | `HelianthusLegacyConfiguration` | Kotlin `@Configuration` | `@Bean` methods translate directly. Wait until Batch 1-4 are done so the types it references are already Kotlin |

**Verification:** `mvn clean test` + start the application.

### Batch 6: Test conversions

| Order | Test | Notes |
|-------|------|-------|
| 13 | `ResultFrameTest.java` | Testing Kotlin code — convert first |
| 14 | `JacksonResultFrameMarshallFormatterTest.java` | Active JSON output test |
| 15 | `JdbcRowStreamTest.java` | `@SpringBootTest` with H2 |
| 16 | `LegacyQueryApiTest.java` | End-to-end integration |
| 17 | `DataSourceIntegrationTest.java` | DataSource wiring |

**Verification:** `mvn clean test` after each.

---

## What to Keep Java (and why)

| Class | Reason to stay Java | When to migrate |
|-------|-------------------|-----------------|
| `HelianthusApplication` | `@SpringBootApplication` on Kotlin `object` has subtle differences with `main()` and component scanning | After all other migrations are stable |
| `ParseQueryConfigurationUtil` | Commons Digester uses reflection + JavaBean conventions. Kotlin properties may break `addSetProperties`/`addCallMethod` | Phase 4: replace with YAML catalog. Delete entirely |
| `SpringQueryConfigurationFactoryBean` | Implements Spring `FactoryBean`/`InitializingBean`. Interaction with Digester is delicate | Phase 4: replace with YAML catalog. Delete entirely |

---

## What to Delete/Replace (not migrate)

| Class | Action | When |
|-------|--------|------|
| `TableResultBean` | **DELETE** | After `executeQuery()` is removed from `GenericDataAccess` |
| `TableMappingHandler` | **DELETE** | After `executeQuery()` is removed |
| `TableResultBeanAdapter` | **DELETE** | After legacy workflow is removed |
| `ConnectionProvider` | **REPLACE** with `DataSource` | After `DataBaseGenericDataAccessImpl` is refactored |
| `ConnectionProviderService` | **REPLACE** with `DataSource` lookup | After `DataBaseGenericDataAccessImpl` is refactored |
| `DataSourcePoolConnectionProvider` | **REPLACE** with direct `DataSource` | After `DataBaseGenericDataAccessImpl` is refactored |
| `DataBaseGenericDataAccessImpl` | **REPLACE** with `JdbcTemplate`-based implementation | Separate phase. Not a migration — a rewrite |
| `GenericDataAccess` | **MIGRATE_LATER** after `executeQuery()` is removed | Remove `executeQuery()` method, then migrate interface + impl to Kotlin |

---

## Risks

| Risk | Mitigation |
|------|-----------|
| `OperationMappingHelper` has 4 `HashMap` fields populated by Digester via setters | Keep JavaBean-compatible `setMapping()`, `setTypeNameMapping()`, etc. Use `@JvmField` or explicit setters in Kotlin. Test XML loading after migration |
| `ParseQueryConfigurationUtil` uses Digester `addCallMethod("queries/query-config/query", "setQuery", 1)` which calls JavaBean setters on `QueryConfigBean`/`QueryParameterBean` | These beans are already Kotlin data classes with `@JvmOverloads` or `var` properties. Verify Digester compatibility before touching `ParseQueryConfigurationUtil` |
| `DataBaseGenericDataAccessImpl` uses `java.util.logging.Logger` (not SLF4J) | When replacing with `JdbcTemplate`, switch to SLF4J. Do not migrate as-is |
| `DataSourcePoolConnectionProvider` silently swallows `SQLException` and returns `null` | This is a **bug**. When replacing with direct `DataSource`, let exceptions propagate |
| Exception classes have 5 constructors each (boilerplate) | Kotlin only needs `(message: String? = null, cause: Throwable? = null)`. Verify Java callers work with default parameters |
| `GenericDataAccess` interface has `executeQuery()` returning `TableResultBean` | Remove `executeQuery()` before migrating. Pipeline only uses `executeQueryStream()` |

---

## Recommended First Kotlin Migration Task

**Migrate `QueryMappingResult` to a Kotlin data class.**

Rationale:
- Simplest possible migration: 3 fields, no behavior, no Spring annotations
- On the active runtime path (`ResolveStep` consumes it)
- Validates that Kotlin/Java interop works for the core module
- No Digester/Spring/JDBC risk
- Sets the pattern for the other bean migrations

Target:
```kotlin
package helianthus.core.bean

data class QueryMappingResult(
    var dataSource: String? = null,
    var query: String? = null,
    var typeNameArray: Array<String>? = null
)
```

Note: `var` fields with defaults are required because `OperationMappingHelper.getQuery()` creates an instance and sets fields via setters. Digester also uses setters. After migration, verify `mvn clean test`.

---

## Answers to Specific Questions

### Which classes are safe Kotlin data class migrations?

`QueryMappingResult` — the only remaining Java bean that is a pure data holder on the active path.

### Which classes are risky because of Spring/Digester/JavaBean compatibility?

- `ParseQueryConfigurationUtil` — Digester uses reflection + JavaBean conventions
- `SpringQueryConfigurationFactoryBean` — implements Spring `FactoryBean`/`InitializingBean`
- `OperationMappingHelper` — populated by Digester via setters
- `HelianthusLegacyConfiguration` — `@Configuration` with `@Bean` methods (low risk, but should wait until referenced types are Kotlin)

### Which classes should stay Java until YAML catalog replaces XML?

- `ParseQueryConfigurationUtil`
- `SpringQueryConfigurationFactoryBean`
- Both will be deleted entirely in Phase 4 when `queryConfig.xml` + Digester are replaced by YAML + JSON Schema

### Which classes should stay Java until DataAccess is simplified?

- `GenericDataAccess` — has `executeQuery()` returning `TableResultBean`. Remove legacy method first
- `DataBaseGenericDataAccessImpl` — replace with `JdbcTemplate`, don't migrate
- `ConnectionProvider`, `ConnectionProviderService`, `DataSourcePoolConnectionProvider` — replace with `DataSource`

### Which Java tests should be converted to Kotlin?

All 5 active tests (delete `TableResultBeanAdapterTest` with its class):
1. `ResultFrameTest.java` — highest priority (tests Kotlin code)
2. `JacksonResultFrameMarshallFormatterTest.java`
3. `JdbcRowStreamTest.java`
4. `LegacyQueryApiTest.java`
5. `DataSourceIntegrationTest.java`
