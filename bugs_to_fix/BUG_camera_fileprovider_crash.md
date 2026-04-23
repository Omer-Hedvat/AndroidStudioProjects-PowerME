# BUG: Camera crashes app in AI photo flow — FileProvider not declared

## Status
[x] Fixed

## Severity
P0 blocker — app crashes immediately on Camera tap; photo-to-workout flow completely unusable

## Description
Tapping **Camera** in the AI photo flow calls `FileProvider.getUriForFile()` to create a temp URI for the camera output. This requires two things that are both missing:

1. A `<provider>` entry in `AndroidManifest.xml` declaring `androidx.core.content.FileProvider` with `android:authorities="${applicationId}.provider"`
2. A `res/xml/file_paths.xml` resource defining which directories the provider is allowed to share (at minimum: `<cache-path name="ai_photo" path="." />`)

Without these, `FileProvider.getUriForFile()` throws `IllegalArgumentException: Failed to find configured root that contains .../cache/ai_workout_photo_*.jpg` at the point the Camera button is tapped, crashing the app.

## Steps to Reproduce
1. Launch app → Workouts → Quick Start → Add from picture
2. Tap **Camera**
3. Observe: app crashes immediately with `IllegalArgumentException`

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/file_paths.xml` (new)

## Assets
- Related spec: `AI_SPEC.md` (§4 photo input flow)

## Fix
1. Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="ai_photo" path="." />
</paths>
```

2. Add to `AndroidManifest.xml` inside `<application>`:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

## Fix Notes
<!-- populated after fix is applied -->
