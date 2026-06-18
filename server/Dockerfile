# Build stage
FROM eclipse-temurin:25-jdk-noble AS build

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY pom.xml /build/pom.xml
COPY helianthus/pom.xml /build/helianthus/pom.xml
COPY helianthus-web/pom.xml /build/helianthus-web/pom.xml
COPY helianthus/src /build/helianthus/src
COPY helianthus-web/src /build/helianthus-web/src

RUN mvn clean package -pl helianthus-web -DskipTests -am -q

FROM eclipse-temurin:25-jre-noble

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /build/helianthus-web/target/helianthus-web-1.0.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD curl -f http://localhost:8080/health || exit 1

CMD ["java", "-jar", "/app/app.jar"]
