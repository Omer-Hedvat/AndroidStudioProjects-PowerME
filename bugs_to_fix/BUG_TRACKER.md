# Bug Tracker

Single source of truth for **active** bug status. Wrapped bugs are in `ARCHIVE.md`.

**Statuses:** `Open` | `In Progress` | `Completed` | `Wrapped` | `Pending` | `Blocked`

- `Completed` — dev done, tests pass, summary file exists, ready for QA on device
- `Wrapped` — user QA'd and ran `/wrap_task` (build + test + commit + push) → move to `ARCHIVE.md`
- `Pending` — intentionally deferred (external dep, future milestone, or parked)
- `Blocked` — waiting on external platform resolution (not PowerME code)

| Bug | Title | Status | Severity | Depends on | Blocks | Files Changed |
|-----|-------|--------|----------|------------|--------|---------------|
| [BUG_body_composition_ignores_hc](BUG_body_composition_ignores_hc.md) | BodyCompositionCard shows empty state — does not pull weight/body fat from HC | 🔴 Blocked (external HC issue) | P1 | — | — | `HealthConnectManager.kt`, `TrendsRepository.kt` |
| [BUG_hc_extended_reads_no_data](BUG_hc_extended_reads_no_data.md) | All HC data missing — full Health Connect sync broken | 🔴 Blocked (external HC issue) | P0 | — | — | multiple HC + Trends files |
| [BUG_nuke_hc_debug_cleanup](BUG_nuke_hc_debug_cleanup.md) | Remove HC nuke debug tooling (temporary code, must not ship) | ⏸️ Pending | P1 | BUG_write_workout_session_oversized ✅, HC lockup resolved | — | `HealthConnectManager.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt` |
| [BUG_exercise_type_mismatches](BUG_exercise_type_mismatches.md) | exerciseType mismatches — 2 invalid BODYWEIGHT enum + 2 wrong type assignments | ✅ Completed | P2 | — | — | `master_exercises.json`, `MasterExerciseSeeder.kt` |
| [BUG_ai_textbox_dark_mode_bg](BUG_ai_textbox_dark_mode_bg.md) | AI text generator text box has wrong background in dark mode | 🔵 Open | P3 | — | — | — |

---

## How to Use This File

- **Start of session:** Find an `Open` bug, run `/start_task <slug>` to mark In Progress.
- **Dev done:** Update status to `Completed`. Create `SUMMARY_<slug>.md`, fill Fix Notes in `BUG_<slug>.md`.
- **After user QA:** Run `/wrap_task <slug>` — builds, tests, commits, pushes, then **move the row to `ARCHIVE.md`**.
- **Archived bugs:** Search with `grep -i "<keyword>" bugs_to_fix/ARCHIVE.md`
