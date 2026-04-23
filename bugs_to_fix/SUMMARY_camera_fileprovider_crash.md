# Fix Summary: Camera crashes app in AI photo flow

## Root Cause
`AiWorkoutGenerationScreen` calls `FileProvider.getUriForFile()` to produce a content URI for the camera output file. This API requires two declarations that were both missing:
1. A `<provider>` entry in `AndroidManifest.xml` registering `androidx.core.content.FileProvider`
2. A `res/xml/file_paths.xml` resource listing which directories the provider may share

Without them, `FileProvider.getUriForFile()` throws `IllegalArgumentException: Failed to find configured root that contains .../cache/ai_workout_photo_*.jpg` at the moment the Camera button is tapped, crashing the app immediately.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added `<provider>` block for `androidx.core.content.FileProvider` with `${applicationId}.provider` authority |
| `app/src/main/res/xml/file_paths.xml` | New file — declares `<cache-path name="ai_photo" path="." />` so the provider can share files from `cacheDir` |

## Surfaces Fixed
- AI photo flow: Camera button no longer crashes; camera app opens and returns photo to app correctly

## How to QA
1. Workouts → Quick Start → Add from picture
2. Tap **Camera**
3. Camera app opens — take a photo
4. App receives the photo and begins OCR processing (spinner visible)
5. No crash at any point
