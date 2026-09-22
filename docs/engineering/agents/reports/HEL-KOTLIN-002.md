# HEL-KOTLIN-002 — Runtime Migration Preparation & Characterization

- **Date:** 2026-09-20
- **Role:** Clio — Software Engineer
- **Status:** Complete. Full server test suite passes (271 tests).

---

## 1. Changes

### 1.1 Framework-neutral principal (new)

- `security/Principal.kt` — `data class Principal(name: String, roles: Set<String>)`
  plus `const val ADMIN_ROLE = "ADMIN"`. This is the Helianthus-owned representation
  of an authenticated caller. Role names carry no framework prefix.
- `security/SpringAuthenticationAdapter.kt` — `fun Authentication.toPrincipal()`,
  the single runtime-boundary point that translates Spring Security authorities into
  Helianthus role names (strips the `ROLE_` prefix, drops non-role authorities).

### 1.2 Permission evaluators decoupled from Spring Security

- `OperationPermissionEvaluator` and `EntityPermissionEvaluator` now depend only on
  `Principal`. All `org.springframework.security.core.Authentication` references were
  removed from these two classes. Their logic (ADMIN bypass, operation/config role
  union, entity read roles, empty-role semantics) is unchanged.

### 1.3 Runtime adapters updated

- `HelianthusController`, `EntityCrudController`, `CatalogController` now convert the
  Spring `Authentication` to a `Principal` at the boundary via `auth.toPrincipal()` and
  pass the `Principal` to the evaluators.

### 1.4 Dead code removed

See §4.

### 1.5 Characterization + contract tests added

See §2 and §5.

### 1.6 Test catalog extended

- `src/test/resources/operations.yml` gained a `config-secured` operation with an
  `admin-only` configuration carrying configuration-level `security.roles: [ADMIN]`.
  This is required to exercise configuration-level roles over HTTP (the existing test
  catalog had none). Test-only; production `operations.yml` is unchanged.

---

## 2. Characterized HTTP Contract

`HttpContractCharacterizationTest` (30 tests) drives the running server over HTTP with
the JDK `HttpClient` (no Spring test utilities) and pins the following:

| Area | Pinned behavior |
|---|---|
| Operations | 200 JSON success (exact body), default-config resolution, explicit config projection, parameterized single-row, missing-required-param → 400, unknown op → 404, unknown config → 404 |
| Entities | list (rowCount), get-by-PK (exact JSON body), unknown entity → 404, missing row → 404, field filtering, ordering, pagination, unknown filter column → 400 |
| Formats | JSON (exact body), CSV (exact header + rows), XML (structure), HTML (structure); exact `Content-Type` per format |
| Security | 401 unauthenticated; 403 + `"Access denied"` for insufficient operation-level, configuration-level, and entity-read roles; 200 for sufficient roles; ADMIN bypass |
| Errors | 400 (`Missing required parameter`, `limit must be an integer`, `orderBy column … not in entity fields`, `Filter column … not in entity fields`), 401, 403 (`Access denied`), 404 (operation/config/entity/row), 406 unsupported format |

Exact bodies are pinned for JSON (entity get-by-id and `all-products`) and for CSV;
XML and HTML are pinned structurally (root/table/element presence with exact values).
Stable JSON is asserted as a full golden string.

---

## 3. Security Boundary

```
org.springframework.security.core.Authentication
                     │
                     ▼
        runtime adapter (SpringAuthenticationAdapter.toPrincipal)
                     │
                     ▼
        helianthus.core.security.Principal
                     │
                     ▼
   OperationPermissionEvaluator / EntityPermissionEvaluator
```

The evaluators contain no Spring Security imports (verified). Spring-specific role
extraction lives solely in the adapter.

---

## 4. Removed Dead Code

Confirmed no production callers before deletion (grep-verified):

- `helianthus-web/.../MarshallFormatterException.kt`
- `helianthus-web/.../marshall/ResultFrameMarshaller.kt`
- `helianthus-web/.../marshall/tableresult/JacksonResultFrameMarshallFormatter.kt`
- `helianthus-web/.../bean/QueryConfigBean.kt`
- `helianthus-web/.../bean/QueryParameterBean.kt`
- `helianthus/.../IncongruentColumnValueLengthException.kt`
- `helianthus-web/src/test/java/.../marshall/tableresult/JacksonResultFrameMarshallFormatterTest.java`

The empty `marshall` directories (main and test) were removed. No remaining references
exist (verified).

---

## 5. Test Coverage Added

- `HttpContractCharacterizationTest` — 30 black-box HTTP characterization tests (§2).
- `OperationPermissionEvaluatorTest` — 15 framework-neutral unit tests (no Spring):
  operation-level accept/reject, configuration-level accept/reject, role union, ADMIN
  bypass, no/empty required roles, multiple caller roles, unknown operation, unknown
  configuration (both no-op-roles and op-roles-enforced cases), visibility filtering.
- `ResultFrameJsonContractTest` — 1 test pinning the exact `ResultFrame` JSON
  (schema, columns, names, types, nullable, rows, nulls, metadata, rowCount).
- `EntityPermissionEvaluatorTest` — updated to use `Principal`; Spring Security test
  objects (`TestingAuthenticationToken`, `SimpleGrantedAuthority`) removed.

---

## 6. Unexpected Findings

1. **`ResultFrame` JSON serializes bean properties alphabetically.** The runtime
   (Jackson 3 via Spring Boot) emits `{"metadata":…,"rows":…,"schema":…}` and orders
   each column as `{"name":…,"nullable":…,"type":…}` and metadata as
   `{"executionTimeMs":…,"rowCount":…}`. HEL-KOTLIN-001 assumed declaration order.
   The characterization tests now pin the real (alphabetical) order.

2. **`ResultSchema` leaks a `columnCount` field into JSON.** `columnCount` is a computed
   Kotlin property (`val columnCount get() = columns.size`) and is serialized alongside
   `columns`. This is now part of the pinned JSON contract — a shape detail HEL-KOTLIN-001
   did not record.

3. **Row maps preserve column order** while bean properties are alphabetized. The `rows`
   entries are `Map` values serialized in insertion (schema-column) order, so the two
   orderings differ within the same document. Pinned as-is.

4. **`checkPermission` treats an unknown configuration as "no required roles"** when the
   operation itself declares no roles (returns `true`; the 404 surfaces later from
   `OperationCatalog.resolveOperation`). This behavior is now pinned in a unit test and
   is subtly different from what a naive reading of HEL-KOTLIN-001 would imply.

No discrepancy contradicts HEL-KOTLIN-001's core conclusion (Spring is a replaceable
shell); these are refinements discovered by writing the characterization tests.

---

## 7. Validation

- `mvn clean test` (from `server/`): **BUILD SUCCESS** — 271 tests, 0 failures, 0 errors.
- New/updated test counts: characterization 30, operation-permission 15, JSON contract 1,
  entity-permission 12 (updated). Removed 3 dead marshaller tests.
- Verified: evaluators have no Spring Security imports; deleted classes have no remaining
  references; no Ktor dependency in any POM.

---

## 8. Deferred Work

Explicitly retained (not done in this task):

- **JDBC/coroutine spike** — decide blocking-vs-`Flow` data access.
- **Ktor JWT/OIDC/Keycloak spike** — reproduce `realm_access.roles` → `ROLE_*` mapping.
- **Four-format Ktor responder spike** — JSON/XML/CSV/HTML equivalence.
- **Kotlin-native pipeline evaluation** — the mutable `PipelineContext` / error-in-context
  design is a candidate Stage 2 improvement; not modified here.
