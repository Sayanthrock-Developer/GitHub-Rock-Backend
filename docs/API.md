# API v1

## `GET /v1/health`

Reports the GitHub Rock backend, PostgreSQL, Redis, and Meilisearch state.

## `GET /v1/config`

Returns public app-version and feature-availability metadata. It never returns secrets.

Store flags advertise the complete GitHub Store-compatible surface: search, explore, categories, topics, repository details, README, user lookup, events, and badges.

## GitHub Store-compatible API

GitHub Rock proxies the public store API surface implemented by and modeled after:

https://github.com/kurikomi-labs/komi-store-backend

The upstream base URL is configured with `STORE_BACKEND_BASE_URL` and defaults to `https://api.github-store.org`.

### `GET /v1/store/health`

Checks the configured upstream store backend.

### `GET /v1/store/search?q=`

Searches the store catalog. Query parameters are forwarded to the upstream service.

### `GET /v1/store/search/explore?q=&page=`

Runs deep paginated store/GitHub exploration.

### `GET /v1/store/categories/{category}/{platform}`

Returns the upstream ranked category/platform list.

### `GET /v1/store/topics/{bucket}/{platform}`

Returns repositories grouped by an upstream topic bucket and platform.

### `GET /v1/store/repo/{owner}/{name}`

Returns repository details from the upstream store service.

### `GET /v1/store/readme/{owner}/{name}`

Returns the cached README representation provided by the upstream service.

### `GET /v1/store/user/{username}`

Returns cached GitHub user information.

### `POST /v1/store/events`

Forwards opt-in store telemetry to the upstream API. The request body is passed as JSON and is not persisted by GitHub Rock Backend.

### `GET /v1/store/badge/...`

Provides the compatible per-repository and global M3 badge endpoints.

## `POST /v1/auth/device/start`

Starts GitHub Device Flow using the configured public OAuth client ID. The backend requests the same scopes as the Android app:

```text
repo workflow read:user user:email read:org notifications user:follow
```

Returns `503 oauth_unavailable` when OAuth is not configured.

## `POST /v1/auth/device/poll`

Body:

```json
{ "device_code": "..." }
```

Returns `pending`, `slow_down`, `authorized`, `expired`, `denied`, or `error`.

## `POST /v1/auth/device/refresh`

Body:

```json
{ "refresh_token": "..." }
```

Tokens are proxied to the requesting Android client and are not stored by this service.

## `POST /v1/github/webhooks`

Requires valid `X-Hub-Signature-256`, `X-GitHub-Delivery`, and `X-GitHub-Event` headers. Payloads are capped at 1 MiB. Delivery IDs are persisted for replay protection; full payloads are not stored.
