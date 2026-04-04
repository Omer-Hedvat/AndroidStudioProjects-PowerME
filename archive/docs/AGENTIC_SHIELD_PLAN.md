# PowerME: "The Agentic Shield" Implementation Plan

## Mission Statement
Complete v7 → v10 transformation with validation-driven development, prioritizing data safety and test coverage before feature rollout.

## User Context
- **Name**: the developer
- **Age**: 38 years
- **Height**: 181.5cm (longer femurs → biomechanical adjustments)
- **Injuries**: L4-L5 (Lower Back), Medial Epicondylitis (Elbow)
- **Training Goal**: Hypertrophy
- **Design**: OLED Black (#000000) + Cyber-Lime (#32CD32)

## Implementation Philosophy: "Safety First"
1. **Test before you build** - Unit tests for critical parsers
2. **Validate before you migrate** - Pre-migration data integrity checks
3. **Guard before you expose** - SQL console security guardrails
4. **Log before you execute** - Debug visibility for AI responses

---

## PHASE 1: TESTING & SECURITY FOUNDATION

### 1.1 Unit Testing - ActionParserTest.kt ⭐ START HERE
**Goal**: Prove ActionParser can reliably extract JSON from noisy Gemini responses

**Location**: `/app/src/test/java/com/powerme/app/actions/ActionParserTest.kt`

**Test Cases** (10 scenarios):
1. ✅ **Plain JSON** - `{"action": "update_weight", "weightKg": 92.0}`
2. ✅ **Markdown Code Block** - ` ```json\n{...}\n``` `
3. ✅ **Text + JSON** - "Sure! I'll update that for you.\n```json\n{...}\n```"
4. ✅ **Multiple Actions** - Two JSON blocks in one response
5. ✅ **Malformed JSON** - Missing closing brace
6. ✅ **Wrong Action Type** - `{"action": "invalid_action"}`
7. ✅ **Missing Required Field** - UpdateWeight without weightKg
8. ✅ **Extra Whitespace** - JSON with leading/trailing spaces
9. ✅ **Unicode Characters** - Exercise names with accents
10. ✅ **No JSON Present** - Plain text response only

**Success Criteria**:
- All valid JSON formats extracted correctly
- Malformed JSON returns empty list (graceful failure)
- No crashes on edge cases

**Dependencies**:
- JUnit 4
- Kotlinx Serialization

**Implementation Steps**:
1. Add JUnit dependency to build.gradle.kts
2. Create test fixtures (sample Gemini responses)
3. Write test class with @Test annotations
4. Verify parser handles all 10 cases
5. Add edge case tests (empty strings, null values)

---

### 1.2 Migration Guard - PreMigrationValidator.kt
**Goal**: Ensure zero data loss during v7 → v10 migrations

**Location**: `/app/src/main/java/com/powerme/app/data/database/PreMigrationValidator.kt`

**Validation Checks**:
```kotlin
data class MigrationSnapshot(
    val workoutCount: Int,
    val setCount: Int,
    val exerciseCount: Int,
    val chatMessageCount: Int,
    val healthStatsCount: Int,
    val timestamp: Long
)
```

**Pre-Migration Steps**:
1. Count all records in critical tables
2. Store snapshot in SharedPreferences
3. Run migration in transaction
4. Post-migration: Compare counts
5. If mismatch detected: Log error + notify user

**Critical SQL Queries**:
```sql
-- Pre-migration snapshot
SELECT COUNT(*) FROM workouts;
SELECT COUNT(*) FROM workout_sets;
SELECT COUNT(*) FROM exercises;
SELECT COUNT(*) FROM chat_messages;

-- Post-migration verification
SELECT COUNT(*) FROM workouts; -- Must match
SELECT COUNT(*) FROM workout_sets; -- Must match
SELECT COUNT(*) FROM exercises; -- Must match
SELECT COUNT(*) FROM chat_messages; -- Must match

-- New table checks
SELECT COUNT(*) FROM gym_profiles; -- Expect 2 (Home, Work)
SELECT COUNT(*) FROM injury_tracker; -- Expect 2 (L4-L5, Elbow)
SELECT COUNT(*) FROM user_biometrics; -- Expect 1 (singleton)
```

**Rollback Strategy**:
- If validation fails: Do NOT commit transaction
- Display error dialog with snapshot counts
- Provide "Export Data" button for manual backup

---

### 1.3 SQL Console Security - SQLSafetyValidator.kt
**Goal**: Prevent accidental data destruction through SQL console

**Location**: `/app/src/main/java/com/powerme/app/ui/settings/SQLSafetyValidator.kt`

**Security Rules**:
1. ✅ **Allow**: SELECT statements only
2. ❌ **Block**: DROP, DELETE, UPDATE, INSERT, ALTER, CREATE, PRAGMA, ATTACH
3. ✅ **Auto-modify**: Append `LIMIT 100` if no LIMIT specified
4. ✅ **Read-only connection**: Use `db.openHelper.readableDatabase`

**Regex Patterns**:
```kotlin
private val selectPattern = "^SELECT\\s+.*".toRegex(RegexOption.IGNORE_CASE)
private val dangerousKeywords = listOf(
    "DROP", "DELETE", "UPDATE", "INSERT", "ALTER",
    "CREATE", "PRAGMA", "ATTACH", "DETACH", "VACUUM"
)
```

**Validation Flow**:
```
User Query → Trim whitespace → Check starts with SELECT
→ Check for dangerous keywords → Append LIMIT 100
→ Execute on read-only DB → Return results
```

**Error Messages**:
- "Only SELECT queries allowed for safety"
- "Dangerous keyword '{keyword}' detected - query blocked"
- "Query auto-limited to 100 rows to prevent UI freeze"

---

### 1.4 Debug Logging - GeminiResponseLogger.kt
**Goal**: Visibility into raw Gemini responses before parsing

**Location**: `/app/src/main/java/com/powerme/app/util/GeminiResponseLogger.kt`

**Log Levels**:
```kotlin
sealed class GeminiLogLevel {
    object Debug: GeminiLogLevel()      // Full response text
    object ActionOnly: GeminiLogLevel() // Only ActionBlock JSON
    object Error: GeminiLogLevel()      // Parse failures
}
```

**Implementation**:
```kotlin
class GeminiResponseLogger @Inject constructor() {
    fun logResponse(
        prompt: String,
        response: String,
        parsedActions: List<ActionBlock>,
        level: GeminiLogLevel = GeminiLogLevel.Debug
    ) {
        when (level) {
            GeminiLogLevel.Debug -> {
                Log.d("GeminiResponse", """
                    ===== PROMPT =====
                    $prompt
                    ===== RESPONSE =====
                    $response
                    ===== PARSED ACTIONS (${parsedActions.size}) =====
                    ${parsedActions.joinToString("\n")}
                """.trimIndent())
            }
            // ... other levels
        }
    }
}
```

**Dev Toast** (Debug builds only):
```kotlin
if (BuildConfig.DEBUG && parsedActions.isNotEmpty()) {
    Toast.makeText(
        context,
        "🤖 ${parsedActions.size} action(s) parsed",
        Toast.LENGTH_SHORT
    ).show()
}
```

---

## PHASE 2: THE "AGENTIC" BRAIN

### 2.1 JSON ActionBlock Parser Integration ✅ COMPLETED
**Status**: Already implemented in Sprint 4
- ActionParser extracts JSON from responses
- Handles plain JSON and markdown code blocks
- Returns List<ActionBlock>

### 2.2 Command Execution ⚠️ PARTIALLY COMPLETED
**Implemented**:
- ✅ UpdateWeight (functional)
- ⚠️ UpdateInjury (stub - needs InjuryTrackerRepository)
- ⚠️ SwitchGym (stub - needs GymProfileRepository)
- ⚠️ UpdateEquipment (stub - needs GymProfileRepository)

**Remaining Work**:
1. Create InjuryTrackerRepository (Sprint 6)
2. Create GymProfileRepository (Sprint 5)
3. Implement remaining action strategies
4. Add integration tests

### 2.3 Context Injection Enhancement ✅ COMPLETED
**Status**: System prompt updated with:
- ActionBlock protocol
- 181.5cm biomechanics awareness
- L4-L5 protection strategy
- Format examples for all actions

**Remaining Work**:
- Replace hardcoded UserProfile with UserBiometrics from DB
- Add activeGym context from GymProfileRepository
- Add active injuries from InjuryTrackerRepository

---

## PHASE 3: THE CONTENT REVOLUTION

### 3.1 Master Exercise Seed - MasterExercises.json
**Goal**: 150+ exercises organized into families with biomechanical metadata

**Location**: `/app/src/main/res/raw/master_exercises.json`

**Schema**:
```json
{
  "version": "1.0",
  "lastUpdated": "2026-02-16",
  "exercises": [
    {
      "name": "Barbell Back Squat",
      "muscleGroup": "Legs",
      "equipmentType": "Barbell",
      "exerciseType": "STRENGTH",
      "familyId": "squat_family",
      "youtubeVideoId": "ultWZbUMPL8",
      "setupNotes": "Height 181.5cm: Bar on upper traps. Longer femurs require 30-35° torso lean. Keep neutral spine (L4-L5). Knees track over toes.",
      "committeeNotes": "Dr. Brad: 8-12 reps for hypertrophy. Noa: Monitor L4-L5. Boris: Top set + back-offs.",
      "restDurationSeconds": 180,
      "barType": "STANDARD",
      "isFavorite": false,
      "isCustom": false
    }
  ]
}
```

**Exercise Families** (8 base → 150+ total):
1. **Squat Family** (20): Back Squat, Front Squat, Goblet, Bulgarian Split, Leg Press, Hack, Safety Bar, etc.
2. **Deadlift Family** (15): Conventional, RDL, Trap Bar, Sumo, Single-Leg RDL, etc.
3. **Bench Press Family** (18): Flat BB, Incline DB, Decline, Cable Fly, Machine Press, etc.
4. **Row Family** (18): BB Row, DB Row, Chest-Supported, Cable, T-Bar, Seal Row, etc.
5. **Overhead Press Family** (12): Standing OHP, Seated DB, Machine, Arnold Press, etc.
6. **Pull-up/Lat Family** (15): Pull-ups, Chin-ups, Lat Pulldown, Neutral Grip, etc.
7. **Leg Curl/Extension Family** (12): Lying, Seated, Nordic, Extension, etc.
8. **Accessories** (40+): Curls, Extensions, Raises, Face Pulls, Calves, Core, etc.

**Curated YouTube Sources**:
- **Renaissance Periodization** (Dr. Mike Israetel)
- **Jeff Nippard** (Science-based)
- **Athlean-X** (Injury prevention)

**Biomechanical Metadata Requirements**:
- Every setupNotes must reference 181.5cm height implications
- L4-L5 safety cues for spinal compression exercises
- Elbow-friendly grip alternatives for pulling movements
- Moment arm calculations where relevant

**Implementation Steps**:
1. Research and curate 150+ exercises with YouTube IDs
2. Write setupNotes for each (biomechanics-aware)
3. Organize into familyId groups
4. Create MasterExerciseSeeder.kt
5. Seed on first app launch or forced re-seed

---

### 3.2 YouTube Integration - YouTubePlayerBottomSheet.kt
**Goal**: In-app video demonstrations for exercises

**Dependency**:
```kotlin
implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
```

**Location**: `/app/src/main/java/com/powerme/app/ui/components/YouTubePlayerBottomSheet.kt`

**UI Flow**:
1. Exercise card shows play icon (bright if videoId present, dim if null)
2. Tap play → ModalBottomSheet slides up
3. YouTube player loads and cues video
4. User watches, then closes sheet

**Implementation**:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubePlayerBottomSheet(
    videoId: String,
    exerciseName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateGrey
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(exerciseName, fontSize = 20.sp, color = CyberLime)
            Spacer(modifier = Modifier.height(16.dp))

            AndroidView(
                factory = { context ->
                    YouTubePlayerView(context).apply {
                        addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                youTubePlayer.cueVideo(videoId, 0f)
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("CLOSE")
            }
        }
    }
}
```

---

### 3.3 Magic Add Dialog - MagicAddDialog.kt
**Goal**: Gemini-powered exercise creation

**Location**: `/app/src/main/java/com/powerme/app/ui/components/MagicAddDialog.kt`

**UI Flow**:
1. User taps "Magic Add" button
2. Dialog appears with text field
3. User types: "Cable Chest Fly"
4. Tap "SEARCH" → Loading state
5. Gemini returns: {muscleGroup: "Chest", equipmentType: "Cable", youtubeVideoId: "..."}
6. Preview card shows metadata
7. User taps "ADD" → Exercise created with isCustom=true

**Gemini Prompt**:
```
Exercise: "Cable Chest Fly"

Return ONLY JSON with this exact format:
{
  "muscleGroup": "Chest|Back|Legs|Shoulders|Arms|Core",
  "equipmentType": "Barbell|Dumbbells|Cable|Machine|Bodyweight",
  "youtubeVideoId": "video_id_from_RP_or_Jeff_Nippard_or_AthleanX",
  "setupNotes": "Form cues for 181.5cm height with L4-L5 and elbow safety"
}

If exercise not found, return: {"error": "Exercise not recognized"}
```

**Implementation**:
- MagicAddViewModel with Gemini API call
- MagicAddUiState (Idle, Loading, Success, Error)
- Error handling for unrecognized exercises
- Auto-populate new Exercise entity with isCustom=true

---

## PHASE 4: THE DS TOOLKIT & UI

### 4.1 Robust Statistics - RobustStatistics.kt
**Goal**: Modified Z-score (MAD-based) for small dataset outlier detection

**Location**: `/app/src/main/java/com/powerme/app/analytics/RobustStatistics.kt`

**Problem**: Standard Z-scores are sensitive to outliers in small samples (n=4-12 workouts/month)

**Solution**: Modified Z-score using Median Absolute Deviation
```
M_Z = 0.6745 * (x_i - median) / MAD
where MAD = median(|x_i - median|)
```

**Implementation**:
```kotlin
object RobustStatistics {
    fun calculateModifiedZScore(values: List<Double>, target: Double): Double? {
        if (values.size < 3) return null

        val sortedValues = values.sorted()
        val median = sortedValues[sortedValues.size / 2]

        val absoluteDeviations = values.map { abs(it - median) }
        val mad = absoluteDeviations.sorted()[absoluteDeviations.size / 2]

        if (mad == 0.0) return 0.0

        return 0.6745 * (target - median) / mad
    }
}
```

**Thresholds**:
- |M_Z| > 3.5: Extreme outlier (~3σ equivalent)
- |M_Z| > 2.5: Strong outlier (~2σ equivalent)
- |M_Z| > 2.0: Moderate outlier

**Update WeeklyInsightsCalculator**:
- Replace standard Z-score with Modified Z-score
- Adjust threshold from 2.0 to 2.5 (more conservative)
- Update Boaz's analysis text to reference MAD

---

### 4.2 Visual Badges - ZScoreBadge.kt
**Goal**: Color-coded statistical indicators in History UI

**Location**: `/app/src/main/java/com/powerme/app/ui/components/ZScoreBadge.kt`

**Design**:
```
┌─────────┐
│    M    │  <- Modified indicator
│  2.3    │  <- Z-score value
│  MAD    │  <- Method indicator
└─────────┘
```

**Color Coding**:
- |M_Z| > 3.5: CyberLime alpha=1.0 (bright)
- |M_Z| > 2.5: CyberLime alpha=0.8 (medium)
- |M_Z| > 2.0: CyberLime alpha=0.6 (dim)

**Placement**: VolumeAnomalyCard in MetricsScreen

---

### 4.3 Injury Tracker UI - InjuryTrackerCard.kt
**Goal**: Manual injury severity tracking (1-10 scale)

**Location**: `/app/src/main/java/com/powerme/app/ui/settings/InjuryTrackerCard.kt`

**UI Components**:
- Joint name (from TargetJoint enum)
- Active/Inactive toggle switch
- Severity slider (1-10)
- Risk level indicator: LOW (1-3), MODERATE (4-6), HIGH (7-10)
- Last updated timestamp
- Optional notes field

**Data Flow**:
```
User adjusts slider → ViewModel updates InjuryTracker
→ ExerciseRepository filters contraindicated exercises
→ War Room receives updated context
```

---

## IMPLEMENTATION ORDER (Recommended)

### Week 1: Safety & Testing
1. ✅ ActionParserTest.kt (10 test cases)
2. ✅ PreMigrationValidator.kt
3. ✅ SQLSafetyValidator.kt
4. ✅ GeminiResponseLogger.kt

### Week 2: Database & Repositories
5. ⚠️ Migration v8 → v9 (GymProfile entity)
6. ⚠️ GymProfileRepository + DAO
7. ⚠️ Migration v9 → v10 (InjuryTracker + UserBiometrics)
8. ⚠️ InjuryTrackerRepository + UserBiometricsRepository

### Week 3: Agentic Features
9. ⚠️ Complete ActionExecutor (SwitchGym, UpdateInjury, UpdateEquipment)
10. ⚠️ Update ContextInjector with DB-backed context
11. ⚠️ Integration tests for action execution

### Week 4: Content & DS Tools
12. ⚠️ MasterExercises.json (150+ exercises)
13. ⚠️ MasterExerciseSeeder.kt
14. ⚠️ YouTubePlayerBottomSheet.kt
15. ⚠️ RobustStatistics.kt + Modified Z-score
16. ⚠️ ZScoreBadge.kt + UI integration

### Week 5: Polish & Testing
17. ⚠️ MagicAddDialog.kt
18. ⚠️ InjuryTrackerCard.kt
19. ⚠️ SQL Console UI (triple-tap trigger)
20. ⚠️ End-to-end testing

---

## CRITICAL FILES INVENTORY

### Testing Infrastructure (NEW)
- `/app/src/test/java/com/powerme/app/actions/ActionParserTest.kt`
- `/app/src/main/java/com/powerme/app/data/database/PreMigrationValidator.kt`
- `/app/src/main/java/com/powerme/app/ui/settings/SQLSafetyValidator.kt`
- `/app/src/main/java/com/powerme/app/util/GeminiResponseLogger.kt`

### Database Layer (UPDATED)
- `/app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` - v10
- `/app/src/main/java/com/powerme/app/data/database/GymProfile.kt` - NEW
- `/app/src/main/java/com/powerme/app/data/database/InjuryTracker.kt` - NEW
- `/app/src/main/java/com/powerme/app/data/database/UserBiometrics.kt` - NEW
- `/app/src/main/java/com/powerme/app/di/DatabaseModule.kt` - Add migrations 8→9→10

### Actions Framework (COMPLETED)
- `/app/src/main/java/com/powerme/app/actions/ActionBlock.kt` ✅
- `/app/src/main/java/com/powerme/app/actions/ActionParser.kt` ✅
- `/app/src/main/java/com/powerme/app/actions/ActionExecutor.kt` ⚠️ Needs completion
- `/app/src/main/java/com/powerme/app/actions/ActionResult.kt` ✅

### War Room (UPDATED)
- `/app/src/main/java/com/powerme/app/ui/chat/ChatViewModel.kt` ✅
- `/app/src/main/java/com/powerme/app/ui/chat/ContextInjector.kt` ✅
- `/app/src/main/java/com/powerme/app/ui/chat/WarRoomChatScreen.kt` ✅

### Content & UI (NEW)
- `/app/src/main/res/raw/master_exercises.json` - 150+ exercises
- `/app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt`
- `/app/src/main/java/com/powerme/app/ui/components/YouTubePlayerBottomSheet.kt`
- `/app/src/main/java/com/powerme/app/ui/components/MagicAddDialog.kt`
- `/app/src/main/java/com/powerme/app/ui/components/ZScoreBadge.kt`
- `/app/src/main/java/com/powerme/app/ui/settings/InjuryTrackerCard.kt`

### Analytics (UPDATED)
- `/app/src/main/java/com/powerme/app/analytics/RobustStatistics.kt` - NEW
- `/app/src/main/java/com/powerme/app/analytics/WeeklyInsightsCalculator.kt` - Update to use MAD

---

## SUCCESS METRICS

### Phase 1 (Testing & Security)
- ✅ 10/10 ActionParser test cases pass
- ✅ Pre-migration validator catches count mismatches
- ✅ SQL console blocks all dangerous keywords
- ✅ Dev toast shows parsed action count

### Phase 2 (Agentic Brain)
- ✅ "Set current weight to 95kg" updates last set
- ✅ "My back hurts at level 8" creates InjuryTracker entry
- ✅ "Switch to Home gym" changes active profile
- ✅ ActionBadge shows success/failure feedback

### Phase 3 (Content Revolution)
- ✅ 150+ exercises seeded with YouTube IDs
- ✅ YouTube player loads videos in ModalBottomSheet
- ✅ Magic Add correctly identifies exercise metadata
- ✅ setupNotes reference 181.5cm height for all exercises

### Phase 4 (DS Toolkit)
- ✅ Modified Z-score handles small datasets (n=4)
- ✅ Z-score badges display in History UI
- ✅ SQL console executes SELECT queries with LIMIT 100
- ✅ Injury tracker slider updates severity in real-time

---

## RISK MITIGATION

### Data Loss Risk
- **Mitigation**: PreMigrationValidator + transaction rollback
- **Backup**: Export JSON before migration
- **Recovery**: Import from JSON if validation fails

### AI Parsing Failures
- **Mitigation**: 10 test cases + graceful fallback
- **Logging**: GeminiResponseLogger captures raw responses
- **UX**: ActionBadge shows clear error messages

### SQL Injection Risk
- **Mitigation**: Strict regex + keyword blacklist
- **Defense**: Read-only database connection
- **Limit**: Auto-append LIMIT 100

### YouTube API Quota
- **Mitigation**: Use cueVideo() instead of loadVideo()
- **Fallback**: Disable autoplay to save quota
- **Alternative**: Link to YouTube app if quota exceeded

---

## NEXT STEPS

Start with Phase 1.1: **ActionParserTest.kt**

This will validate that our AI command parsing is bulletproof before we build the rest of the agentic features on top of it.
