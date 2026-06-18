# Helianthus

Helianthus is a Kotlin-first declarative backend platform inspired by Apache Cocoon, ColdFusion, Supabase, and PostgREST. It allows developers to define operations, entities, and data pipelines declaratively, exposing APIs and multiple representations without requiring custom controller development.

The current codebase is a modernization of a legacy Java middleware that already demonstrates this core value proposition: SQL operations exposed as HTTP endpoints returning JSON, XML, HTML, or CSV. The platform is being rebuilt with a clean Kotlin foundation while preserving the core idea.

## Why Helianthus Exists

Most backend development involves writing repetitive controllers, services, and repositories to expose data over HTTP. Helianthus inverts this model: you declare what you want — an operation, its SQL, its output format — and the platform handles the HTTP layer automatically.

Inspiration drawn from:
- **Apache Cocoon** — pipelines, XML transformations, declarative flows
- **ColdFusion** — ease of data exposure, rapid development
- **Supabase** — auto-generated APIs from database schema
- **PostgREST** — direct HTTP access to database operations

## Quick Start

### Clean stack (PostgreSQL only)

```bash
# Start PostgreSQL
docker compose up -d

# Build and run the server
cd server
mvn clean install -DskipTests
java -jar helianthus-web/target/helianthus-web-1.0.jar
```

```bash
curl http://localhost:8080/health
# {"status":"ok","service":"helianthus"}
```

### Starter stack (PostgreSQL + sample data + server)

```bash
docker compose -f docker-compose.starter.yml up --build
```

This starts PostgreSQL preloaded with sample product data and the Helianthus server with a seeded operations catalog.

Once running, try these endpoints:

```bash
# Health check
curl http://localhost:8080/health
# {"status":"ok","service":"helianthus"}

# All products (default configuration)
curl http://localhost:8080/api/op/products/default.json

# Compact view (projected columns)
curl http://localhost:8080/api/op/products/compact.json

# Expensive products (filtered + projected)
curl http://localhost:8080/api/op/products/expensive.json

# Single product by code
curl "http://localhost:8080/api/op/product/default.json?productCode=S10_1678"

# All product lines
curl http://localhost:8080/api/op/productlines/default.json
```

Stop the starter stack:

```bash
docker compose -f docker-compose.starter.yml down -v
```

## Project Layout

```
helianthus/
├── server/                  # Kotlin/Java Maven multi-module backend
│   ├── Dockerfile           # Multi-stage container build
│   ├── helianthus/          # helianthus-core: interfaces, JDBC, services
│   ├── helianthus-web/      # Spring Boot application, HTTP layer
│   └── pom.xml              # Parent POM
├── samples/
│   └── starter/
│       ├── operations.yml   # Seeded operations catalog
│       └── db/
│           ├── schema.sql   # Sample schema
│           └── init.sql     # Sample data
├── docs/
│   └── legacy/              # Historical reference files
├── docker-compose.yml       # Clean stack (PostgreSQL only)
├── docker-compose.starter.yml  # Starter stack with sample data + server
└── .env.example             # Environment variables template
```

## Technology

- Java 25, Kotlin, Maven multi-module
- Spring Boot 4.1.0 (Spring MVC, Spring JDBC)
- PostgreSQL with HikariCP connection pooling
- Jackson (JSON); XML and HTML formatters available

## Current Status

Phase 4 complete. The platform uses a rich YAML operations catalog with reusable queries, named configurations, and pipeline steps (project, filter, limit). Operations are resolved declaratively — HTTP requests never contain SQL.

See [docs/DOCKER-STARTER-DESIGN.md](docs/DOCKER-STARTER-DESIGN.md) for the starter environment design.
