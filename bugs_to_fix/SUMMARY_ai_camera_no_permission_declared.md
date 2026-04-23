# SUMMARY: BUG_ai_camera_no_permission_declared

## Root Cause
`AiWorkoutGenerationScreen` wrapped its Camera button behind a `ContextCompat.checkSelfPermission(CAMERA)` check + `ActivityResultContracts.RequestPermission()` launcher. Because `android.permission.CAMERA` was never declared in `AndroidManifest.xml`, Android silently denies the runtime request without showing any dialog, so the camera path was permanently unreachable on any fresh install.

## Fix
Removed the `cameraPermission` launcher and its `if/else` permission branch entirely. `ActivityResultContracts.TakePicture()` delegates capture to the system camera app via `Intent.ACTION_IMAGE_CAPTURE` — the calling app does not need to hold `CAMERA` for this. The camera URI is now created and launched directly, with no permission check.

## Files Changed
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt`
