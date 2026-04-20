# BUG: WarmUpRampSection crashes with UnknownFormatConversionException

## Status
[x] Fixed

## Severity
P0 blocker
- App force-closes when opening any exercise that has a warm-up ramp and a percentageLabel containing `%` (e.g. "50%"). This blocks the entire Exercise Detail Screen for common exercises like Barbell Back Squat.

## Description
`AboutTab.kt:399` constructs a format string by embedding `set.percentageLabel` (e.g. "50%") directly into the format template before calling `.format()`:

```kotlin
Text("%.1f kg (${set.percentageLabel})".format(set.weight), ...)
```

When `percentageLabel` = "50%", the resulting template string becomes `"%.1f kg (50%)"`, and the `%` before `)` is interpreted by `String.format()` as the start of a format specifier — `%)` is not a valid conversion, causing `java.util.UnknownFormatConversionException: Conversion = ')'`.

## Steps to Reproduce
1. Navigate to the **Exercises** tab
2. Open any exercise that has a warm-up ramp — e.g. "Barbell Back Squat"
3. Tap the ABOUT tab (default)
4. Observe: app crashes with `UnknownFormatConversionException` before the warm-up ramp section renders

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt`

## Assets
- Related spec: `future_devs/EXERCISE_DETAIL_TABS_V2_SPEC.md`

## Fix Notes
`AboutTab.kt:399`: Changed `"%.1f kg (${set.percentageLabel})".format(set.weight)` to `"${"%.1f".format(set.weight)} kg (${set.percentageLabel})"`. The original code embedded `percentageLabel` (e.g. "50%") into the format template before calling `.format()`, causing `%` to be interpreted as a format specifier. Fix formats the float first, then interpolates the label string — no format specifiers ever reach the embedding step.
