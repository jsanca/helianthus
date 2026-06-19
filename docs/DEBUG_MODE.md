# Debug Mode Guide

This guide explains how to run Helianthus in debug mode for troubleshooting and development.

## Remote Debugging

To enable remote debugging, start the server with the JDWP agent:

```bash
JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
java -jar helianthus-web/target/helianthus-web-1.0.jar
```

Then connect your IDE debugger to `localhost:5005`.

### IntelliJ IDEA

1. Run → Edit Configurations
2. Click + → Remote JVM Debug
3. Host: `localhost`, Port: `5005`
4. Click Debug

### VS Code

Add to `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug Helianthus",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

### Eclipse

1. Run → Debug Configurations
2. Remote Java Application
3. Connection: `localhost:5005`

## Debug Logging

Enable debug logging for specific packages:

```bash
java -jar helianthus-web/target/helianthus-web-1.0.jar \
  --logging.level.helianthus=DEBUG \
  --logging.level.org.springframework.security=DEBUG
```

Or in `application.yml`:

```yaml
logging:
  level:
    helianthus: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
```

### Useful Log Categories

- `helianthus.core.web.HelianthusController` — Request handling, operation execution
- `helianthus.core.pipeline` — Pipeline steps (Resolve, Bind, Query, Filter, etc.)
- `helianthus.core.catalog.OperationCatalog` — Catalog loading and resolution
- `helianthus.core.security.OperationPermissionEvaluator` — Permission checks
- `org.springframework.security` — Authentication and authorization

## Request Tracing

Each request gets a unique `X-Request-ID` header (auto-generated or passed in). Use it to trace a request through logs:

```bash
curl -H "X-Request-ID: my-test-123" \
  http://localhost:8080/api/op/products/default.json
```

Then grep logs:

```bash
grep "my-test-123" application.log
```

## Actuator Endpoints

Spring Boot Actuator provides runtime information:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info

# Metrics (if enabled)
curl http://localhost:8080/actuator/metrics
```

See all available endpoints:

```bash
curl http://localhost:8080/actuator
```

## Common Issues

### Operation Returns No Rows

1. Check parameters are being passed correctly:
   ```bash
   curl "http://localhost:8080/api/op/product/default.json?productCode=S10_1678"
   ```

2. Enable debug logging to see bound parameters:
   ```
   helianthus.core.pipeline.BindStep: Bound 1 parameters for operation: product
   ```

3. Check the SQL in operations.yml matches your database schema

### Permission Denied

1. Check user roles in Keycloak
2. Enable security debug logging:
   ```
   logging.level.org.springframework.security=DEBUG
   ```

3. Check `OperationPermissionEvaluator` logs:
   ```
   helianthus.core.security.OperationPermissionEvaluator: Permission check: operationId=products user=guest permitted=false
   ```

### Database Connection Issues

1. Check database is running:
   ```bash
   docker compose ps
   ```

2. Verify connection parameters in `application.yml`
3. Check logs for connection errors:
   ```
   HikariPool-1 - Exception during pool initialization
   ```

## Profiling

To profile the application, use JVM flags:

```bash
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
  -jar helianthus-web/target/helianthus-web-1.0.jar
```

Open `recording.jfr` in JDK Mission Control or VisualVM.

## Memory Dumps

Generate a heap dump:

```bash
jmap -dump:format=b,file=heap.bin <pid>
```

Analyze with Eclipse MAT or VisualVM.

## Thread Dumps

Generate a thread dump:

```bash
jstack <pid> > threads.txt
```

Useful for diagnosing deadlocks or slow requests.
