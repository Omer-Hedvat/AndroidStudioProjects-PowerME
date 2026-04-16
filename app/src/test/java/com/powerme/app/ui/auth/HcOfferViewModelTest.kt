package com.powerme.app.ui.auth

import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.health.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HcOfferViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockHealthConnectManager: HealthConnectManager
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockHealthConnectManager = mock()
        mockAppSettingsDataStore = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        HcOfferViewModel(mockHealthConnectManager, mockAppSettingsDataStore)

    // ── HC not available: isDone immediately ─────────────────────────────────

    @Test
    fun `init - HC not available - isDone true immediately`() {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(false)

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isDone)
        assertFalse(viewModel.uiState.value.hcAvailable)
    }

    // ── HC available: isDone false on init ────────────────────────────────────

    @Test
    fun `init - HC available - isDone false`() {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)

        val viewModel = createViewModel()

        assertFalse(viewModel.uiState.value.isDone)
        assertTrue(viewModel.uiState.value.hcAvailable)
    }

    // ── skipHc: dismisses and sets isDone ─────────────────────────────────────

    @Test
    fun `skipHc - sets hcOfferDismissed and isDone true`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        whenever(mockAppSettingsDataStore.setHcOfferDismissed(true)).thenAnswer { }

        val viewModel = createViewModel()
        viewModel.skipHc()
        runCurrent()

        assertTrue(viewModel.uiState.value.isDone)
        verify(mockAppSettingsDataStore).setHcOfferDismissed(true)
    }

    // ── Permission granted: sets isDone ──────────────────────────────────────

    @Test
    fun `onHcPermissionResult - all granted - isDone true, dismisses offer`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(true)
        whenever(mockAppSettingsDataStore.setHcOfferDismissed(true)).thenAnswer { }

        val viewModel = createViewModel()
        viewModel.onHcPermissionResult(emptySet())
        runCurrent()

        assertTrue(viewModel.uiState.value.isDone)
        verify(mockAppSettingsDataStore).setHcOfferDismissed(true)
    }

    // ── Permission denied: sets hcPermissionDenied, stays on screen ──────────

    @Test
    fun `onHcPermissionResult - not all granted - hcPermissionDenied true, isDone false`() = runTest(testDispatcher) {
        whenever(mockHealthConnectManager.isAvailable()).thenReturn(true)
        whenever(mockHealthConnectManager.checkPermissionsGranted()).thenReturn(false)

        val viewModel = createViewModel()
        viewModel.onHcPermissionResult(emptySet())
        runCurrent()

        assertTrue(viewModel.uiState.value.hcPermissionDenied)
        assertFalse(viewModel.uiState.value.isDone)
    }
}
