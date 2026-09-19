# NSE Watcher — Project State & Continuation Log

> **Purpose:** This is the handoff/state file for continuing NSE Watcher development across separate ChatGPT conversations.
>
> **Important:** This is intentionally **not** the repository README. It is an internal project-continuity document. Update it after meaningful implementation/audit steps so a new coding chat can understand exactly where the project stands without re-auditing or accidentally undoing correct work.

**Repository:** `KEdev-jimmy/NSE-Watcher`  
**Product:** NSE Watcher Android app  
**Primary goal:** Evidence-grounded NSE market intelligence for beginner investors.  
**Current roadmap phase:** **Phase 10 — Real Market & Index Context**  
**Latest verified implementation area:** Company Intelligence financial evidence provenance + Android CI verification.  
**Latest implementation commit before this state update:** `19459fc4aaa2cb607dd9fea07d7045daed357738` — Align financial growth fixture with provider values.

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

# 32. 1D chart session-filter correction — 19 Sep 2026

A post-implementation diff audit caught an important issue before CI/runtime verification: after removing the synthetic previous-close candle, the 1D backend still needed to explicitly replace the returned candle array with the latest actual trading session. That filter is now retained without synthetic data.

This keeps 1D history:
- actual intraday observations only
- latest Nairobi session only
- no previous-close fake candle
- session open/close metadata preserved

This was corrected before treating the implementation as verified.

# 33. Company latest-session clarity and NOW view — 19 Sep 2026

**Status: implementation added; CI/runtime verification pending**

## User-facing refinement

The previous company header wording used:
- % this session
- % last session
- % latest

This was technically correct but too ambiguous at first glance when the market was closed. The user should be able to identify exactly which observation the displayed change refers to without opening At a glance.

The company header now prefers a compact observation stamp:

    +X.XX% • 18 Sep 26 • 4:45 PM • CLOSE

During an open session the state becomes:

    +X.XX% • 19 Sep 26 • 11:45 AM • LATEST

If no usable timestamp is returned, the numeric change is still shown with the state label but no fabricated timestamp.

The timestamp is derived from the existing history/session evidence in this order:
1. sessionCloseAt
2. lastDate
3. observedAt

It is converted to Africa/Nairobi before display.

## NOW view

Added a new NOW selector alongside the existing:
- 1D
- 1W
- 1M
- 3M
- 6M
- 1Y
- 3Y
- 5Y

Important architecture rule:

**NOW is not a new market-data endpoint and does not mean real-time.**

It is a presentation view of the existing verified 1D intraday session data. It reuses the 1D backend request, so:
- no second market-data architecture is introduced
- no live/tick data is fabricated
- no intermediate observations are invented
- the latest actual returned observation remains the end of the line

The NOW description explicitly says:

    Latest available intraday data • 15 min delayed

The provider documentation states that African equity prices are exchange-supplied on a 15-minute-delayed basis, the API targets a 15-minute refresh cadence, and asOf is the actual observation timestamp. It also states that missing intraday observations are not interpolated.

## Refresh behavior

The existing 15-minute application refresh remains the source of refresh cadence.

A small connection was added so that when the existing auto-refresh obtains a newer stock snapshot:
- liveStocks is updated as before
- an already-selected company is also updated from the refreshed stock list
- Company Intelligence history observes the refreshed price and reloads the selected period
- therefore the graph can move to a newer actual observation after a successful refresh

This does not add a second polling system.

## Files changed

- app/src/main/java/ke/co/nsewatcher/CompanyIntelligence.kt
  - added NOW view
  - added exact observation date/time to the company header
  - changed 1D wording from generic Today to Current trading session
  - changed session return wording from today to session
  - kept timestamp/date semantics in Africa/Nairobi
  - reloads history when the existing refreshed stock price changes

- app/src/main/java/ke/co/nsewatcher/DesignActivity.kt
  - keeps selected company synchronized with the existing 15-minute auto-refresh snapshot

## Evidence / freshness rule

The UI must never call delayed data live or real time.

Provider evidence:
- MyStocks Partner API contract guarantees: exchange-supplied African equity prices are 15-minute delayed; asOf is the actual observation timestamp.
- MyStocks API reference: intraday observations may be sparse and missing observations are not interpolated.

## Verification

- Static code audit: completed before implementation.
- Android CI for the new commits: **not yet checked**.
- Manual runtime testing still required:
  1. closed-session header shows date + time + CLOSE
  2. open-session header shows latest observation + LATEST
  3. NOW loads without a separate endpoint
  4. NOW remains clearly delayed rather than live
  5. 1D → 1W → 1M → 3M → 6M → 1Y → 3Y → 5Y → NOW all remain stable
  6. pinch zoom and horizontal drag remain functional
  7. successful 15-minute refresh updates an already-open company when a newer provider observation exists
  8. no synthetic or interpolated graph points appear

## Next logical step

Check Android CI for the latest commit chain. If CI passes, install the APK and perform the full period/refresh test before adding any further intraday metric such as last 2 hours.

# 34. NOW chart freshness label — 19 Sep 2026

The NOW chart now shows the latest actual observation timestamp directly beneath the period description when available:

    Latest observation • 18 Sep 26 • 4:45 PM EAT • 15 min delayed

If the provider does not return a usable observation timestamp, the UI falls back to:

    Latest available intraday observation • 15 min delayed

This is display-only and uses existing history/session timestamps. It does not estimate the current time as a market observation.

Verification remains pending Android CI and manual APK testing.
# 35. Combine duplicate company session summaries and compact header timestamp — 19 Sep 2026

The screenshot review identified two overlapping sections:
- Last trading session at a glance
- Today at a glance

They were presenting the same session open/close/change/freshness information in two different cards. Keeping both increased vertical length and made the Company Intelligence page feel repetitive.

## Decision

The two sections are now combined into one adaptive section:
- Today's trading session while the market is known to be open
- Trading session at a glance when the market is closed
- Trading session when status is unavailable

The combined card keeps the useful details from the more informative section:
- Open
- Latest/Close
- session change
- exact session date
- latest observation timestamp
- market state / freshness explanation
- next regular session when supplied

The duplicate today-vs-last-session presentation is removed.

## Company header refinement

The previous change/timestamp was displayed inline beside the large price at 13sp. On a phone this competed with the price and could become visually heavy.

The header is now vertically structured:
- large price remains the visual anchor
- compact change/timestamp line is 9sp
- exchange/data explanation remains smaller below it

Example:

    KSh 36.45
    +3.26% • 18 Sep 26 • 6:30 PM EAT • CLOSE

When the market is open, the final state becomes LATEST.

This directly identifies the date/time represented by the percentage without requiring the user to open the session card.

## Product judgment

This change is intentionally conservative:
- it removes duplicated information rather than adding another card
- it preserves the detailed session evidence
- it makes the most important observation timestamp visible near the price
- it avoids calling delayed data live
- it does not change the underlying market-data architecture

## Verification

Android CI/runtime verification is still pending for this latest commit. Manual testing should confirm:
1. only one session-summary section is visible
2. closed market shows completed session date/time
3. open market shows latest observation/date/time
4. compact header fits on narrow phones without crowding
5. no session values are duplicated with conflicting labels

# 36. Cleanup audit: session math, refresh propagation, and quote percentage units — 19 Sep 2026

## Duplicate UI cleanup

The Company Intelligence screen no longer contains separate 'Last trading session at a glance' and 'Today at a glance' cards. They are combined into the adaptive `SessionAtGlance` section. The current source contains one session-summary section for 1D/NOW.

## Math correction

During the cleanup audit, the stock percentage parser was tightened.

The provider contract uses `changePct` for percentage change and documents that `changePct` can be derived from `price` and `previousClose` when omitted. A generic `change` field was previously accepted before `changePct`; that was unsafe because its unit was not explicitly established in the app boundary.

New rule:
1. use explicit provider `changePct` when finite
2. otherwise derive `(price - previousClose) / previousClose * 100` when previousClose is valid
3. otherwise keep the change unavailable rather than inventing 0%

This prevents an absolute price delta from accidentally being displayed as a percentage.

## Session-baseline correction

The combined session card previously fell back from `sessionChangePct` to the stock's daily change. Those two values have different baselines:
- session change = session close/latest minus session open
- daily change = latest price versus previous close

The fallback was removed. If the provider does not return a valid session change, the session card does not relabel a daily change as a session change.

## Refresh propagation correction

The existing 15-minute auto-refresh loop used a captured selected-company value, which could become stale after navigation. The loop now uses `rememberUpdatedState` so it can update the currently selected company without restarting the 15-minute timer.

Company Intelligence now also observes `MarketRefreshController.state.lastSuccessfulRefreshMs`. A successful market refresh therefore reloads the selected history even when the numeric price itself is unchanged. This is important for NOW because a new timestamp can arrive at the same price.

No second price polling loop was introduced.

## Observation timestamp cleanup

The session summary now prefers the actual session close candle timestamp for its 'Latest observation' line, with the provider observation timestamp as fallback. This keeps the displayed time tied to the chart observation whenever that evidence exists.

## Provider freshness basis

MyStocks currently documents African equity prices as exchange-supplied and 15-minute delayed, with a 900-second refresh target and `asOf` representing the actual observation timestamp. Intraday candles may be sparse and missing observations are not interpolated.

## Verification state

Latest implementation commits:
- `6a8f11a8f233ab57cb2280575d42c4fbcbd6c1d1` — percentage-unit math correction
- `875b74178d6524adba29a71017d6d87433f91b62c` — exact session baseline
- `40d890493d3aadc203bfaf5fd8645d077fd594a3` — remove obsolete fallback parameter
- `2b57c3ef19599f0f7461e390d0ac461605778f71` — stable auto-refresh selected-company propagation
- `af93f9f859c136e0df3c7dc0ade4da11d4abf5a1` — reload history on successful refresh

Android CI and APK runtime testing still need to be checked after this cleanup chain. No build/runtime success is assumed until the actual workflow result is available.

## Manual regression checklist

- Company Intelligence has one session-summary section, not two
- closed session shows date/time and CLOSE
- open session shows latest observation and LATEST
- session percentage is never substituted with daily percentage
- NOW reloads after a successful 15-minute refresh even if price is unchanged
- 1D and NOW contain only actual provider observations
- 1W, 1M, 3M, 6M, 1Y, 3Y, 5Y remain available
- pinch zoom and horizontal pan remain intact
- unavailable values remain unavailable rather than becoming 0%
- percentage calculations use percentage units, not absolute price deltas
# 37. Additional UI/data cleanup — 19 Sep 2026

Additional low-risk cleanup completed:
- Removed the pre-filled hardcoded Paper Portfolio balance and SCOM/KCB positions. Paper mode now starts empty rather than presenting fabricated holdings or values.
- Removed the duplicate stock price from each Companies list row; price and daily change remain in the right-side summary.
- Changed Companies discovery copy from recommendation-like wording to neutral research wording: 'Research. Analyze. Understand. Explore sourced NSE company information.'

These changes do not alter the market-data pipeline or calculations.

Android CI/runtime verification remains required after the cleanup chain.
# 38. Cleanup pass: stale chart state and unavailable change handling — 19 Sep 2026

## Company Intelligence cleanup

Removed two subtle stale-data paths:
- When a history request returned fewer than two points, the old chart history could remain in memory. The screen now replaces the history state with the returned list, so an unavailable/insufficient response cannot leave an older chart visible after refresh.
- 1D/NOW percentage fallback previously used the general chart state. It now uses the current `HistoryResult` points only when a session return is not supplied, preventing a stale earlier period from becoming the displayed current-session return.

## Header data-quality cleanup

If the provider does not supply a usable percentage change, the company header no longer formats an invalid/placeholder numeric value as a percentage. It shows `Change unavailable` with the latest evidence timestamp/state instead.

## Verification

Latest code commit: `9f89ccdaa61a85c2d68679ea3755c9f1cf2057ed`.
Vercel status for the code commit should be checked after deployment. Android CI is triggered by pushes to `main`, but the GitHub connector's workflow-run lookup currently returns no run for this commit, so Android build success is not claimed.

## Provider-data basis reviewed

MyStocks' current partner documentation confirms exchange-supplied African equity prices are 15-minute delayed, `asOf` is the actual observation timestamp, intraday candles may be sparse, and missing observations are not interpolated. The app continues to label the feed as delayed and does not create missing observations.

## Remaining low-risk review items

- Keep the static fallback catalogue visibly distinguishable from live backend data through existing freshness/source metadata.
- Do not introduce a faster price polling loop than the provider's documented refresh cadence.
- Do not add an intraday metric such as a 2-hour return until the returned observation density is sufficient to calculate it from actual timestamps.
- Manual APK verification remains the final check for the duplicate-card removal because an APK downloaded before the latest commits can still contain the older UI.

# 39. Closed-market Company Intelligence clarification — 19 Sep 2026

**Status: implementation added; CI/runtime verification pending**

## Audit before implementation

The current main source was re-audited before changing the Company Intelligence screen.

Confirmed already fixed and therefore not rebuilt:
- The two separate "Today at a glance" / "Last trading session at a glance" cards are already combined into one SessionAtGlance section.
- Market-status unknown state already exists and does not default to CLOSED.
- 1D session data already uses actual latest Nairobi-session candles.
- Synthetic previous-close chart observations are already removed.
- Session percentage no longer falls back to the quote's daily percentage.

A genuine remaining UX/data-semantics issue was found:
- On a known closed market, the company header could still display the numeric percentage as though it were current context.
- The Market behaviour card could still show a 1D/NOW session percentage on Saturday/Sunday without making the closed state the primary message.
- The combined session card still emphasized OPEN/CLOSE/session movement rather than the simple closed-market state the user needs on non-trading days.

## Implementation

Commit:
- 7201e4138ec6dadacdcc7fcba286ae7f94a920b4

Changes:
- When the market is known CLOSED, the company header now prioritizes:
  - MARKET CLOSED
  - the actual close observation date/time when available
- The combined session section remains a single section and now becomes a clear Market status card.
- When closed, the card shows:
  - MARKET CLOSED
  - the actual completed-session date
  - the close observation time
  - the actual closing price
  - the next regular session when supplied
- The closed-state card does not present the session percentage as current movement.
- The 1D/NOW Market behaviour summary now shows MARKET CLOSED instead of a current-looking session percentage when the market is known closed.
- No 0.00% was fabricated. Closed is a market state, not a zero movement measurement.
- Open-market behavior remains unchanged: actual latest observation/session information continues to be shown.
- Unknown market status remains explicitly unknown.

## Files changed

- app/src/main/java/ke/co/nsewatcher/CompanyIntelligence.kt
- PROJECT_STATE.md

## Verification

- Static current-code audit: completed before implementation.
- GitHub code update: completed.
- Android CI: not yet verified for this commit.
- APK/manual runtime verification: pending.

## Next priority

Audit the Company Intelligence financial metrics end-to-end before changing their display:
1. trace Revenue/Profit/EPS/ROE/Margin/Debt-Equity/P-E/P-B/Dividend Yield/Market Cap from provider response through normalization to UI;
2. verify units (KSh, millions, billions, etc.) rather than assuming them;
3. verify reporting period (FY/annual/quarterly/TTM/etc.);
4. verify growth metric basis (YoY/QoQ/etc.);
5. only then change labels/formatting.

Do not infer financial units or periods from the displayed number alone.


# 40. Company Intelligence financial-period/unit audit — 19 Sep 2026

**Status: implementation added; CI/runtime verification pending**

## Audit before implementation

The current Company Intelligence implementation was re-audited before changing the financial metrics.

A genuine data-semantics issue was found:
- The backend parser selected the first numeric financial column by position.
- StockAnalysis financial tables can place TTM before FY columns.
- The UI section was labelled "Latest reported annual financial evidence", but the parser could therefore select TTM values instead of the latest FY values.
- For Nairobi Securities Exchange PLC, the current source explicitly shows TTM revenue of KSh 1.76B and TTM net income of KSh 857.54M, while FY 2025 revenue is KSh 1.061B and FY 2025 net income is KSh 272.24M. The source identifies the financial tables as Millions KES and the fiscal year as January–December. This confirms that the old positional parsing could materially change the meaning of the displayed numbers. citeturn4search0turn2search0
- Growth was being recalculated from rounded displayed financial values before using the provider's supplied annual growth. That can create small discrepancies. The parser now prefers the source's growth value and only calculates growth when the source value is absent.
- The UI displayed raw EPS, P/E, P/B, ROE, margin and growth numbers without consistently identifying units or meaning.
- The Growth card displayed "EPS (latest)" rather than EPS growth, despite the backend already calculating EPS growth.

## Implementation

Backend commits:
- 5577b7d02f9e06f50e370b762c4a41eaa117dca9 — select annual FY column and carry financial unit/period metadata
- 797897787c78cbe47e23ac426c9046a33c978a9f — correct FY selector regex
- 989aa02df4ca0e2d3e7f141beb23d5b2a2691dde — prefer provider-supplied annual growth values
- afe3e4a17cbaa6f1dfa952daff201684dbc60e7f — add parser regression test

Android commits:
- f676fe195f060d26fe9f94b38ed0add4d16e3966 — carry financial unit and reporting-period metadata
- 91e51d75fd1461304b2b495240d3e008e4744aa5 — clarify financial metric units/periods
- 74040024af1d4a28bb4b22590869ff26f14d2c13 — refine metric formatting and period labels
- 701dbfea0d505782a52a8331fd6cd313747b5993 — format EPS with its full meaning

## New semantics

The annual financial parser now:
- finds the first column explicitly labelled FY YYYY;
- uses the corresponding Period Ending value;
- excludes a preceding TTM column from the annual Company Intelligence summary;
- identifies the financial values as Millions KES when coming from the StockAnalysis financial source;
- prefers the provider's reported annual growth percentages over recalculating from rounded values.

The UI now makes the displayed meaning explicit:
- Revenue/Profit: KSh with M/B formatting based on the sourced Millions KES unit.
- Financial period: explicit FY and year-end information.
- EPS: KSh X.XX / share and the label includes "Earnings Per Share".
- ROE: explicit percentage.
- Net margin: explicit percentage.
- Revenue growth / Profit growth / EPS growth: explicitly labelled YoY and shown as percentages.
- P/E: shown as a multiple and expanded as Price-to-Earnings.
- P/B: shown as a multiple and expanded as Price-to-Book.
- Dividend yield: explicit percentage.
- Growth now shows EPS growth instead of repeating the latest EPS value.

## Important example

For NASE:NSE, the source currently distinguishes:
- TTM revenue: 1,760 million KES = KSh 1.76B
- FY 2025 revenue: 1,061 million KES = KSh 1.061B
- TTM net income: 857.54 million KES
- FY 2025 net income: 272.24 million KES
- FY 2025 EPS: 1.04
- FY 2025 revenue growth: 30.17%
- FY 2025 EPS growth: 133.91%

The Company Intelligence annual section should use the FY values, not the TTM values. citeturn4search0

## Verification

- Current repository audit: completed before implementation.
- Provider/source semantics checked against current StockAnalysis pages: completed. citeturn4search0turn3search0
- Backend regression test added for TTM-vs-FY selection.
- Android CI/build: not independently verified yet.
- APK/manual UI verification: pending.

## Next logical step

After CI/build verification, continue the same Company Intelligence audit with:
1. verify P/E, P/B, ROE, Debt/Equity and Dividend Yield are sourced with the correct current/annual basis;
2. verify the displayed valuation metrics are not being mixed between current ratios and FY ratios without labels;
3. verify evidence/source presentation clearly distinguishes current market ratios from annual financial results;
4. then audit timestamp/fetch provenance on the financial evidence.

Do not add assumptions for missing units or periods.


## Follow-up correction to Step 40

A post-change static cross-file audit caught one integration issue before verification: the backend already returned EPS growth, but Android's CompanyIntelligenceCache.Profile did not yet carry an epsGrowth field. The UI change therefore needed a matching model/parser field.

Corrected in commit:
- 1fc9a952e3c3cd19fed1c6de05fe1e82c856b8f5

The Android profile model/parser now carries epsGrowth from the backend, completing the backend → Android → UI path.

This was caught by the current-code cross-file audit; no assumption was made that the UI change was complete just because the backend field existed.

Android CI/runtime verification remains pending.


# 41. Company Intelligence ratio-basis audit — 19 Sep 2026

**Status: implementation added; CI/runtime verification pending**

## Audit before implementation

The current main code was re-audited after the annual financial fix. The ratio parser was found to read the first data column of the StockAnalysis ratio tables without explicitly verifying its basis. The current StockAnalysis tables have a dedicated **Current** column followed by FY columns, so the values being parsed were in practice current ratios, but the app did not carry that basis into the UI.

This mattered because the Financial health card was labelled as latest annual financial evidence while including ROE and Debt/Equity values that come from the current ratio snapshot. The Valuation card also used current P/E/P/B/dividend yield/market cap values without explicitly saying they were current rather than FY historical ratios.

The current source distinguishes, for NASE:NSE, for example:
- Current P/E 8.00 vs FY 2025 P/E 19.39
- Current P/B 2.39 vs FY 2025 P/B 2.15
- Current ROE 34.80% vs FY 2025 ROE 12.30%
- Current dividend yield 2.64% vs FY 2025 dividend yield 3.80%
- Current period ending Sep '26 vs FY 2025 period ending Dec '25

These are source distinctions, not app-derived estimates. citeturn0search0turn0search1

## Implementation

Backend:
- parseRatios() now locates the explicit Current column instead of assuming the first numeric column.
- If the source does not expose a recognizable Current column, ratio values are left unavailable rather than guessed from an FY column.
- The response now carries ratioBasis: "Current" and the corresponding ratioPeriod.
- ratioBasis and ratioPeriod are carried through the merged profile.
- Added regression tests for Current-vs-FY selection and the missing-Current safety case.

Android:
- CompanyIntelligenceCache.Profile now carries ratioBasis and ratioPeriod.
- The annual Financial health card now contains only annual financial evidence: Revenue, Profit, EPS and Net margin.
- ROE and Debt/Equity were moved to the current-ratio section so they are no longer presented as annual FY metrics.
- The valuation area is now labelled **Current ratios & valuation** and displays the current ratio period when supplied.
- The UI explicitly states that these are the source's current snapshot, not FY 2025 historical ratio values.

Commits:
- 02cd82a27f8c3205680781e4d0c33f4566e20736 — make company ratios explicitly current-basis
- a2a5e00f53139cf82fae6d5c990bc10550f5d0e2 — carry ratio basis metadata through company intelligence
- c4a4b0d5411f1e11fce9a7ef96d1313f3608bc74 — carry current ratio basis into Android
- 6c5d86a97d8839b246e65744032380cf62503e25 — separate annual financials from current ratios
- 9777c0f255d7e5483012ba6b58fdc7b4e94a2821 — test current-basis company ratios

## Verification

- Current-code audit before implementation: completed.
- Source semantics: checked against current StockAnalysis ratio/statistics pages. citeturn0search0turn0search1
- Backend regression tests: added, but not executed locally in this environment.
- Android CI/build: not independently verified yet.
- APK/manual runtime verification: pending.

## Already fixed and intentionally not changed

- Annual financial parsing already selects explicit FY columns rather than TTM.
- Provider-supplied annual growth values are already preferred.
- EPS growth is already carried through the Android model/parser.
- Closed-market Company Intelligence semantics remain unchanged.
- The single combined session-status card remains unchanged.

## Next logical audit

Audit financial evidence provenance end-to-end: verify the financial/ratio fetchedAt, source URLs, provider update dates, and UI timestamp wording so users can tell when the numbers were sourced and what date they represent. Do not add a timestamp that is merely the app fetch time and present it as the financial statement date.

# 42. CI failure cluster 595–609 audit — 19 Sep 2026

**Status: audited; no code rollback/fix justified**

The reported 595–609 failures were cross-checked against the actual commit SHAs and current repository state.

Available commit-status data shows:
- 595 `afe3e4a...`: Vercel success
- 596 `989aa02...`: Vercel failure
- 597 `f676fe1...`: Vercel success
- 598 `91e51d7...`: Vercel success
- 599 `7404002...`: Vercel success
- 600 `701dbfe...`: Vercel success
- 601 `fcf2af8...`: Vercel failure with Vercel build-rate-limit target
- 602 `1fc9a95...`: Vercel failure with Vercel build-rate-limit target
- 603 `e8dc6a4...`: Vercel failure with Vercel build-rate-limit target
- 604 `02cd82a...`: Vercel failure with Vercel build-rate-limit target
- 605 `a2a5e00...`: Vercel failure with Vercel build-rate-limit target
- 606 `c4a4b0d...`: Vercel failure with Vercel build-rate-limit target
- 607 `6c5d86a...`: Vercel failure with Vercel build-rate-limit target
- 608 `9777c0f...`: Vercel failure with Vercel build-rate-limit target
- 609 `f328ef5...`: Vercel failure with Vercel build-rate-limit target

The current Android workflow was inspected. It runs backend quality tests for company intelligence and market status, then a Gradle build and APK artifact upload.

The financial and ratio changes represented by these commits remain present in current main, including regression tests and the backend-to-Android EPS/ratio metadata paths.

Conclusion:
- Do not revert the financial/ratio implementation merely to clear historical red statuses.
- The 601–609 Vercel failures are consistent with build-rate limiting rather than evidence of nine independent source regressions.
- Historical failed runs cannot be retroactively made successful. The correct resolution is to fix any genuine root cause and obtain a new successful run.
- Individual GitHub Actions job logs for push-triggered runs 595–609 were not exposed by the available workflow-run connector, so their exact job-level error text was not claimed as inspected.
- Future changes should be batched where practical to reduce unnecessary Vercel build-rate pressure.

No source-code change was made in this audit because the available evidence does not justify one.



# 43. Financial evidence provenance audit — 19 Sep 2026

**Status: implementation added; automated CI verification pending**

## Audit before implementation

The current `main` branch was re-audited after the CI failure-cluster review. The annual financial and current-ratio semantics from the previous audits are present.

A remaining provenance gap was confirmed:

- `fetchedAt` was the app/backend request time.
- Evidence source URLs were already present.
- Annual financial `financialPeriod` represented the reported fiscal/period-ending basis.
- Current ratios already carried `ratioBasis` and `ratioPeriod`.
- However, the backend did not preserve StockAnalysis/S&P Global's separate **Last updated** and **Last checked** dates.
- The UI therefore had no way to distinguish provider data update date from the app fetch time.

This distinction matters because a provider update date is not the same thing as the financial statement period, and the app fetch time is not a reporting date.

Current StockAnalysis pages explicitly expose this distinction. For example, its Safaricom financials page identifies S&P Global Market Intelligence as the data source, gives a Last updated date, and separately gives a Last checked date. It also states that financial data updates after earnings releases. citeturn0search0turn0search3

## Implementation

Backend `backend/lib/companyIntelligence.js` now:

- parses the source page's **Last updated** date when present;
- parses the source page's **Last checked** date when present;
- keeps those dates separate from `fetchedAt`;
- carries financial provenance as:
  - `financialProviderUpdatedAt`
  - `financialPageCheckedAt`
- carries ratio provenance as:
  - `ratioProviderUpdatedAt`
  - `ratioPageCheckedAt`
- adds provider update/check metadata to normalized evidence records;
- adds the relevant financial/ration period to external evidence records;
- keeps the existing source URLs unchanged.

Android `CompanyIntelligenceCache.Profile` and evidence models now carry the same provenance metadata.

Company Intelligence UI now:
- shows **Provider data updated** under annual financials;
- shows **Provider data updated** under current ratios;
- optionally shows **Source page checked** when supplied;
- relabels the existing backend `fetchedAt` display from **Fetched** to **App fetch time**.

The UI therefore does not present an app fetch timestamp as a financial reporting date.

Implementation commits:
- `7eefb446c8835cc808a0a6f13d5e5d11854677e8` — backend provenance metadata
- `b670b1b336386decd73483a65fb948007312c9ee` — backend provenance tests
- `36b990ad9dbbf83a0a04ae3d4aba6bbfb7b11de4` — Android provenance model/parser
- `7f6697109b3a24451323dfe01c1c0f888727aa7c` — Company Intelligence provenance wording

## Verification

- Current main branch was re-audited before implementation: completed.
- StockAnalysis source semantics were checked: completed. citeturn0search0turn0search3
- New backend regression tests were added: completed.
- Local test execution could not be performed because the execution environment could not resolve GitHub, so no local test pass is claimed.
- Vercel status for all four implementation commits currently reports the same **build-rate-limit** target, consistent with the previously identified Vercel account/build-rate limitation.
- GitHub Actions push-run job logs are still not exposed through the available workflow-run connector, so Android CI is not claimed as passed.

## Next logical step

Do not revert this provenance implementation because of the Vercel red status.

The next verification target is a new Android CI run after the provenance batch is complete. If that run is unavailable or blocked, continue auditing only where a real code/data-semantic gap is confirmed; do not create repeated commits solely to turn the historical Vercel statuses green.

The financial provenance layer should remain the basis for any later AI explanation work. AI remains intentionally gated until the evidence/data pipeline is stable.


# 44. Cross-chat handoff — 19 Sep 2026

**Purpose:** This section is the authoritative handoff for the next ChatGPT coding conversation. Read this before making any NSE Watcher change.

## Current product

NSE Watcher is an Android NSE intelligence app for beginner investors. It is **not** just a quote viewer. The product goal is evidence-grounded, understandable NSE market intelligence.

Core pipeline:

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

AI explanation is intentionally **gated** until the underlying data, evidence, provenance, freshness and semantics are stable.

## Current implementation state

The app already has substantial working functionality and must not be rebuilt from scratch.

### Home
- Active Home: `app/src/main/java/ke/co/nsewatcher/HomeDashboard.kt`
- Structured intelligence engine: `HomeIntelligence.kt`
- Gainers **and** Losers already exist.
- Market breadth, sector pulse, corporate actions, company news, What Changed?, evidence/provenance and market index context already exist.
- Existing green visual identity is intentional.
- Keep Home compact; avoid oversized icons/cards and unnecessary whitespace.

### Market/session/freshness
- Market status semantics were hardened so missing/unknown/conflicting status remains `UNKNOWN`, not CLOSED.
- Freshness distinguishes current session, end of day and unknown.
- Shared refresh controller exists; 15 minutes is a refresh/check interval, **not** proof that every provider observation is exactly 15 minutes delayed.
- Closed-market Company Intelligence semantics were fixed: known CLOSED must be shown as MARKET CLOSED, not as a fake 0.00% movement or current-looking percentage.
- Synthetic chart/history observations were removed.
- 1D session selection now uses the latest real Nairobi trading session and actual open/close data.
- Longer-period candles are chronologically ordered when timestamps are valid.

### Company Intelligence
The following audits/fixes are already implemented and must be preserved:

1. **Annual financial period/unit semantics**
   - Annual parser explicitly selects the FY YYYY column rather than positional first-column/TTM data.
   - StockAnalysis financial source values are carried with unit/period metadata.
   - Provider-supplied annual growth values are preferred over recalculation from rounded values.
   - Android carries EPS growth and financial metadata.

2. **Current vs FY ratio basis**
   - Ratio parser explicitly locates the provider's Current column.
   - Missing recognizable Current column => unavailable; do not guess from FY.
   - Current ratios carry `ratioBasis` and `ratioPeriod`.
   - Annual Financial health contains annual Revenue/Profit/EPS/Net margin.
   - ROE/Debt-Equity and valuation metrics are presented as current-ratio information with explicit basis.
   - Do not mix current ratios with FY financials without labeling.

3. **Financial evidence provenance**
   - Backend preserves provider **Last updated** and **Last checked** dates separately from app/backend `fetchedAt`.
   - Fields include:
     - `financialProviderUpdatedAt`
     - `financialPageCheckedAt`
     - `ratioProviderUpdatedAt`
     - `ratioPageCheckedAt`
   - Android models/parsers carry the same provenance.
   - UI uses:
     - **Provider data updated**
     - **Source page checked** when available
     - **App fetch time** for the actual app fetch timestamp.
   - Never label app fetch time as the financial statement/reporting date.

## CI status that must be understood

The recent Vercel failures are **not** a reason to undo correct implementation.

Historical runs 601–609 were checked and the Vercel failures consistently showed the same build-rate-limit target. They should be treated as infrastructure/account build-rate limitation evidence, not nine separate source-code regressions.

A real Android CI issue was nevertheless found during this audit:
- Run **616** failed in the backend financial parser regression test because the fixture did not explicitly contain the provider growth rows; the expected 30.17% was being compared with 30.15%.
- This was corrected by commit:
  `19459fc4aaa2cb607dd9fea07d7045daed357738`
  — **Align financial growth fixture with provider values**.
- Android CI **run 617 completed SUCCESSFULLY** on that commit.
- Therefore the backend quality tests and Android build path represented by run 617 passed. Do not reopen the already-fixed 30.15%/30.17% issue unless a new current failure proves it has returned.

## What to do next

Do **not** start another broad redesign.

The next conversation should:

1. Treat `PROJECT_STATE.md` and the actual current `main` branch as the starting point.
2. Re-audit the current code before every implementation step.
3. Verify the financial provenance implementation is actually present end-to-end.
4. Check current Android CI evidence before making any new fix.
5. Continue the remaining hardening audits only where a real gap is demonstrated.
6. After every meaningful implementation, update `PROJECT_STATE.md`.
7. Once the evidence/data pipeline is genuinely stable, close/harden the current phase rather than inventing work.
8. Do not jump to Phase 13 AI merely because the app is feature-rich.

## Non-negotiable development rules

These rules come from the user and must be followed in the next chat:

- **Deep audit first.** Every section requires an end-to-end audit before implementation.
- **Verify against the actual current repository.** Do not assume an old audit note means an issue still exists.
- **Do not make changes just because an issue is listed in PROJECT_STATE.md.** Confirm it still exists in current code.
- **Correctness over speed.**
- If a genuine issue is found, fix it immediately when safe.
- Group related safe fixes when they share one root cause.
- Do not create repeated commits merely to turn CI/Vercel history green.
- Historical failed workflow runs cannot be retroactively fixed; obtain a new verification run after real fixes.
- **Never fabricate data.** If unavailable, show unavailable/unknown/empty state.
- Never invent prices, financials, revenue, profit, EPS, dividends, corporate actions, volume, index values, news, evidence, URLs, dates, timestamps, portfolio holdings, watchlist companies, causal explanations, confidence scores or AI conclusions.
- Never invent NASI/NSE20/NSE25 values.
- Do not present calculated sector averages as official NSE sector indices.
- Do not use BUY/SELL/HOLD recommendations or predictive market claims.
- Do not treat correlation as proof of causation; use explicit evidence relationships such as related/possible/not-established where appropriate.
- Do not infer financial units, reporting periods or growth basis from displayed numbers.
- Preserve source/provenance and make source links/timestamps truthful.
- Distinguish provider reporting/update dates from app fetch time.
- Preserve existing working features instead of rebuilding them.
- Do not invent portfolio/watchlist state from demo or legacy stores.
- Keep the UI compact, useful and beginner-readable.
- Avoid fluffy text, excessive whitespace, giant rounded cards and oversized icons.
- Preserve the existing green branding unless the user specifically requests a visual change.
- Do not add speculative features simply because they appear on an old feature list.
- Keep AI explanation gated until the evidence/data foundation is stable.
- When the user says **continue**, first inspect/re-audit the actual current `main` branch, then proceed.

## Important "already exists — do not rebuild" reminders

- HomeIntelligenceEngine
- Home Gainers and Losers
- EvidenceGraph
- Movement intelligence / Why Stock Moving
- News relevance filtering
- Home provenance
- Market index context
- Shared market refresh state
- Market freshness/session semantics
- Closed-market Company Intelligence semantics
- FY-vs-TTM financial parsing
- Current-vs-FY ratio basis
- Financial provider update/check provenance

The next chat should improve or harden these only if the current code audit proves a real defect.

## User working preference

The user is not asking for unnecessary complexity. He wants the app to become **correct, trustworthy, differentiated and genuinely useful**, not simply larger.

When explaining findings, use simple, direct English:
- what was checked
- what was found
- whether it is a real issue
- what will be changed
- how it will be verified

Do not overwhelm the user with speculative problems.

## Current checkpoint

**Latest verified Android CI:** run 617 — SUCCESS.  
**Latest implementation commit before this state update:** `19459fc4aaa2cb607dd9fea07d7045daed357738`.  
**Current active concern:** finish verification/hardening of Company Intelligence financial provenance and then continue only with real, evidence-backed gaps.  
**AI:** intentionally deferred until the evidence/data pipeline is stable.

This handoff is intended to prevent the next conversation from repeating old audits, undoing correct fixes, mistaking Vercel rate-limit failures for source regressions, or implementing features that already exist.
