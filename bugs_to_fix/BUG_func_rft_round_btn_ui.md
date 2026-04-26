# BUG: RFT "ROUND ✓" button too small; AMRAP and RFT round button UI inconsistent

## Status
[ ] Open

## Severity
P2 normal
- The primary action button in RFT is the most-tapped element during a block — it must be large and easy to tap mid-workout

## Description
In the RFT overlay the "ROUND ✓" confirmation button is too small. In AMRAP, the equivalent action (the BlindTapZone or round-tap element) has a different visual treatment. Both overlays perform the same core action (log a completed round) and should share the same large, prominent button UI.

Design direction (use /ui-ux-pro-max):
- The ROUND button in RFT should be a large, full-width (or near full-width) primary action button, easy to tap with a thumb at any angle.
- AMRAP and RFT should use the same button component/style for this action so the UX is consistent across block types.

## Steps to Reproduce
1. Create an RFT block (e.g. 5 rounds, Burpee + Box Jump).
2. Start the workout and tap ▶ START BLOCK.
3. Observe: the "ROUND ✓" button is small and hard to tap confidently.
4. Compare with AMRAP BlindTapZone — the visual and interaction patterns differ.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/runner/RftOverlay.kt`, `ui/workout/runner/AmrapOverlay.kt`

## Assets
- Related spec: `FUNCTIONAL_TRAINING_SPEC.md`
- Design tool: invoke `/ui-ux-pro-max` when implementing

## Fix Notes
<!-- populated after fix is applied -->
