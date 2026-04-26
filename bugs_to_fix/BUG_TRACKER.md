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
| [BUG_ai_textbox_dark_mode_bg](BUG_ai_textbox_dark_mode_bg.md) | AI text generator text box has wrong background in dark mode | ✅ Completed | P3 | — | — | `AiWorkoutGenerationScreen.kt` |
| [BUG_func_overlay_mid_round_alert](BUG_func_overlay_mid_round_alert.md) | Functional overlays missing mid-round alert (haptic/sound) | ✅ Completed | P2 | — | — | `TimerEngine.kt`, `FunctionalBlockRunner.kt` |
| [BUG_func_overlay_typography_too_small](BUG_func_overlay_typography_too_small.md) | RFT/AMRAP overlay typography too small — round number, exercises, reps unreadable at arm's length | 🔵 Open | P2 | — | — | — |
| [BUG_post_workout_rpe_redesign](BUG_post_workout_rpe_redesign.md) | Post-workout RPE auto-pops instead of offering an "Add RPE" button with RPE scale titles | 🔵 Open | P2 | — | — | — |
| [BUG_func_rft_round_btn_ui](BUG_func_rft_round_btn_ui.md) | RFT "ROUND ✓" button too small; AMRAP and RFT round button UI inconsistent | 🔵 Open | P2 | — | — | — |
| [BUG_func_overlay_alert_timing](BUG_func_overlay_alert_timing.md) | Functional overlay alert timing wrong — EMOM needs 30s+10s alerts; RFT/AMRAP need mid-cap alert | 🔵 Open | P1 | — | — | — |
| [BUG_func_emom_skip_round_broken](BUG_func_emom_skip_round_broken.md) | EMOM "Skip Round" button does not advance to next round or update round counter | 🔵 Open | P1 | — | — | — |
| [BUG_func_routine_view_no_block_ui](BUG_func_routine_view_no_block_ui.md) | Routine summary and edit pages show flat exercise list instead of functional block structure | 🔵 Open | P2 | func_block_card_layout ✅ | — | — |
| [BUG_func_no_block_edit](BUG_func_no_block_edit.md) | No way to edit a functional block's parameters (type, duration, rounds, cap) after routine creation | 🔵 Open | P1 | — | — | — |
| [BUG_func_rft_screen_off](BUG_func_rft_screen_off.md) | Screen turns off during RFT overlay despite "Keep Screen On — Always" setting | 🔵 Open | P1 | — | — | — |
| [BUG_func_overlay_ring_tap_keyboard](BUG_func_overlay_ring_tap_keyboard.md) | Tapping progress ring in functional overlays triggers soft keyboard | 🔵 Open | P1 | — | — | — |
| [BUG_func_picker_excludes_strength_functional](BUG_func_picker_excludes_strength_functional.md) | Functional block exercise picker excludes STRENGTH-typed functional exercises (Power Clean, KB Swing, Front Squat, etc.) | ✅ Completed | P1 | — | — | `TemplateBuilderScreen.kt` |

---

## How to Use This File

- **Start of session:** Find an `Open` bug, run `/start_task <slug>` to mark In Progress.
- **Dev done:** Update status to `Completed`. Create `SUMMARY_<slug>.md`, fill Fix Notes in `BUG_<slug>.md`.
- **After user QA:** Run `/wrap_task <slug>` — builds, tests, commits, pushes, then **move the row to `ARCHIVE.md`**.
- **Archived bugs:** Search with `grep -i "<keyword>" bugs_to_fix/ARCHIVE.md`
