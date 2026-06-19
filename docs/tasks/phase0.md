# Helianthus Modernization Plan — Phase 0

## Objective

Modernize the existing Helianthus codebase before introducing new product features.

This phase focuses on build health, dependency cleanup, Java 25 compatibility, Spring Boot 4.1.0 dependency management, PostgreSQL as the default database driver, and preparing the project for a future Kotlin migration.

This phase does not redesign Helianthus yet.

---

## Guiding Decision

Use:

```text
Java 25 + Spring Boot 4.1.0 + Maven multi-module
```

Do not use WebFlux yet.

Reason:

The current project is JDBC-based and mostly legacy Java. Introducing WebFlux, coroutines, R2DBC, and Kotlin at the same time would add unnecessary complexity. Spring MVC + JDBC is the safer first modernization target. Kotlin and coroutine-friendly APIs can be introduced later once the baseline is clean.

---

## Phase 0.1 — Parent POM Baseline

### Goal

Make the root parent POM the single source of version management.

### Tasks

1. Add explicit properties:

```xml
<java.version>25</java.version>
<spring.boot.version>4.1.0</spring.boot.version>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

2. Import Spring Boot dependency management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring.boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

3. Update `maven-compiler-plugin` to use:

```xml
<release>${java.version}</release>
```

4. Keep the existing modules:

```xml
<module>helianthus-bean</module>
<module>helianthus</module>
<module>helianthus-transformer</module>
<module>helianthus-web</module>
<module>helianthus-web-deploy</module>
```

### Acceptance Criteria

```bash
mvn help:effective-pom
mvn clean validate
```

Both commands should work.

---

## Phase 0.2 — Dependency Inventory

### Goal

Identify obsolete dependencies module by module before deleting them.

### Tasks

Run:

```bash
mvn dependency:tree
```

Capture dependencies related to:

```text
Spring Framework 4.x
javax.servlet
JSTL
taglibs
XStream
Jettison
Dom4J
Commons DBCP
MySQL Connector
JUnit 3/4
Jetty legacy plugin
```

### Acceptance Criteria

Create a short internal note or checklist showing which module owns each legacy dependency.

---

## Phase 0.3 — Replace Database Driver and Pooling

### Goal

Move from MySQL + Commons DBCP to PostgreSQL + Spring Boot managed HikariCP.

### Tasks

1. Remove MySQL connector from active runtime dependencies.
2. Add PostgreSQL runtime dependency:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

3. Remove Commons DBCP dependency.
4. Do not manually configure Hikari unless required.
5. Prefer Spring Boot datasource configuration later:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/helianthus
    username: helianthus
    password: helianthus
```

### Acceptance Criteria

The build no longer depends on Commons DBCP or MySQL for the default runtime path.

---

## Phase 0.4 — Remove Legacy Servlet/JSP Stack

### Goal

Stop depending on pre-Jakarta servlet and JSP-era libraries.

### Remove

```xml
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>servlet-api</artifactId>
</dependency>

<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>jstl</artifactId>
</dependency>

<dependency>
    <groupId>taglibs</groupId>
    <artifactId>standard</artifactId>
</dependency>
```

### Replace With

In the web module:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### Acceptance Criteria

The web module uses Spring Boot web infrastructure and no longer directly depends on old servlet/JSTL/taglibs artifacts.

---

## Phase 0.5 — Replace Old Spring Dependencies

### Goal

Remove explicit Spring Framework 4.x dependencies and allow Spring Boot 4.1.0 to manage Spring versions.

### Tasks

1. Remove explicit old Spring dependencies from module POMs.
2. Add only the needed Spring Boot starters.
3. Keep the number of starters small.

Initial allowed starters:

```xml
spring-boot-starter-web
spring-boot-starter-jdbc
spring-boot-starter-test
```

Do not add yet:

```text
spring-boot-starter-security
spring-boot-starter-oauth2-resource-server
spring-boot-starter-actuator
spring-boot-starter-webflux
```

Those come later.

### Acceptance Criteria

No module should directly pin Spring Framework 4.x versions.

---

## Phase 0.6 — Replace XStream JSON Path

### Goal

Stop using XStream/Jettison for JSON output.

### Tasks

1. Remove XStream/Jettison from the active JSON formatter path.
2. Introduce Jackson-based JSON serialization.
3. Keep the output model simple.
4. Do not attempt full XML parity yet.

### Suggested Approach

Create or adapt a formatter equivalent to:

```text
TableResultJsonFormatter
```

Backed by Jackson `ObjectMapper`.

### Acceptance Criteria

At least one existing operation or sample `TableResultBean` can be serialized to JSON with Jackson.

---

## Phase 0.7 — Test Modernization

### Goal

Move away from JUnit 3.8.1 and direct JUnit 4 dependencies.

### Replace

```xml
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>3.8.1</version>
</dependency>
```

### With

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Tasks

1. Convert old test classes gradually.
2. Prefer JUnit Jupiter.
3. Add at least one smoke test that proves the modernized module can load or serialize.

### Acceptance Criteria

```bash
mvn test
```

Runs using JUnit Jupiter.

---

## Phase 0.8 — Minimal Spring Boot Runtime

### Goal

Make `helianthus-web` or a new runtime module start as a Spring Boot app.

### Tasks

1. Add a minimal application entrypoint:

```java
@SpringBootApplication
public class HelianthusApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelianthusApplication.class, args);
    }
}
```

2. Add a minimal health endpoint, either:

```text
GET /health
```

or later:

```text
GET /actuator/health
```

For this phase, a simple `/health` controller is acceptable.

### Acceptance Criteria

The app starts locally and returns:

```json
{"status":"ok","service":"helianthus"}
```

---

## Phase 0.9 — Legacy Deploy Module Decision

### Goal

Decide what to do with `helianthus-web-deploy`.

### Options

1. Keep temporarily as a sample module.
2. Convert into a Spring Boot sample app.
3. Remove later after `helianthus-web` becomes self-contained.

### Recommendation

Do not delete it immediately. First make the main build healthy. Then convert or retire it in a later cleanup commit.

### Acceptance Criteria

A decision is recorded, but destructive changes are deferred.

---

## Phase 0.10 — Kotlin Readiness, Not Kotlin Migration

### Goal

Prepare for Kotlin without migrating yet.

### Tasks

1. Keep Java code compiling cleanly.
2. Avoid new Java patterns that would make Kotlin migration painful.
3. Favor simple immutable DTOs where practical.
4. Avoid static-heavy utility expansion.
5. Do not add Kotlin Maven plugin yet unless a Kotlin module is actually introduced.

### Acceptance Criteria

The project remains Java-only but has a cleaner module and dependency baseline suitable for later Kotlin migration.

---

## Done Criteria for Phase 0

Phase 0 is done when:

```text
- Parent POM imports Spring Boot 4.1.0 dependency management.
- Java 25 is configured centrally.
- PostgreSQL replaces MySQL as the default runtime driver.
- Commons DBCP is removed from the default runtime path.
- Old servlet-api, JSTL, and taglibs are removed.
- Old Spring 4.x dependencies are removed.
- JUnit 3.8.1 is removed.
- JSON serialization no longer depends on XStream/Jettison.
- A minimal Spring Boot app starts.
- A health endpoint responds.
- mvn clean test or mvn clean package succeeds.
- No Kotlin migration has started yet.
- No new Helianthus product features have been introduced yet.
```

---

## Explicit Non-Goals

Do not implement in this phase:

```text
YAML operation catalog
JSON Schema validation
automatic CRUD
reverse engineering wizard
natural-ish schema
Keycloak
Spring Security
MinIO/S3
Varnish
HTML template rendering
Cloud functions
GraalJS
React admin UI
Kotlin migration
WebFlux
R2DBC
Jigsaw
```

These belong to later modernization/product phases.
