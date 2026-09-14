# Secure market-data backend preparation

The Android client must communicate only with a backend controlled by the app owner. Store MyStocks Africa Partner API (or any replacement provider) credentials in the backend's secret manager/environment configuration, never in Android source, resources, `BuildConfig`, or Git.

## Proposed client endpoints

- `GET /market/status`
- `GET /market/summary`
- `GET /stocks`
- `GET /stocks/{symbol}/quote`
- `GET /stocks/{symbol}/candles`
- `GET /stocks/{symbol}/news`
- `GET /stocks/{symbol}/corporate-actions`
- `GET /movers`

Responses should state provider, exchange timestamp, retrieval timestamp, currency, and `live`/`delayed` status. The app must surface missing, stale, delayed, empty, rate-limited, invalid, and failed responses rather than implying live prices.

## Responsibilities

The backend should validate provider data, cache allowed responses, normalize symbols, enforce rate limits, and evaluate alert conditions without notification spam. It should expose authenticated user-specific watchlist/alert synchronization only after an authentication design is selected. Android may keep offline caches and user settings locally; server credentials never leave the backend.

## Notifications

Create Android notification channels on-device and have the backend dispatch only actionable, deduplicated alerts (for example an agreed daily-move or unusual-volume threshold). Let users opt in/out by alert type and stock.
