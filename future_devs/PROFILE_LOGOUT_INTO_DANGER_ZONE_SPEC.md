# Profile — Move Log Out into Danger Zone

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | move_privacy_to_profile ✅ |
| **Blocks** | — |
| **Touches** | `ui/profile/ProfileScreen.kt` |

---

## Overview

The "Log Out" button (Firebase app sign-out) currently sits as a standalone `OutlinedButton` above the "Danger Zone" divider in `ProfileScreen`. Since log-out is an account lifecycle action — the same category as Delete Account — it belongs inside the Danger Zone section, ordered above Delete Account.

---

## Current layout

```
HcMetricsCard
[Log Out]          ← standalone, above divider
─────────────────
Danger Zone
[Delete Account]
```

## Target layout

```
HcMetricsCard
─────────────────
Danger Zone
[Log Out]          ← first action inside Danger Zone
[Delete Account]   ← below Log Out, unchanged
```

---

## Implementation

In `ProfileScreen.kt`, move the `Log Out` `item { }` block into the existing Danger Zone `item { }` block, placing it above the Delete Account button. Add a `Spacer(8.dp)` between Log Out and Delete Account. Remove the standalone `item { }` for Log Out that currently lives above the Danger Zone divider.

No ViewModel, navigation, or logic changes — pure layout reorder within the LazyColumn.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/profile/ProfileScreen.kt` — move Log Out button inside Danger Zone item block

---

## How to QA

1. Open Profile → scroll to bottom.
2. Verify the "Danger Zone" section contains two buttons in order: "Log Out" then "Delete Account".
3. Verify there is no standalone Log Out button above the Danger Zone divider.
4. Tap Log Out → verify confirmation dialog appears → confirm → verify sign-out navigates to Welcome screen.
