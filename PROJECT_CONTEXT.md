# NSE Watcher — Project Handoff & Development Context

> **Purpose of this file:** persistent development handoff/context for future coding chats.  
> This is **not** the main README. It records the decisions, architecture, phases, audits, implementation history, constraints, and next steps that must be preserved when continuing work on NSE Watcher.

## 0. IMPORTANT — READ THIS FIRST

NSE Watcher is being developed as a serious NSE/Kenya market intelligence Android app.

The most important product principle is:

**RAW DATA → CALCULATION → EXPLANATION → EVIDENCE**

The app must help a beginner understand what the market/company data says without inventing facts or pretending that unavailable data exists.

### Non-negotiable rules

- Never fabricate stock prices, financials, EPS, revenue, profit, dividends, corporate actions, indices, volume, news, evidence, source URLs, portfolio holdings/value, watchlist companies, AI conclusions, or confidence values.
- If data is unavailable, say/show that it is unavailable or omit the section.
- If data is delayed and the delay is actually known, show the delay honestly.
- Do not use hardcoded market/demo values as if they were real.
- Do not create fake AI explanations.
- Do not invent causal explanations from correlation or headlines.
- Do not use BUY / SELL / HOLD recommendations.
- Do not use arbitrary numerical "AI scores" or fake confidence scores.
- Calculations must be distinguishable from raw provider facts.
- Evidence should remain attached to the claim/calculation it supports.
- Source, date/time, URL, company/symbol and period should be preserved when available.
- Do not invent NASI, NSE20 or NSE25 values.
- Do not create a second parallel intelligence/data-fetching architecture.
- Preserve working existing functionality and UI unless a change is explicitly required.
- Prefer surgical, incremental changes over large rewrites.
- Do not expose, print, replace, or modify secrets.
- Test/build before claiming a change is working.
- If a provider does not return a requested data type, omit it rather than inserting fallback/demo values.

---

# 1. PRODUCT DIRECTION

NSE Watcher is intended to be more than a normal stock-price dashboard.

The desired experience is:

1. **What is happening?**
2. **What changed?**
3. **What data supports that?**
4. **What is calculated versus directly reported?**
5. **What is known, unknown, or not established?**
6. Eventually: **Can the app explain the evidence in beginner-friendly language?**

The Home screen should therefore remain intelligence-first rather than becoming a collection of unrelated market widgets.

Current conceptual Home hierarchy:

1. NSE Today / Market Pulse
2. Today's Intelligence
3. What Changed?
4. Market Movers
5. Sector Pulse
6. Personal Intelligence / Watchlist only when genuine user data exists
7. Corporate Actions / Important News
8. Compact Quick Actions

---

# 2. VISUAL / UI PRINCIPLES

Existing green visual system should be preserved:

- HomeGreen: #00A859
- HomeLightGreen: #E9F8F0
- HomeDarkGreen: #063D2A
- HomeTextDark: #12352A
- HomeMuted: #64756D
- HomeBorder: #DDE9E3
- HomeRed: #E94A4A

Avoid:

- giant rounded cards
- excessive whitespace
- oversized icons
- redundant sections
- fake dashboard metrics
- dense technical wording that beginners cannot understand

The user specifically wanted the existing Home hero/refined design preserved where possible.

---

# 3. CURRENT REPOSITORY / IMPORTANT FILES

Repository:

**KEdev-jimmy/NSE-Watcher**

Important areas:

- `app/src/main/java/ke/co/nsewatcher/HomeDashboard.kt`
  - active Home screen
- `app/src/main/java/ke/co/nsewatcher/DesignActivity.kt`
  - navigation / older inactive Home code also exists here
- `app/src/main/java/ke/co/nsewatcher/HomeIntelligence.kt`
  - structured Home intelligence engine
- `app/src/main/java/ke/co/nsewatcher/data/MyStocksCache.kt`
  - market/provider data cache and API parsing
- `backend/api/market.js`
  - backend market endpoint
- `backend/lib/movementIntelligence.js`
  - movement intelligence backend
- `backend/api/moving.js`
  - movement intelligence API
- `app/src/main/java/ke/co/nsewatcher/WhyStockMoving.kt`
  - Android movement intelligence UI
- `backend/api/analyst.js`
  - evidence-grounded analyst endpoint
- `backend/lib/companyIntelligence.js`
  - company intelligence backend
- `app/src/main/java/ke/co/nsewatcher/CompanyIntelligence.kt`
  - company intelligence UI
- `app/src/main/java/ke/co/nsewatcher/data/NewsCache.kt`
  - news feed/cache
- `backend/lib/newsFeed.js`
  - news feed backend
- `backend/lib/newsRelevance.js`
  - deterministic news relevance layer
- `app/src/test/java/ke/co/nsewatcher/HomeIntelligenceEngineTest.kt`
  - Home intelligence tests
- `app/src/test/java/ke/co/nsewatcher/domain/EvidenceGraphTest.kt`
  - evidence graph tests
- `.github/workflows/android.yml`
  - Android CI

---

# 4. ACTIVE HOME SCREEN

The active Home is:

`HomeDashboard.kt`

It is routed from `DesignActivity` and receives real stock data plus callbacks for company/news/market navigation.

There is an older `Home()` implementation inside `DesignActivity.kt` containing hardcoded paper-portfolio values. It is **not the active Home** and must not be reused.

That old code is technical debt and should only be removed in a dedicated cleanup after confirming it is not needed by navigation/tests.

---

# 5. PHASE ROADMAP

Current roadmap:

- Phase 8 → Unified Evidence Graph — **COMPLETE**
- Phase 9 → Complete Home Provenance — **COMPLETE**
- Phase 10 → Real Market & Index Context — **CURRENT / IN PROGRESS**
- Phase 11 → Genuine Personal Portfolio / Watchlist
- Phase 12 → Evidence Quality & Data Confidence
- Phase 13 → AI Explanation Layer

The project must not jump prematurely to Phase 13 or add fake AI.

---

# 6. PHASE 8 — UNIFIED EVIDENCE GRAPH

## Status

**COMPLETE**

## Goal

Create one evidence architecture that can represent:

- raw market observations
- company data
- news
- calculations
- relationships between evidence
- provenance

Architecture:

```
RAW DATA
   ↓
NORMALIZATION
   ↓
EVIDENCE
   ↓
RELATIONSHIPS
   ↓
CALCULATIONS
   ↓
INTELLIGENCE
   ↓
AI EXPLANATION (later)
```

## Implemented

An `EvidenceGraph` was introduced in the domain evidence layer.

Tests cover:

- duplicate evidence records removed
- invalid relationships removed
- self relationships removed
- `relatedTo` returns only explicit matching relationship types

Company intelligence exposes an evidence graph.

Movement intelligence wraps movement evidence, related evidence and movement relationships in the same graph.

Home intelligence also creates an evidence graph.

Important:

**The evidence graph does not infer causation.**

Existing movement relationships can be:

- RELATED
- POSSIBLE
- NOT_ESTABLISHED

This distinction must be preserved.

---

# 7. PHASE 9 — COMPLETE HOME PROVENANCE

## Status

**COMPLETE**

## Goal

Make Home intelligence traceable back to actual data/evidence.

## Implemented

Home intelligence now creates evidence for:

### Market breadth

Evidence ID:

`calculation:market-breadth`

It records that breadth was calculated from the available stock feed.

### Sector calculations

Evidence IDs follow:

`calculation:sector-<sector>`

Sector values are explicitly described as:

**average of available counters**

They are **not** presented as official NSE sector indices.

### Largest gainer / loser

Evidence IDs follow:

`market:<symbol>`

### Company news

News evidence retains:

- source
- source URL
- publication date
- symbol
- company/category information

### Home UI provenance

Today's Intelligence cards can show:

- Evidence
- Source
- Date
- Source link when a real URL exists

What Changed rows can also display compact source information.

No source link is invented.

## Important UI work

`HomeDashboard.kt` was changed so intelligence cards accept structured evidence.

The source URL is opened using the existing URI handler only when the URL is actually an HTTP/HTTPS URL.

Evidence dates are formatted only when a real date exists.

---

# 8. NEWS RELEVANCE LAYER

## Status

**IMPLEMENTED — FIRST DETERMINISTIC VERSION**

Files:

- `backend/lib/newsRelevance.js`
- `backend/lib/newsFeed.js`
- `backend/lib/movementIntelligence.js`
- `NewsItem`
- `NewsCache.kt`

The system now distinguishes market-relevant news from unknown/less relevant news.

Fields include:

- `intelligenceRelevance`
- `intelligenceRelevanceReason`

The current implementation is keyword/category based.

It is intentionally **not** presented as a perfect semantic AI classifier.

Home company news requires:

- company identity
- `intelligenceRelevance == "market"`

Company-specific news can still be shown on company pages under their own evidence context.

### CI incident that was fixed

Several Android CI runs failed because test fixtures defaulted to:

`intelligenceRelevance = "unknown"`

The fixture was corrected to explicitly use:

`intelligenceRelevance = "market"`

with a test reason.

The next CI run passed.

---

# 9. MOVEMENT INTELLIGENCE

## Status

**ALREADY IMPLEMENTED**

This is important because earlier audits had difficulty locating it, but repository inspection confirmed it exists.

Backend:

`backend/lib/movementIntelligence.js`

API:

`backend/api/moving.js`

Android:

- `MovementIntelligenceCache.kt`
- `WhyStockMoving.kt`

The backend combines:

- current quote
- historical daily candles
- company news
- dividend history

It calculates movement over:

- 1D
- 1W
- 1M
- 3M

It attaches dated evidence.

Relationships are classified as:

- related
- possible
- not-established

The system explicitly states that correlation is not proof of causation.

The company page integrates:

`WhyStockMovingSection(s.symbol)`

Therefore movement intelligence is genuinely integrated into the company experience.

---

# 10. COMPANY INTELLIGENCE

## Status

**IMPLEMENTED**

Android company screen uses:

`CompanyIntelligenceCache.load(symbol)`

Backend:

`/api/company?action=intelligence&symbol=...`

Company intelligence can contain:

- company profile
- revenue
- profit
- EPS
- ROE
- margins
- debt/equity
- P/E
- P/B
- dividend yield
- financial history
- dividends
- evidence
- source
- fetched time
- partial/data-quality state

The backend combines MyStocks information with financial-history/dividend information and calculates annual growth where data permits.

---

# 11. EVIDENCE-GROUNDED ANALYST

## Status

**IMPLEMENTED**

`backend/api/analyst.js`

The analyst endpoint:

- uses supplied evidence
- requires evidence IDs
- distinguishes fact from interpretation
- avoids invented explanations
- avoids BUY/SELL/HOLD

Do not weaken this evidence boundary when adding future AI.

---

# 12. COMPANY EVIDENCE UI

The company page has an Evidence section explaining sources such as:

- Market price — MyStocks Africa
- Company profile — MyStocks Africa company profile
- Dividends — MyStocks Africa dividend history
- News & actions — MyStocks Africa company intelligence feed

When available, fetched timestamps are shown.

The UI warns users to verify material announcements against the issuer/NSE.

The company intelligence perspective is deterministic:

- supporting evidence
- counter evidence
- unknown due to missing evidence

The old ambiguous "Confidence" terminology was changed toward:

**Evidence coverage**

This avoids pretending that a numerical confidence score exists.

---

# 13. PRICE CHART IMPROVEMENT

## Status

**IMPLEMENTED**

The company price graph was audited and date/time labels were determined to be important.

Existing backend candles already had timestamps, but Android previously retained only prices.

`MyStocksCache.kt` now has:

```kotlin
data class HistoryPoint(
    val close: Double,
    val date: String = ""
)
```

`HistoryResult` now keeps:

- prices
- points

Company intelligence passes actual history points into the chart.

Axis labels adapt by period:

- 1D → time
- 1W → trading dates
- 1M → spaced dates / weekly progression
- 3M / 6M → months
- 1Y → selected points across the year
- 3Y / 5Y → years

No hardcoded July/August/September labels were introduced.

The vertical axis remains actual prices.

Existing period selector preserved:

- 1D
- 1W
- 1M
- 3M
- 6M
- 1Y
- 3Y
- 5Y

---

# 14. HARD-CODED FALLBACK INCIDENT

## Status

**FIXED**

The user requested removal of hardcoded fallback stocks.

An initial GitHub update accidentally truncated `DesignActivity.kt`. This was detected and repaired using the known-good version.

The temporary fallback removal resulted in:

- `fallbackStocks` removed
- live stock list fallback set to empty
- a sentinel empty `Stock` was used only to avoid an immediate nullable refactor

The active app opens companies only from real stock lists.

Search confirmed `fallbackStocks` no longer existed.

Important:

The old inactive `Home()` in `DesignActivity.kt` still has hardcoded paper portfolio values. This is not active functionality.

---

# 15. MARKET DATA SOURCE

## MyStocks Africa

Existing backend market data uses MyStocks Africa.

The provider feed is documented as approximately 15-minute delayed for stock market data.

Existing market backend supports stock market data and movers.

Secrets include:

- `MYSTOCKS_BASE_URL`
- `MYSTOCKS_API_KEY`

Never expose their values.

The Android app consumes backend endpoints rather than directly embedding provider secrets.

---

# 16. PHASE 10 — REAL MARKET & INDEX CONTEXT

## Status

**CURRENT PHASE — IN PROGRESS**

Goal:

Make Home understand not just individual stocks but the broader NSE market context.

The first implementation established a real-provider path for:

- NASI
- NSE 20
- NSE 25

Known provider symbols:

- `^NASI`
- `^N20I`
- `^N25I`

Important provider caveat:

The public MyStocks pages confirm these index symbols exist, but the Partner API documentation does not explicitly document a dedicated index endpoint. Therefore the implementation uses the existing quote surface and gracefully omits indices if the provider does not return them.

Never insert historical blog/article values as fallback index data.

---

# 17. PHASE 10 — INDEX BACKEND

## Implemented

`backend/api/market.js`

Added:

`action === "indices"`

It requests:

`/market/quotes?symbols=%5ENASI,%5EN20I,%5EN25I`

It accepts flexible provider response shapes:

- data array
- data object
- quotes array

It parses:

- symbol
- name
- price / last / close
- previousClose / prevClose
- changePct / changePercent
- asOf / timestamp

Invalid/non-finite records are omitted.

Returned structure is approximately:

```json
{
  "source": "MyStocks Africa",
  "delayMinutes": null,
  "fetchedAt": "...",
  "indices": [...],
  "requested": ["^NASI", "^N20I", "^N25I"]
}
```

### Important correction

The index endpoint previously claimed:

`delayMinutes: 15`

That was removed because the exact delay for the returned index observations was not verified.

Do not reintroduce a fixed 15-minute index delay unless the actual provider metadata/documentation supports it.

---

# 18. PHASE 10 — ANDROID INDEX DATA

## Implemented

`app/src/main/java/ke/co/nsewatcher/data/MyStocksCache.kt`

Added:

```kotlin
data class MarketIndex(
    val symbol: String,
    val name: String,
    val value: Double,
    val changePct: Double?,
    val asOf: String = ""
)
```

Added:

`loadMarketIndices()`

Backend URL:

`https://nse-watcher.vercel.app/api/market?action=indices`

The parser:

- reads `indices`
- validates finite values
- preserves `asOf`
- omits invalid records

---

# 19. PHASE 10 — HOME INDEX UI

## Implemented

`HomeDashboard.kt`

Home loads market indices alongside the existing news feed.

A compact:

**Market Index Pulse**

appears after the Home hero when real index records exist.

It can show:

- NASI
- NSE 20
- NSE 25

Each card shows:

- label
- actual value
- actual percentage change when returned
- provider date when available

Current footer wording:

**NSE index data • MyStocks Africa • provider timestamp when available**

This deliberately does not call MyStocks the "official index source".

---

# 20. PHASE 10 — INDEX EVIDENCE GRAPH

## Implemented

Index observations are now part of the same `HomeIntelligenceEngine` evidence architecture.

Index evidence IDs:

`index:<symbol>`

Example:

`index:^nasi`

Evidence includes:

- source
- symbol
- observed timestamp/date when available
- actual index value
- actual change when available
- current observation period

Index observations are related to the market-breadth calculation when both exist.

This preserves one evidence graph rather than creating a separate index intelligence architecture.

---

# 21. PHASE 10 — MARKET INDEX PULSE INTERPRETATION

## Implemented

`HomeIntelligenceEngine` now creates:

`market-index-pulse`

It determines only the observed direction of available indices:

- all available tracked indices higher
- all available tracked indices lower
- mixed
- direction unavailable

It does **not** predict future movement.

It explicitly says the interpretation is:

**not a forecast or trading signal**

This is a factual market-context layer, not an investment recommendation.

---

# 22. IMPORTANT BUG FIX — INTELLIGENCE REFRESH

Home intelligence originally used:

`remember(currentStocks, news)`

The index data is loaded asynchronously.

It was changed to include:

`marketIndices`

so the intelligence snapshot recalculates when index data arrives.

This prevents the Home intelligence engine from remaining based on the initial empty index list.

---

# 23. PHASE 10 — CURRENT TEST COVERAGE

`HomeIntelligenceEngineTest.kt` currently covers:

### Gainers / losers

- strict separation
- no overlap
- positive versus negative movement

### Breadth

- advancing
- declining
- unchanged
- evidence record

### Sector calculations

- explicit average
- member count
- evidence record
- evidence relationships

### News

- evidence source
- URL
- date
- no invented financial conclusion

### Index evidence

- index becomes an evidence record
- MyStocks Africa source retained
- observation date retained

### Market index pulse

- observed lower direction
- no-forecast language
- evidence count
- relationship with market breadth

Do not claim these tests passed in CI unless the relevant GitHub Actions run has actually been checked.

---

# 24. RECENT IMPORTANT COMMITS

Most recent development commits at the time this handoff was created:

- `27ebece8e7cc3a6f262832ce39e18cb2ba13d0f9`
  - Avoid unverified delay claim for NSE indices
- `e54df00e6ea7c9b01bd648fb9a6589640502cf62`
  - Test NSE index evidence provenance
- `c41489277a61814293b6602b1427c68224a2d0a9`
  - Wire NSE index observations into Home intelligence
- `cc24dfadb1289a4d592e90edf5e6c46021437a5e`
  - Integrate NSE index observations into Home evidence graph
- `0ee7046dd4375805d62a77f18d7217fb1c7625bf`
  - Clarify NSE index data provenance
- `6e9780434fe352d8c26f5ac85381efb9eb0d11b3`
  - Add compact NSE index context to Home
- `36da3186d6533bbc72dd67f7e9bca7fc67fc394c`
  - Parse NSE index market context
- `c0f142b7a35be38f80ac25dad8ddb272784b571c`
  - Add verified NSE index market context endpoint

Later Phase 10 commits in the current branch:

- `1b06a433a67943bf8030802dbbe922fe69c8ae29`
  - Add evidence-grounded market index pulse
- `c3ecccde3564c8be6083aa4837035ce4c4fabcd8`
  - Refresh Home intelligence when index data arrives
- `ca1980b534f9a0e8fcd4f9e305c949c3eb2b8d30`
  - Test market index pulse interpretation

---

# 25. ANDROID CI

Current workflow:

`.github/workflows/android.yml`

There is one Android CI workflow file.

It runs on:

- workflow dispatch
- push to main
- pull request to main

Environment:

- Java 17 Temurin
- Android platform 35
- Build tools 35.0.0
- Gradle 8.14.4

Build command:

`gradle --no-daemon build`

It uploads:

`NSE-Watcher-debug-apk`

Important:

Multiple entries in GitHub Actions are normally **workflow runs**, not multiple workflow files.

Do not create another Android workflow unless there is a specific architectural reason.

---

# 26. KNOWN CI HISTORY

There were several temporary failures during incremental implementation.

Important examples:

- Home news relevance fixture regression
  - fixed
  - subsequent CI passed
- Home provenance nullable evidence-date compile issue
  - fixed
- Several Phase 9 runs failed while provenance wiring was being completed
  - fixed
  - Phase 9 ultimately completed
- Index work introduced new CI runs that must be verified before claiming the latest Phase 10 changes are green

The correct practice for a new chat is:

1. inspect the latest commit
2. inspect the workflow run associated with it
3. if failed, inspect job logs
4. fix only the relevant issue
5. verify a subsequent run

---

# 27. PHASE 10 — NEXT UNFINISHED STEP

The next planned step is:

## Market Context Freshness

Home must clearly distinguish:

### Current-session data

Examples:

- stock movement feed
- current gainers/losers
- current breadth
- current sector calculations

### End-of-day / stale index observations

Examples:

- an index observation whose `asOf` is from a previous trading session

The app must never make an older observation look like a live/current-session value.

### Required behavior

If an index observation has a real timestamp/date:

- preserve it
- calculate its freshness relative to the current time/session
- label it appropriately

Possible neutral labels:

- Current observation
- Delayed
- End of day
- Previous session
- As of 17 Sep 2026

Only use a label when the underlying timestamp/data supports it.

If freshness cannot be determined reliably:

- show the provider timestamp/date
- do not claim "live"
- do not invent a delay

This should be implemented in the existing data/evidence path, not by adding another data architecture.

---

# 28. PHASE 10 — IMPORTANT FRESHNESS CAUTIONS

Do not assume:

**MyStocks stock delay = index delay**

The stock feed is documented as delayed, but index data may be end-of-day depending on what the provider returns.

Therefore:

- stock delay metadata should remain separate
- index freshness should be derived from actual `asOf` / provider metadata
- do not display "15 min delayed" on an index unless verified

Also consider Nairobi/East Africa time when formatting timestamps for the user.

---

# 29. PHASE 10 — PROVIDER VERIFICATION

A future continuation should verify whether the deployed backend actually receives:

- NASI
- NSE 20
- NSE 25

from the provider.

If provider response is empty:

**that is not a failure requiring fake fallback data.**

Instead:

- keep the UI omitted
- surface unavailable state only where useful
- investigate the supported provider endpoint/symbol format
- do not inject values from articles or public pages into the application feed

The public provider pages confirm index symbols exist, but the Partner API documentation did not clearly document a dedicated index endpoint. This remains an implementation risk to verify.

---

# 30. HOME ARCHITECTURE CURRENTLY

Current intended architecture:

```
                    REAL PROVIDER DATA
                           |
             +-------------+-------------+
             |                           |
        Stock feed                    News feed
             |                           |
             +-------------+-------------+
                           |
                  HomeIntelligenceEngine
                           |
          +----------------+----------------+
          |                |                |
     Calculations      Intelligence      Evidence
          |                |                |
          +----------------+----------------+
                           |
                     EvidenceGraph
                           |
                      HomeDashboard
                           |
        +------------------+------------------+
        |                  |                  |
       Home          What Changed?      Market Context
```

Index data should continue through this same architecture.

---

# 31. EXISTING HOME FEATURES THAT MUST BE PRESERVED

Do not accidentally remove or replace:

- Home hero
- current green branding
- real stock feed
- Gainers
- Losers
- Market Movers
- breadth
- Sector Pulse
- Corporate Actions
- Important News
- What Changed?
- Today's Intelligence
- Quick Actions
- company navigation
- news navigation
- market navigation
- evidence/provenance display
- truthful portfolio disconnected state

---

# 32. PORTFOLIO / WATCHLIST STATUS

There is no verified active Home portfolio source.

Therefore:

- do not show fake account values
- do not show KSh 0 as if it were the user's real portfolio
- do not invent holdings
- do not invent watchlist/followed companies

The Home portfolio card was moved toward an honest:

**Portfolio not connected**

state.

There is a `WatchlistStore` / `MarketRepository` foundation in the repository, but it should not be assumed to represent genuine user-owned watchlist state until the actual data flow is traced.

Portfolio/watchlist work belongs to **Phase 11**.

---

# 33. OLD FABRICATED DATA

`IntelligenceData.kt` was deleted because it contained fabricated/demo data including:

- fake NASI
- fake NSE20
- fake NSE25
- sample financial metrics
- placeholder corporate events

Search found no remaining references.

Do not recreate this file or introduce equivalent demo values into another file.

---

# 34. EXTERNAL FEATURE AUDIT / FUTURE IDEAS

An external feature prompt was audited against the actual repository.

The following classifications should be remembered as roadmap guidance, not reasons to code immediately.

### Already done

1. Company current-state snapshot — already implemented

### Viable / future

2. News sentiment aggregation — partial, potentially high value, methodology needed
3. Macro/political context — viable later
4. Evidence-derived status — partial; avoid arbitrary numerical score
5. Alerts on status change — depends on status model
6. Side-by-side company comparison — not implemented, viable
7. Local context translation — viable later
8. Beginner/explainer mode — high-value future feature
11. Evidence-grounded Q&A — backend partially exists
13. Dividend calendar — partial; reliability metric needs methodology
14. Evidence-backed ranked lists — partial; Gainers/Losers already exist
16. Company story mode — viable
17. Historical scenario calculator — conditional on historical data completeness
19. Noise-vs-signal news filter — first deterministic version already exists
22. Shareable evidence-based viability report — viable later

### Hold / skip for now

9. Diaspora support — skip for now
10. Community sentiment — skip for now
12. Sheng/Swahili toggle — skip for now
15. Smart onboarding — skip for now
18. Gamification — skip for now
20. Portfolio intelligence tracker — wait for genuine user data
21. Multi-broker/CDS — requires real integrations
23. Offline/USSD — hold

Do not let these future ideas derail Phase 10.

---

# 35. DEVELOPMENT STYLE FOR FUTURE CHATS

When continuing this project:

## First

Audit before changing.

Inspect:

- relevant current files
- recent commits
- workflows
- API/data flow
- existing models
- existing caches
- evidence architecture
- current UI

## Then

Classify the requested feature:

- FULLY IMPLEMENTED
- PARTIALLY IMPLEMENTED
- NOT IMPLEMENTED
- IMPLEMENTED BUT INCORRECT/MISLEADING
- IMPLEMENTED BETTER THAN ORIGINAL

## Then

Implement the smallest safe change.

## Then

Run/verify tests and CI.

## Finally

Report:

### A. Architecture before
### B. Architecture after
### C. Existing features preserved
### D. Features now powered by HomeIntelligenceEngine
### E. Features still genuinely missing
### F. Features that exist visually but are not yet backed by real data/evidence
### G. Files changed
### H. Workflows/API/backend touched, if any
### I. Build/test result
### J. Remaining architectural problems

Only include sections A-J when a full implementation report is requested.

---

# 36. DO NOT REPEAT PAST MISTAKES

### Mistake: assuming a feature is missing without searching

Movement intelligence was initially difficult to locate but is actually implemented and integrated.

### Mistake: rebuilding Gainers/Losers

Gainers AND Losers already exist. Preserve them.

### Mistake: hardcoded fallback market data

Never add it.

### Mistake: calling provider data "official"

MyStocks Africa is the provider in this architecture. Do not call it the official NSE source unless the underlying evidence supports that exact claim.

### Mistake: assuming all provider data has the same delay

Stock delay and index freshness must be treated separately.

### Mistake: using a second intelligence architecture

Everything should continue through `HomeIntelligenceEngine` and the evidence graph.

### Mistake: fake AI

AI can only explain evidence that actually exists.

### Mistake: changing unrelated screens

Keep changes scoped to the current phase.

---

# 37. CURRENT ONE-SENTENCE STATUS

**NSE Watcher has completed Unified Evidence Graph and Complete Home Provenance, and is currently in Phase 10 building real NSE market/index context; index observations are now connected to HomeIntelligenceEngine and EvidenceGraph, and the next unfinished task is freshness-aware labeling that distinguishes current-session observations from end-of-day/stale index data.**

---

# 38. CONTINUATION INSTRUCTION FOR THE NEXT CHAT

When a new coding chat starts, tell the assistant:

> Read `PROJECT_CONTEXT.md` before making any code changes. This file is the persistent handoff for NSE Watcher. Do not restart completed phases, do not recreate deleted fabricated data, and do not redesign existing architecture. First verify the current repository and latest CI state against this document, then continue from the exact unfinished Phase 10 step.



# 39. PHASE 10 — FRESHNESS IMPLEMENTATION AUDIT / CORRECTION

## Status

**IMPLEMENTED AND CORRECTED**

The repository audit found that the previous handover had already added a first freshness implementation in commits `0f11fe8c4916668b6d9d93c38eb7c49a9ceaaf93`, `db6f4942418c47032ffd4d6964583790548daa63`, and `f11d5d2612c8683b2b36dae3cc4ff00653d3d4e3`. Those freshness commits passed Android CI.

Before continuing, the audit found two gaps: freshness was calculated in Compose instead of being preserved through the provider data → intelligence → evidence path, and the Home intelligence `remember(...)` key did not include market-open state.

This correction now:

- calculates index freshness in the existing `MyStocksCache` path from provider `asOf` and Nairobi time;
- loads market status before classifying index observations;
- preserves freshness through `MarketIndex → HomeMarketIndex → EvidenceRecord → Home UI`;
- records the freshness mode in evidence provenance;
- keeps stock-feed delay separate from index freshness;
- converts exact current-session timestamps to East Africa Time in the UI;
- uses only neutral labels supported by the available information.

Same-day timestamped observations while the market is closed remain **UNKNOWN / As of** unless the provider gives enough metadata to establish end-of-day status. No freshness claim is invented.

### Verification at audit start

- Repository: `KEdev-jimmy/NSE-Watcher`
- Latest handover commit before this correction: `87ffedd658f17bb631eb2a9d997657619db27c6a`
- Latest verified Android CI run before this correction: run 452, successful
- Freshness test run 451 was also successful
- The deployed `/api/market?action=indices` endpoint could not be independently fetched from this environment, so live provider receipt of NASI/N20/N25 remains to be verified separately.

### Next step

Verify the correction with a new Android CI run. Then verify the deployed index endpoint returns actual NASI/N20/N25 observations. If provider data is empty, keep the UI omitted and investigate the supported provider endpoint/symbol format; never add fallback values.


## 39.1 POST-CHANGE CI STATUS

After the freshness correction, GitHub Actions run **457** was created for commit `943e9b70bdc115e98fb6cd9865f9c73a4dc7a64a`. At the latest audit check it was **in progress**, so the correction is **not yet declared CI-green**. No further code changes are being made until that verification completes.


# 40. PHASE 11 — WATCHLIST FOUNDATION AUDIT / SAFE START

## Status

**STARTED — STORAGE FOUNDATION CLEANED**

The Phase 11 audit confirmed that the repository contained a WatchlistStore / MarketRepository foundation, but also contained an unused DemoMarketRepository with hardcoded sample prices, movements, historical series, demo news, and a pre-populated watchlist. Repository tree/code inspection found no active references to DemoMarketRepository, so it was not safe to use it as the basis for genuine personal intelligence.

The safe first step was therefore to remove the demo repository implementation and make the retained watchlist storage genuinely user-owned:

- WatchlistStore now starts with an empty list.
- No NSE symbols are pre-populated.
- A symbol is stored only after an explicit add() call.
- Added symbols are normalized by trimming and uppercasing.
- Blank symbols are ignored.
- Removal uses the same normalization.
- The existing DataStore persistence mechanism is preserved.
- No Home UI was wired to this storage yet.
- No portfolio/holding values were added or inferred.

This keeps the Phase 11 boundary honest: a watchlist is a user's explicitly selected list of companies, not an inferred portfolio and not a demo list.

## Commit

- e33f2aa04ef2b964339dc6da99d32954728d8c37
  - Phase 11: make watchlist storage empty by default

## Verification

- Latest Android CI verification before this change: run 459, successful, for commit 5b0605d194fe6bd880a6e7d6c2c08a8386098bf7.
- The Phase 11 storage change itself now requires a new Android CI run before it is declared verified.
- Repository inspection found no code-search references to DemoMarketRepository; the implementation was therefore treated as unused demo architecture rather than active functionality.

## Next step

Build the first genuine user-facing watchlist flow using the existing real stock feed:

1. explicitly add/remove a real company from the user's watchlist;
2. persist only those user selections;
3. display current provider-backed data for selected symbols;
4. keep watchlist separate from portfolio/ownership;
5. keep all resulting market observations inside the existing evidence/intelligence architecture.

Do not add default symbols, fake holdings, broker/CDS integration, or portfolio value calculations.


# 41. PHASE 11 — FIRST GENUINE WATCHLIST ACTION

## Status

**IMPLEMENTED — REQUIRES CI VERIFICATION**

The first user-facing watchlist flow is now connected to the existing company intelligence screen.

### Behavior

- The company intelligence header now exposes an explicit Watch / Watching action.
- The action reads the persisted WatchlistStore state.
- Tapping Watch adds only that selected company's normalized symbol.
- Tapping Watching removes it.
- The watchlist remains separate from portfolio/ownership.
- No default companies are added.
- Existing company intelligence data, history, news, evidence and navigation remain in the existing architecture.
- No new market-data provider or intelligence architecture was introduced.

### Files changed

- app/src/main/java/ke/co/nsewatcher/CompanyIntelligence.kt
  - added optional watchlist state/action to the existing company header.
- app/src/main/java/ke/co/nsewatcher/DesignActivity.kt
  - connects the company screen to WatchlistStore.
- app/src/main/java/ke/co/nsewatcher/data/MarketRepository.kt
  - retained the empty-by-default persistent watchlist foundation.
- PROJECT_CONTEXT.md
  - recorded Phase 11 progress.

### Important correction

The initial implementation attempt briefly wrapped CompanyIntelligence in another LazyColumn. That was corrected immediately after auditing CompanyIntelligence.kt because CompanyIntelligence already owns its LazyColumn. The final implementation keeps a single company-page scroll container.

### Verification

Latest completed CI before the watchlist UI changes was run 459 and successful. The current Phase 11 commits require a new Android CI verification before being declared green.

### Next step

After CI passes, audit the Companies screen for the cleanest place to expose watchlist state and then add a dedicated Watchlist view only if the existing navigation structure can support it without duplicating data architecture. The view must read the user's persisted symbols and hydrate them from the existing real stock feed; an empty watchlist must remain a valid state.


# 42. PHASE 11 — CI COMPILATION FIX

## Status

**FIXED — REQUIRES NEW CI VERIFICATION**

Android CI runs 464 and 465 both failed during Kotlin compilation because `DesignActivity.kt` contained two imports of the same `WatchlistStore` class, making the import ambiguous.

### Fix

Removed the duplicate `ke.co.nsewatcher.data.WatchlistStore` import from `app/src/main/java/ke/co/nsewatcher/DesignActivity.kt`.

### Verification

- CI run 464: failed at Kotlin compilation due to duplicate `WatchlistStore` import.
- CI run 465: failed at Kotlin compilation due to the same duplicate import.
- Fix commit: `53e03450f4e94760be87c2066014d8417c07a83e`
- A new CI run is required before declaring the build green.

### Next step

Wait for the new Android CI run and inspect the actual result. If green, continue the Phase 11 watchlist UI audit. Do not add further watchlist functionality until the build is verified.

# 43. PHASE 11 — DEDICATED WATCHLIST VIEW

## Status

**IMPLEMENTED — REQUIRES CI VERIFICATION**

The genuine watchlist flow now has a dedicated view connected to the existing navigation and real stock feed.

### Behavior

- The Companies screen now exposes a compact Watchlist action.
- A dedicated Watchlist page reads persisted symbols from the existing WatchlistStore.
- Only explicitly watched symbols are displayed; there are no default/demo companies.
- Selected symbols are hydrated from the existing in-memory real stock feed loaded by MyStocksCache.
- Each available watched company shows its provider-backed current price and daily change.
- Tapping a watched company opens the existing Company Intelligence page.
- Users can remove a company directly from the Watchlist page.
- If a persisted symbol is temporarily missing from the current provider feed, it is shown honestly as unavailable instead of receiving a placeholder price.
- An empty watchlist has an explicit empty state explaining how to add a company.
- Watchlist remains separate from portfolio/ownership.
- No new market-data provider or parallel intelligence architecture was introduced.

### Files changed

- app/src/main/java/ke/co/nsewatcher/DesignActivity.kt
  - added Page.WATCHLIST navigation
  - added dedicated Watchlist composable
  - added Companies-screen Watchlist entry point
  - wired existing WatchlistStore and existing real stock feed

### Verification

- Implementation commit: 729fff704dd9b7cd892017c29c7ce18681e43b5d
- Android CI verification is required for this change before declaring it green.
- No provider fallback/demo market values were added.

### Next step

Run and inspect the new Android CI result. If green, perform a focused Phase 11 audit of the watchlist/company interaction and evidence integration before moving to Phase 12. Do not add portfolio/holdings assumptions or AI explanations.

# 44. PHASE 11 — WATCHLIST AUDIT HARDENING

## Status

**IMPLEMENTED — REQUIRES CI VERIFICATION**

Focused audit after the dedicated Watchlist view passed CI found two small integration improvements worth making before closing Phase 11:

- The Watchlist page now refreshes the existing MyStocksCache stock feed when the page is opened, so it does not depend only on the app-level initial load.
- The Watchlist page explicitly identifies its price/daily-change provenance as MyStocks Africa and warns that market data may be delayed and material announcements should be verified with the issuer or NSE.

No new provider, duplicate data architecture, fabricated values, or portfolio assumptions were introduced.

Implementation commit: ee9d839824b601f6812a2dbd2486bf763733a570b

Next step: verify the Android CI result. If green, Phase 11 can undergo final closure review; otherwise inspect the actual CI failure before further changes.

# 45. PHASE 11 — FINAL CLOSURE AUDIT

## Status

**COMPLETE**

Phase 11 has passed its final closure review. The user confirmed the latest Android CI for the watchlist hardening change completed successfully.

### Closure checklist

- Watchlist storage is user-owned and empty by default.
- Companies enter the watchlist only through an explicit Watch action.
- Watchlist add/remove state is persisted through the existing DataStore-backed WatchlistStore.
- Company Intelligence exposes Watch / Watching state using the same persisted store.
- A dedicated Watchlist page is reachable from Companies through existing navigation.
- Watchlist entries are hydrated from the existing MyStocks real stock feed; no demo repository or fallback market values are used.
- Available entries show provider-backed price and daily change.
- Missing provider data is represented as unavailable rather than fabricated.
- Users can open the existing Company Intelligence page from a watched company.
- Users can remove a watched company from the Watchlist page.
- Empty watchlist behavior is explicit and honest.
- Watchlist is kept separate from portfolio/holdings; no ownership or broker/CDS data is inferred.
- Watchlist market-data provenance and delay caveat are visible.
- Existing company evidence/intelligence architecture remains the source for the detailed company view.
- No parallel market-data or intelligence architecture was introduced.
- Android CI for the latest Phase 11 hardening was confirmed successful by the user.
- The repository's existing Home intelligence tests continue to cover evidence graph, index observations, freshness provenance, gainers/losers, sector calculations and evidence-grounded news behavior.

### Known limitation carried forward

There is not yet a dedicated unit-test suite for the Android DataStore-backed WatchlistStore/UI flow. The flow has been structurally audited and CI-verified, but device-level interaction testing remains a future QA improvement rather than a reason to block Phase 11 closure.

### Phase 11 result

The app now has a genuine, user-controlled watchlist foundation and user-facing flow without pretending that watchlist membership equals ownership or inventing market data.

### Next phase

**Phase 12 — Evidence Quality & Data Confidence**

Focus on the quality, completeness, freshness and transparency of evidence across the app. Preserve the existing principle: raw data → calculation → explanation → evidence. Do not jump to AI-generated conclusions until the evidence/data-quality layer is sufficiently trustworthy.


# 46. PHASE 12 — EVIDENCE QUALITY & DATA CONFIDENCE AUDIT

## Status

**AUDIT COMPLETE — NO CODE CHANGES YET**

Phase 12 began with a repository audit of the existing evidence, market-data, company-intelligence, news, movement-intelligence and test layers. The goal was to identify where the app can currently distinguish a real observation from a calculation, how freshness is represented, and where missing/partial data can be mistaken for a valid value.

### What is already strong

- EvidenceGraph is shared across Home intelligence and validates duplicate records and invalid relationships.
- Home calculations explicitly identify their source and calculation nature.
- Market-index observations carry provider timestamps where supplied and preserve a freshness mode.
- Company intelligence exposes provider status, partial state, provider errors, evidence count and availability flags.
- Company financial history is explicitly sourced to StockAnalysis / S&P Global Market Intelligence rather than presented as issuer data.
- Movement intelligence uses dated events and labels relationships as related, possible or not-established; it explicitly states that timing does not prove causation.
- News items preserve source, URL, publication date, symbol/company metadata, verification classification and deterministic relevance reasoning.
- Existing tests cover evidence graph structure, Home calculations, index provenance/freshness and evidence-grounded news behavior.

### Findings requiring Phase 12 attention

**1. HIGH — Stock-feed fallback can lose provenance.**
MyStocksCache.loadStocks() falls back from the Vercel stocks response to the repository data/mystocks/stocks.json when the backend response is empty. The resulting Stock model does not carry source, observation timestamp, freshness mode or whether the record came from fallback. Downstream Home evidence currently labels valid stock observations as MyStocks Africa, so a fallback observation could be represented as if it were provider-live data. This must be fixed before stronger confidence labels are introduced.

**2. HIGH — Missing stock change currently becomes 0.0.**
The stock parser chooses supplied change, supplied changePct, or a derived change; if none is available it currently assigns 0.0. A missing movement should remain unavailable, not become an unchanged observation. This can affect gainers/losers/breadth and any intelligence built from the change field.

**3. MEDIUM — Stock observations lack first-class freshness/provenance in the Android model.**
Stock currently carries price/change/history but not provider source, observed-at timestamp, freshness mode, or quality issues. Home evidence reconstructs source as MyStocks Africa instead of carrying observation provenance from ingestion. Phase 12 should move provenance through the data model rather than infer it at presentation time.

**4. MEDIUM — Volume availability is not distinguished from zero volume.**
The stock parser defaults missing volume to 0L. A true zero-volume observation and an unavailable volume are therefore indistinguishable. Home breadth sums non-negative volume, so a missing value can silently participate as zero. Phase 12 should represent volume availability explicitly.

**5. MEDIUM — News date validation is permissive.**
News normalization filters malformed dates in some paths, but withinWindow() allows an item through when its publication date is missing or cannot be parsed. Such records can then enter evidence with an empty/unknown date. This is acceptable only if the UI clearly labels freshness as unknown; it should not be treated as fresh evidence.

**6. MEDIUM — Company data has availability flags but no normalized field-level quality model.**
Company intelligence reports profile/dividend/history/news/valuation availability and provider status, but it does not yet consistently express field-level freshness, period type, conflict status, completeness, or whether a value is provider-reported versus calculated. Phase 12 should strengthen this without replacing the existing architecture.

**7. MEDIUM — Cache age is not separated from provider observation age.**
Company intelligence uses cache-control with s-maxage=600 and stale-while-revalidate=1800, while responses expose fetchedAt. That timestamp represents response generation, not necessarily the age of the underlying provider observation or the age at which a client received cached content. The app should not use fetchedAt alone as proof that the market observation itself is fresh.

**8. MEDIUM — External fundamentals need clearer field-level attribution.**
The company intelligence layer correctly identifies StockAnalysis / S&P Global Market Intelligence as the fundamentals source, but merged profile fields can combine MyStocks and external values. The evidence layer should make the source of each material field explicit and avoid presenting a merged value as if one source supplied the entire profile.

**9. LOW — Dedicated Watchlist interaction tests are still absent.**
Phase 11 closure recorded that there are no dedicated unit tests for the DataStore-backed WatchlistStore/UI interaction. This remains a QA improvement for later, but it is not a blocker for the evidence-quality work.

### Audit conclusion

The repository already has a strong evidence-graph foundation, but the ingestion layer is not yet strict enough for a trustworthy confidence system. The most important Phase 12 work is therefore provenance and missing-data correctness first, followed by freshness/completeness/conflict representation. No AI expansion should be used to mask these gaps.

### Safe implementation order

1. Fix missing-vs-zero semantics for stock movement and volume.
2. Carry source, observed-at and freshness/provenance through stock ingestion, including explicit fallback identification.
3. Make evidence records consume actual ingestion provenance rather than assuming MyStocks Africa.
4. Strengthen news freshness/unknown-date handling.
5. Add field-level company data quality/source attribution and conflict handling.
6. Add focused tests for the new quality rules.
7. Run Android/backend CI verification before closing each meaningful change.

No Phase 12 production-data claims are made by this audit. Live provider receipt remains a separate verification task when the environment permits it.


# 47. PHASE 12 — STEP 1 COMPLETE: MISSING-DATA SEMANTICS

Step 1 was implemented without changing the public numeric shape of Stock, avoiding a broad nullable refactor.

### Changes

- Stock now carries changeAvailable and volumeAvailable.
- Existing Stock constructors default these flags to true, preserving existing test/UI construction semantics.
- MyStocks stock ingestion now distinguishes a real supplied/derived movement from a missing movement, and a real non-negative volume from unavailable volume.
- Missing movement no longer semantically means unchanged: the numeric field remains 0.0 only as a compatibility placeholder while changeAvailable=false marks it unavailable.
- Missing volume similarly retains 0L only as a compatibility placeholder while volumeAvailable=false marks it unavailable.
- Home intelligence excludes stocks without available movement from gainers, losers, unchanged count, sector calculations and market breadth.
- Reported market volume only sums stocks whose volume is explicitly available.
- Stock evidence is not created when movement data is unavailable, preventing an unavailable movement from entering the Evidence Graph as a valid market observation.
- Added a regression test proving an unavailable stock is excluded from movers, breadth and evidence, while available zero-volume data remains usable.

### Verification state

Code and test changes are committed, but Phase 12 Step 1 is not yet considered verified complete until Android CI is inspected for the latest commit sequence. No production-data claim is made here.

### Next step

After CI verification, continue Phase 12 Step 2: carry actual stock provenance (source, observed-at/freshness and explicit fallback identity) through ingestion into evidence, rather than reconstructing MyStocks Africa at the Home evidence adapter.


# 48. PHASE 12 — STEP 2: STOCK PROVENANCE THROUGH INGESTION

## Status

**IMPLEMENTED — PENDING CI VERIFICATION**

Step 2 carries stock provenance from ingestion into the existing `Stock` model and evidence graph without creating a parallel data architecture.

### Implemented

- `Stock` now preserves `source`, `observedAt`, `freshnessMode`, and `dataOrigin`.
- `MyStocksCache.loadStocks()` distinguishes the Vercel/MyStocks-backed `backend` path from the checked-in `fallback` catalogue.
- Backend records use a provider/source field when present, otherwise identify the source as `MyStocks Africa`.
- Fallback records are explicitly identified as `NSE Watcher fallback catalogue`, so they cannot silently appear as live MyStocks observations.
- Provider observation time is read from `lastPriceUpdate`, then `asOf` when available.
- Freshness is conservative: older observations are `STALE`; same-day observations remain `UNKNOWN` until a verified market-session state is combined with them; missing/unparseable times are `UNKNOWN`.
- `EvidenceAdapters.fromStock()` now consumes the stock's actual source, observation time and freshness instead of always reconstructing `MyStocks Africa`.
- Step 1 missing-change and missing-volume semantics remain intact.

### Important limitation

This step does not infer `CURRENT_SESSION` or `END_OF_DAY` for stock observations because stock loading is not yet joined to verified market-status state. No provider delay is invented.

### Verification

Android CI still needs to be checked before Step 2 is marked fully verified.

### Next safe step

After CI verification, continue with Step 3: make Home evidence and related calculations consistently consume the carried stock provenance, then strengthen news freshness handling.