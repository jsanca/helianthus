# Legacy Deployment Files

These files are historical references from the original `helianthus-web-deploy`
WAR deployment model, preserved for reference. The active runtime is now the
Spring Boot JAR in `server/helianthus-web/`.

- `web.xml` — Servlet 2.3 descriptor (updated to Jakarta Servlet 6.0 during
  Phase 0 modernization). Registered `HelianthusServlet` at `/*` and loaded
  Spring XML context from `applicationContextExample.xml` and
  `applicationContextCore.xml`.

- `applicationContextCore.xml` — Spring bean wiring for formatters, workflow,
  path handler, query config parser, and the `HelianthusService`.

- `applicationContextExample.xml` — MySQL datasource configuration via Commons
  DBCP (`BasicDataSource`), `DataSourcePoolConnectionProvider`, and
  `ConnectionProviderService`. This config no longer works after Phase 0
  (MySQL and Commons DBCP removed in favor of PostgreSQL and HikariCP).

- `queryConfig.xml` — Sample SQL-to-URL operation mappings for the
  `classicmodels` sample database.

- `mysqlsampledatabase.sql` — MySQL dump of the `classicmodels` sample schema
  and data. Not yet migrated to PostgreSQL.
