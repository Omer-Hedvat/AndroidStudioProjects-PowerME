# Fix Summary: Use RPE toggle misaligned — multi-line subtitle breaks row layout

## Root Cause
The "Use RPE" settings row used `verticalAlignment = Alignment.CenterVertically` on its `Row`. When the subtitle ("Automatically open the RPE picker after completing each set") wraps to two lines, the `Switch` is vertically centered against the full two-line column height instead of aligning with the headline. This makes the toggle appear to float in the middle of the text block rather than beside the label — contrary to the MD3 `ListItem` pattern where trailing controls top-align with the headline.

Additionally, the `Column` had no end padding, so the subtitle text ran right up to the edge of the switch with no visual breathing room.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` | Changed `verticalAlignment = Alignment.CenterVertically` → `Alignment.Top` on the Use RPE row; added `Modifier.padding(end = 12.dp)` to the text `Column` |

## Surfaces Fixed
- Settings screen → Display & Workout card → Use RPE row

## How to QA
1. Open the app and navigate to **Settings**
2. Scroll to the **Display & Workout** card
3. Verify the **Use RPE** toggle is aligned with the "Use RPE" title text (top-aligned), not floating in the middle of the subtitle
4. Verify a clear horizontal gap exists between the subtitle text and the switch
5. Tap the toggle — it should still toggle `useRpeAutoPop` correctly
6. Verify **Keep screen on** row (single-line subtitle) is visually unchanged
