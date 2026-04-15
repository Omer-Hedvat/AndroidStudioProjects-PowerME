package com.powerme.app.ui.profile

import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.User
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.repository.HealthHistoryRepository
import com.powerme.app.data.repository.MetricLogRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for the Personal Info slice of ProfileViewModel.
 *
 * Covers: load from User entity, individual field updates, save flow, toggle training target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelPersonalInfoTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockMetricLogRepository: MetricLogRepository
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockUserSessionManager: UserSessionManager
    private lateinit var mockHealthHistoryRepository: HealthHistoryRepository

    private val sampleUser = User(
        email = "test@example.com",
        name = "Alice",
        dateOfBirth = 631152000000L, // 1990-01-01 UTC
        averageSleepHours = 7.5f,
        parentalLoad = 2,
        gender = "FEMALE",
        occupationType = "ACTIVE",
        chronotype = "MORNING",
        trainingTargets = "Hypertrophy,Strength"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockMetricLogRepository = mock()
        mockAppSettingsDataStore = mock()
        mockUserSessionManager = mock()
        mockHealthHistoryRepository = mock()

        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        whenever(mockMetricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(flowOf(emptyList()))
        whenever(mockMetricLogRepository.getByType(MetricType.BODY_FAT)).thenReturn(flowOf(emptyList()))
        whenever(mockHealthHistoryRepository.getActiveEntries()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): ProfileViewModel = ProfileViewModel(
        userSessionManager = mockUserSessionManager,
        metricLogRepository = mockMetricLogRepository,
        appSettingsDataStore = mockAppSettingsDataStore,
        healthHistoryRepository = mockHealthHistoryRepository
    )

    // ── Load ──────────────────────────────────────────────────────────────────

    @Test
    fun `loadPersonalInfo populates state from User entity`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser) }
        val vm = buildViewModel()
        runCurrent()

        assertEquals("Alice", vm.uiState.value.nameInput)
        assertEquals(631152000000L, vm.uiState.value.dateOfBirth)
        assertEquals("7.5", vm.uiState.value.averageSleepHoursInput)
        assertEquals("2", vm.uiState.value.parentalLoadInput)
        assertEquals("FEMALE", vm.uiState.value.gender)
        assertEquals("ACTIVE", vm.uiState.value.occupationType)
        assertEquals("MORNING", vm.uiState.value.chronotype)
        assertEquals(setOf("Hypertrophy", "Strength"), vm.uiState.value.selectedTrainingTargets)
    }

    @Test
    fun `loadPersonalInfo with null user leaves defaults`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(null) }
        val vm = buildViewModel()
        runCurrent()

        assertEquals("", vm.uiState.value.nameInput)
        assertNull(vm.uiState.value.dateOfBirth)
        assertEquals("", vm.uiState.value.averageSleepHoursInput)
        assertTrue(vm.uiState.value.selectedTrainingTargets.isEmpty())
    }

    // ── Field updates ─────────────────────────────────────────────────────────

    @Test
    fun `updateNameInput updates nameInput state`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(null) }
        val vm = buildViewModel()
        runCurrent()

        vm.updateNameInput("Bob")
        assertEquals("Bob", vm.uiState.value.nameInput)
    }

    @Test
    fun `updateGender toggles off when same value selected`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser) }
        val vm = buildViewModel()
        runCurrent()

        // sampleUser has gender = FEMALE — selecting again should clear it
        vm.updateGender("FEMALE")
        assertEquals("", vm.uiState.value.gender)
    }

    @Test
    fun `toggleTrainingTarget adds and removes targets`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser) }
        val vm = buildViewModel()
        runCurrent()

        // Add Cardio
        vm.toggleTrainingTarget("Cardio")
        assertTrue("Cardio" in vm.uiState.value.selectedTrainingTargets)

        // Remove Strength (already selected)
        vm.toggleTrainingTarget("Strength")
        assertTrue("Strength" !in vm.uiState.value.selectedTrainingTargets)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Test
    fun `savePersonalInfo calls saveUser with updated fields`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser) }
        val vm = buildViewModel()
        runCurrent()

        vm.updateNameInput("Charlie")
        vm.updateOccupationType("SEDENTARY")
        vm.savePersonalInfo()
        runCurrent()

        val captor = argumentCaptor<User>()
        verify(mockUserSessionManager).saveUser(captor.capture())
        assertEquals("Charlie", captor.firstValue.name)
        assertEquals("SEDENTARY", captor.firstValue.occupationType)
        assertEquals("Saved", vm.uiState.value.personalInfoSaveMessage)
    }

    @Test
    fun `savePersonalInfo no-ops when getCurrentUser returns null`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(null) }
        val vm = buildViewModel()
        runCurrent()

        vm.savePersonalInfo()
        runCurrent()

        verify(mockUserSessionManager, never()).saveUser(any())
    }

    @Test
    fun `dismissPersonalInfoSaveMessage clears message`() = runTest(testDispatcher) {
        runBlocking { whenever(mockUserSessionManager.getCurrentUser()).thenReturn(sampleUser) }
        val vm = buildViewModel()
        runCurrent()

        vm.savePersonalInfo()
        runCurrent()

        vm.dismissPersonalInfoSaveMessage()
        assertNull(vm.uiState.value.personalInfoSaveMessage)
    }
}
