# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend for the **GitHub Rock** Android developer control centre.

> The Android app remains usable without this service. Direct GitHub repository, issue, pull-request, workflow, release, and download actions continue to use the user's GitHub authorization whenever possible.

## Included in v0.1

- Kotlin 2.4, Ktor 3.5, and JDK 21
- PostgreSQL with Flyway migrations and HikariCP
- Redis connectivity
- Meilisearch connectivity
- Public health and runtime configuration endpoints
- Stateless GitHub OAuth Device Flow start, poll, and refresh proxy
- Android-aligned OAuth scopes, including native follow/unfollow support
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
| POST | `/v1/auth/device/refresh` | Refresh an expiring GitHub OAuth token |
| POST | `/v1/github/webhooks` | Verify and accept GitHub webhooks |

See [`docs/API.md`](docs/API.md).

## Connect the Android app

1. Deploy this repository behind HTTPS.
2. Configure `GITHUB_OAUTH_CLIENT_ID` and the server-only `GITHUB_OAUTH_CLIENT_SECRET`.
3. Verify `/v1/health` and `/v1/config`.
4. In GitHub Rock, open **Profile → About → App information → GitHub Rock Backend connection**.
5. Enter the deployed HTTPS base URL and run the connection test.

The Android app can also receive the endpoint at build time through `GITHUB_ROCK_BACKEND_URL`. The OAuth client secret must never be copied into the Android repository, `local.properties`, GitHub Actions variables, or an APK.

## Run locally

```bash
cp .env.example .env
# Fill GITHUB_OAUTH_CLIENT_ID, GITHUB_OAUTH_CLIENT_SECRET, and GITHUB_WEBHOOK_SECRET
bash scripts/start-local.sh
```

Open `http://localhost/v1/health`. Caddy is the only public service; the Ktor application remains private on the Docker network.

## Verify before a pull request

Run the unit tests, fat-JAR build, Compose validation, Shell syntax checks, and Docker image build:

```bash
bash scripts/verify.sh
```

Set `BUILD_CONTAINER=0` only when you intentionally need to skip the local container build:

```bash
BUILD_CONTAINER=0 bash scripts/verify.sh
```

The direct CI build uses Gradle 8.13 with JDK 21. The Docker build independently verifies the Gradle 9.6 builder image.

## Production requirements

Set `APP_ENV=production`, use an HTTPS `PUBLIC_BASE_URL`, and replace every placeholder secret. Set `CADDY_ADDRESS` to the production hostname, such as `api.example.com`, so Caddy provisions HTTPS automatically. The application refuses to start when production configuration is missing or unsafe.

Only Caddy exposes public ports. PostgreSQL, Redis, Meilisearch, and the Ktor application remain on the private Docker network.

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
