# Functional Block Card Layout (single card per block in template builder)

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `completed` |
| **Effort** | S |
| **Depends on** | func_template_wizard ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/TemplateBuilderScreen.kt` |

---

## Overview

Currently, functional blocks (AMRAP, RFT, EMOM, TABATA) in the template builder render each exercise as its own `Card`, identical to how STRENGTH training exercises are displayed. This creates visual confusion — the block header floats above a list of individual exercise cards, making it unclear that the exercises belong together inside a single block.

This task changes the layout so that each functional block renders as a **single `Card`** containing the `BlockHeader` at the top and all of the block's exercise rows inside it. STRENGTH blocks and unblocked exercises retain the existing per-exercise card layout (backward compatible).

---

## Behaviour

- **Functional blocks (AMRAP / RFT / EMOM / TABATA):** rendered as one `Card` per block.
  - Card contains: `BlockHeader` composable + exercise rows for all exercises in that block.
  - Card has a single drag handle at the card level for block reordering.
  - Exercise rows within the card remain individually reorderable (within the block only).
  - Exercise rows do NOT have their own card wrapper.
- **STRENGTH blocks / unblocked exercises:** no change — individual exercise cards as before.
- **Mixed-type routines (HYBRID):** each block type renders with its own rule independently.
- **Organize / Reorder mode:** block-level drag handles remain visible on functional block cards; per-exercise drag handles remain on STRENGTH exercise cards.

---

## UI Changes

- Functional block `BlockHeader` + exercise rows are wrapped in a single `Card` using `MaterialTheme.shapes.medium` and `MaterialTheme.colorScheme.surfaceVariant` background (or `surface` — match the existing card style used for exercise rows).
- Remove the `Card` wrapper from individual exercise rows that belong to a functional block.
- Block padding: `16.dp` horizontal inside the outer card; exercise rows have the existing internal padding.
- No visual change to STRENGTH exercise rows.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/TemplateBuilderScreen.kt` — restructure `LazyColumn` items so functional block groups emit a single `Card` item rather than a `BlockHeader` item + N individual exercise card items

---

## How to QA

1. Open a PURE_FUNCTIONAL routine with 2 blocks (e.g. AMRAP 12min + RFT 5rds).
2. Verify each block renders as one card: block header at the top, exercises listed inside the same card.
3. Verify no individual card border/shadow appears around each exercise row within a functional block.
4. Open a PURE_GYM routine — verify exercises still show individual cards (no regression).
5. Open a HYBRID routine with 1 STRENGTH block + 1 AMRAP block — verify STRENGTH exercises are individual cards; AMRAP block is a single card.
6. Reorder exercises within a functional block card — verify drag-reorder still works.
7. Reorder blocks (block-level drag handles) — verify block card moves as a unit.
8. Delete a block via overflow menu — verify entire card disappears.
