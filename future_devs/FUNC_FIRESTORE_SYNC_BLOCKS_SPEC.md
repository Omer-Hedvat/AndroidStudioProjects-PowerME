# Functional Training — Firestore Block Sync

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | M |
| **Depends on** | func_block_entities_migration ✅ |
| **Blocks** | func_active_functional_runner |
| **Touches** | `data/sync/FirestoreSyncManager.kt` |

> **Full spec:** `FUNCTIONAL_TRAINING_SPEC.md §5` — read before touching any file in this task.

---

## Overview

Extend `FirestoreSyncManager` to embed `blocks` arrays inside the existing workout and routine Firestore documents (not as sub-collections). Adds back-compat handling for legacy cloud docs that lack the `blocks` field.

This task ships alone (before any UI) so that the Firestore document shape is stable on all devices before functional blocks are visible in the UI.

---

## Behaviour

- `pushWorkout(workoutId)` fetches `WorkoutBlock` rows for the workout and includes them as a `blocks: List<Map>` array in the workout doc. Each `WorkoutSet` map in the `sets` array gains a `blockId` field.
- `pushRoutine(routineId)` does the same for `RoutineBlock` rows and the `exercises` array (gains `blockId`, `holdSeconds`).
- `pullFromCloud()` reconstructs blocks on pull with LWW (`block.updatedAt >= local.updatedAt`).
- **Back-compat:** if a remote doc lacks the `blocks` field (pre-v50 client), skip block reconstruction entirely — the local STRENGTH backfill block from migration already exists. Do NOT synthesize a new block on pull.
- Round-trip invariant: `push → wipe local → pull` must reproduce identical block structure and scores.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/data/sync/FirestoreSyncManager.kt` — extend `pushWorkout`, `pushRoutine`, `Workout.toFirestoreMap`, `pullFromCloud`

---

## How to QA

1. Create a workout with one STRENGTH block. Push to Firestore. Open Firestore console; verify the workout doc has a `blocks` array with one entry.
2. Clear local DB (or reinstall). Pull from cloud. Verify block is reconstructed with the correct `type=STRENGTH` and all sets point to it.
3. Test back-compat: manually edit a cloud doc to remove the `blocks` field. Pull on device; verify no crash and no duplicate block created.
4. Run round-trip integration test from CI (`FirestoreSyncRoundTripTest`).
