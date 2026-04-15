package com.powerme.app.ui.settings

import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the Health Connect slice of SettingsViewModel.
 *
 * Covers availability, permission, sync success/failure, and permission result callbacks.
 * Non-HC dependencies get minimal stubs so the ViewModel can init without crashing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelHealthConnectTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockUserSettingsDao: UserSettingsDao
    private lateinit var mockDatabase: PowerMeDatabase
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockFirestoreSyncManager: FirestoreSyncManager
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockHealthConnectManager: HealthConnectManager

    private lateinit var viewModel: SettingsViewModel

    private val sampleData = HealthConnectReadResult(
        weight = 80.0,
        height = 180f,
        bodyFat = 15.0,
        sleepMinutes = 450,
        hrv = 55.0,
        rhr = 60,
        steps = 8000,
        lastSyncTimestamp = System.currentTimeMillis() - 60_000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockUserSettingsDao = mock()
        mockDatabase = mock()
        mockAppSettingsDataStore = mock()
        mockFirestoreSyncManager = mock()
        mockAuth = mock()
        mockHealthConnectManager = mock()

        // Minimal stubs for non-HC init paths
        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockAppSettingsDataStore.keepScreenOn).thenReturn(flowOf(false))
        whenever(mockAppSettingsDataStore.themeMode).thenReturn(flowOf(ThemeMode.DARK))
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockAuth.currentUser).thenReturn(null)
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
        healthConnectManager = mockHealthConnectManager
    )

    // ── Test 1: HC not available ─────────────────────────────────────────────

    @Test
    fun `HC not available - healthConnectAvailable is false and checking clears`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        viewModel = buildViewModel()
        // Before coroutine runs: still in checking state
        assertTrue(viewModel.uiState.value.healthConnectChecking)

        runCurrent()

        assertFalse(viewModel.uiState.value.healthConnectChecking)
        assertFalse(viewModel.uiState.value.healthConnectAvailable)
        assertFalse(viewModel.uiState.value.healthConnectPermissionsGranted)
        assertNull(viewModel.uiState.value.healthConnectData)
    }

    // ── Test 2: HC available, permissions not granted ────────────────────────

    @Test
    fun `HC available but permissions not granted - shows not connected state`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking { whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false) }

        viewModel = buildViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value.healthConnectAvailable)
        assertFalse(viewModel.uiState.value.healthConnectPermissionsGranted)
        assertNull(viewModel.uiState.value.healthConnectData)
    }

    // ── Test 3: HC available + permissions granted, init only checks status ──

    @Test
    fun `HC available and granted on init - sets both flags, does not load data`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
        }

        viewModel = buildViewModel()
        runCurrent()

        // Init checks availability and permissions only — no HC read on startup
        assertTrue(viewModel.uiState.value.healthConnectAvailable)
        assertTrue(viewModel.uiState.value.healthConnectPermissionsGranted)
        assertNull(viewModel.uiState.value.healthConnectData)
    }

    // ── Test 4: syncHealthConnect success ─────────────────────────────────────

    @Test
    fun `syncHealthConnect success - syncing transitions and data populated`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockHealthConnectManager.readAllData()).thenReturn(sampleData)
            whenever(mockHealthConnectManager.syncAndRead()).thenReturn(sampleData)
        }

        viewModel = buildViewModel()
        runCurrent()

        viewModel.syncHealthConnect()
        runCurrent()

        // After sync completes, syncing=false, data populated, no error
        assertFalse(viewModel.uiState.value.healthConnectSyncing)
        assertNotNull(viewModel.uiState.value.healthConnectData)
        assertNull(viewModel.uiState.value.healthConnectError)
        verify(mockHealthConnectManager).syncAndRead()
    }

    // ── Test 5: syncHealthConnect failure ─────────────────────────────────────

    @Test
    fun `syncHealthConnect failure - error set and syncing false`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockHealthConnectManager.readAllData()).thenReturn(sampleData)
            whenever(mockHealthConnectManager.syncAndRead()).thenThrow(RuntimeException("HC read failed"))
        }

        viewModel = buildViewModel()
        runCurrent()

        viewModel.syncHealthConnect()
        runCurrent()

        assertFalse(viewModel.uiState.value.healthConnectSyncing)
        assertNotNull(viewModel.uiState.value.healthConnectError)
        assertTrue(viewModel.uiState.value.healthConnectError!!.contains("HC read failed"))
    }

    // ── Test 6: onPermissionResult all granted → triggers sync ───────────────

    @Test
    fun `onHealthConnectPermissionResult all granted - sets permissions and triggers sync`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
        runBlocking {
            // checkPermissionsGranted not called by onHealthConnectPermissionResult anymore;
            // the callback's granted set is trusted directly.
            whenever(mockHealthConnectManager.syncAndRead()).thenReturn(sampleData)
        }

        viewModel = buildViewModel()
        runCurrent()

        viewModel.onHealthConnectPermissionResult(HealthConnectManager.ALL_PERMISSIONS)
        runCurrent()

        assertTrue(viewModel.uiState.value.healthConnectPermissionsGranted)
        // syncHealthConnect was called — syncing completed, data should be populated
        assertNotNull(viewModel.uiState.value.healthConnectData)
    }

    // ── Test 7: onPermissionResult partial → no sync ─────────────────────────

    @Test
    fun `onHealthConnectPermissionResult partial - permissions remain false, no sync`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)
        runBlocking { whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false) }

        viewModel = buildViewModel()
        runCurrent()

        viewModel.onHealthConnectPermissionResult(setOf("partial.permission"))
        runCurrent()

        assertFalse(viewModel.uiState.value.healthConnectPermissionsGranted)
        assertNull(viewModel.uiState.value.healthConnectData)
    }
}
