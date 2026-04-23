# BUG: Remove HC nuke debug tooling (temporary code, must not ship)

## Status
[ ] Open

## Severity
P1 high — debug-only code with a destructive button must not reach production

## Description
During the HC corrupted-record investigation session (2026-04-22), a debug "NUKE HC DATA" button and supporting code were added to PowerME. This code is intentionally kept until the HC lockup is resolved and `BUG_write_workout_session_oversized` is wrapped. Once both conditions are met, all of this must be reverted.

## Everything Added — Full Revert Checklist

### `health/HealthConnectManager.kt`
- [ ] Remove `nukePowerMEData(healthConnectClient, clientRecordIds)` function and its block comment

### `ui/settings/SettingsViewModel.kt`
- [ ] Remove `nukeHcInProgress: Boolean` field from `SettingsUiState`
- [ ] Remove `nukeHcResult: String?` field from `SettingsUiState`
- [ ] Remove `_nukePermissionPending: Boolean` private var
- [ ] Remove `prepareNukePermissionRequest()` function
- [ ] Remove `nukeHcData()` function
- [ ] Remove the nuke-awareness block from `onHealthConnectPermissionResult()` (the `_nukePermissionPending` check at the top of that function)
- [ ] Remove `androidx.health.connect.client.permission.HealthPermission` and `ExerciseSessionRecord` inline imports inside `onHealthConnectPermissionResult()`

### `ui/settings/SettingsScreen.kt`
- [ ] Remove the entire `// ── DEBUG: HC Nuke` `item { }` block (the red button, spinner, result label, and `writePermission` val) from the `LazyColumn`

## Dependencies
- **Depends on:** BUG_write_workout_session_oversized (must be Wrapped first), HC device lockup resolved
- **Blocks:** —
- **Touches:** `health/HealthConnectManager.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`

## Assets
- Related spec: `HEALTH_CONNECT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
