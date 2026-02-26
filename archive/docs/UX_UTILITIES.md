# UX & Automation Specifications

## 1. WakeLock (Screen Always On)
- **Constraint**: Screen must NOT dim or turn off while a Workout Session is active.
- **Implementation**: Use `DisposableEffect` in the WorkoutScreen to toggle `android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`.

## 2. Auto-Rest Timer Logic
- **Trigger**: Automatically starts when a Set is marked as 'Completed'.
- **Default Durations**:
    - Compound Lifts (Squat, Bench): 180s.
    - Isolation Lifts (Curls, Lateral Raises): 90s.
- **UI**: A circular countdown overlay or a sticky notification that remains visible even if the user switches apps (using a Foreground Service).
- **Sound/Haptic**: Vibration and a subtle tone when 5 seconds remain.