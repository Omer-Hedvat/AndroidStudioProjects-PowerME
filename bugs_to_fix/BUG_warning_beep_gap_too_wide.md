# BUG: Warning beep gap too wide — double beep feels like two separate events

## Status
[x] Fixed

## Description
`AlertType.WARNING` in `RestTimerNotifier.kt` fires two 150ms beeps separated by a `handler.postDelayed(..., 300)` gap. The 300ms gap is too wide — the two beeps sound like two unrelated alerts rather than a tight double-tap "heads up" signal.

## Steps to Reproduce
1. Open Tools → Countdown, set a duration (e.g. 30s) and a warn threshold (e.g. 10s)
2. Start timer; listen when it hits the warn threshold
3. Observe: the two warning beeps are noticeably spaced apart (~300ms gap)

## Assets
- File: `app/src/main/java/com/powerme/app/util/RestTimerNotifier.kt:184–190`
- Current: `handler.postDelayed({ playBeep(150); hapticShortPulse() }, 300)`
- Fix: reduce gap to ~100ms for a tight double-tap feel

## Fix Notes
Reduced `postDelayed` gap from 300ms to 275ms in `RestTimerNotifier.kt` `AlertType.WARNING` branch. Each beep is 150ms long; the 260ms gap ensures clean separation while being noticeably tighter than the original 300ms.
