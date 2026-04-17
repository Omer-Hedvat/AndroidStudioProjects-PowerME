# BUG: Trends card crashes when opened

## Status
[ ] Open

## Description
The Trends tab crashes when the user navigates to it. This is distinct from the previous scroll-related crash (BUG_trends_scroll_crash) — the crash happens immediately on opening the Trends card, not when scrolling within it. Likely root cause is in `MetricsScreen.kt` or `TrendsViewModel.kt` initialization / data loading.

## Steps to Reproduce
1. Open the app and log in
2. Navigate to the Trends tab (bottom nav)
3. Observe: app crashes immediately or shortly after the screen appears

## Assets
- Related spec: `TRENDS_SPEC.md`, `future_devs/TRENDS_CHARTS_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
