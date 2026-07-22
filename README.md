# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend foundation for the **GitHub Rock** Android developer control centre.

> The Android app remains usable without this service. Direct GitHub repository, issue, pull-request, workflow, release, and download actions should continue to use the user's GitHub authorization whenever possible.

## Included in v0.1

- Kotlin 2.4, Ktor 3.5, and JDK 21
- PostgreSQL with Flyway migrations and HikariCP
- Redis connectivity
- Meilisearch connectivity
- Public health and runtime configuration endpoints
- Stateless GitHub OAuth Device Flow proxy
- HMAC-SHA256 GitHub webhook verification
- Webhook replay protection using delivery IDs
- Structured JSON errors
- Request logging without token bodies
- Docker Compose stack with Caddy, PostgreSQL, Redis, and Meilisearch
- Unit tests, Gradle CI, Docker CI, and Dependabot

## Languages

| Language group | Purpose |
|---|---|
| Kotlin | Ktor application, services, routes, security, storage, and tests |
| HTML | Static backend status and API overview in [`web/index.html`](web/index.html) |
| Shell | Local verification and Docker Compose startup scripts |
| Other | Docker, YAML, SQL, Gradle Kotlin DSL, Caddy, and configuration files |

## API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/health` | Backend and dependency health |
| GET | `/v1/config` | Public app version and feature flags |
| POST | `/v1/auth/device/start` | Start GitHub Device Flow |
| POST | `/v1/auth/device/poll` | Poll GitHub Device Flow |
| POST | `/v1/github/webhooks` | Verify and accept GitHub webhooks |

See [`docs/API.md`](docs/API.md).

## Run locally

```bash
cp .env.example .env
# Fill GITHUB_OAUTH_CLIENT_ID and GITHUB_WEBHOOK_SECRET
bash scripts/start-local.sh
```

Open `http://localhost:8080/v1/health` when using the default local Caddy host.

## Verify before a pull request

Run the unit tests, fat-JAR build, Compose validation, Shell syntax checks, and Docker image build:

```bash
bash scripts/verify.sh
```

Set `BUILD_CONTAINER=0` only when you intentionally need to skip the local container build:

```bash
BUILD_CONTAINER=0 bash scripts/verify.sh
```

CI runs the same core checks with Gradle 9.6 and JDK 21.

## Production requirements

Set `APP_ENV=production`, use an HTTPS `PUBLIC_BASE_URL`, and replace every placeholder secret. The application refuses to start when production configuration is missing or unsafe.

Only Caddy exposes public ports. PostgreSQL, Redis, and Meilisearch remain on the private Docker network.

## Next milestones

1. GitHub App installation JWT and short-lived installation tokens
2. Repository, release, and workflow caching
3. Meilisearch indexing and GitHub fallback search
4. Workflow-run monitoring and push notification delivery
5. Optional favourites, settings, and recent-history sync
6. Privacy-safe opt-in telemetry and announcements

## License

Copyright 2026 Sayanthrock Developer.

Licensed under the [Apache License 2.0](LICENSE).
