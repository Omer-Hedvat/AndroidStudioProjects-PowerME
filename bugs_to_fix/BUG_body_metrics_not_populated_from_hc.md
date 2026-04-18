# BUG: Body Metrics weight and body fat not populated from Health Connect data

## Status
[x] Fixed

## Description
In the Profile screen's Body Metrics card, the Weight (kg) and Body Fat (%) fields appear empty even though the user has Health Connect connected and HC contains weight/body fat data. The Height field correctly shows "182" and the "Last: 182 cm" label appears, but Weight and Body Fat are blank. The HC sync pipeline reads weight and body fat via `READ_WEIGHT` and `READ_BODY_FAT` permissions and stores them in the `MetricLog` table and `User` entity (dual-sink). Likely root cause: `ProfileViewModel` is not reading the latest weight/body fat from the database on init, or the HC sync is writing to a different sink than what `ProfileViewModel` reads from.

Affected screen: `ProfileScreen.kt` — Body Metrics card (Weight and Body Fat fields).

## Steps to Reproduce
1. Ensure Health Connect is connected and contains weight/body fat records
2. Open the app and navigate to Profile (top bar icon)
3. Look at the Body Metrics card
4. Observe: Weight (kg) and Body Fat (%) fields are empty, despite HC data being available
5. Note: Height shows correctly (182 cm), confirming HC sync works for at least one metric

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ProfileViewModel.kt`, `ProfileScreen.kt`, `HealthConnectManager.kt`, `MetricLogRepository.kt`

## Assets
- Screenshot provided by user showing empty Weight and Body Fat fields with Height populated
- Related spec: `HEALTH_CONNECT_SPEC.md`, `SETTINGS_SPEC.md`, `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md`

## Fix Notes
Root cause (primary): `ProfileViewModel.loadPersonalInfo()` loaded `heightCm` from the User entity as a fallback (via `loadUserHeight()`), but never read `weightKg` or `bodyFatPercent`. Weight and body fat relied solely on the `MetricLog` Flow which may be empty if HC sync hasn't run in the current session.

Root cause (secondary): `observeMetricLogs()` was unconditionally setting `lastWeight = latestKg` and `lastBodyFat = latest`, overwriting the User entity values with `null` when MetricLog was empty.

Fix: Added `loadUserWeight()` and `loadUserBodyFat()` private methods (matching the `loadUserHeight()` pattern) and called them from `loadPersonalInfo()` after `loadUserHeight()`. Also changed `observeMetricLogs()` to use `latestKg ?: state.lastWeight` and `latest ?: state.lastBodyFat` so empty MetricLog emissions don't overwrite User entity fallback values.
