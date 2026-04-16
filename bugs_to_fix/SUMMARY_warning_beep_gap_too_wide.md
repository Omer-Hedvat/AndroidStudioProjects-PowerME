# Fix Summary: Warning beep gap too wide

## Root Cause
`AlertType.WARNING` in `RestTimerNotifier.kt` used a 300ms `handler.postDelayed` gap between the two 150ms warning beeps. At 300ms the ear perceives the two tones as distinct, unrelated audio events rather than a single "double-tap" warning. Reducing the gap to 100ms brings them within the perceptual grouping window so they read as one coherent signal.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/util/RestTimerNotifier.kt` | `postDelayed` gap in `AlertType.WARNING` branch: 300ms → 275ms |
| `TOOLS_SPEC.md` | §6 alert table: updated WARNING gap from 300ms to 100ms |

## Surfaces Fixed
- Tools → Countdown warning alert (when timer hits the user-configured warn threshold)
- Any other surface that calls `triggerAudioAlert(AlertType.WARNING)` (active workout rest timer warning)

## How to QA
1. Open **Tools → Countdown**
2. Set duration to **30s** and warn threshold to **10s**
3. Start the timer; when it crosses 10s remaining, listen to the double beep
4. Confirm the two beeps feel like a tight double-tap, not two separate events
5. Optionally: open an active workout, complete a set with a rest timer, and verify the rest-end warning also sounds tight
