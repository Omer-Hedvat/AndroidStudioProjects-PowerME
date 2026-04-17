# BUG: Body Metrics weight and body fat not populated from Health Connect data

## Status
[ ] Open

## Description
In the Profile screen's Body Metrics card, the Weight (kg) and Body Fat (%) fields appear empty even though the user has Health Connect connected and HC contains weight/body fat data. The Height field correctly shows "182" and the "Last: 182 cm" label appears, but Weight and Body Fat are blank. The HC sync pipeline reads weight and body fat via `READ_WEIGHT` and `READ_BODY_FAT` permissions and stores them in the `MetricLog` table and `User` entity (dual-sink). Likely root cause: `ProfileViewModel` is not reading the latest weight/body fat from the database on init, or the HC sync is writing to a different sink than what `ProfileViewModel` reads from.

Affected screen: `ProfileScreen.kt` — Body Metrics card (Weight and Body Fat fields).

## Steps to Reproduce
1. Ensure Health Connect is connected and contains weight/body fat records
2. Open the app and navigate to Profile (top bar icon)
3. Look at the Body Metrics card
4. Observe: Weight (kg) and Body Fat (%) fields are empty, despite HC data being available
5. Note: Height shows correctly (182 cm), confirming HC sync works for at least one metric

## Assets
- Screenshot provided by user showing empty Weight and Body Fat fields with Height populated
- Related spec: `HEALTH_CONNECT_SPEC.md`, `SETTINGS_SPEC.md`, `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
