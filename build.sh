#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${1:-helianthus-server:paketo}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Building Helianthus starter stack assets..."
echo "Root:         $ROOT_DIR"
echo "Server image: $IMAGE_NAME"
echo

"$ROOT_DIR/scripts/build-paketo-server.sh" "$IMAGE_NAME"

echo
echo "Building Helianthus client Docker image..."
docker compose -f "$ROOT_DIR/docker-compose.starter.yml" build client

echo
echo "Done."
echo
echo "Run with:"
echo "docker compose -f docker-compose.starter.yml -f docker-compose.starter.paketo.yml up"
