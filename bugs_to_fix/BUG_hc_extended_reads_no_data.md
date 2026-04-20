# BUG: All Health Connect data missing — full HC sync broken

## Status
[x] Fixed

## Severity
P0 blocker
- ALL Health Connect data is missing from the app: weight and body fat on Profile, HR/VO₂/SpO₂/Calories on Body & Vitals, body composition chart. This is a full HC sync failure affecting every feature that depends on HC data.

## Description
After QA, confirmed that no Health Connect data surfaces anywhere in the app — not basic reads (weight, body fat) nor extended reads (HR, VO₂ Max, SpO₂, Active Calories). HC is connected (permissions granted) but data is not flowing through.

Likely root cause: regression introduced by the `BUG_body_composition_ignores_hc` fix, which touched `HealthConnectManager.kt`. A change there likely broke the sync pipeline for all HC data types, not just body composition.

Possible failure modes:
1. `HealthConnectManager` sync method no longer called on app start / login
2. HC permission check failing silently — reads attempted without permission, returning empty
3. Data written to `health_connect_sync` table incorrectly (wrong columns, schema mismatch after migration)
4. `HealthConnectSyncDao` query broken — data in DB but not being read back

Confirm by checking: does the `health_connect_sync` table have any rows at all after a manual sync trigger? If empty, the write path is broken. If populated but data doesn't surface in UI, the read path is broken.

## Steps to Reproduce
1. Ensure Health Connect is connected and has weight, body fat, HR data (wearable or manual HC entry)
2. Navigate to Profile → Body Metrics — observe: weight and body fat empty
3. Navigate to Trends → Body & Vitals card — observe: all rows empty
4. Navigate to Trends → Body Composition card — observe: empty state

## Dependencies
- **Depends on:** —
- **Blocks:** BUG_body_composition_ignores_hc QA (body composition card will remain empty if HC sync is broken)
- **Touches:** `HealthConnectManager.kt`, `MetricsViewModel.kt`, `TrendsRepository.kt`, `AppSettingsDataStore.kt`, `HealthConnectSyncDao.kt`

## Assets
- Related spec: `HEALTH_CONNECT_SPEC.md`, `TRENDS_SPEC.md`

## Fix Notes
Root cause confirmed as failure mode #3: `BUG_body_composition_ignores_hc` expanded `HealthConnectSync` with 22 new columns (v43) but never added the DB migration. Every `INSERT OR REPLACE INTO health_connect_sync` call threw `SQLiteException: table has no column named avgHeartRateBpm`, caught silently by the try-catch in `syncAndRead()`. The table stayed empty, so all HC data showed "--" everywhere.

Fix summary:
- **MIGRATION_42_43** added to `DatabaseModule.kt`: `ALTER TABLE health_connect_sync ADD COLUMN` for all 22 new v43 columns (avgHeartRateBpm, peakHeartRateBpm, hrZone1–5Pct, activeCaloriesKcal, vo2MaxMlKgMin, distanceMetres, spo2Percent, lowSpO2Flag, deepSleepMinutes, remSleepMinutes, lightSleepMinutes, awakeMinutes, sleepEfficiency, sleepScore, sleepRespiratoryRate, elevatedRespiratoryRateFlag)
- **DB version bumped to 46**: chains 43→44 (exercise joints), 44→45 (import fields), 45→46 (stress_vectors table)
- **Extended HC reads**: `HealthConnectManager` now reads HR zones, SpO₂, VO₂, active calories, sleep stages, respiratory rate
- **MetricType** extended with `VO2_MAX`, `SPO2`
- **BodyVitalsCard**: rows for HR/VO₂/SpO₂ and Active Calories surfaced conditionally
- **ReadinessEngine**: deep sleep (W=0.10) and respiratory rate (W=0.05) signals added
- **TrendsRepository**: `getBodyCompositionData()` merges HC history; `getChronotypeData()` replaces `getWorkoutsByTimeOfDay()`; `getBodyStressMap()` added
- **New files**: `SleepStageCalculator`, `HrZoneCalculator`, `ExerciseStressVector*`, `StressVectorSeeder`, `StressAccumulationEngine`
- **AndroidManifest**: 6 new HC read permissions added
- All 703 unit tests pass

**Round 2 fix (on-device QA failure):**

Root cause: `MetricsViewModel.syncHealthConnect()` was discarding the `HealthConnectReadResult` returned by `syncAndRead()`, then re-reading exclusively from `healthConnectSyncDao.getLatestSync()`. If the DB write inside `syncAndRead()` failed silently (exception caught and only printed to stderr), the DAO returned null and the Metrics screen showed nothing. The Settings screen was unaffected because it stores the returned result directly in UI state.

- **MetricsViewModel**: `syncHealthConnect()` now applies the live `HealthConnectReadResult` directly to UI state before calling `doLoadBodyVitals()`. `doLoadBodyVitals()` fields now use null-coalescing (`?: it.bodyVitals.X`) so a null `latestSync` does not overwrite live HC values.
- **HealthConnectManager**: replaced `e.printStackTrace()` with `Log.e(TAG, ...)` in `syncAndRead()` DB write catch block. Added `Log.d` after `readAllData()` result construction for on-device Logcat observability.
- **MetricsViewModelBodyVitalsTest**: added `syncHealthConnect - uses returned HC result even when DB write failed and getLatestSync returns null` test.
- All tests pass (build clean).
