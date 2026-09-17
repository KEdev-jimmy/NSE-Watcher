# NSE Watcher News Pipeline

The News page keeps its existing Android UI. The backend now separates news ingestion from the API route so additional sources can be added without changing the app screen.

## Current pipeline

1. **External sources**
   - MyStocks Africa partner API for NSE market-intelligence and corporate-action data.
   - Business Daily Africa Business RSS.
   - The Standard Business RSS.

2. **Ingestion**
   - `backend/lib/newsSources.js` contains source connectors and the lightweight RSS/Atom parser.
   - Each external request has a timeout and source-level failure is isolated with `Promise.allSettled`.

3. **Processing & enrichment**
   - `backend/lib/newsFeed.js` normalizes provider field differences.
   - NSE ticker/company matching is applied from provider metadata and article titles.
   - External media is filtered for NSE/market relevance before entering the app feed.
   - Articles are restricted to the existing 90-day window and deduplicated per source.
   - Source URL and verification metadata are retained; RSS items point back to the publisher's article.

4. **API**
   - `backend/api/news.js` remains the stable `/api/news` entry point and delegates to the layered feed service.
   - Existing `feed`, `detail`, and `company` actions remain available.
   - The feed response exposes the source registry and any partial-source errors without exposing provider API keys.

## Next architecture stages

The diagram's storage and intelligence layers are intentionally not faked in this first news-source implementation. The next stages can add persistent article storage, scheduled ingestion, cross-source story matching, company/filing enrichment, sentiment analysis, and AI evidence summaries while keeping the Android News UI contract stable.
