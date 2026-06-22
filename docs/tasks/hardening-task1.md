Task — Helianthus 0.1.1 Hardening: Roadmap + Starter Smoke Tests + products-search fix

Context:
Helianthus 0.1.0 is working. Dockerfile and Paketo paths work. The starter stack has:
- default datasource: products/productlines
- secondary datasource: customers/orders
- Keycloak guest/admin
- React Admin UI
- JSON/CSV/XML/HTML outputs

Input files to review:
- samples/starter/operations.yml
- samples/starter/db/schema.sql
- samples/starter/db/init.sql
- samples/starter/db/secondary-schema.sql
- samples/starter/db/secondary-init.sql

Goals:
1. Add an updated ROADMAP.md.
2. Add stronger starter smoke tests.
3. Fix products-search, which is currently buggy.

Part A — ROADMAP.md

Create docs/ROADMAP.md or root ROADMAP.md.

Reflect the real current state:
- 0.1.0: completed secure operation runner milestone.
- 0.1.1: hardening, packaging, smoke tests, docs.
- Next major phase: Declarative CRUD.
- AOT/native image is nice-to-have, not immediate priority.
- Future-forward ideas should point to docs/FUTURE-FORWARD-DATA-COMPOSITION.md if present.

Do not copy the old roadmap blindly. The old roadmap is outdated because security, docker, UI, YAML catalog, and multi-datasource already exist.

Part B — Starter smoke tests

Add or extend integration smoke tests for the starter catalog.

Test these operations using the real starter catalog structure:

Default datasource:
- products/default.json returns rows.
- products/compact.json returns only projected columns:
  productCode, productName, productLine.
- products/expensive.json returns rows with buyPrice > 50 and projected columns:
  productCode, productName, buyPrice.
- product/default.json?productCode=S10_1678 returns exactly one product.
- products-by-line/default.json?productLine=Classic Cars returns only Classic Cars.
- products-by-price/default.json?minPrice=50&maxPrice=100 returns rows in range.
- products-in-stock/default.json?inStockOnly=true returns rows with quantityInStock > 0.
- inventory-report/default.json is ADMIN-only.
- public-catalog/default.json is guest-visible.

Secondary datasource:
- customers/default.json returns customer rows.
- customer/default.json?customerNumber=103 returns Atelier graphique.
- customer-orders/default.json?customerNumber=103 returns that customer’s orders.
- high-value-customers/default.json returns creditLimit > 50000.

Formats:
- At least one operation works in json.
- At least one operation works in csv.
- At least one operation works in xml.
- At least one operation works in html.

Security:
- unauthenticated /api/op/** returns 401.
- guest cannot access inventory-report.
- admin can access inventory-report.
- guest can access public-catalog.

Part C — Fix products-search

Current issue:
products-search declares optional parameters:
- productLine
- minPrice

But its SQL is currently:
SELECT * FROM products ORDER BY productCode

So parameters are not used.

Preferred solution:
Implement proper optional SQL parameter support.

Option 1, quick compatible fix:
Change SQL to use nullable positional params, but avoid requiring duplicated user parameters in YAML.

Possible desired SQL:
SELECT *
FROM products
WHERE (? IS NULL OR productLine = ?)
AND (? IS NULL OR buyPrice >= ?)
ORDER BY productCode

But this currently requires binding productLine twice and minPrice twice.

Better solution:
Add support for repeated named parameters.

Example desired catalog:
query: |
SELECT *
FROM products
WHERE (:productLine IS NULL OR productLine = :productLine)
AND (:minPrice IS NULL OR buyPrice >= :minPrice)
ORDER BY productCode

Rules:
- Existing positional ? queries must continue working.
- Named parameter queries may be introduced for new operations.
- Optional missing params bind as null.
- Required missing params still return 400.
- Type coercion remains the same.

Tests for products-search:
- no params returns all products.
- productLine=Classic Cars returns only Classic Cars.
- minPrice=80 returns only products with buyPrice >= 80.
- both productLine=Classic Cars and minPrice=80 work together.
- invalid minPrice returns 400.

If named parameter support is too large for this task:
- document the limitation clearly.
- implement the smallest safe fix.
- do not duplicate parameters in a confusing way without a comment/test.

Part D — Verification

Run:
cd server && mvn clean test

Also verify manually if possible:
docker compose -f docker-compose.starter.yml up --build
docker compose -f docker-compose.starter.yml -f docker-compose.starter.paketo.yml up

Acceptance:
- ROADMAP.md exists and reflects the real project state.
- Starter smoke tests cover default + secondary datasources.
- products-search works with optional filters.
- Existing operations still work.
- All tests pass.