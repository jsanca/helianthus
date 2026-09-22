# Module Diagram

## Purpose

Show the three Maven modules of the Helianthus server, their dependency direction, and the responsibility of each. This is an architectural orientation diagram — it does not enumerate every class.

## Source evidence

- `server/pom.xml` declares the three modules: `helianthus`, `helianthus-runtime`, `helianthus-web`.
- `server/helianthus/pom.xml` depends on no other Helianthus module.
- `server/helianthus-runtime/pom.xml` depends only on `helianthus:helianthus-core`.
- `server/helianthus-web/pom.xml` depends only on `helianthus:helianthus-runtime`.
- The dependency graph is therefore strictly `web → runtime → core` (acyclic, no inverse edges).

## Diagram

```mermaid
flowchart TB
    subgraph WEB["helianthus-web (Spring Boot application)"]
        WEB_ROLE["HTTP transport<br/>Spring MVC controllers<br/>Authentication adapter<br/>Content negotiation<br/>Catalog / datasource / security / web config<br/>ResultFrame message converters"]
    end

    subgraph RUNTIME["helianthus-runtime (framework-neutral facade)"]
        RUNTIME_ROLE["HelianthusRuntime facade<br/>Catalog semantics &amp; permission rules<br/>Operation pipeline (resolve → bind → query → project → filter → limit → materialize)<br/>Entity execution (list / get-by-id)<br/>Catalog read-model (CatalogSummary)"]
    end

    subgraph CORE["helianthus-core (result + data-access contracts)"]
        CORE_ROLE["ResultFrame / ResultSchema / CloseableRowStream<br/>GenericDataAccess contract<br/>SqlDialect contract<br/>NamedParameterSql parser<br/>DataAccessFactory cross-module seam<br/>JDBC implementation (internal)"]
    end

    WEB -->|"depends on<br/>runtime facade only"| RUNTIME
    RUNTIME -->|"depends on<br/>data-access contracts<br/>+ result model"| CORE

    WEB -.- WEB_API["Public surface: HelianthusRuntime,<br/>HelianthusRuntimeBuilder, ResultFrame,<br/>CatalogSummary, EntityListRequest,<br/>Principal, exceptions, path handlers"]
    RUNTIME -.- RUNTIME_API["Public surface: HelianthusRuntime,<br/>HelianthusRuntimeBuilder, Principal,<br/>CatalogSummary, EntityListRequest,<br/>path handlers, public exceptions"]
    CORE -.- CORE_API["Public surface: ResultFrame,<br/>CloseableRowStream, GenericDataAccess,<br/>SqlDialect, SqlExecutionPlan,<br/>DataAccessFactory, NamedParameterSql,<br/>public exceptions"]
```

## What the diagram is communicating

- **Acyclic dependency direction.** `web → runtime → core`. `core` has no dependency on either sibling; `runtime` does not depend on `web`.
- **Boundaries, not implementations.** Each module box names its responsibility rather than listing every class.
- **Public surfaces are listed for orientation** — they are the only types a downstream consumer should target.

## Principal classes involved

| Module | Entry types |
| --- | --- |
| `helianthus-web` | `HelianthusApplication`, `HelianthusController`, `EntityCrudController`, `CatalogController`, `CatalogConfig`, `DataSourceConfig`, `SecurityConfig`, `HelianthusExceptionHandler`, `RequestLoggingFilter`, `HelianthusWebConfiguration` |
| `helianthus-runtime` | `HelianthusRuntime`, `HelianthusRuntimeBuilder`, `CatalogLoader`, `OperationCatalog`, `EntityCatalog`, `PipelineFactory`, `EntityService` |
| `helianthus-core` | `ResultFrame`, `CloseableRowStream`, `GenericDataAccess`, `SqlDialect`, `NamedParameterSql`, `DataAccessFactory` |

## Related diagrams

- [Core class diagram](./classes-core.md)
- [Runtime class diagram](./classes-runtime.md)
- [Web class diagram](./classes-web.md)
