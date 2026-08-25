# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend for the **GitHub Rock** Android developer control centre.

> The Android app remains usable without this service. Direct GitHub repository, issue, pull-request, workflow, release, and download actions continue to use the user's GitHub authorization whenever possible.

## What this project does

GitHub Rock Backend provides the small set of server-side capabilities that should not live inside the Android application, especially OAuth operations that require a client secret and verified GitHub webhook intake.

The service is intentionally stateless for OAuth access tokens: GitHub tokens are proxied to the Android client and are not stored by this service.

### Main capabilities

- GitHub OAuth Device Flow start, polling, and refresh proxy
- Android-aligned OAuth scopes, including follow/unfollow support
- GitHub webhook signature verification and delivery replay protection
- PostgreSQL persistence for webhook delivery IDs
- Redis and Meilisearch connectivity for future backend-assisted features
- Public health and runtime configuration endpoints
- Structured JSON errors and request logging without token bodies
- Production Docker Compose deployment with Caddy, PostgreSQL, Redis, and Meilisearch

## Architecture

```text
GitHub Rock Android
        |
        | HTTPS
        v
      Caddy
        |
        v
   Ktor Backend
     /     \
    /       \
 GitHub   PostgreSQL
   API       |
             +-- webhook delivery IDs

Redis / Meilisearch remain private Docker-network dependencies.
```

Only Caddy exposes public ports in the production Compose stack. PostgreSQL, Redis, Meilisearch, and Ktor remain on the private Docker network.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/health` | Backend and dependency health |
| GET | `/v1/config` | Public app version and feature flags |
| POST | `/v1/auth/device/start` | Start GitHub Device Flow |
| POST | `/v1/auth/device/poll` | Poll GitHub Device Flow |
| POST | `/v1/auth/device/refresh` | Refresh an expiring GitHub OAuth token |
| POST | `/v1/github/webhooks` | Verify and accept GitHub webhooks |

Full request/response details are in [`docs/API.md`](docs/API.md).

## API examples

### Check backend health

```bash
curl http://localhost/v1/health
```

A healthy deployment returns JSON describing the backend and its configured dependencies. Use the endpoint as the first deployment smoke test.

### Read public configuration

```bash
curl http://localhost/v1/config
```

The response contains public compatibility and feature metadata only. It must never contain OAuth client secrets or other server-only credentials.

### Start GitHub Device Flow

```bash
curl -X POST http://localhost/v1/auth/device/start
```

When OAuth is configured, the response contains the GitHub Device Flow information required by the Android client. If OAuth is unavailable, the backend returns an `oauth_unavailable` error instead of exposing server configuration.

### Poll Device Flow

```bash
curl -X POST http://localhost/v1/auth/device/poll \
  -H 'Content-Type: application/json' \
  -d '{"device_code":"YOUR_DEVICE_CODE"}'
```

Possible authorization states include `pending`, `slow_down`, `authorized`, `expired`, and `denied`.

### Refresh an OAuth token

```bash
curl -X POST http://localhost/v1/auth/device/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"YOUR_REFRESH_TOKEN"}'
```

The refresh token is exchanged through GitHub using the server-only client secret. The backend does not persist the token.

### GitHub webhook endpoint

```bash
curl -X POST http://localhost/v1/github/webhooks \
  -H 'X-GitHub-Event: ping' \
  -H 'X-GitHub-Delivery: example-delivery-id' \
  -H 'X-Hub-Signature-256: sha256=YOUR_HMAC_SIGNATURE' \
  -H 'Content-Type: application/json' \
  -d '{"zen":"example"}'
```

Webhook requests must include a valid HMAC-SHA256 signature and delivery ID. The server caps payloads at 1 MiB and rejects replayed delivery IDs.

> **Security:** The values marked `YOUR_*` are examples only. Never commit real access tokens, refresh tokens, OAuth client secrets, webhook secrets, or generated signatures.

## Connect the Android app

1. Deploy this repository behind HTTPS.
2. Configure `GITHUB_OAUTH_CLIENT_ID` and the server-only `GITHUB_OAUTH_CLIENT_SECRET`.
3. Verify `/v1/health` and `/v1/config`.
4. In GitHub Rock, open **Profile → About → App information → GitHub Rock Backend connection**.
5. Enter the deployed HTTPS base URL and run the connection test.

The Android app can also receive the endpoint at build time through `GITHUB_ROCK_BACKEND_URL`. The OAuth client secret must never be copied into the Android repository, `local.properties`, GitHub Actions variables, or an APK.

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
