package com.omerhedvat.powerme.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.omerhedvat.powerme.actions.ActionBlock
import com.omerhedvat.powerme.actions.ActionExecutor
import com.omerhedvat.powerme.actions.ActionParser
import com.omerhedvat.powerme.actions.ActionResult
import com.omerhedvat.powerme.actions.PlannedExercise
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.ChatMessage
import com.omerhedvat.powerme.data.database.MetricType
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.repository.AnalyticsRepository
import com.omerhedvat.powerme.data.repository.ChatRepository
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import com.omerhedvat.powerme.data.repository.HealthStatsRepository
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.MetricLogRepository
import com.omerhedvat.powerme.data.repository.StateHistoryRepository
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import com.omerhedvat.powerme.util.GeminiResponseLogger
import com.omerhedvat.powerme.util.GoalDocumentManager
import com.omerhedvat.powerme.util.MedicalPatch
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import com.omerhedvat.powerme.util.SessionSummaryManager
import com.omerhedvat.powerme.util.StatePatchManager
import com.omerhedvat.powerme.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class WarRoomMode { LOADING, INTERVIEW, ACTIVE }

data class PendingRoutinePreview(
    val routineId: Long,
    val routineName: String,
    val targetDate: String,
    val exercises: List<PlannedExercise>
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsApiKey: Boolean = false,
    val actionResults: List<ActionResult> = emptyList(),
    val sessionWasReset: Boolean = false,
    val warRoomMode: WarRoomMode = WarRoomMode.LOADING,
    val pendingMedicalPatch: MedicalPatch? = null,
    val pendingGoalPatch: com.omerhedvat.powerme.util.GoalPatch? = null,
    val pendingRoutine: PendingRoutinePreview? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val healthStatsRepository: HealthStatsRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val contextInjector: ContextInjector,
    private val securePreferencesManager: SecurePreferencesManager,
    private val sessionSummaryManager: SessionSummaryManager,
    private val actionExecutor: ActionExecutor,
    private val geminiResponseLogger: GeminiResponseLogger,
    private val goalDocumentManager: GoalDocumentManager,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val userSessionManager: UserSessionManager,
    private val statePatchManager: StatePatchManager,
    private val routineDao: RoutineDao,
    private val stateHistoryRepository: StateHistoryRepository,
    private val userSettingsDao: UserSettingsDao,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val metricLogRepository: MetricLogRepository,
    private val modelRouter: ModelRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _medicalDoc = MutableStateFlow<MedicalRestrictionsDoc?>(null)
    val medicalDoc: StateFlow<MedicalRestrictionsDoc?> = _medicalDoc.asStateFlow()

    private var generativeModel: GenerativeModel? = null
    private var currentUserContext: UserContext? = null
    private val actionParser = ActionParser()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val SESSION_GAP_MS = 12 * 60 * 60 * 1000L
    }

    init {
        loadMessages()
        viewModelScope.launch {
            initializeSession()
        }
    }

    /**
     * Full boot sequence:
     * 1. Check API key
     * 2. Load UserContext (User + GoalDocument + MedicalDoc)
     * 3. 12-hour reset check → State Patch generation
     * 4. Create GenerativeModel with 6-layer system prompt
     * 5. Determine WarRoomMode (INTERVIEW or ACTIVE)
     * 6. If ACTIVE → postArchitectGreeting
     */
    private suspend fun initializeSession() {
        val hasKey = securePreferencesManager.hasApiKey()
        _uiState.update { it.copy(needsApiKey = !hasKey) }
        if (!hasKey) return

        // Step 2: Load UserContext
        val user = userSessionManager.getCurrentUser()
        val goalDoc = goalDocumentManager.getGoalDocument()
        val medicalDoc = medicalLedgerRepository.getRestrictionsDoc()

        val userContext = if (user != null) {
            UserContext(user = user, goalDocument = goalDoc, medicalDoc = medicalDoc)
        } else null

        currentUserContext = userContext
        _medicalDoc.value = medicalDoc

        // Step 3: 12-hour reset check
        val lastTimestamp = chatRepository.getLastMessageTimestamp()
        val isStaleSession = lastTimestamp != null &&
            (System.currentTimeMillis() - lastTimestamp) > SESSION_GAP_MS

        if (isStaleSession) {
            val messages = chatRepository.getAllMessagesOnce()
            if (messages.isNotEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
                val statePatchResult = generateStatePatch(messages, userContext)
                statePatchResult?.let { patch ->
                    // Apply goal patch automatically (no confirmation needed)
                    patch.goalPatch?.let { gp ->
                        if (gp.operation != "NO_CHANGE") {
                            statePatchManager.applyGoalPatch(gp)
                            // Refresh userContext after goal update
                            val updatedGoal = goalDocumentManager.getGoalDocument()
                            currentUserContext = userContext?.copy(goalDocument = updatedGoal)
                        }
                    }
                    // Medical patch → show confirmation dialog
                    patch.medicalPatch?.let { mp ->
                        if (mp.operation != "NO_CHANGE") {
                            _uiState.update { it.copy(pendingMedicalPatch = mp) }
                        }
                    }
                    // Save delta summary
                    if (patch.deltaSummary.isNotBlank()) {
                        sessionSummaryManager.saveSummary(patch.deltaSummary)
                    }
                } ?: run {
                    // Fallback: plain delta summary
                    val summary = generateDeltaSummary(messages)
                    if (summary != null) sessionSummaryManager.saveSummary(summary)
                }

                chatRepository.clearHistory()
                _uiState.update { it.copy(isLoading = false, sessionWasReset = true) }
            }
        }

        // Step 4: Create GenerativeModel with 6-layer system prompt
        createGenerativeModel(currentUserContext)

        // Step 5: Determine mode
        val freshUserContext = currentUserContext
        val mode = when {
            freshUserContext == null || freshUserContext.goalDocument == null -> WarRoomMode.INTERVIEW
            freshUserContext.medicalDoc == null -> WarRoomMode.INTERVIEW
            else -> WarRoomMode.ACTIVE
        }
        _uiState.update { it.copy(warRoomMode = mode) }

        // Step 6: If ACTIVE, post Architect greeting
        if (mode == WarRoomMode.ACTIVE && freshUserContext != null) {
            postArchitectGreeting(freshUserContext)
        }
    }

    /**
     * Creates (or recreates) the GenerativeModel with the 6-layer system instruction.
     */
    private suspend fun createGenerativeModel(userContext: UserContext? = null) {
        val apiKey = securePreferencesManager.getApiKey() ?: return
        val language = userSettingsDao.getSettingsOnce()?.language ?: "Hebrew"
        val userOverride = appSettingsDataStore.warRoomModel.first()
        val warRoomModel = modelRouter.resolveWarRoomModel(userOverride)
        generativeModel = GenerativeModel(
            modelName = warRoomModel,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.8f
                topK = 40
                topP = 0.95f
            },
            systemInstruction = content {
                text(
                    contextInjector.getSystemInstruction(
                        userContext = userContext,
                        sessionSummary = sessionSummaryManager.getSummary(),
                        language = language
                    )
                )
            }
        )
    }

    /**
     * Posts the Architect's Hebrew greeting at session start.
     * Low-temp model, no system instruction (prevents persona bleed).
     */
    private suspend fun postArchitectGreeting(userContext: UserContext) {
        val apiKey = securePreferencesManager.getApiKey() ?: return
        try {
            val userOverride = appSettingsDataStore.warRoomModel.first()
            val greetingModel = GenerativeModel(
                modelName = modelRouter.resolveWarRoomModel(userOverride),
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f
                    topK = 20
                    topP = 0.9f
                }
            )

            val lastSummary = sessionSummaryManager.getSummary()
            val summaryHint = if (!lastSummary.isNullOrBlank()) {
                "Last session highlight: ${lastSummary.take(200)}"
            } else ""

            val prompt = """
You are The Architect — the War Room session manager.
Greet ${userContext.user.name ?: "הלוחם"} in Hebrew in 3-4 sentences.
Synthesize: goal phase (${userContext.goalDocument?.phase ?: "—"}), deadline (${userContext.goalDocument?.deadline ?: "—"}), $summaryHint, readiness check.
Example structure: 'שלום [name]. אתחול הושלם. המטרה: [phase] עד [deadline]. [last session insight]. מוכנים לאימון?'
Output ONLY the Hebrew greeting. No ActionBlock. No English.
            """.trimIndent()

            val response = greetingModel.generateContent(prompt)
            val greeting = response.text?.trim() ?: return

            val greetingMessage = ChatMessage(
                message = greeting,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.insertMessage(greetingMessage)

        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Failed to post Architect greeting", e)
        }
    }

    /**
     * Generates a structured State Patch JSON at 12h reset.
     * Includes delta summary + goal/medical patch operations.
     */
    private suspend fun generateStatePatch(
        messages: List<ChatMessage>,
        userContext: UserContext?
    ): com.omerhedvat.powerme.util.StatePatch? {
        val apiKey = securePreferencesManager.getApiKey() ?: return null
        return try {
            val summaryModel = GenerativeModel(
                modelName = modelRouter.getBestFlashModel(),
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topK = 20
                    topP = 0.85f
                }
            )

            val chatText = messages.takeLast(10).joinToString("\n") { msg ->
                if (msg.isUser) "USER: ${msg.message}" else "COMMITTEE: ${msg.message}"
            }

            val latestBoazReport = stateHistoryRepository.getHistoryForType("PERFORMANCE")
                .firstOrNull()?.newValueJson

            val boazSection = if (!latestBoazReport.isNullOrBlank()) {
                "\n\nLAST WORKOUT BOAZ PERFORMANCE ANALYSIS:\n$latestBoazReport"
            } else ""

            val currentGoal = userContext?.goalDocument?.let { g ->
                "Phase: ${g.phase}, Deadline: ${g.deadline}"
            } ?: "Not set"

            val prompt = """
Analyze this training session. Generate a State Patch JSON.
Rules:
- delta_summary: ≤150 words, focus on weights/PRs, injury updates, training decisions
- OVERWRITE GoalDocument ONLY if user explicitly requested a phase change
- APPEND/UPDATE MedicalRestrictionsDoc if new pain signals or cue updates were discussed
- NO_CHANGE if the document is still valid
Current goal: $currentGoal

Return ONLY valid JSON matching this schema:
{
  "delta_summary": "...",
  "goal_patch": {
    "operation": "OVERWRITE" | "NO_CHANGE",
    "reason": "...",
    "new_value": { "phase": "...", "deadline": "YYYY-MM-DD", "priorityMuscles": [], "sessionConstraints": "...", "weeklySessionCount": 4 }
  },
  "medical_patch": {
    "operation": "APPEND" | "UPDATE" | "NO_CHANGE",
    "reason": "...",
    "additions": {
      "red_list_add": [],
      "yellow_list_add": [{"exercise": "...", "modification_cue": "..."}]
    }
  }
}

CONVERSATION:
$chatText$boazSection
            """.trimIndent()

            val response = summaryModel.generateContent(prompt)
            val text = response.text?.trim() ?: return null

            // Extract JSON from response (may be wrapped in code block)
            val jsonStr = extractJsonFromText(text)
            statePatchManager.parseStatePatch(jsonStr)

        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Failed to generate state patch", e)
            null
        }
    }

    private fun extractJsonFromText(text: String): String {
        val codeBlockPattern = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
        val match = codeBlockPattern.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text
    }

    /**
     * Legacy delta summary fallback.
     */
    private suspend fun generateDeltaSummary(messages: List<ChatMessage>): String? {
        val apiKey = securePreferencesManager.getApiKey() ?: return null
        return try {
            val summaryModel = GenerativeModel(
                modelName = modelRouter.getBestFlashModel(),
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topK = 20
                    topP = 0.85f
                }
            )

            val chatText = messages.takeLast(10).joinToString("\n") { msg ->
                if (msg.isUser) "USER: ${msg.message}" else "COMMITTEE: ${msg.message}"
            }

            val prompt = """
Summarize this training session in under 150 words for your future self (The Committee).
Focus only on what matters for the NEXT session:
- Specific weights/reps changes or PRs
- Injury status updates (joints, severity levels)
- Gym or equipment changes
- Training decisions made (e.g. deload, exercise swap)
- Any unresolved questions to follow up on

Be specific with numbers. Omit generic advice. Write in third person about the user.

CONVERSATION:
$chatText
            """.trimIndent()

            summaryModel.generateContent(prompt).text?.trim()
        } catch (e: Exception) {
            android.util.Log.w("ChatViewModel", "Failed to generate delta summary", e)
            null
        }
    }

    /**
     * Confirms the pending medical patch and writes it to DB.
     */
    fun confirmMedicalPatch() {
        val patch = _uiState.value.pendingMedicalPatch ?: return
        viewModelScope.launch {
            statePatchManager.applyMedicalPatch(patch)
            val updatedMedical = medicalLedgerRepository.getRestrictionsDoc()
            _medicalDoc.value = updatedMedical
            currentUserContext = currentUserContext?.copy(medicalDoc = updatedMedical)
            createGenerativeModel(currentUserContext)
            _uiState.update { it.copy(pendingMedicalPatch = null) }
        }
    }

    /**
     * Rejects the pending medical patch (logs to state_history).
     */
    fun rejectMedicalPatch() {
        val patch = _uiState.value.pendingMedicalPatch ?: return
        viewModelScope.launch {
            statePatchManager.rejectMedicalPatch(patch)
            _uiState.update { it.copy(pendingMedicalPatch = null) }
        }
    }

    fun dismissSessionReset() {
        _uiState.update { it.copy(sessionWasReset = false) }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        if (generativeModel == null) {
            _uiState.update { it.copy(needsApiKey = true) }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val userChatMessage = ChatMessage(
                    message = userMessage,
                    isUser = true,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.insertMessage(userChatMessage)

                val contextPacket = buildContextPacket()

                val isProgressQuery = userMessage.lowercase().let {
                    it.contains("how am i doing") ||
                    it.contains("how's my progress") ||
                    it.contains("weekly insights") ||
                    it.contains("weekly analysis") ||
                    it.contains("how is my training")
                }

                val boazInsights = if (isProgressQuery) {
                    val insights = analyticsRepository.generateWeeklyInsights()
                    contextInjector.formatWeeklyInsightsWithLatex(insights)
                } else null

                val fullPrompt = if (boazInsights != null) {
                    """
CONTEXT DATA:
$contextPacket

BOAZ'S WEEKLY STATISTICAL ANALYSIS:
$boazInsights

USER MESSAGE:
$userMessage

IMPORTANT: Boaz should lead the response since this is a progress/analysis query.
                    """.trimIndent()
                } else {
                    """
CONTEXT DATA:
$contextPacket

USER MESSAGE:
$userMessage
                    """.trimIndent()
                }

                val model = generativeModel ?: return@launch
                val response = model.generateContent(fullPrompt)
                val aiResponse = response.text ?: "I apologize, but I couldn't generate a response."

                val parsedActions = actionParser.extractActions(aiResponse)

                geminiResponseLogger.logResponse(
                    prompt = userMessage,
                    response = aiResponse,
                    parsedActions = parsedActions,
                    showToast = true
                )

                val actionResults = mutableListOf<ActionResult>()

                if (parsedActions.isNotEmpty()) {
                    val recentWorkouts = workoutRepository.getAllWorkouts().first()
                    val activeWorkoutId = recentWorkouts.firstOrNull()?.id

                    parsedActions.forEach { action ->
                        val result = actionExecutor.execute(action, activeWorkoutId)
                        actionResults.add(result)

                        // After routine creation success, show preview card
                        if (action is ActionBlock.CreateWorkoutRoutine && result is ActionResult.Success) {
                            val routineId = result.message.substringAfter("id:").substringBefore(" ").toLongOrNull()
                            if (routineId != null) {
                                _uiState.update {
                                    it.copy(pendingRoutine = PendingRoutinePreview(
                                        routineId = routineId,
                                        routineName = action.routineName,
                                        targetDate = action.targetDate,
                                        exercises = action.exercises
                                    ))
                                }
                            }
                        }

                        // After successful onboarding, refresh context and switch to ACTIVE mode
                        if (action is ActionBlock.SaveUserOnboarding &&
                            result is ActionResult.Success) {
                            val freshGoal = goalDocumentManager.getGoalDocument()
                            val freshMedical = medicalLedgerRepository.getRestrictionsDoc()
                            val user = userSessionManager.getCurrentUser()
                            if (user != null) {
                                val freshContext = UserContext(
                                    user = user,
                                    goalDocument = freshGoal,
                                    medicalDoc = freshMedical
                                )
                                currentUserContext = freshContext
                                createGenerativeModel(freshContext)
                                _uiState.update { it.copy(warRoomMode = WarRoomMode.ACTIVE) }
                                postArchitectGreeting(freshContext)
                            }
                        }
                    }
                }

                val actionDataJson = if (parsedActions.isNotEmpty()) {
                    json.encodeToString(parsedActions)
                } else null

                val aiChatMessage = ChatMessage(
                    message = aiResponse,
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    actionData = actionDataJson
                )
                chatRepository.insertMessage(aiChatMessage)

                _uiState.update {
                    it.copy(isLoading = false, actionResults = actionResults)
                }
            } catch (e: com.google.ai.client.generativeai.type.ResponseStoppedException) {
                val finishReason = e.response.candidates.firstOrNull()?.finishReason?.name ?: ""
                val isContextFull = finishReason == "MAX_TOKENS"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (isContextFull)
                            "AI Context Full: Summarizing workout data..."
                        else
                            "Response stopped (${finishReason.ifEmpty { "unknown reason" }})"
                    )
                }
            } catch (e: com.google.ai.client.generativeai.type.ServerException) {
                val msg = e.message ?: ""
                val isQuota = msg.contains("quota", ignoreCase = true) ||
                    msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                val isModelNotFound = msg.contains("not found", ignoreCase = true) ||
                    msg.contains("404", ignoreCase = true)
                val isTokenLimit = msg.contains("token", ignoreCase = true) ||
                    msg.contains("context", ignoreCase = true) && msg.contains("limit", ignoreCase = true)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = when {
                            isTokenLimit -> "AI Context Full: Summarizing workout data..."
                            isQuota -> "Gemini quota exceeded — try again later or check your plan"
                            isModelNotFound -> "Selected model is unavailable — change the model in Settings"
                            else -> "Gemini server error: $msg"
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to get response: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Sends the Discovery Interview primer to Gemini (hidden from user view).
     * Noa & Brad ask 5 questions covering goal phase, deadline, muscle priorities,
     * session constraints, and injury/medical history.
     */
    fun startDiscoveryInterview() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val model = generativeModel ?: run {
                    _uiState.update { it.copy(isLoading = false, needsApiKey = true) }
                    return@launch
                }

                val primer = """
You are starting a Discovery Interview. Dr. Brad Schoenfeld and Noa are leading.
Ask the user exactly 5 questions in a single message to collect their training profile.
Questions must cover:
1. Training goal phase (MASSING / CUTTING / MAINTENANCE) and target deadline
2. Priority muscle groups (what they want to develop most)
3. Session constraints (time available, frequency per week)
4. Any injuries, pain, or movements they must avoid (RED LIST)
5. Movements they can do with modifications (YELLOW LIST) and what the modification is

Format: Warm intro in Hebrew, then numbered questions in Hebrew.
After collecting all answers, emit a save_user_onboarding ActionBlock.
                """.trimIndent()

                val response = model.generateContent(primer)
                val aiResponse = response.text ?: return@launch

                val interviewMessage = ChatMessage(
                    message = aiResponse,
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.insertMessage(interviewMessage)
                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to start interview: ${e.message}")
                }
            }
        }
    }

    private suspend fun buildContextPacket(): String {
        val workouts = workoutRepository.getAllWorkouts().first().take(3)

        val workoutSetsMap = mutableMapOf<Long, List<com.omerhedvat.powerme.data.database.WorkoutSet>>()
        workouts.forEach { workout ->
            val sets = workoutRepository.getSetsForWorkout(workout.id).first()
            workoutSetsMap[workout.id] = sets
        }

        val allExercises = exerciseRepository.getAllExercises().first()
        val exerciseNamesMap = allExercises.associate { it.id to it.name }

        val healthStats = healthStatsRepository.getLatestHealthStats()

        // 7-day MetricLog averages for Weight and BodyFat (avoids sending raw per-log data points)
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val weightLogs = metricLogRepository.getByType(MetricType.WEIGHT).first()
            .filter { it.timestamp >= sevenDaysAgo }
        val bodyFatLogs = metricLogRepository.getByType(MetricType.BODY_FAT).first()
            .filter { it.timestamp >= sevenDaysAgo }
        val weightAvg = weightLogs.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        val bodyFatAvg = bodyFatLogs.takeIf { it.isNotEmpty() }?.map { it.value }?.average()

        return contextInjector.buildContextPacket(
            workouts = workouts,
            workoutSets = workoutSetsMap,
            exerciseNames = exerciseNamesMap,
            healthStats = healthStats,
            weightKgAvg7d = weightAvg,
            bodyFatPctAvg7d = bodyFatAvg
        )
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearHistory()
            generativeModel = null
            _uiState.update { it.copy(warRoomMode = WarRoomMode.LOADING) }
            initializeSession()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissAllOverlays() {
        _uiState.update {
            it.copy(
                pendingMedicalPatch = null,
                pendingGoalPatch = null,
                pendingRoutine = null,
                error = null,
                sessionWasReset = false
            )
        }
    }

    fun clearActionResults() {
        _uiState.update { it.copy(actionResults = emptyList()) }
    }

    fun dismissRoutinePreview() {
        _uiState.update { it.copy(pendingRoutine = null) }
    }

    fun deleteRoutineAndClear(routineId: Long) {
        viewModelScope.launch {
            routineDao.deleteRoutineById(routineId)
            _uiState.update { it.copy(pendingRoutine = null) }
        }
    }
}
