# Fix Summary: Body Metrics weight/body fat not populated from Health Connect

## Root Cause

Two separate issues combined to produce the empty Weight and Body Fat fields:

**Primary:** `ProfileViewModel.loadPersonalInfo()` reads `user.heightCm` and seeds the Height field via `loadUserHeight()`, but never reads `user.weightKg` or `user.bodyFatPercent`. Weight and body fat relied entirely on the `MetricLog` Flow (`observeMetricLogs()`), which only emits data if HC sync has written entries to the `metric_logs` table in the current session. If the user opens Profile before visiting Settings (where HC sync is triggered), the Flow emits an empty list and the fields stay blank — even though the User entity already has weight/bodyFat from the last sync.

**Secondary:** `observeMetricLogs()` unconditionally set `lastWeight = latestKg` and `lastBodyFat = latest`. When the Flow emits an empty list, `latestKg` and `latest` are `null`, so these fields got overwritten even after being seeded.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/profile/ProfileViewModel.kt` | Added `loadUserWeight(Float?)` and `loadUserBodyFat(Float?)` private methods; call them from `loadPersonalInfo()` after `loadUserHeight()`; changed `observeMetricLogs()` to use `latestKg ?: state.lastWeight` and `latest ?: state.lastBodyFat` to preserve User entity fallback values |
| `app/src/test/java/com/powerme/app/ui/profile/ProfileViewModelPersonalInfoTest.kt` | Added 3 tests: seeds weight from User entity, seeds bodyFat from User entity, both empty when neither source has data |

## Surfaces Fixed

- Profile screen → Body Metrics card: Weight (kg) and Body Fat (%) fields now populate from the User entity on screen open, matching the existing Height behavior

## How to QA

1. Ensure Health Connect is connected and has weight/body fat records (or manually enter values in Profile → Body Metrics and save)
2. Force-close and reopen the app
3. Navigate directly to Profile (skip Settings)
4. Scroll to the Body Metrics card
5. Confirm: Weight and Body Fat fields are pre-filled with the last known values (same values as Height which already worked)
