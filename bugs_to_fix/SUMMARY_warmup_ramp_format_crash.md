# Fix Summary: WarmUpRampSection crashes with UnknownFormatConversionException

## Root Cause
`AboutTab.kt` embedded `set.percentageLabel` (e.g. "50%") directly into a format template string before calling `.format()`:

```kotlin
"%.1f kg (${set.percentageLabel})".format(set.weight)
```

When `percentageLabel` = "50%", the resulting template became `"%.1f kg (50%)"`. The `%` before `)` was interpreted by `String.format()` as the start of a format specifier — `%)` is not a valid conversion, causing `java.util.UnknownFormatConversionException: Conversion = ')'`. This crashed the app on any exercise that has a warm-up ramp with a percentage label.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/detail/AboutTab.kt` | Changed `"%.1f kg (${set.percentageLabel})".format(set.weight)` to `"${"%.1f".format(set.weight)} kg (${set.percentageLabel})"` — formats the float first, then interpolates the label string so no user-supplied `%` characters ever reach `.format()` |

## Surfaces Fixed
- Exercise Detail Screen → ABOUT tab: exercises with warm-up ramp sections (e.g. Barbell Back Squat) no longer crash when `percentageLabel` contains `%`

## How to QA
1. Navigate to the **Exercises** tab
2. Open any exercise that has a warm-up ramp — e.g. "Barbell Back Squat"
3. Tap the **ABOUT** tab
4. Verify: warm-up ramp section renders without a crash, showing e.g. "60.0 kg (50%)"
