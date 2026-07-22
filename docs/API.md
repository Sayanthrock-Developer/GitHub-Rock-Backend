# API v1

## `GET /v1/health`
Reports the backend, PostgreSQL, Redis, and Meilisearch state.

## `GET /v1/config`
Returns public app-version and feature-availability metadata. It never returns secrets.

## `POST /v1/auth/device/start`
Starts GitHub Device Flow using the configured public OAuth client ID.

## `POST /v1/auth/device/poll`
Body: `{ "device_code": "..." }`. Returns `pending`, `slow_down`, `authorized`, `expired`, `denied`, or `error`.

## `POST /v1/github/webhooks`
Requires valid `X-Hub-Signature-256`, `X-GitHub-Delivery`, and `X-GitHub-Event` headers. Payloads are capped at 1 MiB. Delivery IDs are persisted for replay protection; full payloads are not stored.
