# BUG: Trends tab crashes when scrolling to VolumeTrendCard

## Status
[x] Fixed

## Description
The Trends tab crashes as soon as the user scrolls down far enough to see the "VOLUME TREND" card.
The crash is likely triggered by `MuscleGroupVolumeCard` (which renders below VolumeTrendCard) being
pre-composed by the LazyColumn before its `CartesianChartModelProducer` has a host attached.

`BUG_chart_crash_on_filter_change` fixed the same race for `VolumeTrendCard` and `E1RMProgressionCard`
by moving producers into `TrendsViewModel`. `MuscleGroupVolumeCard` was added in Trends Step 4 and
also uses a ViewModel-owned producer (`muscleGroupModelProducer`) — but `pushMuscleGroupToProducer()`
is called from `loadAll()` in `init {}`, which runs before any `CartesianChartHost` has attached. This
is the same pre-attachment race condition.

## Steps to Reproduce
1. Sign in and navigate to the Trends tab
2. Scroll down past the Readiness Gauge card toward "VOLUME TREND"
3. App crashes

## Assets
- Related bug: `BUG_chart_crash_on_filter_change.md` (fixed same pattern for Volume + E1RM cards)
- Related summary: `SUMMARY_chart_crash_on_filter_change.md` — root cause analysis is identical
- LazyColumn card order: `MetricsScreen.kt:72–125` (BodyVitals → Readiness → Volume → E1RM → MuscleGroup)
- Producer initialization: `TrendsViewModel.kt:70` — `muscleGroupModelProducer = CartesianChartModelProducer()`
- Transaction call: `TrendsViewModel.kt:236` — `pushMuscleGroupToProducer()` called from `loadAll()` in init
- Card file: `ui/metrics/charts/MuscleGroupVolumeCard.kt`

## Fix Notes

Confirmed root cause (from logcat):

```
java.lang.IllegalStateException: `CartesianValueFormatter.format` returned an empty string.
Use HorizontalAxis.ItemPlacer and VerticalAxis.ItemPlacer, not empty strings, to control which
x and y values are labeled.
  at CartesianValueFormatterKt.formatForAxis (CartesianValueFormatter.kt:86)
  at HorizontalAxis.getMaxLabelWidth (HorizontalAxis.kt:440)
```

Vico 2.x throws `IllegalStateException` if any `CartesianValueFormatter` lambda returns `""`.
All three x-axis formatters in the chart cards used `?: return@CartesianValueFormatter ""` as
a null-safety fallback. This fires on the very first draw pass before any user interaction —
Vico calls `getMaxLabelWidth` during layout measurement, which hits the formatter for every
x-axis tick, including ticks that map beyond the available data index (no null-safe mapping).

**Definitive fix:** Changed all 4 xFormatters to use `coerceIn(list.indices)` so the formatter
always returns a real `"MMM d"` date string. Empty-list guard returns `"–"` (em-dash — never
empty, never blank regardless of Vico's internal check). Applied to:
- `VolumeTrendCard.kt` xFormatter
- `E1RMProgressionCard.kt` xFormatter
- `MuscleGroupVolumeCard.kt` xFormatter
- `EffectiveSetsCard.kt` xFormatter (4th card, discovered on device testing)

Earlier intermediate attempts (`" "` single space) were insufficient because Vico's
`formatForAxis` may use `isBlank()` internally. The `coerceIn` approach eliminates the null
branch entirely — the formatter can never reach a fallback path.

Prior attempts (LazyColumn→Column, suspend→launch) addressed a different hypothesis and are
irrelevant to this crash — but the Column+verticalScroll change in MetricsScreen is benign and
was left in place.
