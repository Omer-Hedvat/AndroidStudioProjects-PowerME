package com.powerme.app.ui.settings

import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.ai.AiCoreAvailability
import com.powerme.app.ai.AiCoreStatus
import com.powerme.app.ai.GeminiKeyResolver
import com.powerme.app.ai.KeyResolution
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.KeepScreenOnMode
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.secure.SecurePreferencesStore
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.util.TimerSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTimerSoundTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockUserSettingsDao: UserSettingsDao
    private lateinit var mockDatabase: PowerMeDatabase
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockFirestoreSyncManager: FirestoreSyncManager
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockHealthConnectManager: HealthConnectManager
    private lateinit var mockWorkoutDao: WorkoutDao
    private lateinit var mockWorkoutSetDao: WorkoutSetDao
    private lateinit var mockSecurePreferencesStore: SecurePreferencesStore
    private lateinit var mockKeyResolver: GeminiKeyResolver
    private lateinit var mockAiCoreAvailability: AiCoreAvailability

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockUserSettingsDao = mock()
        mockDatabase = mock()
        mockAppSettingsDataStore = mock()
        mockFirestoreSyncManager = mock()
        mockAuth = mock()
        mockHealthConnectManager = mock()
        mockWorkoutDao = mock()
        mockWorkoutSetDao = mock()
        mockSecurePreferencesStore = mock()
        mockKeyResolver = mock()
        whenever(mockSecurePreferencesStore.hasUserGeminiApiKey()).thenReturn(false)
        whenever(mockSecurePreferencesStore.getUserGeminiApiKey()).thenReturn(null)
        whenever(mockKeyResolver.resolve()).thenReturn(KeyResolution.ShippedKey("test-key"))

        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.DURING_WORKOUT))
        whenever(mockAppSettingsDataStore.rpeMode).thenReturn(flowOf(com.powerme.app.data.RpeMode.OFF))
        whenever(mockAppSettingsDataStore.themeMode).thenReturn(flowOf(ThemeMode.DARK))
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockAppSettingsDataStore.hcWorkoutBackfillDone).thenReturn(flowOf(true))
        whenever(mockAppSettingsDataStore.timedSetSetupSeconds).thenReturn(flowOf(3))
        whenever(mockAppSettingsDataStore.timerSound).thenReturn(flowOf(TimerSound.BEEP))
        whenever(mockAppSettingsDataStore.notificationsEnabled).thenReturn(flowOf(true))
        whenever(mockAppSettingsDataStore.workoutStyle).thenReturn(flowOf(WorkoutStyle.HYBRID))
        whenever(mockAuth.currentUser).thenReturn(null)
        mockAiCoreAvailability = mock()
        runBlocking { whenever(mockAiCoreAvailability.check()).thenReturn(AiCoreStatus.NotSupported) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): SettingsViewModel = SettingsViewModel(
        userSettingsDao = mockUserSettingsDao,
        database = mockDatabase,
        appSettingsDataStore = mockAppSettingsDataStore,
        firestoreSyncManager = mockFirestoreSyncManager,
        auth = mockAuth,
        context = mock(),
        healthConnectManager = mockHealthConnectManager,
        workoutDao = mockWorkoutDao,
        workoutSetDao = mockWorkoutSetDao,
        securePreferencesStore = mockSecurePreferencesStore,
        keyResolver = mockKeyResolver,
        aiCoreAvailability = mockAiCoreAvailability
    )

    @Test
    fun `initial timerSound is BEEP`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = buildViewModel()
        runCurrent()

        assertEquals(TimerSound.BEEP, viewModel.uiState.value.timerSound)
    }

    @Test
    fun `setTimerSound updates uiState to BELL`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setTimerSound(TimerSound.BELL)
        runCurrent()

        assertEquals(TimerSound.BELL, viewModel.uiState.value.timerSound)
    }

    @Test
    fun `setTimerSound persists to DataStore`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setTimerSound(TimerSound.CHIME)
        runCurrent()

        verify(mockAppSettingsDataStore).setTimerSound(TimerSound.CHIME)
    }

    @Test
    fun `setTimerSound pushes to Firestore`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setTimerSound(TimerSound.NONE)
        runCurrent()

        verify(mockFirestoreSyncManager).pushAppPreferences()
    }

    @Test
    fun `setTimerSound to NONE updates uiState`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setTimerSound(TimerSound.NONE)
        runCurrent()

        assertEquals(TimerSound.NONE, viewModel.uiState.value.timerSound)
    }
}
