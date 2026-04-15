# Profile & Settings — Separation and Enhancement Spec

| | |
|---|---|
| **Phase** | P2 (split + fitness level + RPE toggle) · P3 (health history) |
| **Status** | `done` (§1 split + §2 health history + §3 fitness level); §4 RPE auto-pop `not-started` |
| **Effort** | M (split) · S (fitness level) · S (RPE toggle) · L (health history) |
| **Depends on** | §2 Health History depends on §1 Profile/Settings split |
| **Roadmap** | `ROADMAP.md §P2` and `§P3` |

**Related specs:** `SETTINGS_SPEC.md`, `PROFILE_SETUP_SPEC.md`, `NAVIGATION_SPEC.md`

---

## 1. Split Profile and Settings into Separate Pages

### Current State
Profile and Settings are combined in a single screen (`SettingsScreen.kt`). A "Profile" section with an "Edit" row lives at the top, followed by all other settings sections.

### What to Build
Two fully separate screens accessible from the top of the main scaffold:

| Screen | Icon | Route |
|---|---|---|
| `ProfileScreen` | `Icons.Default.Person` (filled, user avatar style) | `Routes.PROFILE` |
| `SettingsScreen` (trimmed) | `Icons.Default.Settings` | `Routes.SETTINGS` (existing) |

### Navigation Change
- In `MainAppScaffold` top bar: add a **Profile icon button** immediately to the left of the existing **Settings icon button**
- Both icons sit in the `actions` slot of `TopAppBar`
- Profile icon: user avatar / person icon — if user has a photo, show circular avatar; otherwise show `Icons.Default.AccountCircle`
- Settings icon: existing gear icon (unchanged)

### ProfileScreen Contents
Move from current SettingsScreen into the new ProfileScreen:
- Profile header: avatar + display name + email
- **Personal Info card** (name, DOB, gender, height, weight, goals, chronotype, occupation) — currently in Settings
- **Body Metrics card** (latest weight, body fat, BMI) — currently in Settings
- **Health History section** (see §2 below — new)
- **Fitness Level section** (see §3 below — new)

### SettingsScreen Contents (trimmed)
Remove the Profile section entirely. Keep all other cards:
- Appearance (Theme)
- Workout settings
- Rest timer settings
- Units & Localisation
- Advanced
- Health Connect
- Data management
- Contact and support
- Log out

---

## 2. Health History — Injury & Medical Ledger

### Purpose
Give users a place to record past injuries, surgeries, chronic conditions, or movement restrictions. This data will eventually be fed to the AI personal trainer to personalise exercise selection and RPE targets.

### What to Build
A "Health History" card inside ProfileScreen with a list of health entries and an add button.

### Data Model (new Room entity)

```kotlin
@Entity(tableName = "health_history_entries")
data class HealthHistoryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val type: HealthEntryType,        // INJURY, SURGERY, CONDITION, RESTRICTION
    val title: String,                // e.g. "Left knee ACL tear"
    val bodyRegion: String?,          // e.g. "Left knee", "Lower back"
    val severity: HealthSeverity,     // MILD, MODERATE, SEVERE, RESOLVED
    val startDate: Long?,             // epoch ms
    val resolvedDate: Long?,          // null = ongoing
    val notes: String?,
    val affectedExerciseIds: List<String> = emptyList(),  // JSON-serialised
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val firestoreId: String? = null,
    val lastModifiedAt: Long = System.currentTimeMillis()
)

enum class HealthEntryType { INJURY, SURGERY, CONDITION, RESTRICTION, OTHER }
enum class HealthSeverity  { MILD, MODERATE, SEVERE, RESOLVED }
```

### UI Spec — Health History Card
- Card title: "HEALTH HISTORY" with `+` `IconButton` (opens Add sheet)
- List of entries, each row:
  - Colored left border by severity: `ProError` = SEVERE, amber = MODERATE, `TimerGreen` = RESOLVED, grey = MILD
  - Title + body region subtitle
  - Severity badge chip (e.g. "Ongoing" / "Resolved")
  - Tap → opens edit bottom sheet
- **Add/Edit ModalBottomSheet** fields:
  - Type (segmented: Injury / Surgery / Condition / Restriction / Other)
  - Title (free text, required)
  - Body region (free text with suggestions: "Left knee", "Lower back", "Left shoulder", etc.)
  - Severity (Mild / Moderate / Severe / Resolved)
  - Start date picker
  - Resolved date picker (shown only if severity = RESOLVED)
  - Notes (multiline, optional)
  - "Affected exercises" — future phase (link to exercise library)
- **Empty state:** "Add injuries or conditions to help your trainer personalise your programme"

### Auto Red/Yellow List Mapping (implement in same step)
The `MedicalLedger` entity already exists (`data/database/MedicalLedger.kt`) with `redListJson`, `yellowListJson`, and `injuryHistorySummary`. The exercise library already reads from it to show avoid/caution warnings.

When a `HealthHistoryEntry` is saved or deleted, recompute `MedicalLedger` from all active entries:
- Severity `SEVERE` + ongoing + `affectedExerciseIds` non-empty → add exercise IDs to `redListJson`
- Severity `MODERATE` + ongoing + `affectedExerciseIds` non-empty → add to `yellowListJson`
- Severity `RESOLVED` → remove from both lists
- `injuryHistorySummary`: auto-generate a concatenation of all active entry titles + notes for AI trainer context

This logic lives in `MedicalLedgerRepository` — call it from the same ViewModel method that saves/deletes a `HealthHistoryEntry`.

### AI Integration Note (future)
When AI personal trainer is implemented, pass `injuryHistorySummary` (auto-generated above) as context in the system prompt.

---

## 3. Fitness Level — Trainer Experience

### Purpose
A self-reported training experience level that helps the AI trainer calibrate volume recommendations, intensity targets, and exercise complexity.

### Options Considered

| Option | Pros | Cons |
|---|---|---|
| Beginner / Intermediate / Advanced | Simple, widely understood | Crude — ignores movement-specific experience |
| Training Age (years lifting) | More precise | Users lie / miscalibrate |
| Per-movement proficiency | Most accurate | Too complex for onboarding |
| **Recommended: Hybrid level + training age** | Balances simplicity with nuance | Requires 2 fields |

### Recommended Approach
Two fields in the Profile:
1. **Experience Level** — `ExperienceLevel` enum: `NOVICE`, `TRAINED`, `EXPERIENCED`, `ATHLETE`
   - Descriptions shown in UI:
     - Novice: < 1 year consistent training, still learning movement patterns
     - Trained: 1–3 years, comfortable with compound lifts, tracking progress
     - Experienced: 3+ years, understands periodisation, approaching natural ceiling
     - Athlete: Competitive or performance-driven, coach-level training knowledge
2. **Training Age** — integer years (0–30+), shown as a slider or number picker

### UI Spec — Fitness Level Card
- Card title: "FITNESS LEVEL"
- `ExperienceLevel` displayed as 4 tappable cards in a 2×2 grid, each with icon + label + one-line description
- Selected card gets `primaryContainer` fill + `primary` border
- Training age: `Slider` or `OutlinedTextField` below the grid (0–30, step 1)
- Saves to user profile on change (same `UserRepository.updateProfile()` flow)

### DB Change
Add to `User` entity (or `AppSettingsDataStore`):
```
experienceLevel: String (enum name, default "BEGINNER")
trainingAgeYears: Int (default 0)
```
Requires Room migration increment.

---

## 4. RPE Auto-Pop Setting

### What to Build
A new toggle in **Settings → Workout** section:

> **Use RPE**  
> When enabled, the RPE keyboard pops up automatically after completing each set

### Behaviour
- **Toggle OFF (default):** current behaviour — RPE field is optional, no auto-focus
- **Toggle ON:** immediately after `onCompleteSet()` fires, focus the RPE `TextField` for that set and open the keyboard. After RPE is entered (or user dismisses), dismiss the keyboard.

### Implementation Notes
- Store in `AppSettingsDataStore` as `useRpeAutoPop: Flow<Boolean>` (default `false`)
- In `WorkoutSetRow` (and `CardioSetRow`, `TimedSetRow`): observe the setting; after set completion, conditionally call `rpeFocusRequester.requestFocus()`
- Pair with a `SideEffect` or `LaunchedEffect(set.isCompleted)` guard so focus only requests once per completion event

---

## DB Changes

**Current DB is v38 — assign next available versions at implementation time. Each migration should be a separate version increment.**

### Migration A — Health History (§2)
```sql
CREATE TABLE health_history_entries (
    id TEXT PRIMARY KEY NOT NULL,
    userId TEXT NOT NULL,
    type TEXT NOT NULL,
    title TEXT NOT NULL,
    bodyRegion TEXT,
    severity TEXT NOT NULL,
    startDate INTEGER,
    resolvedDate INTEGER,
    notes TEXT,
    affectedExerciseIds TEXT NOT NULL DEFAULT '[]',
    createdAt INTEGER NOT NULL,
    isArchived INTEGER NOT NULL DEFAULT 0,
    firestoreId TEXT,
    lastModifiedAt INTEGER NOT NULL
);
```

### Migration B — Fitness Level (§3)
```sql
ALTER TABLE users ADD COLUMN experienceLevel TEXT NOT NULL DEFAULT 'NOVICE';
ALTER TABLE users ADD COLUMN trainingAgeYears INTEGER NOT NULL DEFAULT 0;
```

---

## Files to Change

### New Files
| File | Purpose |
|---|---|
| `ui/settings/ProfileScreen.kt` | New Profile screen — avatar, Personal Info, Body Metrics, Health History card, Fitness Level card |
| `ui/settings/ProfileViewModel.kt` | Aggregates profile state; health history CRUD; fitness level updates |
| `data/database/HealthHistoryEntry.kt` | New Room entity |
| `data/database/HealthHistoryDao.kt` | New DAO — insert, update, delete, getAll flow |
| `data/repository/HealthHistoryRepository.kt` | CRUD + auto-mapping trigger to MedicalLedgerRepository |

### Modified Files
| File | Change |
|---|---|
| `ui/navigation/PowerMeNavigation.kt` | Add `Routes.PROFILE`; wire `ProfileScreen` composable |
| `ui/main/MainAppScaffold.kt` | Add Profile `IconButton` to TopAppBar actions, left of Settings icon |
| `ui/settings/SettingsScreen.kt` | Remove Profile section (Personal Info, Body Metrics cards) — they move to ProfileScreen |
| `data/database/User.kt` | Add `experienceLevel: String`, `trainingAgeYears: Int` |
| `data/database/PowerMeDatabase.kt` | Add `HealthHistoryEntry::class` to entities; add `healthHistoryDao()`; add both migrations |
| `di/DatabaseModule.kt` | Add `HealthHistoryDao` and `HealthHistoryRepository` Hilt providers |
| `data/repository/MedicalLedgerRepository.kt` | Add `rebuildFromHealthHistory(entries)` — recomputes red/yellow lists from active entries |
| `data/AppSettingsDataStore.kt` | Add `useRpeAutoPop: Flow<Boolean>` (default `false`) |
| `ui/workout/ActiveWorkoutScreen.kt` | `WorkoutSetRow`: conditional `rpeFocusRequester.requestFocus()` after `onCompleteSet` when `useRpeAutoPop` is true |

---

## QA Checklist (§1–§3, implemented April 2026)

### Navigation
- [ ] Sign in — TopAppBar shows two icons: AccountCircle (left) and Settings gear (right)
- [ ] Tap AccountCircle → Profile screen opens with back arrow and title "Profile"
- [ ] Tap back → returns to the previous tab
- [ ] Tap Settings gear → Settings screen opens (unchanged behaviour)

### Settings screen (trimmed)
- [ ] Settings screen no longer contains a "Personal Info" card
- [ ] Settings screen no longer contains a "Body Metrics" card
- [ ] All remaining cards present: Appearance, Units, Health Connect, Rest Timer, Display & Workout, Data Export, Cloud Sync, Privacy

### Profile screen — Personal Info
- [ ] Personal Info card shows all fields: Name, Date of Birth, Avg Sleep, Children, Gender, Occupation, Chronotype, Training Goals
- [ ] Edit a field and tap "Save Changes" → shows spinner, then "Saved ✓" for ~2 s
- [ ] Re-open Profile → saved values persist

### Profile screen — Body Metrics
- [ ] Body Metrics card shows Weight, Body Fat, Height inputs with "Last: …" summary lines
- [ ] Enter values and save → "Last:" labels update on next open

### Profile screen — Fitness Level (§3)
- [ ] Four tappable tiles: Novice, Trained, Experienced, Athlete — each shows label + description
- [ ] Tapping a tile highlights it (`primaryContainer` fill + `primary` border); previously selected tile deselects
- [ ] Training Age slider moves 0–30 and shows the current value
- [ ] Close and re-open Profile → selected tile and slider value persist

### Profile screen — Health History (§2)
- [ ] Health History card shows "+" button
- [ ] Tap "+" → ModalBottomSheet opens with fields: Type (segmented), Title, Body Region, Severity, Start Date, Notes
- [ ] Fill fields and tap Save → entry appears in the list with a colored left border:
  - SEVERE → red (`ProError`)
  - MODERATE → amber
  - MILD → grey
  - RESOLVED → green (`TimerGreen`)
- [ ] Tap an existing entry → edit sheet opens pre-filled; save updates the entry
- [ ] Empty state text shown when no entries exist
- [ ] Add a SEVERE entry with affected exercises → open Exercise Library → those exercises show an "Avoid" warning badge
- [ ] Mark that entry as RESOLVED → "Avoid" badge disappears from those exercises

*Written April 2026.*
