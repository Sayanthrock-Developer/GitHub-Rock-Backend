# GitHub Rock Backend

Production-oriented Kotlin/Ktor backend for the **GitHub Rock** Android developer control centre.

The backend now also exposes a GitHub Store-compatible API surface modeled on:

- **Reference backend:** https://github.com/kurikomi-labs/komi-store-backend
- **Reference catalog data:** https://github.com/kurikomi-labs/komi-store-backend-data

The integration is real-data driven. It does not ship mock repositories or demo catalog entries.

## What this project does

- GitHub OAuth Device Flow and web OAuth proxy
- GitHub webhook verification and replay protection
- Public health and runtime configuration
- **Komi-compatible store search and discovery**
- Repository details and README lookup
- User lookup
- Topic/category/platform discovery
- Store telemetry forwarding
- M3 badge endpoints

The Android app can consume the GitHub Rock API without depending on the upstream service's hostname.

## Store architecture

```text
GitHub Rock Android
        |
        | HTTPS /v1/store/*
        v
GitHub Rock Backend
        |
        | HTTPS
        v
Komi-compatible Store Backend
kurikomi-labs/komi-store-backend
        |
        +--> catalog / GitHub data
```

The upstream URL is configurable through `STORE_BACKEND_BASE_URL`. The production default is:

`https://api.github-store.org`

This keeps the app-facing API stable while allowing the backend operator to change the upstream deployment without changing the Android client.

## Store API

All GitHub Store-compatible endpoints are available under `/v1/store/`:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/store/health` | Upstream store health |
| GET | `/v1/store/search?q=` | Search store repositories |
| GET | `/v1/store/search/explore?q=&page=` | Deep paginated search |
| GET | `/v1/store/categories/{category}/{platform}` | Ranked category/platform repositories |
| GET | `/v1/store/topics/{bucket}/{platform}` | Topic-bucketed repositories |
| GET | `/v1/store/repo/{owner}/{name}` | Repository details |
| GET | `/v1/store/readme/{owner}/{name}` | Cached repository README |
| GET | `/v1/store/user/{username}` | Cached GitHub user data |
| POST | `/v1/store/events` | Opt-in store telemetry |
| GET | `/v1/store/badge/{owner}/{name}/{kind}/{style}/{variant}` | Per-repository badge |
| GET | `/v1/store/badge/{kind}/{style}/{variant}` | Global badge |

Query parameters are forwarded without collapsing repeated values, so pagination, filters, and multi-value search parameters remain compatible with the upstream API.

### Example

```bash
curl 'http://localhost/v1/store/search?q=kotlin'

curl 'http://localhost/v1/store/categories/android/trending'

curl 'http://localhost/v1/store/repo/OpenHub-Store/GitHub-Store'
```

## Public configuration

`GET /v1/config` advertises the store capability flags so the Android client can enable the corresponding UI options only when the backend supports them.

Store capabilities include:

- `storeBackend`
- `storeSearch`
- `storeExplore`
- `storeCategories`
- `storeTopics`
- `storeRepository`
- `storeReadme`
- `storeUser`
- `storeEvents`
- `storeBadges`

## Core API

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/v1/health` | Backend and dependency health |
| GET | `/v1/config` | Public app version and feature flags |
| POST | `/v1/auth/device/start` | Start GitHub Device Flow |
| POST | `/v1/auth/device/poll` | Poll GitHub Device Flow |
| POST | `/v1/auth/device/refresh` | Refresh an expiring GitHub OAuth token |
| GET | `/v1/auth/github/start` | Start web OAuth with PKCE |
| GET | `/v1/auth/github/callback` | Validate OAuth callback parameters |
| POST | `/v1/auth/github/exchange` | Exchange authorization code for tokens |
| POST | `/v1/github/webhooks` | Verify and accept GitHub webhooks |

Full request/response details are in [`docs/API.md`](docs/API.md).

## Configuration

Copy `.env.example` to `.env` and configure the deployment:

```bash
cp .env.example .env
```

The store integration uses:

```text
STORE_BACKEND_BASE_URL=https://api.github-store.org
```

For a self-hosted compatible deployment, point this variable at that deployment's base URL. Production validation requires an HTTPS URL.

OAuth client secrets, webhook secrets, database passwords, and other server-only credentials must never be copied into the Android project or APK.

## Run locally

```bash
bash scripts/start-local.sh
```

Then verify:

```bash
curl http://localhost/v1/health
curl http://localhost/v1/config
curl 'http://localhost/v1/store/search?q=kotlin'
```

## Verify before a pull request

Run the repository's verification script:

```bash
bash scripts/verify.sh
```

This validates the Gradle build/tests, Compose-related checks, shell scripts, Compose configuration, and Docker image build used by CI.

## Security

- OAuth access and refresh tokens are not persisted by this service.
- Webhook payloads require HMAC-SHA256 verification and replay protection.
- Store responses are proxied as JSON without logging token bodies.
- Store telemetry remains opt-in.
- Production upstream configuration requires HTTPS.

## License

Copyright 2026 Sayanthrock Developer.

Licensed under the [Apache License 2.0](LICENSE).
