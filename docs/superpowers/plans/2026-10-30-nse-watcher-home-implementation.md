# NSE Watcher Home Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the approved NSE Watcher identity and Home redesign using real market and Practice data.

**Architecture:** Reuse the existing Compose Home card functions and navigation callbacks. Add a pure presentation calculation for market snapshot counts, render it in a new card, and revise shared brand/theme assets. CI publishes the already-configured Full and Update APKs after merging.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, Android Gradle Plugin; Node backend tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-30-nse-watcher-home-design.md`

## Global Constraints

- Real verified source data only; unavailable/stale states remain explicit.
- Keep the five destinations unchanged and all existing navigation callbacks working.
- Do not touch signing files or release workflow.
- Home chip uses the three exact status labels in the spec.

## Review Focus

- Unknown/stale quotes must not be counted as advancing or declining.
- Missing observation timestamps and future observations must not inflate market counts.
- No Practice account must yield an honest empty state, not sample money.
- Provisional Practice valuations must not display certified returns.
- Market closed and unknown must have distinct status styling and text.

---

### Task 1: Truthful minute snapshot and status

**Files:** Modify `app/src/main/java/ke/co/nsewatcher/HomeBriefingPresentation.kt`; `app/src/test/java/ke/co/nsewatcher/HomeBriefingPresentationTest.kt`.

**Interfaces:** `HomeMinuteSummary(advancing: Int, declining: Int, watched: Int, verified: Int)`; `HomeBriefingPresentation.minute(stocks: List<Stock>, watched: List<Stock>, now: Instant): HomeMinuteSummary`.

- [ ] Test exact open/closed/unknown text and exclude stale, absent, future or invalid observations.
- [ ] Run focused JUnit test and observe expected red failure.
- [ ] Implement the minimal validated count and status logic.
- [ ] Run focused JUnit and existing test suite before integration.

### Task 2: Brand and shared theme

**Files:** Modify `NseWatcherTheme.kt`, `NseWatcherBrand.kt`, `app/src/main/res/drawable/ic_nse_watcher.xml`, `DesignActivity.kt`.

- [ ] Build the original geometric emerald N with amber data pixel in Compose and vector launcher icon.
- [ ] Apply Midnight Market / Market Daylight colors to shared schemes.
- [ ] Style existing five-tab bottom navigation as a flat, spacious surface with clear selected state.
- [ ] Verify all five callbacks and icon content descriptions remain.

### Task 3: Home visual hierarchy

**Files:** Modify `HomePremiumDashboard.kt`, `HomeBriefingCards.kt`, `HomePremiumComponents.kt`.

- [ ] Promote one current-value Practice hero, retain provisional and empty states, and label any actual balance-history chart.
- [ ] Add real market-minute counts (or unavailable state) and position evidence move above watchlist and news.
- [ ] Remove the duplicate portfolio stat pair from Home; move truthful trading-volume detail below news.
- [ ] Use larger and more consistent touch targets, card spacing, type scale and CTA affordances.
- [ ] Check that all component callbacks lead to their existing destinations.

### Task 4: Verification and distribution

**Files:** No release workflow modifications expected.

- [ ] Run safeguards, backend tests, Android JUnit/Gradle build in CI.
- [ ] Diagnose unrelated baseline test failures separately and preserve data accuracy.
- [ ] Push a branch/PR; wait for an actual green workflow; merge into main only after green.
- [ ] Verify `ci-apk` rolling release contains exactly the signed Full and Update APKs from merged main.
