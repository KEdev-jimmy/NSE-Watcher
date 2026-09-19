# NSE Watcher — Project State & Continuation Log

> **Purpose:** This is the handoff/state file for continuing NSE Watcher development across separate ChatGPT conversations.
>
> **Important:** This is intentionally **not** the repository README. It is an internal project-continuity document. Update it after meaningful implementation/audit steps so a new coding chat can understand exactly where the project stands without re-auditing or accidentally undoing correct work.

**Repository:** `KEdev-jimmy/NSE-Watcher`  
**Product:** NSE Watcher Android app  
**Primary goal:** Evidence-grounded NSE market intelligence for beginner investors.  
**Current roadmap phase:** **Phase 10 — Real Market & Index Context**  
**Latest verified implementation area:** Market freshness/session awareness and shared refresh state.  
**Latest known commit:** `a525237d8b5cb20141157cf796c1861c34349ada` — Fix market freshness nullable refresh timestamp.

---

## 1. Core product direction

NSE Watcher is being developed as an **intelligence-first** NSE application, not merely a stock-price display.

The core architecture/principle is:

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

The product must distinguish:
- raw provider data
- deterministic calculations
- factual observations
- news
- evidence/provenance
- interpretation

### Non-negotiable data rules

Never fabricate:
- stock prices
- financial results
- revenue/profit/EPS
- dividends
- corporate actions
- index values
- market volume
- news
- evidence
- source URLs
- dates
- portfolio holdings/value
- watchlist/followed companies
- AI conclusions
- confidence scores
- causal explanations

If data is unavailable, show an honest unavailable/empty state rather than inventing a fallback.

Do not create BUY/SELL/HOLD recommendations or predictive market claims.

Do not use arbitrary numerical AI scores as a substitute for evidence.

Do not call calculated sector averages official NSE sector indices.

Do not invent NASI/NSE20/NSE25 values.

---

# 2. Visual/product rules

Existing Home visual identity should be preserved unless a change is specifically requested.

Current Home palette:
- HomeGreen: `#00A859`
- HomeLightGreen: `#E9F8F0`
- HomeDarkGreen: `#063D2A`
- HomeTextDark: `#12352A`
- HomeMuted: `#64756D`
- HomeBorder: `#DDE9E3`
- HomeRed: `#E94A4A`

Avoid:
- giant rounded cards
- oversized icons
- excessive whitespace
- redundant sections
- fluffy marketing text

Prefer compact, mobile-readable intelligence.

---

# 3. Home architecture before intelligence work

The active Home screen is:

`app/src/main/java/ke/co/nsewatcher/HomeDashboard.kt`

It is routed from `DesignActivity`.

The active Home contains:
1. Nairobi skyline / NSE Watcher hero
2. truthful portfolio state
3. market breadth
4. Today's Intelligence
5. What Changed?
6. Market Movers
7. Sector Pulse
8. Corporate Actions
9. Important News
10. Quick Actions
11. market-data/provenance messaging

The old hardcoded/demo Home implementation was eventually removed as dead code.

Historical technical debt:
- A generic/demo `WatchlistStore` / `MarketRepository` existed but was not proven to represent genuine user-owned watchlist state.
- Therefore Home must not invent followed companies/watchlist content.
- Portfolio must remain disconnected until a genuine user-owned source is available.

---

# 4. Phase history

## Phase 1 — Initial product/foundation audit

**Status: completed**

Focus:
- understand current Android project
- identify active navigation
- identify real data sources
- distinguish active implementation from old/demo code
- avoid rebuilding working functionality

Important finding:
- `HomeDashboard.kt` is the active Home.
- Old hardcoded Home/demo values were not to be reused.
- Existing MyStocks Africa market feed and news feed were already valuable foundations.

---

## Phase 2 — Home intelligence architecture

**Status: completed**

Created/used structured Home intelligence instead of calculating everything directly inside Compose.

Main file:
- `app/src/main/java/ke/co/nsewatcher/HomeIntelligence.kt`

Structured models include:
- `HomeMarketBreadth`
- `HomeSectorPulse`
- `HomeIntelligenceType`
- `HomeEvidenceReference`
- `HomeIntelligenceItem`
- `HomeChangeItem`
- `HomeIntelligenceSnapshot`
- `HomeIntelligenceEngine`

The engine handles:
- valid stock filtering
- gainers
- losers
- breadth
- sector averages
- corporate actions
- company news
- Today's Intelligence
- What Changed?

Important:
**Gainers AND Losers already existed and were preserved.**

Home now consumes one structured intelligence snapshot rather than duplicating calculations in Compose.

---

## Phase 3 — Remove fabricated/demo intelligence

**Status: completed**

Deleted:
- `app/src/main/java/ke/co/nsewatcher/IntelligenceData.kt`

Reason:
It contained fabricated/demo market values including:
- fake NASI/NSE20/NSE25
- sample financial metrics
- placeholder corporate events

No references remained.

This was critical because the app must never present demo financial data as real NSE information.

---

## Phase 4 — Evidence model foundation

**Status: completed**

Created evidence-domain structures and adapters.

Important commits included:
- `079e469e56aa49b764e4cdf16b9ae666caa0bc9e`
- `0befe46ca498b06cd3e993863a428de31c961b04`

Evidence now supports:
- source
- source URL
- observation/publication date
- symbol/entity
- category/type
- claim/value context

Tests were added for evidence behavior.

---

## Phase 5 — Company evidence integration

**Status: completed**

Company intelligence now carries normalized evidence.

Relevant infrastructure:
- `CompanyIntelligenceCache`
- `CompanyIntelligenceEngine`
- company intelligence backend
- evidence adapters/models

Company screen already had real intelligence for:
- current price
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
- news
- evidence

Evidence UI was added to make provenance visible.

The wording was refined from ambiguous **Confidence** to **Evidence coverage**.

---

## Phase 6 — Movement intelligence + evidence

**Status: completed**

There is an existing substantial movement-intelligence system.

Backend:
- `backend/lib/movementIntelligence.js`
- `backend/api/moving.js`

Android:
- `MovementIntelligenceCache.kt`
- `WhyStockMoving.kt`

The backend uses:
- current quote
- daily candles
- company news
- dividend history
- movement periods such as 1D / 1W / 1M / 3M
- dated evidence

Relationships can be:
- `related`
- `possible`
- `not-established`

The system explicitly avoids treating correlation as proof of causation.

Company page includes `WhyStockMovingSection`.

Important correction:
Movement intelligence **does exist and is integrated**. It must not be accidentally rebuilt.

---

## Phase 7 — News relevance / signal filtering

**Status: completed**

Created:
- `backend/lib/newsRelevance.js`

Added deterministic:
- `intelligenceRelevance`
- `intelligenceRelevanceReason`

The first implementation is keyword/category based.

Purpose:
Separate market-relevant information from general noise before it enters Home intelligence.

`NewsItem` now contains:
- `intelligenceRelevance`
- `intelligenceRelevanceReason`

Home company news requires:
- company identity
- `intelligenceRelevance == "market"`

Company-specific news can still remain available on company pages.

A regression occurred in CI because test fixtures defaulted relevance to `unknown`; it was fixed by explicitly setting:
- `intelligenceRelevance = "market"`
- `intelligenceRelevanceReason = "test market event"`

Fix commit:
- `8dc5fba...`

Android CI subsequently passed.

---

## Phase 8 — Unified Evidence Graph

**Status: completed**

Added `EvidenceGraph`.

Purpose:
Represent explicit relationships between evidence without inventing causality.

Tests cover:
- duplicate record removal
- invalid relationship removal
- self-relationship removal
- explicit relationship filtering

Company intelligence exposes an evidence graph.

Movement intelligence also feeds its evidence/relationships into the graph.

Home intelligence creates relationships for:
- market breadth → individual market evidence
- sector calculation → sector member evidence

Architecture became:

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
```

---

## Phase 9 — Complete Home Provenance

**Status: completed**

Goal:
Make Home intelligence traceable to evidence.

Home What Changed items now have provenance for:
- market breadth
- strongest sector
- weakest sector
- largest gainer
- largest loser
- latest company news

Home intelligence cards can display:
- evidence source
- date
- source link where available

News source URLs are clickable when valid HTTP/HTTPS URLs are present.

Important implementation detail:
Evidence dates are nullable and are only displayed when actually available.

Relevant commits included:
- `1c41fffdd689de99d0867d2057ba188032bf6def`
- `18a00d8b9b8351599d993d60ec2f1937509ae72d`

CI failures during this work were investigated/fixed.

Phase 9 closed after Android CI run #437 passed.

Final Phase 9 architecture:

```
Real market/news data
        ↓
HomeIntelligenceEngine
        ↓
Calculations + Intelligence
        ↓
Evidence Graph
        ↓
Home Intelligence cards
        ↓
Source + date + evidence link
```

---

# 5. External feature audit / future roadmap

A separate feature prompt was audited before implementation.

Important classifications:

1. Company current-state snapshot — already done.
2. News sentiment aggregation — partial; viable later; methodology needed.
3. Macro/political context — viable later; must be neutral/evidence based.
4. Composite score/status — partial; avoid arbitrary numerical scores.
5. Alerts on status change — infrastructure partial; depends on status model.
6. Side-by-side company comparison — not implemented; viable later.
7. Local context translation — viable later.
8. Beginner/explainer mode — viable later.
9. Diaspora support — skip for now.
10. Community sentiment — skip for now.
11. Evidence-grounded Q&A — backend partially exists; later.
12. Sheng/Swahili toggle — skip for now.
13. Dividend reliability score + payout calendar — partial; reliability methodology/data needed.
14. Ranked lists — partial; Gainers/Losers already exist.
15. Smart onboarding — skip for now.
16. Company story mode — viable later.
17. Historical scenario calculator — conditional on historical data completeness.
18. Gamification — skip for now.
19. Noise-vs-signal news filter — first deterministic version already implemented.
20. Portfolio intelligence tracker — partial/legacy foundation; genuine user source still needed.
21. Multi-broker/CDS — hold until real integrations.
22. One-tap shareable viability report — viable later.
23. Offline/USSD — hold.

Do not implement these merely because they exist in the list. Use the roadmap and audit first.

---

# 6. Phase 10 — Real Market & Index Context

**Status: in progress**

Phase 10 objective:

Make Home understand the **market itself**, not only individual stocks.

Roadmap:

```
Phase 8  → Unified Evidence Graph                 ✅
Phase 9  → Complete Home Provenance               ✅
Phase 10 → Real Market & Index Context            ← CURRENT
Phase 11 → Genuine Personal Portfolio/Watchlist
Phase 12 → Evidence Quality & Data Confidence
Phase 13 → AI Explanation Layer
```

---

## Phase 10A — NSE index context

**Status: implemented**

Initially investigated whether NASI/NSE20/NSE25 could be obtained through the existing MyStocks market feed without inventing values.

Public MyStocks pages confirmed index symbols:
- `^NASI`
- `^N20I`
- `^N25I`

Backend index endpoint was added:

`/api/market?action=indices`

It requests the provider's market quote surface for:
- `^NASI`
- `^N20I`
- `^N25I`

The endpoint normalizes flexible provider response shapes and only keeps valid records.

Android added:
`MyStocksCache.MarketIndex`

Fields:
- symbol
- name
- value
- changePct
- asOf
- later: freshnessMode

Home added compact:
**Market Index Pulse**

It shows available:
- NASI
- NSE 20
- NSE 25

If the provider does not return an index, the app omits it rather than inventing a value.

---

## Phase 10B — Index provenance

**Status: implemented**

Index observations were integrated into the existing Home evidence graph.

Evidence IDs use:

`index:<symbol>`

Example:
`index:^nasi`

Each record contains:
- actual observed value
- actual change when available
- MyStocks Africa source
- provider observation timestamp/date when available
- current observation period

Index evidence was tested.

Important provenance correction:
The backend originally returned a hardcoded `delayMinutes: 15` for indices.

That was removed because the 15-minute provider delay was not sufficiently verified for the index observations themselves.

Commit:
- `27ebece8e7cc3a6f262832ce39e18cb2ba13d0f9`

Home wording became:

**“NSE index data • MyStocks Africa • provider timestamp when available”**

This avoids calling MyStocks the official NSE index source.

---

## Phase 10C — Market-level index interpretation

**Status: implemented**

HomeIntelligenceEngine can now generate a neutral Market Index Pulse.

It can state:
- available tracked indices are higher
- available tracked indices are lower
- available tracked indices are mixed
- index direction unavailable

The wording describes observations only.

It explicitly says it is:
- not a forecast
- not a trading signal

Index observations are connected to the market-breadth evidence graph.

Tests verify the observed-direction logic.

---

# 7. Phase 10D — Market freshness / session awareness

**Status: implemented and currently the latest completed Phase 10 layer**

This work was done after the initial index-context implementation.

Goal:
Do not present stale/end-of-day observations as though they are live.

The app now distinguishes market data freshness using concepts such as:

- `CURRENT_SESSION`
- `END_OF_DAY`
- `UNKNOWN`

Index freshness is derived from observation time/date and market-session state instead of simply calling everything live.

Relevant recent commits:

- `fdcf12e7ec226ce787d6f596deaf07043f0223d6`
  - Make Home market freshness provenance glanceable

- `bc0dac4d53111f44020dd1ab5636d6b249b148da`
  - Make Market freshness provenance glanceable

- `c93deb8bd13a44938793f4f32d384f710e08376b`
  - Show market refresh countdown on Home

- `68f23985b1613db064e154854bf8c48f2b9e646e`
  - Add shared market refresh state

- `3e6f8758679cd7f309cd410aafd2c4e35ab7dc71`
  - Track market refresh success and failure

- `a49258c033644147c5453129167c76d31c2ea7d3`
  - Import shared market refresh controller

- `43c67a1ac5036e7362c160844f1a3ecb435e0ff3`
  - Make market freshness aware of closed sessions

- `a525237d8b5cb20141157cf796c1861c34349ada`
  - Fix market freshness nullable refresh timestamp

---

# 8. Shared market refresh controller

File:

`app/src/main/java/ke/co/nsewatcher/MarketRefreshController.kt`

Current core behavior:

- refresh interval: 15 minutes
- tracks last successful refresh
- tracks refresh in progress
- tracks last refresh failure
- calculates seconds until next check
- formats countdown

Conceptually:

```
MarketRefreshController
        ↓
shared refresh state
        ↓
Home / market freshness UI
```

The controller is intentionally shared rather than duplicating refresh timers on different screens.

Current refresh interval:

`15 * 60 * 1000L`

Important:
This is a **refresh/check interval**, not proof that provider data itself is exactly 15 minutes delayed.

---

# 9. Market freshness behavior

Home now has a freshness strip that can distinguish data state.

The current Home code checks available stock data and market status and can present states such as:

- Current session
- End of day
- Unknown

The system also accounts for **closed market sessions**.

Therefore:
- after market close, current-session wording should not imply live movement
- end-of-day observations can be labeled as end-of-day
- unknown timestamps should not be presented as live
- failed refreshes should be visible through refresh state rather than silently pretending the feed updated

---

# 10. Auto-refresh

Auto-refresh was connected to the existing market data feed.

Important commits:
- `cce2b72fae9ccdd7a2ca2851ef00cecf0164d629`
  - Connect auto refresh to market data feed

- `06e725165df0688242bdbe622f5bb02d8cc15497`
  - Fix auto refresh declaration order

The app now records:
- refresh started
- refresh succeeded
- refresh failed

A countdown is displayed on Home.

A duplicate Home freshness strip was removed:

- `0f7970ffc32f8862bfaad42d638d20c83d73f026`

---

# 11. Companies / Market freshness work

Freshness/provenance was also made glanceable on Market and Companies.

Relevant commit:
- `48cd7f4ec3c94dc0f6f9d63c4bf7d2b2c1226256`
  - Add Companies data coverage and freshness strip

Market provenance:
- `bc0dac4d53111f44020dd1ab5636d6b249b148da`

Home provenance:
- `fdcf12e7ec226ce787d6f596deaf07043f0223d6`

These should be preserved and not duplicated unnecessarily.

---

# 12. Existing real data sources

## Market data

Primary market feed:
**MyStocks Africa**

Existing backend:
`backend/api/market.js`

Existing Android cache:
`app/src/main/java/ke/co/nsewatcher/data/MyStocksCache.kt`

The market feed includes concepts such as:
- stocks
- movers
- chart/history
- market snapshot/status
- market refresh state

The provider's stock market feed is described as approximately 15-minute delayed, but this must not automatically be applied to index observations unless returned/verified for those observations.

---

## News

Existing backend:
`/api/news?action=feed`

Android:
`NewsCache`

News can contain:
- source
- publication time
- company
- symbol
- category
- URL
- dividend amount
- ex-date
- payment date
- intelligence relevance
- relevance reason

---

## Company intelligence

Backend:
- company intelligence backend
- financial history
- dividends
- market data
- evidence

Android:
- `CompanyIntelligenceCache`
- `CompanyIntelligenceEngine`
- `CompanyIntelligence.kt`

---

## Movement intelligence

Backend:
- `backend/lib/movementIntelligence.js`
- `backend/api/moving.js`

Android:
- `MovementIntelligenceCache.kt`
- `WhyStockMoving.kt`

---

# 13. Important Home implementation details

Current active Home intelligence calculation is:

`HomeIntelligenceEngine.build(currentStocks, news, marketIndices)`

The `marketIndices` argument was added so index data participates in the same intelligence architecture.

Home uses:
- `intelligence.breadth`
- `intelligence.gainers`
- `intelligence.losers`
- `intelligence.sectors`
- `intelligence.corporateActions`
- `intelligence.companyNews`
- `intelligence.marketIndices`

The engine is recalculated with:

`remember(currentStocks, news, marketIndices)`

This was important because market indices load asynchronously. Without `marketIndices` as a remember key, the first empty index state could remain cached after the real index data arrived.

---

# 14. Gainers and Losers — DO NOT REBUILD

Gainers and losers are already implemented.

Current behavior:
- valid positive changes → gainers
- valid negative changes → losers
- sorted appropriately
- no overlap
- zero-change stocks → unchanged

Existing Home section:
**Market Movers**

It calls the existing:
`MarketMovers(gainers, losers, openCompany)`

Do not remove or replace this merely because a future “ranked lists” feature exists.

---

# 15. Sector Pulse — important limitation

Sector Pulse is currently a calculation from available stock counters.

It calculates:

```
sector average =
sum(counter daily changes) / number of available counters
```

It is explicitly described as:

**“an average of the available counters, not an official NSE sector index.”**

Do not rename it into an official NSE sector index.

---

# 16. Portfolio state

Home does **not** currently have a genuine connected user portfolio source.

The old hardcoded portfolio values were removed.

Current truthful state:
**Portfolio not connected**

Do not:
- display fake KSh 0 as real portfolio value
- invent holdings
- infer holdings
- reuse old paper-portfolio numbers

Phase 11 is intended for genuine personal portfolio/watchlist integration.

---

# 17. Watchlist state

Do not assume the existing generic `WatchlistStore` / `MarketRepository` represents genuine user-owned watchlist data.

A real personal intelligence section should only be added after tracing:
- where the user's actual selections are stored
- persistence
- ownership
- loading
- mutation
- source of truth

---

# 18. Company price chart improvement

The company chart now retains actual historical timestamps.

Updated model:

```
data class HistoryPoint(
    val close: Double,
    val date: String = ""
)
```

The chart labels adapt to selected period:

- 1D → time
- 1W → trading dates
- 1M → spaced dates/weekly progression
- 3M / 6M → months
- 1Y → selected points across year
- 3Y / 5Y → years

The labels come from actual returned candle dates.

Do not hardcode labels such as July/August/September.

The existing period selector remains:
- 1D
- 1W
- 1M
- 3M
- 6M
- 1Y
- 3Y
- 5Y

---

# 19. Android CI workflow

Workflow:

`.github/workflows/android.yml`

Current workflow:
- checkout
- Java 17 / Temurin
- Android SDK
- Android platform 35
- build-tools 35.0.0
- Gradle 8.14.4
- `gradle --no-daemon build`
- upload debug APK

Permissions:
`contents: read`

Do not modify secrets.

Do not claim a CI run passed unless it has actually been checked.

---

# 20. Important historical CI issues

Several Android CI failures were caused by specific regressions and were fixed.

Examples:
- Home test fixture relevance defaulting to unknown
- nullable evidence-date handling
- history model change compilation
- Home provenance wiring
- auto-refresh declaration order
- nullable refresh timestamp

The correct approach is always:
1. inspect failed run
2. identify exact failure
3. make smallest surgical fix
4. rerun/check CI
5. preserve existing functionality

---

# 21. Dead-code cleanup

The old hardcoded Home implementation was removed.

Commit:
- `f5b4a1a945e464ce57e80be73c707241dc838e35`
  - Remove dead hardcoded Home screen

This is important because older copies of Home/demo values must not be accidentally reactivated.

---

# 22. Unsupported index-provider discovery

An important Phase 10 lesson:

Public MyStocks pages confirm that NASI/NSE20/NSE25 exist as provider symbols, but the Partner API documentation did not clearly document a dedicated index endpoint.

Therefore:
- do not invent an index API
- do not use historical article values as current values
- do not create static fallbacks
- use the supported provider market-data path only when it actually returns the requested observations
- if provider returns nothing, omit index context

A previous implementation was changed after discovering that unsupported index symbols should not be requested blindly.

Relevant commit:
- `b16e5801cf9bef1a1c1d158b90467cd193a768c6`
  - Stop requesting unsupported NSE index symbols

This must be checked before making further index-feed changes.

---

# 23. Current architectural state

Current high-level architecture:

```
                  MyStocks Africa
                  /            \
             Market             News
               ↓                 ↓
        MyStocksCache         NewsCache
               ↓                 ↓
               └──────┬──────────┘
                      ↓
              HomeIntelligenceEngine
                      ↓
       ┌──────────────┼──────────────┐
       ↓              ↓              ↓
   Calculations    Intelligence    Evidence
       ↓              ↓              ↓
       └──────────────┼──────────────┘
                      ↓
                 EvidenceGraph
                      ↓
                 Home UI
```

Company and movement intelligence also use the evidence architecture.

The goal is **one coherent intelligence/evidence architecture**, not separate competing systems.

---

# 24. What is NOT implemented yet

Still genuinely missing or intentionally deferred:

### Personal intelligence
- real connected portfolio
- real connected watchlist
- broker/CDS integrations

### Advanced intelligence
- rigorous news sentiment aggregation
- transparent company comparison
- beginner/explainer mode
- evidence-grounded conversational Q&A
- company story mode
- historical scenario calculator
- shareable viability report

### Alerts
Infrastructure exists for refresh state, but meaningful status-change alerts should wait for a clearly defined evidence/status model.

### Macro context
Potential future feature, but must use current verified sources and neutral presentation.

### AI explanation
Not yet the main intelligence engine.

The evidence/calculation architecture must remain the foundation before adding AI.

---

# 25. Current Phase 10 next steps

After the current freshness/session work, the remaining Phase 10 work should be audited before implementation.

Recommended sequence:

1. **Verify freshness behavior against actual provider timestamps**
   - current session vs end-of-day
   - closed market behavior
   - unknown timestamp behavior

2. **Verify index endpoint behavior**
   - whether provider actually returns NASI/NSE20/NSE25
   - do not assume availability
   - do not add fallback values

3. **Make freshness/provenance consistent**
   - Home
   - Market
   - Companies
   - index pulse

4. **Ensure What Changed does not describe stale index data as current**
   - use observation/session state
   - omit or label stale observations appropriately

5. **Only then consider Phase 10 completion**
   - with actual CI/build verification

---

# 26. Current known recent commits

Newest sequence at the time this file was created:

- `a525237d8b5cb20141157cf796c1861c34349ada` — Fix market freshness nullable refresh timestamp
- `43c67a1ac5036e7362c160844f1a3ecb435e0ff3` — Make market freshness aware of closed sessions
- `a49258c033644147c5453129167c76d31c2ea7d3` — Import shared market refresh controller
- `3e6f8758679cd7f309cd410aafd2c4e35ab7dc71` — Track market refresh success and failure
- `c93deb8bd13a44938793f4f32d384f710e08376b` — Show market refresh countdown on Home
- `68f23985b1613db064e154854bf8c48f2b9e646e` — Add shared market refresh state
- `06e725165df0688242bdbe622f5bb02d8cc15497` — Fix auto refresh declaration order
- `cce2b72fae9ccdd7a2ca2851ef00cecf0164d629` — Connect auto refresh to market data feed
- `0f7970ffc32f8862bfaad42d638d20c83d73f026` — Remove duplicate Home freshness strip
- `48cd7f4ec3c94dc0f6f9d63c4bf7d2b2c1226256` — Add Companies data coverage and freshness strip
- `bc0dac4d53111f44020dd1ab5636d6b249b148da` — Make Market freshness provenance glanceable
- `fdcf12e7ec226ce787d6f596deaf07043f0223d6` — Make Home market freshness provenance glanceable
- `27ebece8e7cc3a6f262832ce39e18cb2ba13d0f9` — Avoid unverified delay claim for NSE indices
- `e54df00e6ea7c9b01bd648fb9a658964050cf62` — Test NSE index evidence provenance
- `c41489277a61814293b6602b1427c68224a2d0a` — Wire NSE index observations into Home intelligence
- `cc24dfadb1289a4d592e90edf5e6c46021437a5e` — Integrate NSE index observations into Home evidence graph
- `0ee7046dd4375805d62a77f18d7217fb1c7625bf` — Clarify NSE index data provenance
- `6e9780434fe352d8c26f5ac85381efb9eb0d11b3` — Add compact NSE index context to Home
- `36da3186d6533bbc72dd67f7e9bca7fc67fc394c` — Parse NSE index market context
- `c0f142b7a35be38f80ac25dad8ddb272784b571c` — Add verified NSE index market context endpoint

---

# 27. Handoff instructions for the next ChatGPT/Codex chat

Before changing code:

1. Read this file completely.
2. Treat it as the current project-state/handoff document.
3. Inspect the current repository state and latest commits.
4. Do not assume this file is newer than the code; verify latest commits if there is any conflict.
5. Preserve completed phases.
6. Do not restart Phase 1 audits unless a specific conflict requires it.
7. Continue from the current Phase 10 checkpoint.
8. Do not undo freshness/session-aware behavior.
9. Do not reintroduce fake index values or hardcoded portfolio values.
10. Do not rebuild Gainers/Losers.
11. Do not create a second intelligence architecture.
12. Do not add AI explanations until the evidence/calculation foundation is ready.
13. Make surgical changes and run/check Android CI before claiming success.
14. After every meaningful implementation step, update this file with:
   - what was audited
   - what was changed
   - why it was changed
   - files touched
   - tests/CI result
   - current phase status
   - next logical step
   - any known limitations or risks

---

# 28. Standard continuation feedback format

Use this compact structure in the chat after meaningful changes:

### Proceeding to Phase X — [layer]

**What changed**
- ...

**Architecture now**
```
...
```

**What is preserved**
- ...

**What remains**
- ...

**Verification**
- CI/build/test status: ...

**Next logical step**
- ...

Then update this project-state file so the next conversation can continue from the same point.

---

# 29. Final safety rule for this project

The application is an **evidence-grounded intelligence tool**, not an oracle.

When the data does not support a conclusion:

**Do not fill the gap.**

Say that the evidence is unavailable, stale, incomplete, or not established.

That principle takes priority over making the UI look complete.

# 30. Session-aware chart and "today" semantics audit — 19 Sep 2026

**Status: implementation added; CI/runtime verification pending**

## Audit before implementation

The current repository was re-audited before changing code.

Existing pieces confirmed:
- `MyStocksCache.loadMarketStatus()` already consumes the backend `/api/market?action=status` endpoint.
- The backend already returns provider market status plus `nextOpen` / `nextClose` when supplied.
- `Africa/Nairobi` is already used for timestamp interpretation.
- `MarketRefreshController` already provides shared refresh state and was preserved rather than duplicated.
- `HistoryResult` already carries `sessionOpen`, `sessionClose`, `sessionChangePct`, `sessionOpenAt`, `sessionCloseAt`, and `observedAt`.
- The interactive chart already uses actual candle timestamps for its X-axis.
- The chart period selector already supports 1D / 1W / 1M / 3M / 6M / 1Y / 3Y / 5Y.
- The current backend 1D chart path was injecting a synthetic previous-close candle. That made the chart mix a previous-session baseline with actual intraday observations and could make the first X-axis time misleading.
- The current Android 1D change calculation used the first chart point as a baseline. That was coupled to the synthetic previous-close candle and would become incorrect if the chart became observation-only.
- The installed screenshot showed a "Today at a glance" concept, but that section was not present in the current default-branch `CompanyIntelligence.kt`. This was treated as a repository/UI mismatch rather than assuming unseen code existed.

## Implementation

Combined into one surgical commit:
- Removed the synthetic previous-close candle from the backend 1D chart response. Previous close remains a session baseline where available, but is not presented as an intraday observation.
- Changed Android stock refresh logic so the chart no longer derives the stock's daily change from its first 1D chart point. The stock quote feed remains the source of the daily change.
- Added session-aware company-screen state using the existing market-status endpoint; status is refreshed while the company screen is open.
- Made 1D selected-period return use the backend's `sessionChangePct` when available.
- Added a truthful "Today's at a glance" / "Last trading session at a glance" section using actual session open/close/observation timestamps.
- Changed the company header from ambiguous "% today" wording to "% this session" when open and "% last session" when closed.
- Improved 1W labels to include weekday + date rather than weekday alone.
- Prevented first/last X-axis labels from being clipped at the chart edges.
- Preserved all existing periods, pinch zoom, horizontal pan, evidence rules, and the shared refresh controller.
- No 2-hour movement metric was added yet; it will only be added after verifying that the provider returns sufficient timestamped 1D observations and that the metric can be calculated without estimation.

## Files changed
- `backend/api/market.js`
- `app/src/main/java/ke/co/nsewatcher/data/MyStocksCache.kt`
- `app/src/main/java/ke/co/nsewatcher/CompanyIntelligence.kt`
- `PROJECT_STATE.md`

## Verification
- Static repository audit completed before implementation.
- Android CI for the combined change has **not yet been verified**.
- The prior chart-crash fix commit remains separate and should be checked together with this implementation.
- Do not treat this change as production-safe until Android CI/build and manual period testing pass.

## Next logical step
1. Check CI for the combined commit.
2. If CI passes, install/test 1D → 1W → 1M → 3M → 6M → 1Y → 3Y → 5Y.
3. Verify closed-session wording on a non-trading day and live-session wording during NSE trading.
4. Verify actual 1D candle timestamps and ensure no provider retrieval timestamp is being plotted as a price observation.
5. Only after those checks, consider a sourced "last 2 hours" movement line.

# 31. Market-status unknown-state hardening — 19 Sep 2026

A follow-up audit found that the existing `MarketStatus()` default treated a failed/unavailable status request as `CLOSED`. That could incorrectly tell the user the market was closed.

The status model now distinguishes:
- known OPEN
- known CLOSED
- UNKNOWN / unavailable

The company screen no longer infers "last session" from a failed status request.

The backend 1D cleanup also removed the now-unused previous-close fetch/baseline variables after the chart stopped injecting a synthetic previous-close observation.

Verification remains pending Android CI and runtime testing.
