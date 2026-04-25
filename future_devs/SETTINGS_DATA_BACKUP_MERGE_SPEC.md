# Settings — Merge Data Export + Cloud Sync into "Data & Backup"

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | settings_page_reorder |
| **Touches** | `ui/settings/SettingsScreen.kt`, `SETTINGS_SPEC.md` |

> Read `SETTINGS_SPEC.md §2.7` and `§2.8` before touching any file.

---

## Overview

Settings currently has two separate cards for data management: "Data Export" (export DB to JSON, card 7) and "Cloud Sync" (restore from Firestore, card 8). These are logically the same domain — managing and safeguarding your data — and the split makes the screen feel longer than it needs to be. Merge them into a single "Data & Backup" card.

---

## Behaviour

Combine both cards into one `SettingsCard(title = "Data & Backup")` with two action rows:

### Row 1 — Export
- Label: "Export to JSON"
- Subtitle: "Save a local backup of your workouts and exercises"
- Action: `OutlinedButton("Export")` → `viewModel.exportDatabase()`
- State: `isExporting` shows `CircularProgressIndicator`; success/error text shown below the button (existing logic, unchanged)

### Row 2 — Cloud restore
- Shown only when `isSignedIn == true`
- Label: "Restore from Cloud"
- Subtitle: "Overwrite local data with your Firestore backup"
- Action: `OutlinedButton("Restore")` → `viewModel.restoreFromCloud()`
- State: `isRestoringFromCloud` spinner; `cloudRestoreMessage` result toast (existing logic, unchanged)
- When not signed in: hide the row entirely (no "not signed in" placeholder — the row simply doesn't appear, keeping the card clean)

Both rows separated by a `HorizontalDivider`.

---

## UI Changes

- Remove the two separate `item { DataExportCard() }` and `item { CloudSyncCard() }` blocks from `SettingsScreen.kt`.
- Replace with a single `item { DataAndBackupCard() }` composable containing both rows.
- All ViewModel state and logic is unchanged — only the Composable structure changes.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — merge two card `item { }` blocks into one `DataAndBackupCard` composable
- `SETTINGS_SPEC.md` — replace §2.7 Data Export + §2.8 Cloud Sync with a single §2.7 Data & Backup section; update §1 Card Order table (remove one row, rename remaining)

---

## How to QA

1. Open Settings → verify there is a single "Data & Backup" card (not two separate cards).
2. Signed in: verify both "Export to JSON" and "Restore from Cloud" rows appear with a divider between them.
3. Signed out: verify only "Export to JSON" row appears (Restore is hidden).
4. Tap Export → verify the export flow works as before (spinner, then success/error message).
5. Tap Restore (signed in) → verify the restore flow works as before.
