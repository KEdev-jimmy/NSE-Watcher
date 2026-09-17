# NSE Watcher News Pipeline

The News page keeps its existing Android UI. The backend now separates news ingestion from the API route so additional sources can be added without changing the app screen.

## Current source layer

### Market/media sources
- MyStocks Africa partner API for NSE market-intelligence and corporate-action data.
- Business Daily Africa Business RSS.
- The Standard Business RSS.
- KBC Digital RSS.
- Capital FM Kenya RSS.
- Citizen Digital Business page connector.
- The Star Kenya Business page connector.
- Capital FM NSE page connector.

### Primary/regulatory sources
- Nairobi Securities Exchange public announcements/documents connector.
- Capital Markets Authority Kenya public news/notice connector.

### Company investor-relations sources
- Safaricom Investor Relations.
- Equity Group Investor Relations.
- KCB Group Investor Relations.
- Absa Bank Kenya Investor Relations.
- EABL Investor Relations / announcements.

### Reuters
Reuters is represented as a licensed-source connector, but it is intentionally **not scraped**. Reuters documents authenticated RSS/API delivery for licensed clients. If a licensed Reuters feed URL is supplied as the Vercel `REUTERS_RSS_URL` environment variable, the existing ingestion pipeline will consume it without changing the Android app.

## Ingestion

`backend/lib/newsSources.js` contains RSS/Atom and public HTML-link connectors. RSS sources are parsed directly; public business, regulatory and investor-relations pages are scanned for relevant article/document links. Each external request has an 8-second timeout and source-level failure is isolated with `Promise.allSettled`, so one unavailable publisher does not take down the whole feed.

## Processing & enrichment

- `backend/lib/newsFeed.js` normalizes provider field differences.
- NSE ticker/company matching is applied from source metadata, provider metadata and article titles.
- External media is filtered for NSE/market relevance before entering the app feed.
- Articles are restricted to the existing 90-day window and deduplicated per source.
- Original source URLs and verification metadata are retained so the app can open the publisher/primary document directly.
- Primary company IR sources are tagged with the relevant ticker where known.

## API

- `backend/api/news.js` remains the stable `/api/news` entry point and delegates to the layered feed service.
- Existing `feed`, `detail`, and `company` actions remain available.
- The feed response exposes the source registry and any partial-source errors without exposing provider API keys.
- No Android News UI redesign is required for this source expansion.

## Next architecture stages

The diagram's storage and intelligence layers are intentionally not faked. The next stages can add persistent article storage, scheduled ingestion, cross-source story matching, company/filing enrichment for the wider NSE universe, sentiment analysis, and AI evidence summaries while keeping the Android News UI contract stable.
