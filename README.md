# NSE Watcher

NSE Watcher is an Android application for evidence-grounded Nairobi Securities Exchange market intelligence.

## Current product state

The active app uses provider-backed market and news data where available, with explicit unavailable/fallback states rather than treating missing data as real observations. Company Intelligence, market views, news, watchlists, alerts, evidence/provenance, and Home intelligence are implemented across the current Android and Vercel backend.

The project is designed around:

```
RAW DATA → CALCULATION → EXPLANATION → EVIDENCE
```

The application must not fabricate prices, financials, indices, volume, news, evidence, portfolio holdings, watchlist companies, or AI conclusions. It also does not provide BUY/SELL/HOLD recommendations.

## Build

Android CI builds the application with Java 17, Android SDK 35 and Gradle 8.14.4.

For the detailed implementation history, current audit status, known limitations, verification state, and continuation instructions, see **`PROJECT_CONTEXT.md`**. That file is the authoritative project handoff document.

## Data and safety

Market data may be delayed according to the provider's documented feed characteristics. Observation timestamps and provenance should be used instead of assuming that a refresh interval means a specific observation delay.

NSE Watcher is an information and analysis application, not a brokerage or trading-execution system.
