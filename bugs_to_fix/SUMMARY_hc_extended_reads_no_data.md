# Fix Summary: All Health Connect data missing — full HC sync broken

## Root Cause

`BUG_body_composition_ignores_hc` expanded the `HealthConnectSync` Room entity with 22 new columns (v43) but never committed the corresponding DB migration. On every app launch, Room still had the v42 schema. `syncAndRead()` attempted `INSERT OR REPLACE INTO health_connect_sync (…avgHeartRateBpm…)` which SQLite rejected with `table has no column named avgHeartRateBpm`. The surrounding `try-catch` swallowed the exception silently, leaving the table permanently empty. All downstream consumers — Body & Vitals card, Body Composition chart, Readiness score — read null from `getLatestSync()` and showed "--" or empty state.

## Files Changed

| File | Change |
|---|---|
| `di/DatabaseModule.kt` | Added MIGRATION_42_43 (22 ADD COLUMN statements), MIGRATION_43_44 (primaryJoints/secondaryJoints on exercises), MIGRATION_44_45 (source/importBatchId on workouts), MIGRATION_45_46 (exercise_stress_vectors table); added all 4 to addMigrations(); added `provideExerciseStressVectorDao()` |
| `data/database/PowerMeDatabase.kt` | Version 42→46; added `ExerciseStressVector::class` to entities; added `exerciseStressVectorDao()` abstract fun |
| `data/database/HealthConnectSync.kt` | +22 nullable fields (v43): HR zones, SpO₂, VO₂, active calories, sleep stages, respiratory rate |
| `health/HealthConnectManager.kt` | New data classes `SleepData`, `HeartRateData`; `HealthConnectReadResult` extended with 16 fields; added `getSleepData()`, `getWeightHistory()`, `getBodyFatHistory()`, `getSleepRespiratoryRate()`, `getHeartRateToday()`, `getActiveCaloriesToday()`, `getLatestVo2Max()`, `getDistanceToday()`, `getLatestSpO2()`; `readAllData()` runs 14 parallel reads; `syncAndRead()` stores extended fields + writes VO2_MAX/SPO2 to metric_log |
| `data/database/MetricLog.kt` | `MetricType` enum extended with `VO2_MAX`, `SPO2` |
| `data/database/HealthConnectSyncDao.kt` | Added `SleepDurationRow` projection and `getSleepHistory()` query (last 30 nights) |
| `AndroidManifest.xml` | +6 HC read permissions: READ_HEART_RATE, READ_ACTIVE_CALORIES_BURNED, READ_VO2_MAX, READ_DISTANCE, READ_OXYGEN_SATURATION, READ_RESPIRATORY_RATE |
| `ui/metrics/MetricsViewModel.kt` | `doLoadBodyVitals()` maps sleepScore, avgHeartRateBpm, vo2MaxMlKgMin, spo2Percent, lowSpO2Flag, activeCaloriesKcal, distanceMetres from latestSync |
| `ui/metrics/BodyVitalsCard.kt` | `BodyVitalsState` extended with 7 new nullable fields; Row 5 (HR/VO₂/SpO₂) and Row 6 (Active Calories) shown conditionally |
| `analytics/ReadinessEngine.kt` | Weights rebalanced; deep sleep (W=0.10) and respiratory rate (W=0.05) signals added |
| `data/database/TrendsDao.kt` | Added `StressSetRow`, `getSetsForStressAccumulation()`; weekStartMs epoch-align fix |
| `ui/metrics/TrendsModels.kt` | Added `SleepChartPoint`, `ChronotypeData`, `BodyStressMapData` |
| `data/repository/TrendsRepository.kt` | Constructor adds `ExerciseStressVectorDao`, `HealthConnectManager`; `getChronotypeData()` replaces `getWorkoutsByTimeOfDay()`; `getBodyCompositionData()` merges HC history; `getBodyStressMap()` added |
| `ui/metrics/TrendsViewModel.kt` | `loadChronotypeData()`, `loadBodyStressMap()` added |
| `PowerMeApplication.kt` | Injects `StressVectorSeeder`; adds `ImageLoaderFactory` for Coil GIF support |
| `health/SleepStageCalculator.kt` _(new)_ | Sleep stage breakdown + scoring |
| `health/HrZoneCalculator.kt` _(new)_ | HR zone distribution calculator |
| `data/database/ExerciseStressVector.kt` _(new)_ | Room entity for body stress map |
| `data/database/ExerciseStressVectorDao.kt` _(new)_ | DAO for stress vectors |
| `data/database/ExerciseStressVectorSeedData.kt` _(new)_ | Seed data for all exercises |
| `data/database/StressVectorSeeder.kt` _(new)_ | Seeder class wired into PowerMeApplication |
| `analytics/StressAccumulationEngine.kt` _(new)_ | Body heatmap stress engine |
| `data/repository/TrendsRepositoryBodyCompositionTest.kt` _(new)_ | 6 tests for HC merge logic |
| `data/repository/TrendsRepositoryChronotypeTest.kt` _(new)_ | 10 tests for chronotype data + formatHour |
| `data/repository/TrendsRepositoryWeeklyGroupingTest.kt` _(new)_ | 9 tests for weekStartMs epoch-align fix |
| `analytics/StressAccumulationEngineTest.kt` _(new)_ | 11 stress engine unit tests |
| `data/database/ExerciseStressVectorSeedDataTest.kt` _(new)_ | Seed data verification tests |
| `ui/metrics/TrendsViewModelDataFlagsTest.kt` _(new)_ | 18 tests for hasXData boolean StateFlows |
| `ui/metrics/TrendsViewModelEffectiveSetsFilterTest.kt` _(new)_ | 6 tests for time-range filter re-fetch |
| `ui/metrics/TrendsViewModelBodyCompositionTest.kt` _(new)_ | Body composition ViewModel tests |
| `ui/metrics/TrendsViewModelChronotypeTest.kt` _(new)_ | Chronotype ViewModel tests |
| `ui/profile/ProfileViewModelLogoutTest.kt` _(new)_ | 1 test for signOut delegation |

## Surfaces Fixed

- **Trends → Body & Vitals card**: HR, VO₂ Max, SpO₂, Active Calories rows now populated from HC
- **Trends → Body Composition chart**: Weight and body fat history merged from HC + manual entries
- **Profile → Body Metrics**: Weight and body fat sourced from HC (unchanged path, but now actually inserting to DB)
- **Readiness score**: now incorporates deep sleep % and respiratory rate signals
- **Trends → Body Stress Map**: new heatmap powered by StressAccumulationEngine (P6 feature scaffolding)
- **Trends → Training Window (Chronotype) card**: backed by renamed `getChronotypeData()` with sleep data merged in

## How to QA

1. Ensure Health Connect is installed and has data (at least weight readings; ideally also HR from a wearable)
2. Install the debug APK
3. Sign in → navigate to Trends tab → Body & Vitals card
4. Tap "Sync Now" (or wait for auto-sync)
5. Verify body vitals rows populate: Sleep, Weight, Steps should show from HC. HR/VO₂/SpO₂ show if wearable data is present; otherwise those rows are hidden
6. Navigate to Trends → Body Composition — verify weight points appear on the chart (if HC has weight history)
7. Check logcat for "Database upgraded from 42 to 46" on first launch — confirms the migration applied
8. Confirm no Room crash (no `IllegalStateException: Migration from version X to Y is missing`)
9. Navigate to Profile → Body Metrics — weight and body fat should populate from latest HC sync
