# Helianthus Server — Build Paths

Helianthus provides two image build paths for the server. The Dockerfile path is the official default. The Paketo path is an alternative for experimentation and future AOT work.

---

## Official path — Dockerfile

`server/Dockerfile` is a two-stage build: Maven + JDK 25 (build), Eclipse Temurin JRE 25 (runtime).

```
server/
└── Dockerfile
```

**Build and run:**

```bash
# Used automatically by the default starter stack:
docker compose -f docker-compose.starter.yml up --build
```

The `server` service in `docker-compose.starter.yml` builds directly from `server/Dockerfile`. The catalog is mounted at `/app/operations-starter.yml`.

---

## Alternative path — Paketo buildpack

`spring-boot:build-image` uses the [Paketo buildpacks](https://paketo.io/) via CNB (Cloud Native Buildpacks). The resulting image uses BellSoft Liberica JRE 25, layered Spring Boot app slices, and Paketo's memory calculator.

The builder is `paketobuildpacks/builder-noble-java-tiny` (selected automatically by Spring Boot 4.1). The `BP_JVM_VERSION` is set to `${java.version}` (25) in `helianthus-web/pom.xml`.

### Step 1 — build the Paketo image

```bash
cd server && mvn install -DskipTests -q

cd helianthus-web && mvn spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=helianthus-server:paketo
```

The image is tagged locally as `helianthus-server:paketo`.

### Step 2 — start the starter stack using the Paketo image

```bash
docker compose \
  -f docker-compose.starter.yml \
  -f docker-compose.starter.paketo.yml \
  up
```

> **Do not pass `--build`** — the Paketo image is pre-built by Maven, not by Docker Compose. Passing `--build` would rebuild the image using the Dockerfile and overwrite the Paketo image.

### What the override changes

`docker-compose.starter.paketo.yml` overrides only the `server` service:

| Setting | Dockerfile path | Paketo path |
|---------|----------------|-------------|
| Image | built from `server/Dockerfile` | `helianthus-server:paketo` |
| JVM debug flag | `JAVA_OPTS` | `JAVA_TOOL_OPTIONS` |
| Catalog mount path | `/app/operations-starter.yml` | `/workspace/operations-starter.yml` |

`JAVA_TOOL_OPTIONS` is used because Paketo's launch helper appends to it; `JAVA_OPTS` is not read by the CNB entrypoint (`/cnb/process/web`).

The catalog path changes to `/workspace/` because that is the Paketo image's working directory (`WORKDIR /workspace`). All other services (Postgres × 2, Keycloak, client) are unchanged.

### Debug port

Both paths expose port 5005 for remote debugging. The JWDP agent is enabled in both compose configurations.

---

## Differences at a glance

| | Dockerfile | Paketo |
|---|---|---|
| Entrypoint | `sh -c "java ${JAVA_OPTS} -jar /app/app.jar"` | `/cnb/process/web` (JarLauncher) |
| JRE | Eclipse Temurin 25 | BellSoft Liberica 25 |
| Memory tuning | Manual (`JAVA_OPTS`) | Automatic (Paketo memory calculator) |
| Layer caching | Docker layer cache | CNB build cache volume |
| SBOM | No | Yes (Syft-generated) |
| AOT/CDS hooks | No | Available via `BP_SPRING_AOT_ENABLED`, `BP_JVM_CDS_ENABLED` |

### Known limitations

- `spring-boot:build-image` must be run from the `helianthus-web/` subdirectory (not from `server/`), because the Spring Boot Maven plugin is only declared in that module's POM.
- The multi-module `mvn install` step is required first so that `helianthus-core` is available in the local Maven repository.
