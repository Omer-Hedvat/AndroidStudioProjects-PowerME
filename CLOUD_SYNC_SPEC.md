# CLOUD_SYNC_SPEC.md — PowerME Cloud Sync Engine
**Status:** 🆕 Draft (April 2026)
**Invariants:** Offline-First · Last-Write-Wins · Wi-Fi Only

## 1. Authentication Layer (Identity)
- **Provider:** Firebase Auth using Google Sign-In.
- **Role:** Authentication provides a secure Firebase `uid` to partition data in the cloud. The app remains 100% functional offline.

## 2. Local Database Schema (The Ledger)
- **Field:** Every Room entity must include `lastModifiedMs: Long`.
- **Trigger:** Update this field on every Insert, Update, or Soft-Delete.

## 3. Sync Engine (WorkManager)
- **Implementation:** `CloudSyncWorker` (WorkManager).
- **Constraints:** `RequiresCharging = true` AND `RequiresNetworkType = UNMETERED`.
- **Conflict Resolution:** Last-Write-Wins. Compare local vs. cloud `lastModifiedMs`.

## 4. Custom Media Asset Pipeline
- **Constraint:** No raw MP4s in cloud.
- **Process:** Use local FFmpeg to trim to 3s, strip audio, and convert to Animated WebP (< 300KB).
- **Storage:** Sync .webp to Firebase Storage at `users/{uid}/assets/{exerciseId}.webp`.
