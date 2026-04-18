# BUG: Fitness level selection doesn't persist after app restart

## Status
[x] Fixed

## Description
In the Profile screen, when the user selects a Fitness Level (Novice/Trained/Experienced/Athlete) and adjusts the Training Age slider, the selection does not persist after closing and reopening the app. On return, the Fitness Level card shows no selection highlighted and Training Age resets to default ("< 1 year"). The data is stored in `experienceLevel` + `trainingAgeYears` columns on the `User` entity (v39). Likely root cause: either `ProfileViewModel` is not saving the selection to the database, or it's not loading the saved values back on init. Could also be a Firestore sync issue overwriting local values with empty/default remote values.

Affected screen: `ProfileScreen.kt` — Fitness Level card.

## Steps to Reproduce
1. Open the app and navigate to Profile (top bar icon)
2. Scroll to the Fitness Level card
3. Select "Experienced" (or any level) and drag Training Age slider to e.g. 5 years
4. Navigate away from Profile (e.g., go to Workouts tab)
5. Force-close the app and reopen it
6. Navigate back to Profile → Fitness Level card
7. Observe: no level is highlighted, Training Age shows "< 1 year" — selection was not persisted

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ProfileViewModel.kt`, `ProfileScreen.kt`, `User.kt`, `UserDao.kt`

## Assets
- Screenshot provided by user showing no fitness level selected, Training Age at "< 1 year"
- Related spec: `future_devs/PROFILE_SETTINGS_REDESIGN_SPEC.md §3`

## Fix Notes
Root cause: `FirestoreSyncManager` omitted `experienceLevel` and `trainingAgeYears` from both the push map (`User.toFirestoreMap()`) and the pull deserializer (`DocumentSnapshot.toUser()`). On Firestore pull (triggered on app open when `remoteUpdatedAt >= localUpdatedAt`), the reconstructed User had both fields as null, overwriting the locally saved values.

Fix: Added `"experienceLevel" to experienceLevel` and `"trainingAgeYears" to trainingAgeYears` to `toFirestoreMap()`, and `experienceLevel = getString("experienceLevel")` and `trainingAgeYears = getLong("trainingAgeYears")?.toInt()` to `toUser()`.
