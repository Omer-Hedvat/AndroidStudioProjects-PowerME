package com.powerme.app.ui.settings

import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.ai.GeminiKeyResolver
import com.powerme.app.ai.KeyResolution
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelApiKeyTest {

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

        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockAppSettingsDataStore.keepScreenOn).thenReturn(flowOf(false))
        whenever(mockAppSettingsDataStore.useRpeAutoPop).thenReturn(flowOf(false))
        whenever(mockAppSettingsDataStore.themeMode).thenReturn(flowOf(ThemeMode.DARK))
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockAppSettingsDataStore.hcWorkoutBackfillDone).thenReturn(flowOf(true))
        whenever(mockAppSettingsDataStore.timedSetSetupSeconds).thenReturn(flowOf(3))
        whenever(mockAppSettingsDataStore.timerSound).thenReturn(flowOf(TimerSound.BEEP))
        whenever(mockAppSettingsDataStore.notificationsEnabled).thenReturn(flowOf(true))
        whenever(mockAuth.currentUser).thenReturn(null)
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
        whenever(mockSecurePreferencesStore.hasUserGeminiApiKey()).thenReturn(false)
        whenever(mockSecurePreferencesStore.getUserGeminiApiKey()).thenReturn(null)
        whenever(mockKeyResolver.resolve()).thenReturn(KeyResolution.ShippedKey("shipped-key"))
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
        keyResolver = mockKeyResolver
    )

    @Test
    fun `init with no user key - hasUserApiKey is false and status is UsingDefault`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        assertFalse(viewModel.uiState.value.hasUserApiKey)
        assertEquals(ApiKeyStatus.UsingDefault, viewModel.uiState.value.apiKeyStatus)
    }

    @Test
    fun `init with existing user key - hasUserApiKey is true and status is UsingUser`() = runTest(testDispatcher) {
        whenever(mockSecurePreferencesStore.hasUserGeminiApiKey()).thenReturn(true)
        whenever(mockKeyResolver.resolve()).thenReturn(KeyResolution.UserKey("user-key"))

        val viewModel = buildViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value.hasUserApiKey)
        assertEquals(ApiKeyStatus.UsingUser, viewModel.uiState.value.apiKeyStatus)
    }

    @Test
    fun `saveUserApiKey with valid key - writes to store and updates state`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.updateApiKeyInput("my-gemini-key")
        viewModel.saveUserApiKey()
        runCurrent()

        verify(mockSecurePreferencesStore).setUserGeminiApiKey("my-gemini-key")
        assertTrue(viewModel.uiState.value.hasUserApiKey)
        assertEquals("", viewModel.uiState.value.userApiKeyInput)
        assertEquals(ApiKeyStatus.UsingUser, viewModel.uiState.value.apiKeyStatus)
    }

    @Test
    fun `saveUserApiKey with blank input - does nothing`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.updateApiKeyInput("   ")
        viewModel.saveUserApiKey()
        runCurrent()

        verify(mockSecurePreferencesStore, never()).setUserGeminiApiKey(org.mockito.kotlin.any())
    }

    @Test
    fun `clearUserApiKey - clears store and updates state`() = runTest(testDispatcher) {
        whenever(mockSecurePreferencesStore.hasUserGeminiApiKey()).thenReturn(true)
        whenever(mockKeyResolver.resolve()).thenReturn(KeyResolution.UserKey("user-key"))

        val viewModel = buildViewModel()
        runCurrent()

        viewModel.clearUserApiKey()
        runCurrent()

        verify(mockSecurePreferencesStore).clearUserGeminiApiKey()
        assertFalse(viewModel.uiState.value.hasUserApiKey)
        assertEquals(ApiKeyStatus.UsingDefault, viewModel.uiState.value.apiKeyStatus)
    }

    @Test
    fun `saveUserApiKey trims whitespace`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.updateApiKeyInput("  trimmed-key  ")
        viewModel.saveUserApiKey()
        runCurrent()

        verify(mockSecurePreferencesStore).setUserGeminiApiKey("trimmed-key")
    }

    @Test
    fun `saveUserApiKey does NOT push to Firestore`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.updateApiKeyInput("key")
        viewModel.saveUserApiKey()
        runCurrent()

        verify(mockFirestoreSyncManager, never()).pushAppPreferences()
    }

    @Test
    fun `clearUserApiKey does NOT push to Firestore`() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        runCurrent()

        viewModel.clearUserApiKey()
        runCurrent()

        verify(mockFirestoreSyncManager, never()).pushAppPreferences()
    }
}
