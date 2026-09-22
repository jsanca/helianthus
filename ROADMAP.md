# Helianthus Roadmap

## 0.1.0 — Secure Operation Runner (completed)

The foundational milestone. A declarative SQL catalog drives HTTP endpoints with zero custom controllers.

- YAML operation catalog (`operations.yml`) with inline SQL and shared `queries` blocks
- HTTP endpoints: `GET /api/op/{operationId}/{configurationId}.{format}`
- Output formats: JSON, CSV, XML, HTML
- Pipeline: project, filter (eq/neq/gt/gte/lt/lte/in), limit, derive
- Named (`:param`) and positional (`?`) SQL parameter styles; optional params bind as NULL
- Multi-datasource: `default` + `secondary` PostgreSQL via HikariCP
- Keycloak OIDC authentication; per-operation role-based authorization
- React Admin UI with catalog-driven form generation
- Docker Compose starter stack (2× PostgreSQL + Keycloak + server + client)
- Paketo OCI image build alongside Dockerfile path

## 0.1.1 — Hardening (in progress)

Focus: reliability, packaging, and test coverage. No new user-facing features.

- [ ] Starter smoke tests covering all operations, both datasources, all formats, security checks
- [ ] products-search optional parameter fix (named param repeated binding)
- [ ] ROADMAP, updated CLAUDE.md and AGENTS.md
- [ ] Paketo build script fixed and verified

## 0.2 — Declarative CRUD (next)

Extend the catalog beyond read-only queries to support write operations.

- INSERT / UPDATE / DELETE operations declared in YAML
- Transaction support across multiple operations
- Input validation rules in the catalog
- Audit trail (who changed what, when)
- Conflict detection and optimistic locking

## 0.3 — Catalog & Operations API

Make the catalog itself a managed resource.

- Runtime catalog reload without restart
- REST API for catalog management (add/edit/remove operations)
- Versioned operation history
- Per-tenant catalogs

## Future-Forward

See [`docs/FUTURE-FORWARD-DATA-COMPOSITION.md`](docs/FUTURE-FORWARD-DATA-COMPOSITION.md) for longer-horizon ideas including multi-source pipelines (Mongo, S3, Parquet), data composition (join, union, aggregate across sources), and streaming outputs.

AOT compilation and GraalVM native image are a nice-to-have once the feature set stabilises; they are not on the immediate roadmap.
