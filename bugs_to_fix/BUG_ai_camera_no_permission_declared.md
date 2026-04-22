# BUG: AI photo flow Camera button silently no-ops on fresh install

## Status
[ ] Open

## Severity
P2 normal
- Not a crash or data loss; gallery picker still works as a fallback path.
- But the Camera button produces no visible error — it just does nothing, which is confusing.

## Description
`AiWorkoutGenerationScreen.kt` guards its Camera button behind a `ContextCompat.checkSelfPermission(CAMERA)` check, and calls `ActivityResultContracts.RequestPermission()` when permission is not yet granted. However, `android.permission.CAMERA` is **not declared** in `AndroidManifest.xml`. On modern Android (API 23+), requesting an undeclared permission returns `granted = false` immediately without showing any system dialog. As a result, on a fresh install (or after clearing app data), tapping Camera silently no-ops — no permission prompt, no camera launch, no error message.

Root cause area: `AiWorkoutGenerationScreen.kt` lines 98–103 (permission result callback) and 148–158 (permission check in `onOpenCamera`).

Recommended fix: **drop the permission check entirely**. `ActivityResultContracts.TakePicture()` uses `Intent.ACTION_IMAGE_CAPTURE`, which delegates to the system camera app and does not require the calling app to hold `android.permission.CAMERA`. Remove the `cameraPermission` launcher and its surrounding `if/else` branch; call `cameraLauncher.launch(uri)` directly.

## Steps to Reproduce
1. Fresh install (or Settings → Apps → PowerME → Clear Data).
2. Navigate to Workouts tab.
3. Tap **Quick Start** → **Add from picture**.
4. Ensure the AI screen is in photo/picture mode.
5. Tap the **Camera** button.
6. Observe: nothing happens — no permission dialog, no camera app opens.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt`

## Assets
- Related spec: `AI_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
