# API v1

## `GET /v1/health`

Reports the backend, PostgreSQL, Redis, and Meilisearch state.

## `GET /v1/config`

Returns public app-version and feature-availability metadata. It never returns secrets.

Important mobile flags:

- `oauthDeviceProxy` — Device Flow start and poll are available.
- `oauthRefreshProxy` — expiring OAuth tokens can refresh through the backend.

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
