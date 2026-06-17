# Docker Starter Design

## Overview

The project uses Docker Compose to provide a runnable development and demonstration environment. Two compose files serve distinct purposes.

## Compose File Comparison

| Aspect | `docker-compose.yml` | `docker-compose.starter.yml` |
|--------|---------------------|------------------------------|
| Purpose | Production-like runtime | Demo/playground environment |
| Contents | PostgreSQL only | PostgreSQL + seeded data + sample operations |
| Use Case | Local development, CI | Demos, training, exploration |

## Starter Seed Concept

The `docker-compose.starter.yml` extends the base composition with seed data and pre-registered operations. This provides a working end-to-end experience out of the box: a running database with schema and sample data, plus operation configurations that map to those tables.

Seed artifacts live under `docker/starter/` with the following structure:

```
docker/starter/
├── schema/
│   └── init.sql
├── data/
│   └── seed.sql
└── operations/
    └── catalog.json
```

### Schema Seed (`schema/init.sql`)

Creates the initial database schema. The classicmodels sample database from `docs/legacy/mysqlsampledatabase.sql` serves as a reference; this file is ported to PostgreSQL dialect.

### Data Seed (`data/seed.sql`)

Loads reference data into the schema. For the classicmodels schema, this includes customers, products, orders, and related entities. This enables immediate query capability without manual data entry.

### Operations Catalog Seed (`operations/catalog.json`)

Registers pre-configured operations that map SQL queries to HTTP endpoints. Example operations:

- `GET /operations/customers` — list all customers
- `GET /operations/orders?customerId={id}` — orders for a customer
- `POST /operations/products/search` — product search

Each operation entry contains:
- `operationId` — unique identifier
- `statement` — SQL query or statement
- `datasource` — target database
- `outputFormat` — json, xml, html, or csv

## Proposed Folder Layout

```
helianthus/
├── docker/
│   ├── docker-compose.yml              # Base compose (PostgreSQL only)
│   ├── docker-compose.starter.yml      # Starter compose (full seeding)
│   └── starter/
│       ├── schema/
│       │   └── init.sql
│       ├── data/
│       │   └── seed.sql
│       └── operations/
│           └── catalog.json
├── docs/
│   └── DOCKER-STARTER-DESIGN.md
└── server/
    └── ...
```

### Init Script Mounting

The starter compose mounts seed files into the PostgreSQL container for automatic initialization:

```yaml
volumes:
  - ./docker/starter/schema/init.sql:/docker-entrypoint-initdb.d/01-schema.sql
  - ./docker/starter/data/seed.sql:/docker-entrypoint-initdb.d/02-data.sql
```

## Future Additions (Not Implemented)

The following services are potential future additions for full-stack demos. They are out of scope for the initial starter but may be introduced later.

### Keycloak

Identity provider for OAuth2/OIDC authentication. Would enable:
- Secure operation endpoints
- Token-based access control
- User federation

**Status:** Deferred.

### MinIO

S3-compatible object storage. Would enable:
- File upload operations
- Binary output to object storage
- Blob result handling

**Status:** Deferred.

### Varnish

HTTP caching proxy. Would enable:
- Response caching for expensive queries
- Load balancing across instances
- API rate limiting

**Status:** Deferred.

## Rule: Starter is for Demo/Playground, Not Production

The starter configuration exists solely to provide a frictionless demonstration and exploration environment. It is **not suitable for production use**.

| Concern | Starter | Production |
|---------|---------|------------|
| Credentials | Defaults/well-known | Secrets management |
| Data | Synthetic/sample | Real business data |
| Security | Open, no auth | Keycloak, TLS, network policies |
| Scalability | Single node | Container orchestration |
| Backup | None | Automated, tested backups |
| Monitoring | Basic health | Full observability stack |

Any promotion of starter artifacts to production must undergo security review, credential rotation, and infrastructure planning.
