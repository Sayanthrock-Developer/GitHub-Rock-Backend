# API v1

## `GET /v1/health`

Reports the backend, PostgreSQL, Redis, and Meilisearch state.

## `GET /v1/config`

Returns public app-version and feature-availability metadata. It never returns secrets.

GitHub Rock validates this response before using backend-assisted authentication. The Android client requires `apiVersion=v1`, rejects maintenance mode, checks `minSupportedAppVersion`, and verifies the required OAuth feature flag. When the backend is unavailable or incompatible, the app uses its direct-GitHub fallback when a public OAuth client ID is present.

Important mobile flags:

- `oauthDeviceProxy` — Device Flow start and poll are available.
- `oauthRefreshProxy` — expiring OAuth tokens can refresh through the backend.
- `repositoryCache` — the live store catalog is available.

## Store catalog

The store catalog follows the data-driven model used by [`kurikomi-labs/komi-store-backend-data`](https://github.com/kurikomi-labs/komi-store-backend-data). The backend reads its generated JSON data from `STORE_DATA_BASE_URL`, caches each catalog for 23 hours, and serves stale cached data if an upstream refresh temporarily fails.

Supported categories:

- `trending`
- `new-releases`
- `most-popular`

Supported platforms:

- `android`
- `windows`
- `macos`
- `linux`

### `GET /v1/store`

Returns the available categories, platforms, and all successfully loaded catalogs.

### `GET /v1/store/{category}/{platform}`

Returns one real catalog, for example:

```text
GET /v1/store/trending/android
GET /v1/store/new-releases/windows
GET /v1/store/most-popular/macos
GET /v1/store/most-popular/linux
```

The response preserves repository metadata from the upstream JSON, including owner, description, stars, forks, language, topics, release recency, and ranking data.

### `GET /v1/store/search`

Searches the loaded store catalogs by repository name, full name, description, language, or topic.

```text
GET /v1/store/search?q=music
GET /v1/store/search?q=android&platform=android
GET /v1/store/search?q=editor&category=trending&limit=20
```

`limit` is capped at 100. `category` and `platform` are optional filters.

### `GET /v1/store/repository/{owner}/{repo}`

Returns a repository when it exists in the store catalog, for example:

```text
GET /v1/store/repository/owner/example-app
```

This endpoint intentionally only returns repositories present in the catalog; it does not fabricate metadata or create mock entries.

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

Returns `pending`, `slow_down`, `authorized`, `expired`, `denied`, or `error`. Authorized responses preserve access-token expiry and refresh-token metadata returned by GitHub.

## `POST /v1/auth/device/refresh`

Body:

```json
{ "refresh_token": "..." }
```

Exchanges an expiring refresh token through GitHub using the server-only OAuth client secret. Returns `503 oauth_refresh_unavailable` when the secret is not configured. Tokens are proxied to the requesting Android client and are not stored by this service.

## `POST /v1/github/webhooks`

Requires valid `X-Hub-Signature-256`, `X-GitHub-Delivery`, and `X-GitHub-Event` headers. Payloads are capped at 1 MiB. Delivery IDs are persisted for replay protection; full payloads are not stored.
