# Docker Starter Design

## Overview

The project uses Docker Compose to provide a runnable development and demonstration environment. Two compose files serve distinct purposes.

## Compose File Comparison

| Aspect | `docker-compose.yml` | `docker-compose.starter.yml` |
|--------|---------------------|------------------------------|
| Purpose | Production-like runtime | Demo/playground environment |
| Contents | PostgreSQL only | 2x PostgreSQL + Keycloak + server + client |
| Use Case | Local development, CI | Demos, training, exploration |

## Clean Stack (`docker-compose.yml`)

Single PostgreSQL container for basic development and testing.

```yaml
services:
  postgres:
    image: postgres:17-alpine
    # ... single database instance
```

## Starter Stack (`docker-compose.starter.yml`)

Full-featured demonstration environment with multiple services:

### Services

| Service | Port | Purpose |
|---------|------|---------|
| postgres | 5432 | Primary database (products schema) |
| postgres-secondary | 5433 | Secondary database (customers schema) |
| keycloak | 8081 | Identity provider (OIDC) |
| server | 8080 | Helianthus API backend |
| client | 5173 | React admin UI |

### Keycloak Integration

Keycloak is pre-configured with:
- Realm: `helianthus`
- Roles: `GUEST`, `ADMIN`
- Test users: `guest/guest`, `admin/admin`
- Client ID: `helianthus-client`

The server is configured with OIDC issuer URI and JWK set URI for token validation.

## Starter Seed Concept

The starter compose seeds both databases with sample data and registers operations that map to those tables. Seed artifacts live under `samples/starter/`:

```
samples/starter/
├── operations.yml              # Operations catalog
└── db/
    ├── schema.sql              # Primary database schema (products)
    ├── init.sql                # Primary database seed data
    ├── secondary-schema.sql    # Secondary database schema (customers)
    └── secondary-init.sql      # Secondary database seed data
```

### Schema Seed

The primary database (`schema.sql`) creates the products schema with tables for:
- products
- productlines

The secondary database (`secondary-schema.sql`) creates the customers schema with tables for:
- customers
- orders
- orderdetails
- products (referenced)

### Data Seed

Both databases are preloaded with sample data enabling immediate query capability without manual data entry.

### Operations Catalog Seed (`operations.yml`)

Registers pre-configured operations across both datasources. Example operations:

**Primary datasource (products):**
- `GET /api/op/products/default.json` — paginated product list
- `GET /api/op/products/compact.json` — projected columns
- `GET /api/op/products/expensive.json` — filtered products
- `GET /api/op/product/default.json?productCode=X` — single product

**Secondary datasource (customers):**
- `GET /api/op/customers/default.json` — customer list
- `GET /api/op/customer/default.json?customerNumber=X` — single customer
- `GET /api/op/customer-orders/default.json?customerNumber=X` — customer orders

### Init Script Mounting

The starter compose mounts seed files into PostgreSQL containers for automatic initialization:

```yaml
volumes:
  - ./samples/starter/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
  - ./samples/starter/db/init.sql:/docker-entrypoint-initdb.d/02-init.sql:ro
```

## Folder Layout

```
helianthus/
├── docker/
│   └── keycloak/
│       └── helianthus-realm.json    # Keycloak realm import
├── samples/starter/
│   ├── operations.yml                # Operations catalog
│   └── db/
│       ├── schema.sql
│       ├── init.sql
│       ├── secondary-schema.sql
│       └── secondary-init.sql
├── docker-compose.yml                 # Clean stack
├── docker-compose.starter.yml        # Full starter stack
└── docs/
    └── DOCKER-STARTER-DESIGN.md
```

## Future Additions (Not Implemented)

The following services are potential future additions for extended demos.

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
| Security | Well-known test credentials | Keycloak with proper realms, TLS, network policies |
| Scalability | Single node | Container orchestration, read replicas |
| Backup | None | Automated, tested backups |
| Monitoring | Basic health | Full observability stack |

Any promotion of starter artifacts to production must undergo security review, credential rotation, and infrastructure planning.
