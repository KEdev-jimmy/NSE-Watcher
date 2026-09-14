# NSE Watcher

NSE Watcher is an Android app for monitoring Nairobi Securities Exchange equities. It currently runs in **clearly labelled demo mode**: all quotes, performance, volume, news and status values are samples and must not be treated as live market data or investment advice.

## Build an installable APK

1. Install Android Studio and Android SDK Platform 35 (or set `ANDROID_HOME`).
2. Open this repository in Android Studio, allow Gradle to sync, then select **Build → Build APK(s)**; or run `./gradlew assembleDebug`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on a phone with developer/unknown-source installation enabled.

For a release APK, create a keystore outside the repository and configure Android Studio's signed-bundle/APK flow. Never commit `*.jks`, `key.properties`, provider keys, tokens, or passwords.

## Architecture

`Compose UI → MarketViewModel → MarketRepository → data source/API`.

`DemoMarketRepository` supplies sample SCOM.KE, EQTY.KE, KCB.KE, and ABSA.KE data. `BackendApi` declares the intended secure-backend endpoints and is deliberately not configured with a provider key. Replace the demo repository with a backend repository after a backend is available; do not call MyStocks Africa (or another provider) directly from the app.

The UI provides dashboard, watchlist, stock details (including a historical-chart surface), alerts, and news. It includes loading/error/empty states and manual refresh. The included score is explainable momentum information only, never a forecast or financial recommendation.

## Backend integration

See [docs/backend-integration.md](docs/backend-integration.md) for the endpoint contract and security requirements. A backend should own API credentials, caching, rate limiting, response validation, delayed-data labels, notification evaluation, and Android notification delivery.

## Tests

Run `./gradlew test`. The domain tests cover percentage calculations and transparent signal classification. Extend tests alongside backend response mappings and alert evaluation as those sources are implemented.
