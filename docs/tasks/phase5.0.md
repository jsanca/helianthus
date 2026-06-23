Task — Phase 5.0 Declarative CRUD Design

Goal:
Design the first safe version of declarative CRUD for Helianthus.

Rules:
- Do not implement code yet.
- CRUD must be opt-in.
- Everything is internal unless explicitly exposed.
- Start with read-only CRUD only.
- Writes come later.

Design questions:
1. Catalog model:
    - Should CRUD live under `entities`?
    - How does entity map to datasource/table?
    - How are primary keys declared?
    - How are exposed fields declared?
    - How are hidden fields declared?

2. API shape:
    - Should generated CRUD use `/api/entities/{entity}` or `/api/{entity}`?
    - Should it coexist with `/api/op/**`?
    - How to avoid route conflicts?

3. Read-only first:
    - list
    - get by id

4. Security:
    - read roles
    - write roles later
    - admin override or not?

5. Output:
    - Should list/get return ResultFrame?
    - Should CRUD support json/csv/xml/html immediately or json only first?

6. Validation:
    - What catalog validation is required before exposing an entity?

7. SQL generation:
    - How to quote identifiers safely?
    - How to avoid SQL injection?
    - How to support different SQL dialects later?

8. Runtime registry:
    - How should generated CRUD be visible in `/api/admin/catalog`?

Output:
Create docs/phase-5-declarative-crud-design.md with:
- proposed YAML model
- route design
- security model
- read-only implementation plan
- risks
- acceptance criteria for Phase 5.1

Acceptance:
- No code changes.
- Clear enough to implement Phase 5.1.