package com.powerme.app.ui.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.User
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
import com.powerme.app.util.UserSessionManager
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for ProfileSetupViewModel.
 *
 * Firebase.auth.currentUser is unavailable without the Android runtime, so tests that
 * exercise paths reading displayName or currentUser.email must mock the paths that
 * call Firebase directly. The saveProfile() path requires a Firebase user, so we
 * test the ViewModel up to the point of the Firebase call and rely on the UserSessionManager
 * mock to confirm interaction.
 *
 * HC-related paths are fully testable via mocked HealthConnectManager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockHcManager: HealthConnectManager
    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockFirebaseAuth: FirebaseAuth
    private lateinit var mockFirebaseUser: FirebaseUser
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore

    private val fullHcResult = HealthConnectReadResult(
        weight = 80.0,
        height = 181f,
        bodyFat = 18.0,
        sleepMinutes = 450,
        hrv = 48.0,
        rhr = 58,
        steps = 7842,
        lastSyncTimestamp = System.currentTimeMillis()
    )

    private val emptyHcResult = HealthConnectReadResult(
        weight = null, height = null, bodyFat = null,
        sleepMinutes = null, hrv = null, rhr = null, steps = null,
        lastSyncTimestamp = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockHcManager = mock()
        mockUserSessionManager = mock()
        mockFirebaseAuth = mock()
        mockFirebaseUser = mock()
        mockAppSettingsDataStore = mock()
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        // Default: no signed-in user
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ProfileSetupViewModel(mockHcManager, mockUserSessionManager, mockFirebaseAuth, mockAppSettingsDataStore)

    // ── HC unavailable ──────────────────────────────────────────────────────

    @Test
    fun `when HC unavailable, starts at step 2`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(false)

        val vm = buildViewModel()
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStep)
        assertFalse(vm.uiState.value.hcAvailable)
    }

    // ── HC available, permissions not yet granted ────────────────────────────

    @Test
    fun `when HC available and not granted, starts at step 1`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking { whenever(mockHcManager.checkPermissionsGranted()).thenReturn(false) }

        val vm = buildViewModel()
        runCurrent()

        assertEquals(1, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.hcAvailable)
        assertFalse(vm.uiState.value.hcConnected)
    }

    // ── HC already granted on init ───────────────────────────────────────────

    @Test
    fun `when HC already granted on init, auto-reads data and advances to step 2`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHcManager.checkPermissionsGranted()).thenReturn(true)
            whenever(mockHcManager.readAllData()).thenReturn(fullHcResult)
        }

        val vm = buildViewModel()
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.hcConnected)
        assertEquals(80.0, vm.uiState.value.hcWeight!!, 0.001)
        assertEquals(181f, vm.uiState.value.hcHeight!!, 0.001f)
        assertEquals(18.0, vm.uiState.value.hcBodyFat!!, 0.001)
    }

    // ── permission result: granted ───────────────────────────────────────────

    @Test
    fun `onHcPermissionResult granted, reads data and advances to step 2`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHcManager.checkPermissionsGranted()).thenReturn(false)
            whenever(mockHcManager.readAllData()).thenReturn(fullHcResult)
        }

        val vm = buildViewModel()
        runCurrent()

        // Simulate permission dialog returning all permissions
        runBlocking { whenever(mockHcManager.checkPermissionsGranted()).thenReturn(true) }
        vm.onHcPermissionResult(HealthConnectManager.ALL_PERMISSIONS)
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.hcConnected)
        assertFalse(vm.uiState.value.hcPermissionDenied)
    }

    // ── permission result: denied ────────────────────────────────────────────

    @Test
    fun `onHcPermissionResult denied, sets hcPermissionDenied and stays on step 1`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking { whenever(mockHcManager.checkPermissionsGranted()).thenReturn(false) }

        val vm = buildViewModel()
        runCurrent()

        vm.onHcPermissionResult(emptySet())
        runCurrent()

        assertEquals(1, vm.uiState.value.currentStep)
        assertTrue(vm.uiState.value.hcPermissionDenied)
        assertFalse(vm.uiState.value.hcConnected)
    }

    // ── HC data nulls ────────────────────────────────────────────────────────

    @Test
    fun `when HC granted but no data, hcWeight-height-bodyFat remain null`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking {
            whenever(mockHcManager.checkPermissionsGranted()).thenReturn(false)
            whenever(mockHcManager.readAllData()).thenReturn(emptyHcResult)
        }

        val vm = buildViewModel()
        runCurrent()

        runBlocking { whenever(mockHcManager.checkPermissionsGranted()).thenReturn(true) }
        vm.onHcPermissionResult(HealthConnectManager.ALL_PERMISSIONS)
        runCurrent()

        assertTrue(vm.uiState.value.hcConnected)
        assertNull(vm.uiState.value.hcWeight)
        assertNull(vm.uiState.value.hcHeight)
        assertNull(vm.uiState.value.hcBodyFat)
    }

    // ── skipHc ───────────────────────────────────────────────────────────────

    @Test
    fun `skipHc advances to step 2 without HC data`() = runTest(testDispatcher) {
        whenever(mockHcManager.isAvailable()).thenReturn(true)
        runBlocking { whenever(mockHcManager.checkPermissionsGranted()).thenReturn(false) }

        val vm = buildViewModel()
        runCurrent()
        vm.skipHc()
        runCurrent()

        assertEquals(2, vm.uiState.value.currentStep)
        assertFalse(vm.uiState.value.hcConnected)
    }
}
