# Noaa's Warmup & Mobility Protocol

## 1. Goal
Provide 2-3 dynamic mobility exercises before the main workout, tailored to the target muscle groups and current injury status.

## 2. Variety Engine
- **Input**: List of last 10 warmup exercises performed.
- **Filtering**: Do not suggest the same warmup if it was performed in the last 2 sessions of the same Routine (A/B).
- **Injury Focus**:
    - If Routine involves 'Pressing': Always include 1 Elbow mobility + 1 Shoulder stability.
    - If Routine involves 'Squat/RDL': Always include 1 Lower Back decompression + 1 Hip opener.

## 3. Sample Warmup Library (for Claude to seed)
- **Shoulders**: Face Pulls (light), Wall Slides, Shoulder Dislocates (PVC).
- **Elbow**: Zottman Curls (light), Wrist Rotations, Triceps Stretch (dynamic).
- **Back/Core**: Cat-Cow, Bird-Dog, Dead Bug.
- **Hips**: World's Greatest Stretch, 90/90 Hip Switches.