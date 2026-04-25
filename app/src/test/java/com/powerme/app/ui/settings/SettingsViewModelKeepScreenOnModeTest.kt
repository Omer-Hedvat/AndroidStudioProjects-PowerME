package com.powerme.app.ui.settings

import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.ai.AiCoreAvailability
import com.powerme.app.ai.AiCoreStatus
import com.powerme.app.ai.GeminiKeyResolver
import com.powerme.app.ai.KeyResolution
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.KeepScreenOnMode
import com.powerme.app.data.RpeMode
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
class SettingsViewModelKeepScreenOnModeTest {

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
        mockAiCoreAvailability = mock()

        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.DURING_WORKOUT))
        whenever(mockAppSettingsDataStore.rpeMode).thenReturn(flowOf(RpeMode.OFF))
        whenever(mockAppSettingsDataStore.themeMode).thenReturn(flowOf(ThemeMode.SYSTEM))
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockAppSettingsDataStore.hcWorkoutBackfillDone).thenReturn(flowOf(true))
        whenever(mockAppSettingsDataStore.timedSetSetupSeconds).thenReturn(flowOf(3))
        whenever(mockAppSettingsDataStore.timerSound).thenReturn(flowOf(TimerSound.BEEP))
        whenever(mockAppSettingsDataStore.notificationsEnabled).thenReturn(flowOf(true))
        whenever(mockAppSettingsDataStore.workoutStyle).thenReturn(flowOf(WorkoutStyle.HYBRID))
        whenever(mockAuth.currentUser).thenReturn(null)
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
        whenever(mockSecurePreferencesStore.hasUserGeminiApiKey()).thenReturn(false)
        whenever(mockSecurePreferencesStore.getUserGeminiApiKey()).thenReturn(null)
        whenever(mockKeyResolver.resolve()).thenReturn(KeyResolution.ShippedKey("shipped-key"))
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
    fun `init - keepScreenOnMode defaults to DURING_WORKOUT from DataStore`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        assertEquals(KeepScreenOnMode.DURING_WORKOUT, viewModel.uiState.value.keepScreenOnMode)
    }

    @Test
    fun `init - keepScreenOnMode reflects DataStore value ALWAYS`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.ALWAYS))
        val viewModel = buildViewModel()
        runCurrent()

        assertEquals(KeepScreenOnMode.ALWAYS, viewModel.uiState.value.keepScreenOnMode)
    }

    @Test
    fun `init - keepScreenOnMode reflects DataStore value OFF`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.OFF))
        val viewModel = buildViewModel()
        runCurrent()

        assertEquals(KeepScreenOnMode.OFF, viewModel.uiState.value.keepScreenOnMode)
    }

    @Test
    fun `setKeepScreenOnMode - writes to DataStore and updates uiState`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setKeepScreenOnMode(KeepScreenOnMode.ALWAYS)
        runCurrent()

        verify(mockAppSettingsDataStore).saveKeepScreenOnMode(KeepScreenOnMode.ALWAYS)
        assertEquals(KeepScreenOnMode.ALWAYS, viewModel.uiState.value.keepScreenOnMode)
    }

    @Test
    fun `setKeepScreenOnMode - pushes app preferences to Firestore`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setKeepScreenOnMode(KeepScreenOnMode.OFF)
        runCurrent()

        verify(mockFirestoreSyncManager).pushAppPreferences()
    }

    @Test
    fun `setKeepScreenOnMode - switching to OFF updates uiState`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.ALWAYS))
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setKeepScreenOnMode(KeepScreenOnMode.OFF)
        runCurrent()

        assertEquals(KeepScreenOnMode.OFF, viewModel.uiState.value.keepScreenOnMode)
    }

    @Test
    fun `setKeepScreenOnMode - switching to DURING_WORKOUT updates uiState`() = runTest(testDispatcher) {
        whenever(mockAppSettingsDataStore.keepScreenOnMode).thenReturn(flowOf(KeepScreenOnMode.ALWAYS))
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.setKeepScreenOnMode(KeepScreenOnMode.DURING_WORKOUT)
        runCurrent()

        verify(mockAppSettingsDataStore).saveKeepScreenOnMode(KeepScreenOnMode.DURING_WORKOUT)
        assertEquals(KeepScreenOnMode.DURING_WORKOUT, viewModel.uiState.value.keepScreenOnMode)
    }
}
