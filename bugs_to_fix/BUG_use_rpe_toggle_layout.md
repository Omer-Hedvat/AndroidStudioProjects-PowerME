# BUG: Use RPE toggle layout broken — multi-line subtitle misaligns toggle

## Status
[x] Fixed

## Severity
P2 normal — cosmetic layout issue affecting Settings screen readability

## Description
In the **Settings → Display & Workout** card, the "Use RPE" row has a subtitle
("Automatically open the RPE picker after completing each set") that is long enough
to wrap to a second line. The `Switch` toggle ends up vertically centered against
only the first line of text, making the row look visually broken — the toggle
floats at the top while the text continues below it.

The "Keep screen on" row (same card) does not have this problem because its
subtitle fits on one line.

## Steps to Reproduce
1. Open the app and navigate to **Settings**.
2. Scroll to the **Display & Workout** card.
3. Observe the "Use RPE" row — the subtitle wraps to two lines and the toggle
   is not vertically centered with the full text block.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt`

## Assets
- Screenshot: `bugs_to_fix/assets/use_rpe_toggle_layout/screenshot.png`
- Related spec: `THEME_SPEC.md §9`

## Fix Notes
Changed `verticalAlignment = Alignment.CenterVertically` → `Alignment.Top` on the Use RPE row.
Added `Modifier.padding(end = 12.dp)` to the text Column so the subtitle doesn't crowd the switch.
This matches the MD3 ListItem pattern: trailing control top-aligns with the headline when supporting text wraps to multiple lines.
