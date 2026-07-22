# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend foundation for the **GitHub Rock** Android developer control centre.

> The Android app remains usable without this service. Direct GitHub repository, issue, pull-request, workflow, release, and download actions should continue to use the user's GitHub authorization whenever possible.

## Included in v0.1

- Kotlin 2.1 and Ktor 3 on JDK 21
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
docker compose up --build
```

Open `http://localhost:8080/v1/health` when using the default local Caddy host.

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

Apache-2.0. Add the complete license text before the first public binary release.
