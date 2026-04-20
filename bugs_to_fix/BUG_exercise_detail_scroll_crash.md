# BUG: Exercise Detail Screen crashes on scroll

## Status
[x] Fixed

## Severity
P1 high
- App crashes (force close) when scrolling far enough down in the Exercise Detail Screen. The screen has a long single-column layout with many sections (header, joints, form cues, records, trends charts, warm-up ramp, alternatives, workout history, notes) and the scroll/rendering overhead triggers the crash.

## Description
After the Exercise Detail Sheet Revision (v1) shipped, the `ExerciseDetailScreen` is a full-screen single-column `LazyColumn` / `Column` containing numerous heavy sections. Scrolling past a certain point causes the app to crash — likely a Compose `LazyColumn` nesting issue (scrollable inside scrollable), excessive recompositions, or an OOM from loading all sections eagerly. The crash appears to be proportional to how much workout history data is loaded.

## Steps to Reproduce
1. Navigate to the **Exercises** tab
2. Tap any exercise that has a moderate-to-large workout history (e.g., Back Squat, Bench Press)
3. Scroll down through all sections (header → joints → form cues → records → trends → warm-up → alternatives → history → notes)
4. Observe: app crashes before reaching the bottom

## Dependencies
- **Depends on:** —
- **Blocks:** Exercise Detail Tabs v2 (the redesign will resolve this structurally)
- **Touches:** `ui/exercises/detail/ExerciseDetailScreen.kt`, `ui/exercises/detail/ExerciseDetailViewModel.kt`

## Assets
- Related spec: `future_devs/EXERCISE_DETAIL_SHEET_REVISION_SPEC.md`

## Fix Notes
**Structural fix via Exercise Detail Tabs v2 redesign.**

Root cause: The single `LazyColumn` composed all 13 sections simultaneously — 5 Vico `CartesianChartHost` instances + `BodyOutlineCanvas` + paginated history list were all in the composition tree at once, causing OOM/crash on scroll.

Fix: Restructured `ExerciseDetailScreen` into a 4-tab layout (`HorizontalPager`) with `beyondViewportPageCount = 0`. Each tab has its own `LazyColumn`. The pager only composes the current page and its immediate neighbor — CHARTS (tab 2) and muscle activation (tab 0) are never composed simultaneously. Also fixed hero animation cropping (`ContentScale.Crop` 16:9 → `ContentScale.Fit` with `heightIn(max = 220.dp)`).

Files changed: `ExerciseDetailScreen.kt` (rewritten as orchestrator), `AboutTab.kt`, `HistoryTab.kt`, `ChartsTab.kt`, `RecordsTab.kt`, `DetailComponents.kt` (all new).
