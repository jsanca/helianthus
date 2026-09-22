1. Multi-source pipeline
    - JDBC primary/secondary
    - Mongo
    - S3/object storage
    - CSV/JSON/Parquet files

2. Data composition
    - join
    - union
    - aggregate
    - groupBy
    - sort
    - enrich/hydrate

3. Export adapters
    - CSV
    - XML
    - HTML
    - Parquet
    - XLSX
    - Google Sheets

4. Possible engines
    - native RowStream for small/medium data
    - DuckDB for local analytical joins/files
    - Spark only for distributed/large data
5. Entity operations remain synchronous by default. 
    - Large or long-running entity operations will be modeled as asynchronous operations with operation IDs, progress events over SSE, and cooperative cancellation.
    - This avoids request timeouts, improves UX, and gives the platform a reusable operation runtime for imports, indexing, archiving, and migrations.