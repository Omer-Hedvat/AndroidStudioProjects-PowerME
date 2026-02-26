package com.omerhedvat.powerme.ui.chat

import android.content.Context
import com.omerhedvat.powerme.analytics.WeeklyInsights
import com.omerhedvat.powerme.data.database.HealthConnectSync
import com.omerhedvat.powerme.data.database.HealthStats
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutSet
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WorkoutSummary(
    val date: Long,
    val durationMinutes: Int,
    val totalVolume: Double,
    val exercises: List<String>,
    val notes: String?
)

@Serializable
data class BiometricsSummary(
    val sleepDurationMinutes: Int?,
    val heartRateVariability: Double?,
    val restingHeartRate: Int?,
    val steps: Int?,
    val highFatigueFlag: Boolean = false,
    val anomalousRecoveryFlag: Boolean = false,
    val weightKgAvg7d: Double? = null,
    val bodyFatPctAvg7d: Double? = null
)

@Serializable
data class ContextPacket(
    val recentWorkouts: List<WorkoutSummary>,
    val currentBiometrics: BiometricsSummary?
)

@Singleton
class ContextInjector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    /**
     * Builds Layer 5 — MomentumData (appended to each user message).
     * Contains last 3 sessions + biometrics. Zero PII — no names or personal identifiers.
     */
    fun buildContextPacket(
        workouts: List<Workout>,
        workoutSets: Map<Long, List<WorkoutSet>>,
        exerciseNames: Map<Long, String>,
        healthStats: HealthStats?,
        healthConnectSync: HealthConnectSync? = null,
        weightKgAvg7d: Double? = null,
        bodyFatPctAvg7d: Double? = null
    ): String {
        val workoutSummaries = workouts.take(3).map { workout ->
            val sets = workoutSets[workout.id] ?: emptyList()
            val exerciseList = sets
                .map { set -> exerciseNames[set.exerciseId] ?: "Unknown" }
                .distinct()

            WorkoutSummary(
                date = workout.timestamp,
                durationMinutes = workout.durationSeconds / 60,
                totalVolume = workout.totalVolume,
                exercises = exerciseList,
                notes = workout.notes
            )
        }

        val biometrics = if (healthConnectSync != null) {
            BiometricsSummary(
                sleepDurationMinutes = healthConnectSync.sleepDurationMinutes,
                heartRateVariability = healthConnectSync.hrv,
                restingHeartRate = healthConnectSync.rhr,
                steps = healthConnectSync.steps,
                highFatigueFlag = healthConnectSync.highFatigueFlag,
                anomalousRecoveryFlag = healthConnectSync.anomalousRecoveryFlag,
                weightKgAvg7d = weightKgAvg7d,
                bodyFatPctAvg7d = bodyFatPctAvg7d
            )
        } else {
            healthStats?.let {
                BiometricsSummary(
                    sleepDurationMinutes = it.sleepDurationMinutes,
                    heartRateVariability = it.heartRateVariability,
                    restingHeartRate = it.restingHeartRate,
                    steps = it.steps,
                    weightKgAvg7d = weightKgAvg7d,
                    bodyFatPctAvg7d = bodyFatPctAvg7d
                )
            } ?: if (weightKgAvg7d != null || bodyFatPctAvg7d != null) {
                BiometricsSummary(
                    sleepDurationMinutes = null,
                    heartRateVariability = null,
                    restingHeartRate = null,
                    steps = null,
                    weightKgAvg7d = weightKgAvg7d,
                    bodyFatPctAvg7d = bodyFatPctAvg7d
                )
            } else null
        }

        val contextPacket = ContextPacket(
            recentWorkouts = workoutSummaries,
            currentBiometrics = biometrics
        )

        return json.encodeToString(contextPacket)
    }

    fun formatWeeklyInsightsWithLatex(insights: WeeklyInsights): String {
        val sections = mutableListOf<String>()

        sections.add("## Boaz's Weekly Statistical Analysis")
        sections.add("**Status**: ${insights.status}")
        sections.add("")

        if (insights.volumeLoadAnomalies.isNotEmpty()) {
            sections.add("### Volume-Load Anomalies")
            sections.add("Using Z-score analysis where outliers are defined as \$|Z| > 2\\sigma\$:")
            sections.add("")

            insights.volumeLoadAnomalies.forEach { anomaly ->
                val dateStr = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
                    .format(java.util.Date(anomaly.timestamp))

                sections.add("**${anomaly.type}** (${dateStr}):")
                sections.add("- Volume Load: ${anomaly.volumeLoad.toInt()} kg")
                sections.add("- Z-score: \$Z = ${String.format("%.2f", anomaly.zScore)}\$")

                when (anomaly.type) {
                    "Positive Outlier" -> {
                        sections.add("- Formula: \$Z = \\frac{X - \\mu}{\\sigma} = ${String.format("%.2f", anomaly.zScore)}\$")
                        sections.add("- **Interpretation**: This session exceeded the 4-week rolling mean by more than 2 standard deviations.")
                    }
                    "Negative Outlier" -> {
                        sections.add("- Formula: \$Z = \\frac{X - \\mu}{\\sigma} = ${String.format("%.2f", anomaly.zScore)}\$")
                        sections.add("- **Context**: ${anomaly.healthContext}")
                    }
                }
                sections.add("")
            }
        }

        if (insights.progressionAnomalies.isNotEmpty()) {
            sections.add("### Velocity of Progression Analysis")
            sections.add("Using Rate of Change (RoC) in estimated 1RM:")
            sections.add("Formula: $\\Delta e1RM = \\frac{e1RM_{current} - e1RM_{previous}}{e1RM_{previous}}$")
            sections.add("")

            insights.progressionAnomalies.forEach { anomaly ->
                sections.add("**${anomaly.exerciseName}**:")
                sections.add("- Current e1RM: ${String.format("%.1f", anomaly.currentE1RM)} kg")
                sections.add("- Previous e1RM: ${String.format("%.1f", anomaly.previousE1RM)} kg")
                sections.add("- Rate of Change: $\\Delta e1RM = ${String.format("%.1f", anomaly.rateOfChange * 100)}\\%$")
                sections.add("- **Flag**: ${anomaly.flag}")
                sections.add("")
            }
        }

        insights.healthPerformanceCorrelation?.let { correlation ->
            sections.add("### Health-Performance Correlation")
            sections.add("Pearson Correlation Coefficient: \$r = ${String.format("%.3f", correlation.correlationCoefficient)}\$")
            sections.add("- **Interpretation**: ${correlation.interpretation} correlation")
            sections.add("- **Note**: ${correlation.recommendation}")
            sections.add("")
        }

        sections.add("### Summary")
        sections.add(insights.summary)
        sections.add("")

        if (insights.recommendations.isNotEmpty()) {
            sections.add("### Committee Recommendations")
            insights.recommendations.forEach { recommendation ->
                sections.add("- $recommendation")
            }
        }

        return sections.joinToString("\n")
    }

    /**
     * Builds the full 6-layer system instruction from UserContext.
     *
     * Layer 1 — HEADER: committee_manifest.md (Hebrew), placeholders replaced
     * Layer 2 — USER SEGMENT: Static Profile from userContext.user
     * Layer 3 — STRATEGY SEGMENT: GoalDocument
     * Layer 4 — SAFETY SEGMENT: MedicalRestrictionsDoc
     * Layer 6 — MEMORY SEGMENT: LastDelta Summary (prepended before Layer 1)
     *
     * Layer 5 (MomentumData) is appended to each user message via buildContextPacket().
     *
     * @param userContext Full user context (user, goalDocument, medicalDoc)
     * @param sessionSummary Delta summary from the previous session
     */
    fun getSystemInstruction(
        userContext: UserContext? = null,
        sessionSummary: String? = null,
        language: String = "Hebrew"
    ): String {
        // Layer 6 — MEMORY SEGMENT (prepended)
        val memoryBlock = if (!sessionSummary.isNullOrBlank()) {
            """
HISTORICAL CONTEXT MEMORY (Previous Session Delta Summary):
$sessionSummary

---

""".trimStart()
        } else ""

        // Language directive
        val languageDirective = """
RESPONSE LANGUAGE: $language
All Committee responses must be in $language unless the user writes in the other language.

""".trimStart()

        // Layer 1 — HEADER: Load committee_manifest.md with placeholder substitution
        val manifestText = loadManifestWithPlaceholders(userContext)

        // Layer 2 — USER SEGMENT
        val userSegment = if (userContext != null) buildUserSegment(userContext) else ""

        // Layer 3 — STRATEGY SEGMENT
        val strategySegment = if (userContext?.goalDocument != null) {
            buildStrategySegment(userContext.goalDocument)
        } else ""

        // Layer 4 — SAFETY SEGMENT
        val safetySegment = if (userContext?.medicalDoc != null) {
            buildSafetySegment(userContext.medicalDoc)
        } else ""

        return memoryBlock + languageDirective + manifestText + "\n\n" + userSegment + strategySegment + safetySegment
    }

    private fun loadManifestWithPlaceholders(userContext: UserContext?): String {
        val raw = try {
            context.assets.open("committee_manifest.md")
                .bufferedReader(Charsets.UTF_8)
                .readText()
        } catch (e: Exception) {
            android.util.Log.w("ContextInjector", "Failed to load committee_manifest.md", e)
            return buildFallbackManifest()
        }

        val redListStr = userContext?.medicalDoc?.redList
            ?.joinToString("\n") { "• $it" }
            ?: "(ללא פריטים)"

        val yellowListStr = userContext?.medicalDoc?.yellowList
            ?.joinToString("\n") { "• ${it.exercise} → ${it.requiredCue}" }
            ?: "(ללא פריטים)"

        return raw
            .replace("{{USER_NAME}}", userContext?.user?.name ?: "המשתמש")
            .replace("{{USER_AGE}}", userContext?.user?.age?.toString() ?: "—")
            .replace("{{USER_HEIGHT_CM}}", userContext?.user?.heightCm?.toInt()?.toString() ?: "—")
            .replace("{{GOAL_PHASE}}", userContext?.goalDocument?.phase ?: "—")
            .replace("{{GOAL_DEADLINE}}", userContext?.goalDocument?.deadline ?: "—")
            .replace("{{RED_LIST}}", redListStr)
            .replace("{{YELLOW_LIST}}", yellowListStr)
            .replace("{{OCCUPATION}}", userContext?.user?.occupationType ?: "—")
            .replace("{{CHRONOTYPE}}", userContext?.user?.chronotype ?: "—")
    }

    private fun buildFallbackManifest(): String {
        return """
You are "The Committee" — 8 expert advisors. Respond as a collective with each expert contributing their specialized perspective.

COMMITTEE MEMBERS: Arnold (intensity/pump), Dr. Brad Schoenfeld (hypertrophy science), Noa (physiotherapist/safety), Boris Sheiko (strength/progression), Coach Carter (recovery), Maya (nutrition), Boaz (data science), The Architect (session manager).

RESPONSE FORMAT:
1. Brief committee consensus (1-2 sentences)
2. Individual expert inputs (first person, prefixed with name)
3. Final recommendation (specific action items)
        """.trimIndent()
    }

    private fun buildUserSegment(userContext: UserContext): String {
        val user = userContext.user
        return """

--- USER PROFILE SEGMENT ---
Name: ${user.name ?: "—"}
Age: ${user.age?.toString() ?: "—"}
Gender: ${user.gender ?: "—"}
Height: ${user.heightCm?.let { "${it.toInt()}cm" } ?: "—"}
Weight: ${user.weightKg?.let { "${"%.1f".format(it)} kg" } ?: "—"}
Body Fat: ${user.bodyFatPercent?.let { "${"%.1f".format(it)}%" } ?: "—"}
Training Targets: ${user.trainingTargets ?: "—"}
Occupation Type: ${user.occupationType ?: "—"}
Chronotype: ${user.chronotype ?: "—"}
Parental Load: ${user.parentalLoad?.let { "$it child(ren)" } ?: "—"}
Avg Sleep Hours: ${user.averageSleepHours?.let { "${it}h" } ?: "—"}
---

"""
    }

    private fun buildStrategySegment(goalDoc: GoalDocument): String {
        return """
--- STRATEGY SEGMENT (GoalDocument) ---
Current Phase: ${goalDoc.phase}
Deadline: ${goalDoc.deadline}
Priority Muscles: ${goalDoc.priorityMuscles.joinToString(", ")}
Session Constraints: ${goalDoc.sessionConstraints}
Weekly Session Count: ${goalDoc.weeklySessionCount}
---

"""
    }

    private fun buildSafetySegment(medicalDoc: MedicalRestrictionsDoc): String {
        val redListStr = if (medicalDoc.redList.isEmpty()) "(none)"
        else medicalDoc.redList.joinToString(", ")

        val yellowListStr = if (medicalDoc.yellowList.isEmpty()) "(none)"
        else medicalDoc.yellowList.joinToString("; ") { "${it.exercise} → ${it.requiredCue}" }

        return """
--- SAFETY SEGMENT (Medical Shield) ---
Injury History: ${medicalDoc.injuryHistorySummary}

🔴 RED LIST (absolute veto — Noa has full authority):
$redListStr

🟡 YELLOW LIST (allowed with mandatory execution cue):
$yellowListStr

Noa's veto on RED LIST items overrides ALL other committee members.
---

"""
    }

    /**
     * Legacy overload for backward compatibility — used when no UserContext is available.
     */
    fun getSystemInstruction(sessionSummary: String? = null): String {
        return getSystemInstruction(userContext = null, sessionSummary = sessionSummary)
    }
}
