#!/usr/bin/env bash
set -euo pipefail

SKIP_PACKAGE="${1:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEVOPS="$ROOT/docs/dev-ops"

if [[ -f "$ROOT/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env.local"
  set +a
fi

if [[ "$SKIP_PACKAGE" != "--skip-package" ]]; then
  cd "$ROOT/backend"
  mvn -pl agent-group-app -am clean package -DskipTests
fi

cd "$DEVOPS"
docker compose -f docker-compose-environment.yml up -d
docker compose -f docker-compose-app.yml up -d --build

echo "agent-group started"
echo "backend: http://localhost:8080"
echo "nginx:   http://localhost:18080"
