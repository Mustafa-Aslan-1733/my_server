#!/usr/bin/env bash
set -euo pipefail

if [ -n "${CI_JOB_ID:-}" ]; then
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend-smoke-${CI_PIPELINE_ID:-ci}-${CI_JOB_ID}}"
else
  COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-backend-smoke}"
fi
POSTGRES_CONTAINER_NAME="${POSTGRES_CONTAINER_NAME:-${COMPOSE_PROJECT_NAME}-postgres}"
BACKEND_CONTAINER_NAME="${BACKEND_CONTAINER_NAME:-${COMPOSE_PROJECT_NAME}-app}"
export COMPOSE_PROJECT_NAME POSTGRES_CONTAINER_NAME BACKEND_CONTAINER_NAME
SMOKE_HEALTH_URL="${SMOKE_HEALTH_URL:-http://localhost:8080/health}"

cleanup() {
  docker compose down --remove-orphans --volumes >/dev/null 2>&1 || true
}

trap cleanup EXIT

docker compose config >/dev/null

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not reachable from this session." >&2
  echo "Make sure Docker is running and your user can access /var/run/docker.sock." >&2
  exit 1
fi

docker compose down --remove-orphans --volumes >/dev/null 2>&1 || true
docker compose up --build -d

for attempt in {1..60}; do
  if curl -fsS "$SMOKE_HEALTH_URL" >/dev/null 2>&1; then
    echo "Docker smoke test passed: /health returned a successful response."
    exit 0
  fi

  sleep 2
done

echo "Backend did not become ready in time." >&2
docker compose ps >&2
docker compose logs backend >&2
exit 1
