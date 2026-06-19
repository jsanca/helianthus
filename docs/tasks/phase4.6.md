# Phase 4.6 — Stabilization / Demo Readiness

## Goal
Make the current secure operation runner reliable, observable, debuggable, and presentable before moving to Declarative CRUD.

## 4.6.1 Parameter Binding + Runtime Filtering Gap

Problem:
- UI sends parameters.
- Catalog defines parameters.
- But parameters are not consistently used to:
  - bind SQL parameters
  - apply runtime filters
  - modify/compose SQL WHERE safely

Tasks:
- Audit BindStep, ResolveStep, QueryStep, FilterStep.
- Verify how request params flow into BoundParameters.
- Add tests for parameterized operations.
- Decide first supported model:
  - SQL-bound params first: `where productCode = :productCode` or `?`
  - pipeline filters second
- Fix operations that return no rows unexpectedly.

Done:
- Parameterized operation returns expected rows.
- Missing required param returns 400.
- Invalid param type returns 400.
- Filtering behavior is documented and tested.

## 4.6.2 Broken / Empty Operations

Problem:
- Some operations should return data but return empty results.

Tasks:
- Identify failing operations from starter catalog.
- Add smoke tests for each starter operation/configuration.
- Validate SQL, parameters, datasource, pipeline config, and security.
- Fix seed data or catalog definitions as needed.

Done:
- Every starter operation returns expected data or documented empty result.

## 4.6.3 Observability + Debuggability

Tasks:
- Add structured logs around:
  - request path
  - operationId/configurationId/format
  - authenticated user/roles
  - permission decision
  - query execution start/end
  - datasource
  - row count
  - execution time
  - errors
- Add request id/correlation id.
- Add Spring Boot Actuator.
- Consider OpenTelemetry/OTLP later, but start with logs + actuator.
- Add docs for running server in debug mode.

Debug command example:

```bash
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
java -jar helianthus-web/target/helianthus-web-1.0.jar
````

Done:

* Can trace one request from HTTP → permission → pipeline → JDBC → response.
* Server can run with remote debug port.

## 4.6.4 Cache Study

Do not implement heavy caching yet.

Tasks:

* Study where cache belongs:

    * catalog cache
    * operation result cache
    * HTTP cache/Varnish
    * datasource metadata cache
* Define cache keys:

    * operationId
    * configurationId
    * format
    * params
    * user/roles if security-sensitive
* Decide first safe cache candidate.

Done:

* Short cache design note exists.
* No unsafe result cache added prematurely.



