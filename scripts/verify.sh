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

command -v java >/dev/null 2>&1 || fail "Java is required. Install JDK 21 or newer."
command -v gradle >/dev/null 2>&1 || fail "Gradle is required. Install Gradle 9.6 or use the CI workflow."
command -v docker >/dev/null 2>&1 || fail "Docker is required for Compose validation and the container build."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required."

gradle_version="$(gradle --version | awk '/^Gradle / { print $2; exit }')"
java_version="$(java -version 2>&1 | head -n 1)"

printf 'Gradle: %s\n' "${gradle_version:-unknown}"
printf 'Java:   %s\n' "$java_version"

created_env=0
cleanup() {
  if [[ "$created_env" == "1" ]]; then
    rm -f .env
  fi
}
trap cleanup EXIT

if [[ ! -f .env ]]; then
  cp .env.example .env
  created_env=1
fi

log "Checking Shell syntax"
for script in scripts/*.sh; do
  bash -n "$script"
  printf 'OK  %s\n' "$script"
done

log "Validating Docker Compose configuration"
docker compose config --quiet

log "Running unit tests and building the Ktor fat JAR"
gradle --no-daemon --console=plain clean test buildFatJar

jar_path="build/libs/github-rock-backend.jar"
[[ -s "$jar_path" ]] || fail "Expected server JAR was not created at $jar_path"
printf 'OK  %s\n' "$jar_path"

if [[ "${BUILD_CONTAINER:-1}" == "1" ]]; then
  log "Building the production container"
  docker build -t github-rock-backend:local .
else
  printf '\nSkipping container build because BUILD_CONTAINER=%s\n' "${BUILD_CONTAINER:-0}"
fi

log "Verification completed successfully"
