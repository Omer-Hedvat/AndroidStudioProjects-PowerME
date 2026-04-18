# Fix Summary: Fitness level selection doesn't persist after app restart

## Root Cause

`FirestoreSyncManager` omitted `experienceLevel` and `trainingAgeYears` from both Firestore serialization functions:

- `User.toFirestoreMap()` — push map didn't include these fields, so they were never written to Firestore
- `DocumentSnapshot.toUser()` — pull deserializer didn't read them, so the reconstructed `User` had both fields as `null` (Kotlin data class defaults)

On app start, `pullProfileOnly()` fires and compares `remoteUpdatedAt >= localUpdatedAt`. If the remote doc is newer (any other device or prior session), it calls `userDao.insertUser(user)` with the null-field User, overwriting the local values the user had just set.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/sync/FirestoreSyncManager.kt` | Added `"experienceLevel"` and `"trainingAgeYears"` to `User.toFirestoreMap()`; added `experienceLevel = getString(...)` and `trainingAgeYears = getLong(...)?.toInt()` to `DocumentSnapshot.toUser()` |
| `app/src/test/java/com/powerme/app/ui/profile/ProfileViewModelPersonalInfoTest.kt` | Added 2 tests: loads experienceLevel/trainingAgeYears from User entity; leaves them null/0 when not set |

## Surfaces Fixed

- Profile screen → Fitness Level card: selected tile and training age slider now persist across app restarts

## How to QA

1. Open the app and navigate to Profile (top bar icon)
2. Scroll to the Fitness Level card
3. Tap "Experienced" — tile highlights
4. Drag the Training Age slider to ~5 years
5. Navigate to Workouts tab, then force-close and reopen the app
6. Navigate back to Profile → Fitness Level card
7. Confirm: "Experienced" tile is still highlighted, Training Age shows ~5 years
