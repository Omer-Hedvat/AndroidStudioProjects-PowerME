# Gymvisual Chest+Barbell Catalogue Comparison

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `app/src/main/res/raw/master_exercises.json` (read-only) |

---

## Overview

Research-only task. Scrapes exercise names from gymvisual.com's chest+barbell animated-GIF catalogue (49 items), cross-references them against our 240-exercise master library, and emits a single markdown report. No GIFs are downloaded, no DB changes are made.

gymvisual.com is a paid commercial asset store. Any future use of their content requires purchasing a license from https://gymvisual.com/content/3-terms-and-conditions-of-use.

---

## Behaviour

- Source URL: `https://gymvisual.com/16-animated-gifs/s-1/body_part-chest/equipment_type-barbell/media_type-animated_gifs` (3 pages, ~49 items)
- Our DB side: `muscleGroup == "Chest"` AND `equipmentType == "Barbell"` → 3 exercises
- Matching cascade (mirroring `ExerciseMatcher`): normalize → exact → synonym-expand → Jaro-Winkler ≥ 0.85
- Output: three-section markdown report — DB exercises, matched, unmatched

---

## UI Changes

None.

---

## Files to Touch

- `future_devs/GYMVISUAL_CHEST_BARBELL_DIFF_SPEC.md` — this file (status tracking)
- `bugs_to_fix/assets/gymvisual_chest_barbell_report.md` — deliverable report (written at Step 3)

---

## How to QA

1. Read `bugs_to_fix/assets/gymvisual_chest_barbell_report.md`.
2. Confirm Section A lists the 3 chest+barbell DB exercises with correct searchNames and webp status.
3. Spot-check 2–3 match decisions in Section B for obvious errors.
4. Confirm Section C lists exercises we genuinely don't have (no hallucinations).
