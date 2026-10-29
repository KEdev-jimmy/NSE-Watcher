# NSE Watcher Home and Brand Redesign

## Intent

Give NSE Watcher a distinctive, premium market-research identity and a ten-minute after-work Home experience while preserving provider-backed data, research navigation, Practice, and the signed release pipeline.

## Approved visual references

- Brand: `libfile_ab0fbcae56cc8191b36334aaae33b9e8`
- Midnight Market: `libfile_131c03529e2081918f1ba745fc1ef29b`
- Market Daylight: `libfile_bb43d45f81688191b9040cac67761051`

## Scope

- Update the reusable theme palette, Android launcher emblem, wordmark and five-tab navigation styling.
- Implement a Home-first hierarchy: greeting and status; one primary Practice portfolio card; truthful market-at-a-glance metrics; evidence-backed unusual move; compact watchlist; latest verified news; then trading-volume context.
- Preserve the existing callbacks for company, watchlist, Practice, alerts, news and profile navigation.
- Render real Practice data only. Preserve `provisional` and unavailable states; never use illustrative design figures in production.
- Status chip must say `Market opened`, `Market closed`, or `Status unavailable`, without a timestamp.
- Do not imply volume is liquidity or that a flagged move is a buy/sell signal.

## Constraints and anti-goals

- Existing Android Kotlin/Compose implementation and provider/data stores stay intact.
- No fabricated quotes, chart history, holdings, news, market breadth, recommendations or backend endpoints.
- Do not duplicate portfolio values in a second pair of Home cards.
- Retain the current five destination navigation labels and routing.
- Honor accessible touch targets, readable type and light/dark themes.
- No modification of permanent signing identity, release workflow, unrelated data models, or other pages' logic.

## Approaches

1. Rewrite all screens simultaneously: maximal scope and regression risk; not selected.
2. Adapt existing Compose Home components and shared theme/brand, then expand to other pages: selected for safe reuse of data and callbacks.
3. Replace UI with a static mockup: rejected, because it would break data correctness and navigation.

## Follow-up

Carry the established brand across Market, News, Companies and Practice screens after this Home-focused release, without changing their data behavior.
