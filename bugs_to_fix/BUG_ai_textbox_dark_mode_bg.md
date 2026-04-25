# BUG: AI text generator text box has wrong background in dark mode

## Status
[ ] Open

## Severity
P3 low — cosmetic issue; text input background is not styled correctly in dark mode

## Description
In the AI workout generation screen, the text input field background appears as the wrong color (likely white or light grey) in dark mode instead of a darker grey as seen in the Gemini app. This is a theme token mismatch — the `TextField` or `OutlinedTextField` is either using a hardcoded color or the wrong `MaterialTheme.colorScheme` token for its container color.

## Steps to Reproduce
1. Set device/emulator to Dark Mode
2. Navigate to the AI workout generation screen (Workouts tab → AI generate)
3. Observe: the text input box background appears light/white instead of a dark grey

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt`

## Assets
- Related spec: `THEME_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
