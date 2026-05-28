#!/usr/bin/env bash
set -euo pipefail

DEVOPS="$(cd "$(dirname "$0")" && pwd)"

cd "$DEVOPS"
docker compose -f docker-compose-app.yml down
docker compose -f docker-compose-environment.yml down

echo "agent-group stopped"
