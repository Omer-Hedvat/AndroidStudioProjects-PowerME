# Fix Summary: Health History "Add" sheet — Type segmented buttons wrap mid-word

## Root Cause
The "Type" `SingleChoiceSegmentedButtonRow` has 5 equal-width segments. At standard phone width (~360dp), each segment is ~72dp — too narrow for "Condition" (9 chars) and "Restriction" (11 chars) at 11sp, causing Compose to insert mid-word line-breaks.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/HealthHistoryEntry.kt` | Added `shortLabel: String` property to `HealthHistoryType` enum; `CONDITION` → `"Cond."`, `RESTRICTION` → `"Restrict."`, others default to their `displayName` |
| `app/src/main/java/com/powerme/app/ui/profile/ProfileScreen.kt` | `HealthHistoryBottomSheet`: switched segmented button text from `type.displayName` to `type.shortLabel` |

## Surfaces Fixed
- "Add Health Entry" bottom sheet — Type row no longer wraps mid-word

## How to QA
1. Open the app → Profile tab.
2. Scroll to the Health History card and tap "+".
3. Confirm the Type row shows: **Injury · Surgery · Cond. · Restrict. · Other** — all on a single line, no wrapping.
4. Select "Condition" (shows as "Cond.") — verify the entry saves with type `CONDITION` (full name in the entry row and any edit sheet).
5. Select "Restriction" (shows as "Restrict.") — same check.
