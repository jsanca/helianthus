# Phase 5.0 — Declarative CRUD Design

**Status:** Design Phase  
**Goal:** Design the first safe version of declarative CRUD for Helianthus  
**Scope:** Read-only CRUD only (list, get by id). Writes deferred to Phase 5.2+.

**Important:** Helianthus is not implementing ORM behavior. Entities are declarative table projections with explicit field whitelists, not managed objects with identity tracking, lazy loading, or relationship mapping. Helianthus generates SQL from declarative configuration — it does not abstract the database or manage object lifecycle.

---

## 1. Catalog Model

### 1.1 Should CRUD live under `entities`?

**Decision:** Yes. Introduce a top-level `entities` section in `operations.yml`, separate from `operations`.

**Rationale:**
- Clear separation between custom SQL operations and auto-generated CRUD
- Different validation rules (entities require primary keys, operations do not)
- Different security model (entities have read/write roles; operations have role lists)
- Easier to reason about: "operations = custom SQL, entities = table-backed CRUD"

### 1.2 Entity YAML Model

```yaml
entities:
  products:
    label: Products
    description: Product catalog
    datasource: default
    table: products
    primaryKey: productCode
    fields:
      - productCode
      - productName
      - productLine
      - buyPrice
      - quantityInStock
    security:
      read:
        roles: [GUEST, ADMIN]
      # write: (Phase 5.2+)
      #   roles: [ADMIN]

  customers:
    label: Customers
    description: Customer records
    datasource: secondary
    table: customers
    primaryKey: customerNumber
    fields:
      - customerNumber
      - customerName
      - contactFirstName
      - contactLastName
      - city
      - country
      # phone, addressLine1, state, postalCode, creditLimit are hidden
    security:
      read:
        roles: [ADMIN]
```

### 1.3 How does entity map to datasource/table?

- `datasource`: Must match a key in the top-level `datasources` map (same as operations)
- `table`: The actual database table name (defaults to entity name if omitted)
- Entity name (YAML key) is the API resource identifier

### 1.4 How are primary keys declared?

- `primaryKey`: Single column name (string) or list of column names (composite key)
- **Required** — entity cannot be exposed without a primary key
- Used for `get by id` endpoint and future write operations

**Examples:**
```yaml
# Single primary key
primaryKey: productCode

# Composite primary key (Phase 5.2+ for writes, but declare now)
primaryKey: [orderId, orderLineNumber]
```

### 1.5 How are exposed fields declared?

- `fields`: Explicit whitelist of column names to expose
- **Required** — entity cannot be exposed without at least one field
- Order matters: determines column order in list/get responses
- Omitted columns are hidden from all CRUD responses

**Rationale:** Explicit whitelist is safer than "expose everything except..." — prevents accidental exposure of sensitive columns (passwords, tokens, internal flags).

### 1.6 How are hidden fields declared?

- **Implicit:** Any column in the table that is not in `fields` is hidden
- No explicit `hiddenFields` list — whitelist-only approach
- Hidden fields cannot be filtered, sorted, or returned in any CRUD response

---

## 2. API Shape

### 2.1 Route prefix

**Decision:** `/api/entities/{entityName}`

**Rationale:**
- Avoids conflicts with custom operations at `/api/op/**`
- Clear semantic distinction: "entities = table-backed resources"
- Easier to apply entity-specific middleware (caching, rate limiting, audit logging)
- Aligns with REST conventions (resource-oriented URLs)

### 2.2 Endpoints (Read-Only)

#### List entities
```
GET /api/entities/{entityName}.json
GET /api/entities/{entityName}.csv
GET /api/entities/{entityName}.xml
GET /api/entities/{entityName}.html
```

**Query parameters:**
- `limit` (optional): Max rows to return (default: 100, max: 1000)
- `offset` (optional): Number of rows to skip (default: 0)
- `orderBy` (optional): Column name to sort by (must be in `fields`)
- `orderDir` (optional): `asc` or `desc` (default: `asc`)
- **Filter params:** One query param per exposed field (e.g., `?productLine=Classic Cars&buyPrice=50`)
  - Exact match only (Phase 5.0)
  - Operators (`gt`, `lt`, `in`, etc.) deferred to Phase 5.1+

**Response:** `ResultFrame` (same as operations)

#### Get entity by ID
```
GET /api/entities/{entityName}/{id}.json
GET /api/entities/{entityName}/{id}.csv
GET /api/entities/{entityName}/{id}.xml
GET /api/entities/{entityName}/{id}.html
```

**Response:** `ResultFrame` with 0 or 1 row  
**404:** If no row matches the primary key

### 2.3 Coexistence with `/api/op/**`

**Decision:** Yes, they coexist. No conflicts because:
- Different path prefixes (`/api/entities/` vs `/api/op/`)
- Different controllers (new `EntityCrudController` vs existing `HelianthusController`)
- Different catalog sections (`entities` vs `operations`)

### 2.4 Route conflict avoidance

- Entity names and operation names are in separate namespaces
- An entity named `products` does not conflict with an operation named `products`
- If needed, future validation can warn if entity name matches operation name (but not block)

---

## 3. Read-Only Implementation Plan

### 3.1 List endpoint implementation

**Flow:**
1. `EntityCrudController.handleList(request)` receives request
2. Parse entity name from path
3. Look up entity in `EntityCatalog` (new class, similar to `OperationCatalog`)
4. Check read permissions via `EntityPermissionEvaluator` (new class)
5. Extract query params: `limit`, `offset`, `orderBy`, `orderDir`, filter params
6. Validate filter params against `fields` whitelist
7. Build SQL:
   ```sql
   SELECT {fields} FROM {table}
   WHERE {field1} = ? AND {field2} = ?
   ORDER BY {orderBy} {orderDir}
   LIMIT {limit} OFFSET {offset}
   ```
8. Execute via `GenericDataAccess.executeQueryStream()` (reuse existing infrastructure)
9. Return `ResultFrame`

**SQL generation rules:**
- Use `PreparedStatement` with parameterized queries (no string concatenation)
- Quote identifiers with double quotes (`"productCode"`) to handle reserved words
- Filter params are bound in declaration order (same as positional params in operations)

### 3.2 Get by ID endpoint implementation

**Flow:**
1. `EntityCrudController.handleGet(request)` receives request
2. Parse entity name and ID from path
3. Look up entity in `EntityCatalog`
4. Check read permissions
5. Build SQL:
   ```sql
   SELECT {fields} FROM {table}
   WHERE {primaryKey} = ?
   ```
6. Execute via `GenericDataAccess.executeQueryStream()`
7. Return `ResultFrame` (0 or 1 row)

**Type coercion:**
- Parse ID from path as string
- Coerce to primary key type (look up from database metadata or entity definition)
- For Phase 5.0, assume primary key is `string` or `number` (most common cases)

### 3.3 New classes

#### `EntityCatalog` (in `helianthus-web`)
```kotlin
class EntityCatalog(
    val entities: Map<String, EntityDef>
) {
    fun resolveEntity(entityName: String): EntityDef
}

data class EntityDef(
    val label: String?,
    val description: String?,
    val datasource: String,
    val table: String,
    val primaryKey: PrimaryKeyDef,
    val fields: List<String>,
    val security: EntitySecurityDef?
)

data class PrimaryKeyDef(
    val columns: List<String>  // single or composite
)

data class EntitySecurityDef(
    val read: EntityRoleDef?,
    val write: EntityRoleDef?  // Phase 5.2+
)

data class EntityRoleDef(
    val roles: List<String>
)
```

#### `EntityPermissionEvaluator` (in `helianthus-web`)
```kotlin
class EntityPermissionEvaluator(
    private val entityCatalog: EntityCatalog
) {
    fun checkReadPermission(auth: Authentication, entityName: String): Boolean
    fun checkWritePermission(auth: Authentication, entityName: String): Boolean  // Phase 5.2+
    fun filterVisibleEntities(auth: Authentication): Map<String, EntityDef>
}
```

#### `EntityCrudController` (in `helianthus-web`)
```kotlin
@RestController
class EntityCrudController(
    private val entityCatalog: EntityCatalog,
    private val permissionEvaluator: EntityPermissionEvaluator,
    private val dataAccess: GenericDataAccess
) {
    @GetMapping("/api/entities/**")
    fun handle(request: HttpServletRequest): ResponseEntity<ResultFrame>
}
```

#### `EntityPathHandler` (in `helianthus-web`)
```kotlin
class EntityPathHandler {
    fun parseListPath(path: String): EntityListPathResult
    fun parseGetPath(path: String): EntityGetPathResult
}

data class EntityListPathResult(
    val entityName: String,
    val format: String
)

data class EntityGetPathResult(
    val entityName: String,
    val id: String,
    val format: String
)
```

### 3.4 Catalog loading

**Decision:** Extend `CatalogConfig.kt` to parse `entities` section.

**Flow:**
1. Load `operations.yml` (same as today)
2. Parse `entities` section into `Map<String, EntityDef>`
3. Validate each entity:
   - `datasource` must exist in `datasources` map
   - `primaryKey` must be declared
   - `fields` must be non-empty
   - `primaryKey` columns must be in `fields`
4. Create `EntityCatalog` bean
5. Create `EntityPermissionEvaluator` bean
6. Create `EntityCrudController` bean

**Validation errors:**
- Fail fast at startup if entity definition is invalid
- Log clear error messages: "Entity 'products' has invalid primary key: 'id' is not in fields list"

---

## 4. Security Model

### 4.1 Read roles

- `security.read.roles`: List of roles allowed to read this entity
- If omitted: entity is readable by any authenticated user (same as operations)
- ADMIN role always bypasses (same as operations)

**Example:**
```yaml
entities:
  products:
    security:
      read:
        roles: [GUEST, ADMIN]
```

### 4.2 Write roles (Phase 5.2+)

- `security.write.roles`: List of roles allowed to create/update/delete
- If omitted: writes are disabled (even for ADMIN)
- ADMIN role always bypasses

**Example:**
```yaml
entities:
  products:
    security:
      read:
        roles: [GUEST, ADMIN]
      write:
        roles: [ADMIN]
```

### 4.3 Admin override

**Decision:** Yes, ADMIN role bypasses all entity permission checks (same as operations).

**Rationale:**
- Consistent with existing security model
- Simplifies testing and administration
- ADMIN is "superuser" — should not be blocked by declarative security

### 4.4 Unauthenticated access

**Decision:** No public access. All entity endpoints require authentication (same as operations).

---

## 5. Output Formats

### 5.1 Should list/get return ResultFrame?

**Decision:** Yes, reuse `ResultFrame` for all CRUD responses.

**Rationale:**
- Consistent with operations (same response shape)
- Reuse existing message converters (JSON, XML, HTML, CSV)
- Admin UI can treat entities and operations uniformly

### 5.2 Supported formats

**Decision:** Support all formats immediately (JSON, XML, HTML, CSV).

**Rationale:**
- Converters already exist and are tested
- No additional implementation cost
- Consistent with operations (same format support)

**Example responses:**
```
GET /api/entities/products.json       → JSON ResultFrame
GET /api/entities/products.csv        → CSV table
GET /api/entities/products.xml        → XML ResultFrame
GET /api/entities/products.html       → HTML table
GET /api/entities/products/S10_1678.json → JSON ResultFrame (1 row)
```

---

## 6. Validation

Validation happens in two layers at startup:

### 6.1 Layer 1: JSON Schema validation

**Decision:** Validate `operations.yml` against `operations.schema.json` before parsing.

**Rationale:**
- Catches structural errors early (missing required fields, wrong types, unknown properties)
- Provides clear, standardized error messages
- Single source of truth for catalog structure
- Works for both operations and entities

**Implementation:**
- Load `operations.schema.json` from classpath
- Validate YAML against schema using JSON Schema validator (e.g., `networknt/json-schema-validator`)
- Fail fast with detailed validation errors if schema check fails

**Example errors:**
```
ERROR: operations.yml validation failed:
  - $.entities.products: required property 'primaryKey' is missing
  - $.entities.customers.fields: array must have at least 1 item
  - $.entities.orders.datasource: must be one of [default, secondary]
```

**Schema coverage:**
- Required fields (`primaryKey`, `fields`, `datasource`)
- Type constraints (strings, arrays, objects)
- Enum values (datasource names, security roles)
- Nested structure (security.read.roles, etc.)

### 6.2 Layer 2: Semantic validation

After JSON Schema passes, perform semantic checks that require runtime context:

**Required checks:**
1. Entity must have a `primaryKey` (single or composite)
2. Entity must have at least one field in `fields` list
3. `primaryKey` columns must be in `fields` list
4. `datasource` must exist in top-level `datasources` map
5. `table` must be non-empty (or default to entity name)
6. `security.read.roles` must reference valid roles (if present)

**Why separate from JSON Schema?**
- JSON Schema cannot check cross-references (e.g., datasource exists in `datasources` map)
- JSON Schema cannot check business rules (e.g., primaryKey columns must be in fields)
- Semantic validation requires parsed catalog context

**Validation errors:**
- Fail fast at startup (do not start server with invalid catalog)
- Log clear error messages with entity name and validation failure

**Example:**
```
ERROR: Entity 'products' validation failed:
  - primaryKey 'id' is not in fields list [productCode, productName]
```

### 6.3 Runtime validation

**Required checks:**
1. Entity name must exist in catalog (404 if not)
2. Filter params must reference exposed fields (400 if not)
3. `orderBy` must reference exposed field (400 if not)
4. `limit` must be positive integer (400 if not)
5. `offset` must be non-negative integer (400 if not)
6. Primary key value must be coercible to expected type (400 if not)

---

## 7. SQL Generation

### 7.1 How to quote identifiers safely?

**Decision:** Use `SqlDialect.quoteIdentifier()` for all identifiers (table names, column names).

**Example:**
```sql
SELECT "productCode", "productName" FROM "products" WHERE "productCode" = ?
```

**Rationale:**
- Delegates quoting to the dialect — PostgreSQL uses double quotes, MySQL uses backticks
- Handles reserved words and special characters
- Prevents SQL injection via identifier names

### 7.2 How to avoid SQL injection?

**Rules:**
1. **Never concatenate user input into SQL** — use `PreparedStatement` with parameterized queries
2. **Whitelist filter columns** — only allow filtering on fields in `fields` list
3. **Whitelist orderBy column** — only allow sorting on fields in `fields` list
4. **Validate limit/offset** — must be positive integers
5. **Quote all identifiers** — use `SqlDialect.quoteIdentifier()` for table/column names

**Example (safe):**
```kotlin
val q: (String) -> String = dialect::quoteIdentifier
val sql = """
    SELECT ${fields.joinToString { q(it) }}
    FROM ${q(tableName)}
    WHERE ${q(filterColumn)} = ?
    ORDER BY ${q(orderByColumn)} $orderDir
    ${dialect.limitOffset(limit, offset)}
""".trimIndent()

val params = listOf(filterValue, limit, offset)
dataAccess.executeQueryStream(sql, params)
```

**Example (unsafe — DO NOT DO THIS):**
```kotlin
// WRONG: User input concatenated into SQL
val sql = "SELECT * FROM products WHERE productLine = '$userInput'"
```

### 7.3 SQL dialect abstraction (Phase 5.1)

**Decision:** Introduce minimal `SqlDialect` interface in Phase 5.1 to abstract identifier quoting and LIMIT/OFFSET syntax.

**Scope:** Only two methods — `quoteIdentifier` and `limitOffset`. No DDL, no type mapping, no query building.

```kotlin
interface SqlDialect {
    fun quoteIdentifier(name: String): String
    fun limitOffset(limit: Int, offset: Int): String
}

class PostgresDialect : SqlDialect {
    override fun quoteIdentifier(name: String) = "\"$name\""
    override fun limitOffset(limit: Int, offset: Int) = "LIMIT $limit OFFSET $offset"
}

class H2Dialect : SqlDialect {
    override fun quoteIdentifier(name: String) = "\"$name\""
    override fun limitOffset(limit: Int, offset: Int) = "LIMIT $limit OFFSET $offset"
}
```

**Rationale:**
- H2 (test database) and PostgreSQL use the same quoting and LIMIT/OFFSET syntax, but we should not assume this forever
- MySQL uses backticks for identifiers, SQL Server uses brackets — dialect abstraction prepares for future support
- Minimal scope avoids over-engineering (no ORM-style type mapping or query builders)

**Implementation:**
- `DataSourceConfig` creates a `SqlDialect` bean per datasource based on driver metadata or configuration
- `EntityCrudController` receives dialect map and selects dialect by entity datasource
- Default dialect: `PostgresDialect` (works for both PostgreSQL and H2 in Phase 5.1)

**Future (Phase 5.6+):**
- Add `MySqlDialect`, `SqliteDialect`, etc.
- Extend `SqlDialect` with additional methods if needed (e.g., `booleanLiteral`, `dateFunction`)

---

## 8. Runtime Registry

### 8.1 How should generated CRUD be visible in `/api/admin/catalog`?

**Decision:** Add `entities` section to `CatalogResponse`.

**Example response:**
```json
{
  "app": "Helianthus API",
  "formats": ["json", "html", "csv", "xml"],
  "operations": [ ... ],
  "entities": [
    {
      "name": "products",
      "label": "Products",
      "description": "Product catalog",
      "datasource": "default",
      "table": "products",
      "primaryKey": ["productCode"],
      "fields": ["productCode", "productName", "productLine", "buyPrice", "quantityInStock"],
      "security": {
        "read": { "roles": ["GUEST", "ADMIN"] }
      }
    }
  ]
}
```

**Implementation:**
- Extend `CatalogController.catalog()` to include entities
- Filter entities by read permission (same as operations)
- Add `EntitySummary` data class (similar to `OperationSummary`)

---

## 9. Risks and Mitigations

### 9.1 Risk: SQL injection via filter params

**Mitigation:**
- Whitelist filter columns (only allow fields in `fields` list)
- Use `PreparedStatement` with parameterized queries
- Validate all user inputs (limit, offset, orderBy)

### 9.2 Risk: Accidental exposure of sensitive columns

**Mitigation:**
- Explicit whitelist (`fields` list) — no implicit exposure
- Validation at startup: entity must declare at least one field
- Clear documentation: "If it's not in `fields`, it's hidden"

### 9.3 Risk: Performance issues with large tables

**Mitigation:**
- Default `limit` of 100 (same as operations)
- Max `limit` of 1000 (prevent unbounded queries)
- Require `orderBy` for predictable pagination
- Future: Add indexing recommendations in documentation

### 9.4 Risk: Primary key type coercion errors

**Mitigation:**
- For Phase 5.0, support only `string` and `number` primary keys
- Validate primary key type at startup (look up from database metadata)
- Return 400 if primary key value cannot be coerced

### 9.5 Risk: Composite primary keys are complex

**Mitigation:**
- Phase 5.0: Support only single-column primary keys
- Phase 5.2+: Add composite key support (URL encoding, validation, SQL generation)

### 9.6 Risk: Entity names conflict with operation names

**Mitigation:**
- Separate namespaces (`/api/entities/` vs `/api/op/`)
- No conflict at routing level
- Future: Optional validation warning if names overlap (but not blocking)

---

## 10. Acceptance Criteria for Phase 5.1

Phase 5.1 is the implementation phase. Acceptance criteria:

### 10.1 Catalog model
- [ ] `operations.yml` can declare `entities` section
- [ ] Entity definition includes: `datasource`, `table`, `primaryKey`, `fields`, `security`
- [ ] `operations.schema.json` includes entity definitions
- [ ] Layer 1 validation: YAML validated against JSON Schema at startup
- [ ] Layer 2 validation: Semantic checks after schema validation
  - [ ] Entity must have `primaryKey`
  - [ ] Entity must have at least one field
  - [ ] `primaryKey` columns must be in `fields`
  - [ ] `datasource` must exist
- [ ] Invalid entity definitions fail fast with clear error messages

### 10.2 List endpoint
- [ ] `GET /api/entities/{entityName}.json` returns `ResultFrame`
- [ ] Supports `limit`, `offset`, `orderBy`, `orderDir` query params
- [ ] Supports filtering by exposed fields (exact match)
- [ ] Validates filter params against `fields` whitelist
- [ ] Returns 400 for invalid params (unknown field, negative limit, etc.)
- [ ] Returns 404 for unknown entity
- [ ] Returns 403 for unauthorized access
- [ ] Returns 401 for unauthenticated access

### 10.3 Get by ID endpoint
- [ ] `GET /api/entities/{entityName}/{id}.json` returns `ResultFrame` with 0 or 1 row
- [ ] Returns 404 if no row matches primary key
- [ ] Validates primary key type (string or number)
- [ ] Returns 400 if primary key value cannot be coerced
- [ ] Returns 403 for unauthorized access
- [ ] Returns 401 for unauthenticated access

### 10.4 Output formats
- [ ] List and get endpoints support JSON, XML, HTML, CSV
- [ ] Response shape matches operations (same `ResultFrame` structure)

### 10.5 Security
- [ ] `security.read.roles` controls read access
- [ ] ADMIN role bypasses all permission checks
- [ ] Unauthenticated requests return 401
- [ ] Unauthorized requests return 403

### 10.6 Admin catalog
- [ ] `/api/admin/catalog` includes `entities` section
- [ ] Entities are filtered by read permission
- [ ] Entity summary includes: name, label, description, datasource, table, primaryKey, fields, security

### 10.7 SQL generation
- [ ] All SQL uses `PreparedStatement` with parameterized queries
- [ ] All identifiers are quoted via `SqlDialect.quoteIdentifier()`
- [ ] LIMIT/OFFSET generated via `SqlDialect.limitOffset()`
- [ ] No string concatenation of user input
- [ ] Filter params are bound in declaration order
- [ ] Default dialect is `PostgresDialect` (works for PostgreSQL and H2)

### 10.8 Testing
- [ ] Unit tests for `EntityCatalog` validation
- [ ] Unit tests for `EntityPermissionEvaluator`
- [ ] Unit tests for `EntityPathHandler`
- [ ] Integration tests for list endpoint (with and without filters)
- [ ] Integration tests for get by ID endpoint (found and not found)
- [ ] Integration tests for security (authorized, unauthorized, unauthenticated)
- [ ] Integration tests for all output formats (JSON, XML, HTML, CSV)
- [ ] Integration tests for invalid inputs (unknown entity, unknown field, invalid limit, etc.)

### 10.9 Documentation
- [ ] Update `AGENTS.md` with entity catalog schema
- [ ] Update `USER-GUIDE.md` with entity examples
- [ ] Update `README.md` with entity quick start
- [ ] Add entity examples to `samples/starter/operations.yml`

---

## 11. Deferred to Phase 5.2+

The following features are explicitly deferred:

### Phase 5.2 — Write operations
- Create (POST)
- Update (PUT/PATCH)
- Delete (DELETE)
- Write security (`security.write.roles`)

### Phase 5.3 — Advanced filtering
- Filter operators: `gt`, `lt`, `gte`, `lte`, `in`, `like`, `between`
- Complex filters: `AND`, `OR`, nested conditions
- Filter validation and error messages

### Phase 5.4 — Composite primary keys
- Support for multi-column primary keys
- URL encoding for composite IDs (e.g., `/api/entities/orders/10100-1`)
- SQL generation for composite key lookups

### Phase 5.5 — Relationships
- Foreign key declarations
- Nested resources (e.g., `/api/entities/customers/103/orders`)
- Eager/lazy loading strategies

### Phase 5.6 — Extended SQL dialect support
- MySQL, SQLite dialect implementations
- Additional dialect methods if needed (e.g., `booleanLiteral`, `dateFunction`)

### Phase 5.7 — Caching
- Entity-level cache configuration
- Cache invalidation on writes
- Cache key generation

### Phase 5.8 — Audit logging
- Log all CRUD operations (read and write)
- Audit trail table
- Configurable audit levels (none, read, write, all)

---

## 12. Summary

Phase 5.0 designs a safe, opt-in declarative CRUD system for Helianthus:

- **Not an ORM:** Helianthus generates SQL from declarative configuration — it does not abstract the database or manage object lifecycle
- **Catalog model:** `entities` section with explicit field whitelist and primary key
- **API shape:** `/api/entities/{entity}` (separate from `/api/op/**`)
- **Read-only:** List and get by ID (writes deferred to Phase 5.2)
- **Security:** Read roles, ADMIN bypass, no public access
- **Output:** ResultFrame with all formats (JSON, XML, HTML, CSV)
- **Validation:** Two layers — JSON Schema (structure) then semantic checks (cross-references, business rules)
- **SQL generation:** Parameterized queries, `SqlDialect` for identifier quoting and LIMIT/OFFSET, no injection
- **Runtime registry:** Entities visible in `/api/admin/catalog`

The design prioritizes safety (explicit whitelists, parameterized queries), consistency (reuse ResultFrame, same security model), and clarity (separate namespaces, clear validation errors).

Phase 5.1 implementation should be straightforward given this design. All acceptance criteria are testable and verifiable.
