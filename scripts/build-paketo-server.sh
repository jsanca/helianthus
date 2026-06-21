#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${1:-helianthus-server:paketo}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="$ROOT_DIR/server"

echo "Building Helianthus server Paketo image..."
echo "Root:  $ROOT_DIR"
echo "Image: $IMAGE_NAME"

cd "$SERVER_DIR"

echo "Step 1: package..."
mvn -pl helianthus-web -am package -DskipTests

echo "Step 2: build-image..."
mvn -pl helianthus-web org.springframework.boot:spring-boot-maven-plugin:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName="$IMAGE_NAME"

echo
echo "Done."
echo "Image built: $IMAGE_NAME"
echo
echo "Run with:"
echo "docker compose -f docker-compose.starter.yml -f docker-compose.starter.paketo.yml up"
