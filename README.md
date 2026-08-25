# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend for the **GitHub Rock** Android developer control centre.

> The backend now also provides a real, data-driven GitHub Store catalog. It follows the same generated-data model as [`kurikomi-labs/komi-store-backend-data`](https://github.com/kurikomi-labs/komi-store-backend-data), while keeping GitHub authentication and webhook responsibilities in this service.

## What this project does

GitHub Rock Backend provides server-side capabilities that should not live inside the Android application:

- GitHub OAuth Device Flow start, polling, and refresh proxy
- GitHub Web OAuth with PKCE
- GitHub webhook signature verification and replay protection
- A real GitHub Store catalog backed by generated repository data
- Store categories, platform filters, search, and repository lookup
- PostgreSQL persistence for webhook delivery IDs
- Redis and Meilisearch connectivity for backend-assisted features
- Public health and runtime configuration endpoints
- Structured JSON errors and request logging without token bodies
- Production Docker Compose deployment with Caddy, PostgreSQL, Redis, and Meilisearch

There are no mock repositories or hardcoded demo catalog entries in the store API.

## Store data model

The Store API consumes the generated JSON catalog produced by the public data repository:

`https://github.com/kurikomi-labs/komi-store-backend-data`

The upstream data is organized as:

```text
cached-data/
  trending/
    android.json
    windows.json
    macos.json
    linux.json
  new-releases/
    android.json
    windows.json
    macos.json
    linux.json
  most-popular/
    android.json
    windows.json
    macos.json
    linux.json
```

Each catalog contains real GitHub repository metadata, including owner, description, stars, forks, language, topics, release information, and ranking information.

### Categories

| Category | Purpose | Sort basis |
|---|---|---|
| **Trending** | Recently active repositories with strong momentum | Trending score |
| **New Releases** | Repositories with recent stable releases | Release date |
| **Most Popular** | High-star repositories | Star count |

### Platforms

| Platform | Supported installers in the upstream data |
|---|---|
| **Android** | `.apk` |
| **Windows** | `.exe`, `.msi` |
| **macOS** | `.dmg`, `.pkg` |
| **Linux** | `.AppImage`, `.deb`, `.rpm`, `.pkg.tar.zst` |

## Store API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/store` | List categories, platforms, and loaded catalogs |
| GET | `/v1/store/{category}/{platform}` | Read one category/platform catalog |
| GET | `/v1/store/search?q=...` | Search real catalog data |
| GET | `/v1/store/repository/{owner}/{repo}` | Look up a repository in the catalog |

Examples:

```bash
curl http://localhost/v1/store
curl http://localhost/v1/store/trending/android
curl http://localhost/v1/store/new-releases/windows
curl http://localhost/v1/store/most-popular/macos
curl http://localhost/v1/store/most-popular/linux
curl 'http://localhost/v1/store/search?q=music&platform=android'
curl 'http://localhost/v1/store/search?q=editor&category=trending&limit=20'
curl http://localhost/v1/store/repository/owner/example-app
```

The backend validates category/platform values, deduplicates search results across catalogs, and caps search results at 100.

## Store refresh and caching

`STORE_DATA_BASE_URL` points to the generated catalog source. By default it uses the raw `main` branch of `komi-store-backend-data`:

```text
https://raw.githubusercontent.com/kurikomi-labs/komi-store-backend-data/main/cached-data
```

Each category/platform catalog is cached in memory for **23 hours**, matching the upstream data repository's cache window. If an upstream request fails after a successful load, the backend serves the last known catalog instead of replacing it with fake or empty data.

Set a different HTTPS source when deploying a controlled mirror:

```bash
STORE_DATA_BASE_URL=https://example.com/github-rock/cached-data
```

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
    /    |     \
   /     |      \
OAuth   Store    GitHub Webhooks
          |
          v
   Generated Store JSON
          |
          v
komi-store-backend-data

PostgreSQL stores webhook delivery IDs.
Redis / Meilisearch remain private Docker-network dependencies.
```

Only Caddy exposes public ports in the production Compose stack. PostgreSQL, Redis, Meilisearch, and Ktor remain on the private Docker network.

## Authentication API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/health` | Backend and dependency health |
| GET | `/v1/config` | Public app version and feature flags |
| POST | `/v1/auth/device/start` | Start GitHub Device Flow |
| POST | `/v1/auth/device/poll` | Poll GitHub Device Flow |
| POST | `/v1/auth/device/refresh` | Refresh an expiring GitHub OAuth token |
| GET | `/v1/auth/github/start` | Start GitHub Web OAuth |
| GET | `/v1/auth/github/callback` | Complete the OAuth redirect |
| POST | `/v1/auth/github/exchange` | Exchange the OAuth code with PKCE verifier |
| POST | `/v1/github/webhooks` | Verify and accept GitHub webhooks |

Full request/response details are in [`docs/API.md`](docs/API.md).

## Connect the Android app

1. Deploy this repository behind HTTPS.
2. Configure the GitHub OAuth and production secrets.
3. Configure `STORE_DATA_BASE_URL` if a private mirror is required; otherwise the public generated catalog is used.
4. Verify `/v1/health` and `/v1/config`.
5. Verify `/v1/store` and at least one endpoint for every category/platform combination.
6. In GitHub Rock, open **Profile → About → App information → GitHub Rock Backend connection**.
7. Enter the deployed HTTPS base URL and run the connection test.

The Android app can also receive the endpoint at build time through `GITHUB_ROCK_BACKEND_URL`. The OAuth client secret must never be copied into the Android repository, `local.properties`, GitHub Actions variables, or an APK.

## Requirements

- Kotlin 2.4
- Ktor 3.5
- JDK 21
- PostgreSQL
- Redis
- Meilisearch
- Docker and Docker Compose for the production-style local stack

## Run locally

```bash
cp .env.example .env
# Fill the required OAuth, webhook, and production values as needed.
bash scripts/start-local.sh
```

Open:

```text
http://localhost/v1/health
http://localhost/v1/config
http://localhost/v1/store
```

The Store API does not require a GitHub user token because its source is generated public catalog data. GitHub OAuth tokens remain stateless and are proxied only for authentication flows.

## Verify before a pull request

Run the complete local verification script:

```bash
bash scripts/verify.sh
```

This verifies the Gradle build/tests, Compose configuration, shell scripts, and Docker image. The Store integration should additionally be smoke-tested with:

```bash
curl http://localhost/v1/store
curl http://localhost/v1/store/trending/android
curl http://localhost/v1/store/new-releases/windows
curl http://localhost/v1/store/most-popular/macos
curl http://localhost/v1/store/most-popular/linux
curl 'http://localhost/v1/store/search?q=android'
```

## Production requirements

Set `APP_ENV=production`, use an HTTPS `PUBLIC_BASE_URL`, and replace every placeholder secret. Set `CADDY_ADDRESS` to the production hostname so Caddy provisions HTTPS automatically.

`STORE_DATA_BASE_URL` must also use HTTPS in production.

Only Caddy exposes public ports. PostgreSQL, Redis, Meilisearch, and the Ktor application remain on the private Docker network.

## Included capabilities

- Real Store catalog integration
- Trending / New Releases / Most Popular categories
- Android / Windows / macOS / Linux platform catalogs
- Store search and repository lookup
- 23-hour backend catalog cache with stale-data fallback
- GitHub OAuth Device Flow
- GitHub Web OAuth with PKCE
- GitHub webhook HMAC-SHA256 verification
- Webhook replay protection
- Structured JSON errors
- Request logging without token bodies
- Docker Compose stack with Caddy, PostgreSQL, Redis, and Meilisearch
- Unit tests, Gradle CI, Docker CI, and Dependabot

## License

Copyright 2026 Sayanthrock Developer.

Licensed under the [Apache License 2.0](LICENSE).
