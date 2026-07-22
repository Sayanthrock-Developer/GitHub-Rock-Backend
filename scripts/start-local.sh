#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() {
  printf '\n==> %s\n' "$1"
}

fail() {
  printf '\nERROR: %s\n' "$1" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is required."
command -v curl >/dev/null 2>&1 || fail "curl is required for the health check."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."

if [[ ! -f .env ]]; then
  [[ -s .env.example ]] || fail ".env.example is missing or empty."
  cp .env.example .env
  printf 'Created .env from .env.example.\n'
  printf 'Review GITHUB_OAUTH_CLIENT_ID and GITHUB_WEBHOOK_SECRET before testing those endpoints.\n'
fi

log "Validating Compose configuration"
docker compose config --quiet

log "Starting GitHub Rock Backend"
docker compose up --build -d

log "Waiting for the public health endpoint"
env_caddy_address="$(sed -n 's/^CADDY_ADDRESS=//p' .env | tail -n 1)"
caddy_address="${CADDY_ADDRESS:-${env_caddy_address:-http://localhost}}"
case "$caddy_address" in
  http://*|https://*) ;;
  *) caddy_address="https://$caddy_address" ;;
esac

health_url="${HEALTH_URL:-${caddy_address%/}/v1/health}"
max_attempts="${HEALTH_ATTEMPTS:-30}"
[[ "$max_attempts" =~ ^[1-9][0-9]*$ ]] || fail "HEALTH_ATTEMPTS must be a positive integer."

for ((attempt = 1; attempt <= max_attempts; attempt++)); do
  if curl \
    --fail \
    --silent \
    --show-error \
    --connect-timeout 2 \
    --max-time 5 \
    "$health_url" >/dev/null; then
    printf 'Backend is reachable at %s\n' "$health_url"
    printf 'Use: docker compose logs -f app\n'
    exit 0
  fi

  if [[ "$attempt" -eq "$max_attempts" ]]; then
    printf 'The stack started, but the health endpoint did not become ready.\n' >&2
    docker compose ps >&2
    docker compose logs --tail=80 app >&2 || true
    exit 1
  fi

  sleep 2
done
