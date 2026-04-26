---
name: Batch prompts and QA sessions
description: Merge tasks that touch the same screen or user flow into a single implementation prompt and a single QA pass — never one prompt per task
type: feedback
---

Never generate one prompt per task or one QA checklist per task when multiple tasks share the same screen or user flow.

**Why:** The user explicitly called this out — 4 small tasks on the same screen (e.g. Settings) should be one prompt and one QA pass, not four. Splitting them wastes time and creates unnecessary coordination overhead.

**How to apply:**
- When writing parallel session prompts: group tasks by the files/screens they touch. If 2+ tasks share the same primary screen or composable, combine them into one session prompt.
- When planning QA: group completed tasks by the navigation flow required to test them. Tasks that sit on the same screen, or on screens visited in the same natural user journey, belong in the same QA group.
- Before declaring "ready for QA" on a single task, check if other completed tasks live in the same flow — surface them and propose a combined QA checklist.
- Only split into separate QA groups when the flows are genuinely distinct (e.g. Trends tab vs AI generation screen vs CSV import — different tabs, different navigation paths, no shared journey).
