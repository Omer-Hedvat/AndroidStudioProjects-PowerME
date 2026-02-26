# PowerME v3.0 — Master Specification (Immutable)

## 1. Design System: Stremio Indigo & High Density
- **Palette:** Background (#0F0F1E), Surface (#191932), Primary (#7B5BE4), Secondary/SS (#B3478C).
- **Architecture:** 100% MaterialTheme.colorScheme. No hardcoded hex imports in Composables.
- **UI Aesthetic:** "Strong App" clone. High vertical density.
- **Atomic Components:** - Input Pills: Small rounded rectangles (4dp radius), Background (#2C2C4E).
    - Cues Banner: Muted gold/mustard (#5A4D1A) with 12sp text.

## 2. Gym & Exercise Logic
- **Decoupling:** Barbell/Plates toggles are independent. Disabled equipment = Greyed out (0.45 alpha).
- **Search:** 'Magic Add' uses LOCAL FUZZY MATCHING (SQL `LIKE`). Gemini AI search is deprecated.
- **Exercise List:** Remove 'play' icon signs.
- **Exercise Interaction:** Tapping an exercise opens a Modal Bottom Sheet showing 'Form Cues' and clickable YouTube links.
- **Ordering:** Entire Exercise Blocks reorderable via 1s long-press.

## 3. Data Integrity & P0 Stability
- **Input Validation:** ALL numeric inputs (Weight, Reps, Height) must pass `SurgicalValidator.kt`.
- **Crash Prevention:** If validation fails, UI must retain previous state and never propagate unparseable strings to Room.
- **Ghosting:** 'Previous' column pre-fetched into a Map. Display format: "30kg x 10". Placeholder: "-".
- **Data Cleanse:** Migration 17->18 must explicitly nullify the "181.5 cm:" leak in `setupNotes`.

## 4. Workout Execution Engine
- **Lifecycle:** Routine (Class) -> Workout (Instance).
- **Set Row:** [Set # | Previous | KG | REPS | V].
- **Timer Logic:**
    - Trigger: Starts immediately on 'V' click (Green state).
    - UI: Descending progress bar injected directly below the active set row.
    - Adjustment: Tapping progress bar opens Bottom Sheet with [+30s], [-30s], and [Skip].
- **Supersets (SS):** Visualized via a 4dp vertical strip (Stremio Magenta) on the far-left card edge.

## 5. Routine Management
- **Dashboard Layout:**
    1. "Start Empty Workout" (Header Button).
    2. Active Routine Cards (Summary: Ex Name, Sets, Recency metadata).
    3. Archived Routines (Collapsed default state at bottom).
- **Actions:** 3-dot menu [Edit, Rename, Archive, Duplicate, Share, Delete].
- **Finish Dialog:** If sets remain unchecked -> [Complete All | Discard | Cancel].

## 6. Health Connect & Tools
- **Sync:** Weight, BodyFat, Height (Dual-sink to MetricLog and User table).
- **Clocks:** Tabata and EMOM timers must have persistent [START] and [RESET] buttons.